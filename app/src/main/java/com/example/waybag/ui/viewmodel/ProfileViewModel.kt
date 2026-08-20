package com.example.waybag.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waybag.data.model.UserProfile
import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserUpdateBuilder
import io.github.jan.supabase.postgrest.from
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
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onNameChange(newName: String) {
        _state.update { it.copy(name = newName) }
    }

    fun onBirthdayChange(newBirthday: String) {
        _state.update { it.copy(birthday = newBirthday) }
    }

    fun onEmailChange(newEmail: String) {
        _state.update { it.copy(email = newEmail) }
    }

    fun onPasswordChange(newPassword: String) {
        _state.update { it.copy(password = newPassword) }
    }

    fun uploadAvatar(byteArray: ByteArray) {
        val user = supabase.auth.currentUserOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val fileName = "${user.id}_${UUID.randomUUID()}.jpg"
                val bucket = supabase.storage.from("avatars")
                bucket.upload(fileName, byteArray) {
                    upsert = true
                }
                
                val publicUrl = bucket.publicUrl(fileName)
                
                // Update profile with new avatar URL
                supabase.from("profiles").update(
                    mapOf("avatar_url" to publicUrl)
                ) {
                    filter { eq("id", user.id) }
                }
                
                fetchProfile() // Refresh profile to get the new avatar URL
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun saveProfile() {
        val user = supabase.auth.currentUserOrNull() ?: return
        val currentState = _state.value
        
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, updateSuccess = false) }
            try {
                // 1. Update Profile in public.profiles table
                val updates = mutableMapOf<String, String>()
                if (currentState.name != currentState.userProfile?.name) {
                    updates["name"] = currentState.name
                }
                if (currentState.birthday != currentState.userProfile?.birthday) {
                    updates["birthday"] = currentState.birthday
                }
                
                if (updates.isNotEmpty()) {
                    supabase.from("profiles").update(updates) {
                        filter { eq("id", user.id) }
                    }
                }

                // 2. Update Email/Password in Supabase Auth
                if (currentState.email != currentState.userProfile?.email || currentState.password.isNotEmpty()) {
                    supabase.auth.updateUser {
                        if (currentState.email != currentState.userProfile?.email) {
                            email = currentState.email
                        }
                        if (currentState.password.isNotEmpty()) {
                            password = currentState.password
                        }
                    }
                    // If email changed, we also sync it to the profiles table
                    if (currentState.email != currentState.userProfile?.email) {
                        supabase.from("profiles").update(mapOf("email" to currentState.email)) {
                            filter { eq("id", user.id) }
                        }
                    }
                }

                _state.update { it.copy(isLoading = false, updateSuccess = true, password = "") }
                fetchProfile() // Refresh local state
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    fun resetUpdateSuccess() {
        _state.update { it.copy(updateSuccess = false) }
    }
}
