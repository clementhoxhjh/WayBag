package com.example.waybag.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class BagScan(
    val scan_id: Long? = null,
    val scan_qr: String,
    val status: String = "pending", // "pending", "completed", "failed"
    val failure_reason: String? = null,
    val receipt_timestamp: String? = null,
    val reg_no: String? = null,
    val receipt_image_url: String? = null,
    val store_name: String? = null,
    val total_amount: Double? = null,
    val items_json: String? = null,
    val profile_id: String,
    val bag_id: String

)
