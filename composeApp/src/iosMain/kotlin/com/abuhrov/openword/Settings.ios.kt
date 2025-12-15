package com.abuhrov.openword

import platform.Foundation.NSUserDefaults

actual object Settings {
    actual fun getString(key: String, defaultValue: String): String {
        return NSUserDefaults.standardUserDefaults.stringForKey(key) ?: defaultValue
    }
    actual fun setString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }
    actual fun getLong(key: String, defaultValue: Long): Long {
        // integerForKey returns NSInteger (Long on 64-bit), works for simple IDs
        if (NSUserDefaults.standardUserDefaults.objectForKey(key) == null) return defaultValue
        return NSUserDefaults.standardUserDefaults.integerForKey(key)
    }
    actual fun setLong(key: String, value: Long) {
        NSUserDefaults.standardUserDefaults.setInteger(value, forKey = key)
    }
}