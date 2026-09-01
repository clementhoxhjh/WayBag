package com.example.waybag.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun parseIsoTimestamp(timestamp: String): Date? {
        if (timestamp.isBlank()) return null
        
        val normalized = timestamp.replace("Z", "+0000").replace(Regex("\\.\\d+"), "")
        
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss"
        )
        
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                val hasOffset = timestamp.contains("+") || (timestamp.lastIndexOf("-") > 10) || timestamp.contains("Z")
                if (!hasOffset) {
                    // If no offset, assume it was saved in Malaysia Time (GMT+8)
                    sdf.timeZone = TimeZone.getTimeZone("GMT+8")
                }
                val date = sdf.parse(normalized)
                if (date != null) return date
            } catch (e: Exception) { continue }
        }
        return null
    }
}
