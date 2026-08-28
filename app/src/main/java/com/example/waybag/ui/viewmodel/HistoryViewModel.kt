package com.example.waybag.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.supabase
import com.example.waybag.utils.DateUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

data class HistoryState(
    val scans: List<BagScan> = emptyList(),
    val isLoading: Boolean = true,
    val selectedScanDetail: BagScan? = null
)

class HistoryViewModel : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        fetchHistory()
    }

    fun fetchHistory() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    // 1. Fetch current pending scans to check for expiration
                    val response = supabase.from("bag_scans")
                        .select {
                            filter { eq("userId", user.id) }
                            order("scanQR", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }
                    val allScans = response.decodeList<BagScan>()
                    
                    // 2. Identify expired scans (Pending > 20 mins)
                    val now = Date()
                    val expiredIds = allScans.filter { scan ->
                        if (scan.status.lowercase() == "pending") {
                            val scanDate = DateUtils.parseIsoTimestamp(scan.scanQR)
                            val isExpired = scanDate != null && (now.time - scanDate.time) > 20 * 60 * 1000
                            isExpired
                        } else false
                    }.mapNotNull { it.id }

                    // 3. Batch update expired scans in database
                    if (expiredIds.isNotEmpty()) {
                        try {
                            supabase.from("bag_scans").update({
                                set("status", "failed")
                                set("failureReason", "EXPIRED (Over 20 mins)")
                            }) {
                                filter {
                                    isIn("id", expiredIds)
                                }
                            }
                            // Re-fetch to get updated statuses for the UI
                            val refreshedResponse = supabase.from("bag_scans")
                                .select {
                                    filter { eq("userId", user.id) }
                                    order("scanQR", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                                }
                            _state.update { it.copy(scans = refreshedResponse.decodeList<BagScan>(), isLoading = false) }
                        } catch (e: Exception) {
                            Log.e("ExpirationCheck", "Update failed", e)
                            _state.update { it.copy(scans = allScans, isLoading = false) }
                        }
                    } else {
                        _state.update { it.copy(scans = allScans, isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Failed to fetch or update history", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectScan(scan: BagScan?) {
        _state.update { it.copy(selectedScanDetail = scan) }
    }
}
