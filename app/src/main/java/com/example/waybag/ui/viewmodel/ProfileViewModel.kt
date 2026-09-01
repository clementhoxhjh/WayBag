package com.example.waybag.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.UserProfile
import com.example.waybag.data.supabase
import com.example.waybag.data.CurrentUser
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val userProfile: UserProfile? = null,
    val name: String = "",
    val birthday: String = "",
    val email: String = "",
    val currentPassword: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val updateSuccess: Boolean = false,
    val showDatePicker: Boolean = false
)

class ProfileViewModel : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        fetchProfile()
    }

    private fun fetchProfile() {
        val currentUserId = CurrentUser.userId ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Fetch profile using local session ID
                val profile = supabase.from("profiles").select {
                    filter { eq("profile_id", currentUserId) }
                }.decodeSingleOrNull<UserProfile>()
                
                Log.d("ProfileVM", "Fetched Profile: $profile (UID: $currentUserId)")
                
                profile?.let { p ->
                    _state.update { 
                        it.copy(
                            userProfile = p,
                            name = p.name,
                            birthday = p.birthday,
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
    fun setShowDatePicker(show: Boolean) { _state.update { it.copy(showDatePicker = show) } }

    fun uploadAvatar(byteArray: ByteArray) {
        val currentUserId = CurrentUser.userId ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val fileName = "avatar_${currentUserId}_${System.currentTimeMillis()}.jpg"
                val bucket = supabase.storage.from("avatars")
                
                Log.d("ProfileVM", "Uploading to avatars/$fileName")
                bucket.upload(fileName, byteArray) { upsert = true }
                
                val publicUrl = bucket.publicUrl(fileName)
                Log.d("ProfileVM", "Upload Success. Public URL: $publicUrl")
                
                // Update profile table using direct Postgrest update
                supabase.from("profiles").update(mapOf("avatar_url" to publicUrl)) {
                    filter { eq("profile_id", currentUserId) }
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
        val currentUserId = CurrentUser.userId ?: return
        val currentState = _state.value
        
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, updateSuccess = false) }
            try {
                // Verify current password manually from the fetched profile record
                if (currentState.password.isNotEmpty() || currentState.email != currentState.userProfile?.email) {
                    if (currentState.currentPassword != currentState.userProfile?.password) {
                        _state.update { it.copy(isLoading = false, error = "Current password incorrect") }
                        return@launch
                    }
                }

                // Update Profile Fields directly in the database
                val updates = mutableMapOf<String, String>()
                updates["name"] = currentState.name
                updates["birthday"] = currentState.birthday
                updates["email"] = currentState.email
                if (currentState.password.isNotEmpty()) {
                    updates["password"] = currentState.password
                }
                
                if (updates.isNotEmpty()) {
                    Log.d("ProfileVM", "Saving updates: $updates to UID: $currentUserId")
                    supabase.from("profiles").update(updates) {
                        filter { eq("profile_id", currentUserId) }
                    }
                }

                _state.update { it.copy(isLoading = false, updateSuccess = true, password = "", currentPassword = "") }
                
                // Re-fetch profile to sync UI
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
