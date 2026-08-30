package com.example.waybag.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UserSession(
    val userId: String,
    val email: String,
    val name: String
)

private const val SESSION_FILE = "user_session.json"
private const val PREFS_NAME = "waybag_prefs"
private const val KEY_IS_LOGGED_IN = "is_logged_in"

// ---------- Task 1: SharedPreferences (For simple flags) ----------
fun saveLoginStatus(context: Context, isLoggedIn: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
}

fun isUserLoggedIn(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
}

// ---------- Task 3: JSON file (For structured User data) ----------
fun saveUserSession(context: Context, session: UserSession) {
    val json = Json.encodeToString(session)
    context.openFileOutput(SESSION_FILE, Context.MODE_PRIVATE).use {
        it.write(json.toByteArray())
    }
}

fun loadUserSession(context: Context): UserSession? {
    val file = File(context.filesDir, SESSION_FILE)
    if (!file.exists()) return null
    return try {
        Json.decodeFromString<UserSession>(file.readText())
    } catch (e: Exception) {
        null
    }
}

fun clearStorage(context: Context) {
    saveLoginStatus(context, false)
    val file = File(context.filesDir, SESSION_FILE)
    if (file.exists()) file.delete()
}
