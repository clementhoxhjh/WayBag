package com.example.waybag.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.UserProfile
import com.example.waybag.data.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignUpState(
    val name: String = "",
    val birthday: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val errorMessage: String? = null,
    val selectedImageUri: Uri? = null,
    val showImageSourceSheet: Boolean = false,
    val cameraImageUri: Uri? = null,
    val showDatePicker: Boolean = false
)

class SignUpViewModel : ViewModel() {
    private val _state = MutableStateFlow(SignUpState())
    val state: StateFlow<SignUpState> = _state.asStateFlow()

    fun onNameChange(newValue: String) { _state.update { it.copy(name = newValue) } }
    fun onBirthdayChange(newValue: String) { _state.update { it.copy(birthday = newValue) } }
    fun onEmailChange(newValue: String) { _state.update { it.copy(email = newValue) } }
    fun onPasswordChange(newValue: String) { _state.update { it.copy(password = newValue) } }
    fun onConfirmPasswordChange(newValue: String) { _state.update { it.copy(confirmPassword = newValue) } }
    
    fun onImageSelected(uri: Uri?) { _state.update { it.copy(selectedImageUri = uri) } }
    fun onCameraImageUriCreated(uri: Uri?) { _state.update { it.copy(cameraImageUri = uri) } }
    fun setShowImageSourceSheet(show: Boolean) { _state.update { it.copy(showImageSourceSheet = show) } }
    
    fun clearError() { _state.update { it.copy(errorMessage = null) } }
    fun setShowSuccessDialog(show: Boolean) { _state.update { it.copy(showSuccessDialog = show) } }
    fun setShowDatePicker(show: Boolean) { _state.update { it.copy(showDatePicker = show) } }

    fun signUp(context: Context) {
        val currentState = _state.value
        if (currentState.password != currentState.confirmPassword) {
            _state.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }
        if (currentState.email.isBlank() || currentState.password.isBlank() || currentState.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Please fill all fields") }
            return
        }
        
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                // 1. Verify if email already exists in the profiles table
                val existingUsers = supabase.from("profiles").select {
                    filter { eq("email", currentState.email) }
                }.decodeList<UserProfile>()
                
                if (existingUsers.isNotEmpty()) {
                    _state.update { it.copy(errorMessage = "email is already signed in", isLoading = false) }
                    return@launch
                }

                // 2. Generate a unique profile ID (manual UUID)
                val userId = java.util.UUID.randomUUID().toString()
                
                var avatarUrl: String? = null
                currentState.selectedImageUri?.let { uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes != null) {
                        val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"
                        val bucket = supabase.storage.from("avatars")
                        bucket.upload(fileName, bytes)
                        avatarUrl = bucket.publicUrl(fileName)
                    }
                }

                // 3. Create the profile record directly in the database
                val profile = UserProfile(
                    profile_id = userId,
                    name = currentState.name,
                    birthday = currentState.birthday,
                    email = currentState.email,
                    password = currentState.password,
                    avatar_url = avatarUrl
                )
                supabase.from("profiles").insert(profile)
                
                // 4. Save session locally
                com.example.waybag.data.saveLoginStatus(context, true)
                com.example.waybag.data.saveUserSession(
                    context,
                    com.example.waybag.data.UserSession(
                        userId = userId,
                        email = currentState.email,
                        name = currentState.name
                    )
                )
                
                _state.update { it.copy(showSuccessDialog = true) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "An unexpected error occurred") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
