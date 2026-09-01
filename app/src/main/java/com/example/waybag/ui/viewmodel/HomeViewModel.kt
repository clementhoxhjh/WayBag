package com.example.waybag.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.waybag.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

import com.example.waybag.data.model.UserProfile
import com.example.waybag.data.CurrentUser

data class ChartEntry(
    val value: Float,    // Normalized 0.0 to 1.0 for UI bar size
    val quantity: Int    // Actual count to display as text
)

class HomeViewModel : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _totalScans = MutableStateFlow(0)
    val totalScans: StateFlow<Int> = _totalScans

    private val _weeklyData = MutableStateFlow(List(7) { ChartEntry(0f, 0) })
    val weeklyData: StateFlow<List<ChartEntry>> = _weeklyData

    private val _personalScans = MutableStateFlow<List<BagScan>>(emptyList())

    private val _monthlyData = MutableStateFlow(List(12) { ChartEntry(0f, 0) }) 
    val monthlyData: StateFlow<List<ChartEntry>> = _monthlyData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _dayOffset = MutableStateFlow(0)
    val dayOffset: StateFlow<Int> = _dayOffset

    init {
        refreshData()
    }

    fun refreshData() {
        val currentUserId = CurrentUser.userId ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch Profile using local session ID
                val profile = supabase.from("profiles").select {
                    filter { eq("profile_id", currentUserId) }
                }.decodeSingleOrNull<UserProfile>()
                Log.d("Dashboard", "Refreshed profile: $profile")
                _userProfile.value = profile

                // Fetch Personal Scans for the counter
                val personalScans = supabase.from("bag_scans").select {
                    filter {
                        eq("profile_id", currentUserId)
                        eq("status", "completed")
                    }
                }.decodeList<BagScan>()
                _totalScans.value = personalScans.size
                _personalScans.value = personalScans

                // Fetch GLOBAL Scans for the charts (Total Contribution)
                val globalScans = supabase.from("bag_scans").select {
                    filter {
                        eq("status", "completed") 
                    }
                }.decodeList<BagScan>()
                
                Log.d("Dashboard", "Total Community Scans Fetched: ${globalScans.size}")

                processWeeklyData(_dayOffset.value) // Initial processing
                processMonthlyData(globalScans)  // All users for the monthly community chart
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setDayOffset(offset: Int) {
        _dayOffset.value = offset
        processWeeklyData(offset)
    }

    private fun processWeeklyData(offset: Int) {
        val scans = _personalScans.value
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offset)
        }
        
        // Find the start of the week (Sunday) for the current offset date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        val weekStart = calendar.time
        
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val weekEnd = calendar.time

        // Sunday is 0, Saturday is 6
        val weeklyCounts = FloatArray(7) { 0f }
        
        scans.forEach { scan ->
            try {
                val date: Date? = DateUtils.parseIsoTimestamp(scan.scan_qr)
                if (date != null && !date.before(weekStart) && date.before(weekEnd)) {
                    val scanCal = Calendar.getInstance().apply { time = date }
                    val dayOfWeek = scanCal.get(Calendar.DAY_OF_WEEK)
                    val index = when (dayOfWeek) {
                        Calendar.SUNDAY -> 0
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        else -> 0
                    }
                    weeklyCounts[index] += 1f
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
        val monthlyCounts = FloatArray(12) { 0f } // Jan to Dec
        
        scans.forEach { scan ->
            try {
                val month = scan.scan_qr.substring(5, 7).toInt() // 2024-01-20 -> 01
                if (month in 1..12) {
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
