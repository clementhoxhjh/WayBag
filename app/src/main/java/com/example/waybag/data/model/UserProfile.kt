package com.example.waybag.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val birthday: String,
    val email: String,
    val avatar_url: String? = null
)
