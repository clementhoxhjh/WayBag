package com.example.waybag.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class BagScan(
    val id: Long? = null,
    val userId: String,
    val bagUniqueId: String,
    val scanQR: String, // Renamed from timestamp
    val status: String = "pending", // "pending", "completed", "failed"
    // New fields for Receipt Validation
    val storeName: String? = null,
    val regNo: String? = null,
    val totalAmount: Double? = null,
    val itemsJson: String? = null,
    val receiptTimestamp: String? = null,
    val receiptImageUrl: String? = null,
    val failureReason: String? = null
)
