package com.example.waybag.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.BuildConfig
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.supabase
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class ReceiptAnalysis(
    val storeName: String = "",
    val timestamp: String = "", // ISO format
    val totalAmount: Double = 0.0,
    val hasPlasticBag: Boolean = false,
    val items: List<String> = emptyList(),
    val confidence: Float = 0f
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

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

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
            } catch (e: Exception) {
                Log.e("AITracker", "Failed to fetch pending scans", e)
            }
        }
    }

    fun selectScan(scan: BagScan) {
        _state.update { it.copy(selectedScan = scan, analysisResult = null, validationMessage = null) }
    }

    fun analyzeReceipt(bitmap: Bitmap) {
        val selectedScan = _state.value.selectedScan ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val prompt = """
                    Analyze this retail receipt image. 
                    Extract the following information in JSON format:
                    - storeName: Name of the shop
                    - timestamp: The date and time on the receipt in ISO 8601 format (YYYY-MM-DDTHH:MM:SS)
                    - totalAmount: Total paid amount as a number
                    - hasPlasticBag: Boolean, true if any item like 'Plastic Bag', 'Carrier Bag', or plastic charge is found.
                    - items: List of items purchased.
                    
                    Respond ONLY with the JSON block.
                """.trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val jsonText = response.text?.replace("```json", "")?.replace("```", "")?.trim()
                
                if (jsonText != null) {
                    val analysis = Json { ignoreUnknownKeys = true }.decodeFromString<ReceiptAnalysis>(jsonText)
                    _state.update { it.copy(analysisResult = analysis) }
                    validateRules(analysis, selectedScan)
                } else {
                    _state.update { it.copy(error = "Could not parse AI response") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "AI Analysis failed: ${e.message}") }
            } finally {
                _state.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    private fun validateRules(analysis: ReceiptAnalysis, scan: BagScan) {
        // 1. Check for plastic bag
        if (analysis.hasPlasticBag) {
            _state.update { it.copy(validationMessage = "Validation Failed: Plastic bag detected on receipt.") }
            updateScanStatus(scan, "failed")
            return
        }

        // 2. Time Window Check (20 mins)
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val receiptDate = sdf.parse(analysis.timestamp)
            
            // Robust parsing helper for T_QR (reusing logic from previous fixes)
            val scanDate = parseIsoTimestamp(scan.timestamp)

            if (receiptDate != null && scanDate != null) {
                val diffMins = Math.abs(receiptDate.time - scanDate.time) / (60 * 1000)
                if (diffMins > 20) {
                    _state.update { it.copy(validationMessage = "Validation Failed: Time difference too large (${diffMins} mins). Must be within 20 mins.") }
                    updateScanStatus(scan, "failed")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("AITracker", "Time validation error", e)
        }

        // 3. Success
        _state.update { it.copy(validationMessage = "Success! Receipt validated.", isSuccess = true) }
        updateScanStatus(scan, "completed")
    }

    private fun updateScanStatus(scan: BagScan, status: String) {
        viewModelScope.launch {
            try {
                supabase.from("bag_scans").update({
                    set("status", status)
                }) {
                    filter { eq("id", scan.id ?: 0) }
                }
                fetchPendingScans() // Refresh list
            } catch (e: Exception) {
                Log.e("AITracker", "Failed to update scan status", e)
            }
        }
    }

    private fun parseIsoTimestamp(timestamp: String): Date? {
        val normalized = timestamp.replace("Z", "+0000").replace(Regex("\\.\\d+"), "")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        return try { sdf.parse(normalized) } catch (e: Exception) { null }
    }
}
