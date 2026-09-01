package com.example.waybag.utils

object ValidationUtils {

    /**
     * Validates email format and constraints:
     * - Cannot be empty
     * - First character must be a letter or digit (no symbols)
     * - Local part (before @) max 64 characters
     * - Domain part (after @) max 64 characters
     * - Standard format: name@example.com
     */
    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            return "Please enter an email address"
        }

        val firstChar = trimmed.first()
        if (!firstChar.isLetterOrDigit()) {
            return "First letter of email cannot be a symbol"
        }

        if (!trimmed.contains("@")) {
            return "Invalid email format (must be in name@example.com format)"
        }

        val parts = trimmed.split("@")
        if (parts.size != 2) {
            return "Invalid email format (must contain exactly one '@')"
        }

        val localPart = parts[0]
        val domainPart = parts[1]

        if (localPart.isEmpty()) {
            return "Invalid email format: missing username before '@'"
        }
        if (localPart.length > 64) {
            return "Email username before '@' cannot exceed 64 characters"
        }

        if (domainPart.isEmpty()) {
            return "Invalid email format: missing domain after '@'"
        }
        if (domainPart.length > 64) {
            return "Email domain after '@' cannot exceed 64 characters"
        }

        if (!domainPart.contains(".") || domainPart.startsWith(".") || domainPart.endsWith(".")) {
            return "Invalid email format (must be in name@example.com format)"
        }

        val emailRegex = "^[a-zA-Z0-9][a-zA-Z0-9._%+-]*@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()
        if (!emailRegex.matches(trimmed)) {
            return "Invalid email format (must be in name@example.com format)"
        }

        return null
    }

    /**
     * Validates name constraints:
     * - Cannot be blank
     * - Maximum length 100 characters
     */
    fun validateName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return "Please enter your name"
        }
        if (trimmed.length > 100) {
            return "Name cannot exceed 100 characters"
        }
        return null
    }

    /**
     * Validates password length (minimum 8 characters)
     */
    fun validatePasswordMinLength(password: String, fieldName: String = "Password"): String? {
        if (password.length < 8) {
            return "$fieldName must be at least 8 characters"
        }
        return null
    }
}
