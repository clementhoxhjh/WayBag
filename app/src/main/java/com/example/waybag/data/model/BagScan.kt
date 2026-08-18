package com.example.waybag.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class BagScan(
    val id: Long? = null, // Match Supabase 'int8' primary key
    val userId: String,
    val bagUniqueId: String,
    val timestamp: String, // T_QR
    val status: String = "pending" // "pending", "completed", "failed"
)
