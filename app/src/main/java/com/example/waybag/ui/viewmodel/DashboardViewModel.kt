package com.example.waybag.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import com.example.waybag.data.model.UserProfile
import io.github.jan.supabase.postgrest.query.Columns

data class ChartEntry(
    val value: Float,    // Normalized 0.0 to 1.0 for UI bar size
    val quantity: Int    // Actual count to display as text
)

class DashboardViewModel : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _totalScans = MutableStateFlow(0)
    val totalScans: StateFlow<Int> = _totalScans

    private val _weeklyData = MutableStateFlow(List(7) { ChartEntry(0f, 0) })
    val weeklyData: StateFlow<List<ChartEntry>> = _weeklyData

    private val _monthlyData = MutableStateFlow(List(3) { ChartEntry(0f, 0) }) 
    val monthlyData: StateFlow<List<ChartEntry>> = _monthlyData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        refreshData()
    }

    fun refreshData() {
        val user = supabase.auth.currentUserOrNull() ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch Profile
                val profile = supabase.from("profiles").select {
                    filter { eq("id", user.id) }
                }.decodeSingleOrNull<UserProfile>()
                _userProfile.value = profile

                // Fetch Scans
                val scans = supabase.from("bag_scans").select {
                    filter {
                        eq("userId", user.id)
                        eq("status", "completed") // ONLY count fully validated scans for the Dashboard
                    }
                }.decodeList<BagScan>()

                _totalScans.value = scans.size
                processWeeklyData(scans)
                processMonthlyData(scans)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun processWeeklyData(scans: List<BagScan>) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Get last 7 days starting from Monday
        val weeklyCounts = FloatArray(7) { 0f }
        val dayMap = mapOf(
            Calendar.MONDAY to 0,
            Calendar.TUESDAY to 1,
            Calendar.WEDNESDAY to 2,
            Calendar.THURSDAY to 3,
            Calendar.FRIDAY to 4,
            Calendar.SATURDAY to 5,
            Calendar.SUNDAY to 6
        )

        scans.forEach { scan ->
            try {
                // Supabase timestamp is often ISO 8601
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(scan.timestamp.take(10))
                if (date != null) {
                    calendar.time = date
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val index = dayMap[dayOfWeek]
                    if (index != null) {
                        weeklyCounts[index] += 1f
                    }
                }
            } catch (e: Exception) {}
        }

        // Normalize for height (max 1.0)
        val max = weeklyCounts.maxOrNull() ?: 1f
        val entries = weeklyCounts.map { count ->
            ChartEntry(
                value = if (max > 0) count / max else 0f,
                quantity = count.toInt()
            )
        }
        _weeklyData.value = entries
    }

    private fun processMonthlyData(scans: List<BagScan>) {
        val monthlyCounts = FloatArray(3) { 0f } // Jan, Feb, Mar
        
        scans.forEach { scan ->
            try {
                val month = scan.timestamp.substring(5, 7).toInt() // 2024-01-20 -> 01
                if (month in 1..3) {
                    monthlyCounts[month - 1] += 1f
                }
            } catch (e: Exception) {}
        }

        // Normalize for bar width (max 1.0)
        val max = monthlyCounts.maxOrNull() ?: 1f
        val entries = monthlyCounts.map { count ->
            ChartEntry(
                value = if (max > 0) count / max else 0f,
                quantity = count.toInt()
            )
        }
        _monthlyData.value = entries
    }
}
