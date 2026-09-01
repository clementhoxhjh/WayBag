package com.example.waybag.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.UserProfile
import com.example.waybag.data.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

class LoginViewModel : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmailChange(newValue: String) {
        _state.update { it.copy(email = newValue) }
    }

    fun onPasswordChange(newValue: String) {
        _state.update { it.copy(password = newValue) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun login(context: Context, onLoginSuccess: () -> Unit) {
        val currentState = _state.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter both email and password") }
            return
        }
        
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                // Direct query to profiles table instead of Supabase Auth
                val profile = supabase.from("profiles").select {
                    filter {
                        eq("email", currentState.email)
                        eq("password", currentState.password)
                    }
                }.decodeSingleOrNull<UserProfile>()
                
                if (profile != null) {
                    com.example.waybag.data.saveLoginStatus(context, true)
                    com.example.waybag.data.saveUserSession(
                        context,
                        com.example.waybag.data.UserSession(
                            userId = profile.profile_id,
                            email = profile.email,
                            name = profile.name
                        )
                    )
                    onLoginSuccess()
                } else {
                    // Verify if email exists to provide helpful error message
                    val emailExists = supabase.from("profiles").select {
                        filter { eq("email", currentState.email) }
                    }.decodeList<UserProfile>().isNotEmpty()
                    
                    if (emailExists) {
                        _state.update { it.copy(errorMessage = "password error") }
                    } else {
                        _state.update { it.copy(errorMessage = "email haven't sign in, please go to sign in, tq") }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.localizedMessage ?: "An unexpected error occurred") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
