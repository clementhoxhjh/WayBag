package com.example.waybag.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class QRScanState(
    val scannedId: String? = null,
    val scanTimeTqr: String? = null,
    val isProcessing: Boolean = false,
    val validationError: String? = null
)

class QRScanViewModel : ViewModel() {
    private val _state = MutableStateFlow(QRScanState())
    val state: StateFlow<QRScanState> = _state.asStateFlow()

    fun onScanStarted() {
        _state.update { it.copy(isProcessing = true, validationError = null) }
    }

    fun onScanSuccess(id: String, timestamp: String) {
        _state.update { it.copy(
            isProcessing = false,
            scannedId = id,
            scanTimeTqr = timestamp
        ) }
    }

    fun onScanFailed(error: String) {
        _state.update { it.copy(
            isProcessing = false,
            validationError = error
        ) }
    }

    fun reset() {
        _state.value = QRScanState()
    }

    fun clearError() {
        _state.update { it.copy(validationError = null) }
    }
}
