package com.example.waybag.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val profile_id: String,
    val name: String,
    val birthday: String,
    val email: String,
    val password: String,
    val avatar_url: String? = null
)
