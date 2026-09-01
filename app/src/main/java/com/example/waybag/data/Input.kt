package com.example.waybag.data

import kotlinx.serialization.Serializable

@Serializable
data class ScanInput(
    val scan_qr: String,
    val status: String = "pending", // "pending", "completed", "failed"
    val failure_reason: String? = null,
    val receipt_timestamp: String? = null,
    val reg_no: String? = null,
    val receipt_image_url: String? = null,
    val store_name: String? = null,
    val total_amount: Double? = null,
    val items_json: String? = null
)

@Serializable
data class UserInput(
    val name: String,
    val birthday: String,
    val email: String,
    val password: String,
    val avatar_url: String? = null
)

@Serializable
data class BagInput(
    val bag_id: String
)