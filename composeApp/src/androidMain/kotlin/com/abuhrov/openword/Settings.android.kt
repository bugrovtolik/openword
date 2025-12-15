package com.abuhrov.openword

import android.content.Context
import com.abuhrov.openword.db.AndroidContext

actual object Settings {
    private val prefs by lazy {
        AndroidContext.applicationContext.getSharedPreferences("openword_prefs", Context.MODE_PRIVATE)
    }

    actual fun getString(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
    actual fun setString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    actual fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
    actual fun setLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
}