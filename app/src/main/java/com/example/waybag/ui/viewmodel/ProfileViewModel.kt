package com.example.waybag.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.UserProfile
import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileState(
    val userProfile: UserProfile? = null,
    val name: String = "",
    val birthday: String = "",
    val email: String = "",
    val currentPassword: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val updateSuccess: Boolean = false
)

class ProfileViewModel : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        fetchProfile()
    }

    private fun fetchProfile() {
        val user = supabase.auth.currentUserOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val profile = supabase.from("profiles").select {
                    filter { eq("id", user.id) }
                }.decodeSingleOrNull<UserProfile>()
                
                Log.d("ProfileVM", "Fetched Profile: $profile (UID: ${user.id})")
                
                profile?.let { p ->
                    _state.update { 
                        it.copy(
                            userProfile = p,
                            name = p.name,
                            birthday = p.birthday ?: "",
                            email = p.email,
                            isLoading = false
                        )
                    }
                } ?: run {
                    _state.update { it.copy(isLoading = false, error = "No profile found for this user ID") }
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Fetch error", e)
                _state.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun onNameChange(newName: String) { _state.update { it.copy(name = newName) } }
    fun onBirthdayChange(newBirthday: String) { _state.update { it.copy(birthday = newBirthday) } }
    fun onEmailChange(newEmail: String) { _state.update { it.copy(email = newEmail) } }
    fun onCurrentPasswordChange(pwd: String) { _state.update { it.copy(currentPassword = pwd) } }
    fun onPasswordChange(newPassword: String) { _state.update { it.copy(password = newPassword) } }

    fun uploadAvatar(byteArray: ByteArray) {
        val user = supabase.auth.currentUserOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val fileName = "avatar_${user.id}_${System.currentTimeMillis()}.jpg"
                val bucket = supabase.storage.from("avatars")
                
                Log.d("ProfileVM", "Uploading to avatars/$fileName")
                // Perform upload
                bucket.upload(fileName, byteArray) { upsert = true }
                
                val publicUrl = bucket.publicUrl(fileName)
                Log.d("ProfileVM", "Upload Success. Public URL: $publicUrl")
                
                // Update profile table and wait for response
                supabase.from("profiles").update(mapOf("avatar_url" to publicUrl)) {
                    filter { eq("id", user.id) }
                }
                
                _state.update { it.copy(updateSuccess = true) }
                fetchProfile()
            } catch (e: Exception) {
                Log.e("ProfileVM", "Upload/Update failed", e)
                _state.update { it.copy(isLoading = false, error = "Image update failed: ${e.localizedMessage}") }
            }
        }
    }

    fun saveProfile() {
        val user = supabase.auth.currentUserOrNull() ?: return
        val currentState = _state.value
        
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, updateSuccess = false) }
            try {
                // 1. Update Profile Fields
                val updates = mutableMapOf<String, String>()
                if (currentState.name != currentState.userProfile?.name) updates["name"] = currentState.name
                if (currentState.birthday != currentState.userProfile?.birthday) updates["birthday"] = currentState.birthday
                
                if (updates.isNotEmpty()) {
                    Log.d("ProfileVM", "Saving updates: $updates to UID: ${user.id}")
                    supabase.from("profiles").update(updates) {
                        filter { eq("id", user.id) }
                    }
                }

                // 2. Update Auth (Email/Password)
                if (currentState.email != currentState.userProfile?.email || currentState.password.isNotEmpty()) {
                    Log.d("ProfileVM", "Updating Auth settings...")
                    
                    // IF password is being changed, we MUST verify current password first
                    if (currentState.password.isNotEmpty()) {
                        if (currentState.currentPassword.isBlank()) {
                            _state.update { it.copy(isLoading = false, error = "Please enter current password to set a new one") }
                            return@launch
                        }
                        
                        try {
                            // Re-auth check
                            supabase.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                                this.email = currentState.userProfile?.email ?: ""
                                this.password = currentState.currentPassword
                            }
                        } catch (e: Exception) {
                            _state.update { it.copy(isLoading = false, error = "Current password error") }
                            return@launch
                        }
                    }

                    supabase.auth.updateUser {
                        if (currentState.email != currentState.userProfile?.email) email = currentState.email
                        if (currentState.password.isNotEmpty()) password = currentState.password
                    }
                }

                _state.update { it.copy(isLoading = false, updateSuccess = true, password = "", currentPassword = "") }
                
                // Important: Small delay before re-fetching to let Supabase catch up
                kotlinx.coroutines.delay(1000)
                fetchProfile()
                
            } catch (e: Exception) {
                Log.e("ProfileVM", "Save failed", e)
                _state.update { it.copy(isLoading = false, error = "Update failed: ${e.localizedMessage}") }
            }
        }
    }
    
    fun resetUpdateSuccess() { _state.update { it.copy(updateSuccess = false) } }
}
