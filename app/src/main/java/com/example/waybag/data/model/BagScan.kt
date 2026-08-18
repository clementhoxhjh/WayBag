package com.example.waybag.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BagScan(
    val id: Int? = null,
    val userId: String,
    val bagUniqueId: String,
    val timestamp: String, // T_QR
    val status: String = "pending" // "pending", "completed", "failed"
)
