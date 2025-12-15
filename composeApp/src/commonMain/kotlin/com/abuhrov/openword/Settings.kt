package com.abuhrov.openword

expect object Settings {
    fun getString(key: String, defaultValue: String): String
    fun setString(key: String, value: String)
    fun getLong(key: String, defaultValue: Long): Long
    fun setLong(key: String, value: Long)
}