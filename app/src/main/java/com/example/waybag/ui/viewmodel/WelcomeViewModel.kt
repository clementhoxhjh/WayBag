package com.example.waybag.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.supabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WelcomeState(
    val isVisible: Boolean = false,
    val isCheckingAutoLogin: Boolean = true
)

class WelcomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    fun checkAutoLogin(context: Context, onAutoLogin: () -> Unit) {
        viewModelScope.launch {
            val isLocallyLoggedIn = com.example.waybag.data.isUserLoggedIn(context)
            val session = com.example.waybag.data.loadUserSession(context)
            
            if (isLocallyLoggedIn && session != null) {
                onAutoLogin()
            } else {
                _state.update { it.copy(isCheckingAutoLogin = false) }
                delay(300)
                _state.update { it.copy(isVisible = true) }
            }
        }
    }
}
