package com.abuhrov.openword

import kotlinx.browser.localStorage

actual object Settings {
    actual fun getString(key: String, defaultValue: String): String {
        return localStorage.getItem(key) ?: defaultValue
    }
    actual fun setString(key: String, value: String) {
        localStorage.setItem(key, value)
    }
    actual fun getLong(key: String, defaultValue: Long): Long {
        return localStorage.getItem(key)?.toLongOrNull() ?: defaultValue
    }
    actual fun setLong(key: String, value: Long) {
        localStorage.setItem(key, value.toString())
    }
}