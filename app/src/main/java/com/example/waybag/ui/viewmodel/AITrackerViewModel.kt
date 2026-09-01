package com.example.waybag.ui.viewmodel

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.BuildConfig
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.supabase
import com.example.waybag.data.CurrentUser
import com.example.waybag.utils.DateUtils
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class ReceiptAnalysis(
    val storeName: String = "",
    val regNo: String = "", 
    val timestamp: String = "",
    val totalAmount: String = "",
    val hasPlasticBag: String = "",
    val items: List<JsonElement> = emptyList()
)

data class AITrackerState(
    val pendingScans: List<BagScan> = emptyList(),
    val selectedScan: BagScan? = null,
    val isAnalyzing: Boolean = false,
    val analysisResult: ReceiptAnalysis? = null,
    val validationMessage: String? = null,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false // Added for pull-to-refresh
)

class AITrackerViewModel : ViewModel() {

    private val _state = MutableStateFlow(AITrackerState())
    val state: StateFlow<AITrackerState> = _state.asStateFlow()

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                isLenient = true // Very important: handles loose JSON formatting
                coerceInputValues = true // Converts types where possible
            })
        }
    }

    init {
        fetchPendingScans()
    }

    fun fetchPendingScans() {
        val currentUserId = CurrentUser.userId ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try {
                // 1. Fetch current pending scans
                val allPending = supabase.from("bag_scans").select {
                    filter {
                        eq("profile_id", currentUserId)
                        eq("status", "pending")
                    }
                }.decodeList<BagScan>()

                // 2. Identify expired scans (Pending > 20 mins)
                val now = Date()
                val expiredIds = allPending.filter { scan ->
                    val scanDate = DateUtils.parseIsoTimestamp(scan.scan_qr)
                    val isExpired = scanDate != null && (now.time - scanDate.time) > 20 * 60 * 1000
                    isExpired
                }.mapNotNull { it.scan_id }

                // 3. Batch update expired scans in database
                if (expiredIds.isNotEmpty()) {
                    try {
                        supabase.from("bag_scans").update({
                            set("status", "failed")
                            set("failure_reason", "EXPIRED (Over 20 mins)")
                        }) {
                            filter {
                                isIn("scan_id", expiredIds)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AITracker", "Expiration update failed", e)
                    }
                }

                // 4. Update state with only non-expired pending scans
                val validPending = allPending.filterNot { scan -> 
                    expiredIds.contains(scan.scan_id)
                }
                _state.update { it.copy(pendingScans = validPending, isRefreshing = false) }
            } catch (t: Throwable) {
                Log.e("AITracker", "Fetch failed", t)
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun selectScan(scan: BagScan?) {
        _state.update { it.copy(
            selectedScan = scan, 
            analysisResult = null, 
            validationMessage = null, 
            error = null,
            isSuccess = false
        ) }
    }

    fun analyzeReceipt(bitmap: Bitmap) {
        val selectedScan = _state.value.selectedScan ?: return
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true, error = null, isSuccess = false, analysisResult = null) }
            try {
                val availableModels = withContext(Dispatchers.IO) {
                    try {
                        val response = httpClient.get("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                        val body = response.body<JsonObject>()
                        body["models"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()
                    } catch (e: Exception) { emptyList() }
                }

                val targetModel = availableModels.find { it.contains("gemini-1.5-flash") } ?: availableModels.firstOrNull() ?: "models/gemini-1.5-flash"

                val result = withContext(Dispatchers.IO) {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                    val prompt = """
                        SYSTEM: MALAYSIAN RETAIL AUDIT EXPERT.
                        TASK: Extract EXACT data from the receipt image.
                        
                        CRITICAL INSTRUCTIONS:
                        - DO NOT GUESS. If time is missing, return "MISSING".
                        - timestamp: Search very carefully for date/time. Combine with HH:MM or HH:MM:SS.
                        - regNo: EXTRACT THE FULL REGISTRATION NUMBER. 
                          Example: "202401012345 (1309876-V)". MUST INCLUDE THE PARENTHESES AND ALL CHARACTERS.
                        
                        OUTPUT ONLY RAW JSON WITH QUOTES FOR ALL VALUES:
                        { 
                          "storeName": "Name", 
                          "regNo": "Number", 
                          "timestamp": "ISO_TIME", 
                          "totalAmount": "Amount", 
                          "hasPlasticBag": "true or false", 
                          "items": ["Item 1 Name", "Item 2 Name"] 
                        }
                    """.trimIndent()

                    Log.d("AITracker", ">>> SENDING REQUEST TO GEMINI...")
                    // Using the exact model name from your SDK snippet
                    // We use v1beta as 'lite' versions are often in beta/preview
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=$apiKey"
                    val response = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            putJsonArray("contents") {
                                addJsonObject {
                                    putJsonArray("parts") {
                                        addJsonObject { put("text", prompt) }
                                        addJsonObject {
                                            putJsonObject("inline_data") {
                                                put("mime_type", "image/jpeg")
                                                put("data", base64Image)
                                            }
                                        }
                                    }
                                }
                            }
                            putJsonObject("generationConfig") {
                                put("temperature", 0.1) 
                                put("maxOutputTokens", 1024)
                            }
                        })
                    }

                    Log.d("AITracker", ">>> RESPONSE STATUS: ${response.status}")

                    if (response.status == HttpStatusCode.OK) {
                        val body = response.body<JsonObject>()
                        val rawText = body["candidates"]?.jsonArray?.get(0)?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.get(0)?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: ""
                        
                        Log.d("AITracker", ">>> AI RAW REPLY: $rawText")

                        val jsonStartIndex = rawText.indexOf("{")
                        val jsonEndIndex = rawText.lastIndexOf("}")
                        
                        if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
                            val cleanJson = rawText.substring(jsonStartIndex, jsonEndIndex + 1)
                            Log.d("AITracker", ">>> EXTRACTED JSON: $cleanJson")
                            Json { ignoreUnknownKeys = true }.decodeFromString<ReceiptAnalysis>(cleanJson)
                        } else {
                            Log.e("AITracker", ">>> ERROR: JSON NOT FOUND IN REPLY")
                            null
                        }
                    } else {
                        val errCode = response.status.value
                        Log.e("AITracker", ">>> HTTP ERROR: $errCode")
                        if (errCode == 429) {
                            _state.update { it.copy(error = "Server busy") } // Exactly as requested
                        } else {
                            _state.update { it.copy(error = "AI Server Error ($errCode)") }
                        }
                        null
                    }
                }

                if (result != null) {
                    _state.update { it.copy(analysisResult = result) }
                    
                    // Upload the receipt image
                    val receiptUrl = withContext(Dispatchers.IO) {
                        try {
                            val fileName = "receipt_${selectedScan.scan_id}_${System.currentTimeMillis()}.jpg"
                            val bucket = supabase.storage.from("receipts")
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                            bucket.upload(fileName, outputStream.toByteArray()) { upsert = true }
                            bucket.publicUrl(fileName)
                        } catch (e: Exception) {
                            Log.e("AITracker", "Receipt upload failed", e)
                            null
                        }
                    }
                    
                    validateAndSubmit(result, selectedScan, receiptUrl)
                } else {
                    _state.update { it.copy(error = "AI failed to audit receipt. Ensure info is visible.") }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(error = "Audit Error: ${t.localizedMessage}") }
            } finally {
                _state.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    private fun validateAndSubmit(analysis: ReceiptAnalysis, scan: BagScan, receiptImageUrl: String? = null) {
        viewModelScope.launch {
            Log.d("AITracker", "Validating: $analysis")

            val missingFields = mutableListOf<String>()
            if (analysis.storeName.isBlank() || analysis.storeName.length < 2) missingFields.add("Shop Name")
            if (analysis.regNo.isBlank() || analysis.regNo.lowercase().contains("not found")) missingFields.add("Registration Number")
            
            val amountValue = analysis.totalAmount.toDoubleOrNull() ?: 0.0
            if (amountValue <= 0) missingFields.add("Total Amount")

            if (analysis.timestamp.isBlank() || analysis.timestamp.contains("MISSING", ignoreCase = true)) {
                missingFields.add("Receipt Time")
            }

            // 1. Check for Duplicate Receipt
            try {
                val duplicateCount = supabase.from("bag_scans").select {
                    filter {
                        eq("reg_no", analysis.regNo)
                        eq("total_amount", amountValue)
                        eq("status", "completed")
                    }
                }.decodeList<BagScan>().size

                if (duplicateCount > 0) {
                    val errorMsg = "FAILED: This receipt (RegNo: ${analysis.regNo}) has already been recorded."
                    _state.update { it.copy(validationMessage = errorMsg, isSuccess = false) }
                    
                    // Record to DB as failed right away
                    saveResultsToDatabaseInScope(scan, analysis, "failed", receiptImageUrl)
                    
                    // Automatically clear selection after a short delay so user sees the error
                    delay(3000)
                    selectScan(null)
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("AITracker", "Duplicate check error", e)
            }

            // 2. Check for plastic bag
            val plasticBagFound = analysis.hasPlasticBag.toString().lowercase().let { 
                it == "true" || it == "yes" 
            }
            if (plasticBagFound) {
                _state.update { it.copy(validationMessage = "FAILED: Plastic bag charge detected.") }
                saveResultsToDatabaseInScope(scan, analysis, "failed", receiptImageUrl)
                return@launch
            }

            // 3. Check for missing data
            if (missingFields.isNotEmpty()) {
                val reason = "FAILED: Missing information (${missingFields.joinToString(", ")}). Please ensure the receipt is clear, not blurry, and contains full details."
                _state.update { it.copy(validationMessage = reason) }
                saveResultsToDatabaseInScope(scan, analysis, "failed", receiptImageUrl)
                return@launch
            }

            // 4. Time Window Check (Production Logic: 20 mins)
            try {
                val receiptDate = parseSimpleIso(analysis.timestamp)
                val scanDate = DateUtils.parseIsoTimestamp(scan.scan_qr)

                if (receiptDate != null && scanDate != null) {
                    val diffMillis = Math.abs(receiptDate.time - scanDate.time)
                    val diffMins = diffMillis / (60 * 1000)
                    if (diffMins > 20) {
                        _state.update { it.copy(validationMessage = "FAILED: EXCEEDED 20-min window (Gap: ${diffMins}m).") }
                        saveResultsToDatabaseInScope(scan, analysis, "failed", receiptImageUrl)
                        return@launch
                    }
                }
            } catch (e: Exception) {}

            // Success Path
            _state.update { it.copy(validationMessage = "SUCCESS! Impact recorded.", isSuccess = true) }
            saveResultsToDatabaseInScope(scan, analysis, "completed", receiptImageUrl)
        }
    }

    private suspend fun saveResultsToDatabaseInScope(scan: BagScan, analysis: ReceiptAnalysis, status: String, receiptImageUrl: String? = null) {
        try {
            val itemsJson = Json.encodeToString(analysis.items)
            val reason = if (status == "failed") _state.value.validationMessage else null
            val amountValue = analysis.totalAmount.toDoubleOrNull() ?: 0.0

            supabase.from("bag_scans").update({
                set("status", status)
                set("store_name", analysis.storeName)
                set("reg_no", analysis.regNo)
                set("total_amount", amountValue)
                set("items_json", itemsJson)
                set("receipt_timestamp", analysis.timestamp) // Save the receipt time
                set("receipt_image_url", receiptImageUrl) // Save the image URL
                set("failure_reason", reason) // Save why it failed
            }) {
                filter { eq("scan_id", scan.scan_id ?: 0L) }
            }
            fetchPendingScans()
        } catch (e: Exception) {
            Log.e("AITracker", "DB update error", e)
        }
    }

    private fun parseSimpleIso(timestamp: String): Date? {
        val cleaned = timestamp.trim().replace("T", " ") // Handle ISO 'T' separator
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )
        for (fmt in formats) {
            try { 
                val sdf = SimpleDateFormat(fmt, Locale.US).apply { 
                    // Force Malaysia time for parsing receipt times
                    timeZone = TimeZone.getDefault()
                    isLenient = false 
                }
                val date = sdf.parse(cleaned)
                if (date != null) {
                    Log.d("AITracker", "Successfully parsed $timestamp as $date using format $fmt")
                    return date
                }
            } catch (e: Exception) {}
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
