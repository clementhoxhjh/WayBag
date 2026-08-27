package com.example.waybag.ui.viewmodel

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.BuildConfig
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.supabase
import com.example.waybag.utils.DateUtils
import io.github.jan.supabase.auth.auth
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
    val totalAmount: Double = 0.0,
    val hasPlasticBag: Boolean = false,
    val items: List<String> = emptyList()
)

data class AITrackerState(
    val pendingScans: List<BagScan> = emptyList(),
    val selectedScan: BagScan? = null,
    val isAnalyzing: Boolean = false,
    val analysisResult: ReceiptAnalysis? = null,
    val validationMessage: String? = null,
    val isSuccess: Boolean = false,
    val error: String? = null
)

class AITrackerViewModel : ViewModel() {

    private val _state = MutableStateFlow(AITrackerState())
    val state: StateFlow<AITrackerState> = _state.asStateFlow()

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    init {
        fetchPendingScans()
    }

    fun fetchPendingScans() {
        val user = supabase.auth.currentUserOrNull() ?: return
        viewModelScope.launch {
            try {
                val scans = supabase.from("bag_scans").select {
                    filter {
                        eq("userId", user.id)
                        eq("status", "pending")
                    }
                }.decodeList<BagScan>()
                _state.update { it.copy(pendingScans = scans) }
            } catch (t: Throwable) {
                Log.e("AITracker", "Fetch failed", t)
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
                        
                        OUTPUT ONLY JSON:
                        { 
                          "storeName": "Exact Shop Name", 
                          "regNo": "FULL_NUMBER_WITH_PARENTHESES", 
                          "timestamp": "ISO_TIMESTAMP_OR_MISSING", 
                          "totalAmount": 0.00, 
                          "hasPlasticBag": Boolean, 
                          "items": ["Item1", "Item2"] 
                        }
                    """.trimIndent()

                    val url = "https://generativelanguage.googleapis.com/v1beta/$targetModel:generateContent?key=$apiKey"
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
                                put("temperature", 0.0) // Absolute consistency
                                put("topP", 1.0)
                                put("maxOutputTokens", 2048)
                            }
                        })
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val body = response.body<JsonObject>()
                        val rawText = body["candidates"]?.jsonArray?.get(0)?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.get(0)?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: ""
                        
                        val jsonStartIndex = rawText.indexOf("{")
                        val jsonEndIndex = rawText.lastIndexOf("}")
                        
                        if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
                            val cleanJson = rawText.substring(jsonStartIndex, jsonEndIndex + 1)
                            Json { ignoreUnknownKeys = true }.decodeFromString<ReceiptAnalysis>(cleanJson)
                        } else null
                    } else null
                }

                if (result != null) {
                    _state.update { it.copy(analysisResult = result) }
                    
                    // Upload the receipt image
                    val receiptUrl = withContext(Dispatchers.IO) {
                        try {
                            val fileName = "receipt_${selectedScan.id}_${System.currentTimeMillis()}.jpg"
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
            if (analysis.totalAmount <= 0) missingFields.add("Total Amount")
            if (analysis.timestamp.isBlank() || analysis.timestamp.contains("MISSING", ignoreCase = true)) {
                missingFields.add("Receipt Time")
            }

            // 1. Check for Duplicate Receipt (Strictly by regNo as requested)
            try {
                val duplicateCount = supabase.from("bag_scans").select {
                    filter {
                        eq("regNo", analysis.regNo)
                        eq("status", "completed")
                    }
                }.decodeList<BagScan>().size

                if (duplicateCount > 0) {
                    _state.update { it.copy(validationMessage = "FAILED: This receipt (RegNo: ${analysis.regNo}) has already been recorded.") }
                    saveResultsToDatabaseInScope(scan, analysis, "failed", receiptImageUrl)
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("AITracker", "Duplicate check error", e)
            }

            // 2. Check for plastic bag
            if (analysis.hasPlasticBag) {
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
                val scanDate = DateUtils.parseIsoTimestamp(scan.scanQR)

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
            supabase.from("bag_scans").update({
                set("status", status)
                set("storeName", analysis.storeName)
                set("regNo", analysis.regNo)
                set("totalAmount", analysis.totalAmount)
                set("itemsJson", itemsJson)
                set("receiptTimestamp", analysis.timestamp) // Save the receipt time
                set("receiptImageUrl", receiptImageUrl) // Save the image URL
            }) {
                filter { eq("id", scan.id ?: 0L) }
            }
            fetchPendingScans()
        } catch (e: Exception) {
            Log.e("AITracker", "DB update error", e)
        }
    }

    private fun parseSimpleIso(timestamp: String): Date? {
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd")
        for (fmt in formats) {
            try { return SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getDefault() }.parse(timestamp) } catch (e: Exception) {}
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
