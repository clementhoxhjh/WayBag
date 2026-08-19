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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class ReceiptAnalysis(
    val storeName: String = "",
    val gstId: String = "", // Required for Malaysia
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
                        MALAYSIAN RECEIPT AUDIT. Identify Company Name, GST ID (or SST/Registration No), Total Amount, and Items.
                        Check if any 'Plastic Bag' item exists.
                        Return ONLY this JSON:
                        {
                          "storeName": "Name",
                          "gstId": "GST/Registration Number",
                          "timestamp": "YYYY-MM-DDTHH:MM:SS",
                          "totalAmount": 0.00,
                          "hasPlasticBag": Boolean,
                          "items": ["list", "of", "items"]
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
                        })
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val body = response.body<JsonObject>()
                        val text = body["candidates"]?.jsonArray?.get(0)?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.get(0)?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: ""
                        
                        val jsonText = text.replace("```json", "").replace("```", "").trim()
                        Json { ignoreUnknownKeys = true }.decodeFromString<ReceiptAnalysis>(jsonText)
                    } else null
                }

                if (result != null) {
                    _state.update { it.copy(analysisResult = result) }
                    validateAndSubmit(result, selectedScan)
                } else {
                    _state.update { it.copy(error = "AI failed to audit receipt. Ensure company name and total are visible.") }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(error = "Audit Error: ${t.localizedMessage}") }
            } finally {
                _state.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    private fun validateAndSubmit(analysis: ReceiptAnalysis, scan: BagScan) {
        val missingFields = mutableListOf<String>()
        if (analysis.storeName.isBlank() || analysis.storeName.length < 2) missingFields.add("Shop Name")
        if (analysis.gstId.isBlank() || analysis.gstId.lowercase() == "n/a") missingFields.add("GST/SST ID")
        if (analysis.totalAmount <= 0) missingFields.add("Total Amount")
        if (analysis.timestamp.isBlank()) missingFields.add("Receipt Time")

        // 1. Check for plastic bag (Highest priority failure)
        if (analysis.hasPlasticBag) {
            _state.update { it.copy(validationMessage = "FAILED: Single-use plastic bag charge detected.") }
            saveResultsToDatabase(scan, analysis, "failed")
            return
        }

        // 2. Check for missing data (Strict Audit)
        if (missingFields.isNotEmpty()) {
            val reason = "FAILED: Could not identify ${missingFields.joinToString(", ")}. Please ensure the receipt is flat and clear."
            _state.update { it.copy(validationMessage = reason) }
            saveResultsToDatabase(scan, analysis, "failed")
            return
        }

        // 3. Success Path (All mandatory data present + No plastic bags)
        _state.update { it.copy(validationMessage = "SUCCESS! Contribution recorded.", isSuccess = true) }
        saveResultsToDatabase(scan, analysis, "completed")
    }

    private fun saveResultsToDatabase(scan: BagScan, analysis: ReceiptAnalysis, status: String) {
        viewModelScope.launch {
            try {
                val itemsJson = Json.encodeToString(analysis.items)
                
                supabase.from("bag_scans").update({
                    set("status", status)
                    set("storeName", analysis.storeName)
                    set("gstId", analysis.gstId)
                    set("totalAmount", analysis.totalAmount)
                    set("itemsJson", itemsJson)
                }) {
                    filter { eq("id", scan.id ?: 0L) }
                }
                fetchPendingScans()
            } catch (e: Exception) {
                Log.e("AITracker", "DB Update failed", e)
            }
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
