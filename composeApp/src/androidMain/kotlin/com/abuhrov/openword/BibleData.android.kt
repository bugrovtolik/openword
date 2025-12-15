package com.abuhrov.openword

import com.abuhrov.openword.db.AndroidContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.FileOutputStream
import java.io.IOException

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual suspend fun checkDatabaseFile(name: String): Boolean {
    val context = AndroidContext.applicationContext
    val dbFile = context.getDatabasePath(name)
    return dbFile.exists() && dbFile.length() > 0
}

actual suspend fun installDatabaseFile(name: String) {
    val context = AndroidContext.applicationContext
    val dbFile = context.getDatabasePath(name)

    dbFile.parentFile?.mkdirs()

    if (dbFile.exists() && dbFile.length() == 0L) {
        dbFile.delete()
    }

    try {
        println("Attempting to install $name...")

        // Use recursive search to find the file anywhere in assets
        val assetPath = findAssetPathRecursive(context, name)

        if (assetPath == null) {
            println("--- ASSET DUMP START ---")
            logAllAssets(context, "")
            println("--- ASSET DUMP END ---")
            throw Exception("Asset '$name' NOT FOUND in APK.")
        }

        println("Found asset at: '$assetPath'. Copying to: ${dbFile.absolutePath}")

        context.assets.open(assetPath).use { inputStream ->
            FileOutputStream(dbFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
        }

        println("Successfully installed $name. Size: ${dbFile.length()} bytes")

    } catch (e: Exception) {
        if (dbFile.exists()) dbFile.delete()
        throw e
    }
}

/**
 * Recursively searches for a file by name in the assets folder.
 */
fun findAssetPathRecursive(context: android.content.Context, targetName: String, root: String = ""): String? {
    val list = try {
        context.assets.list(root) ?: emptyArray()
    } catch (e: IOException) {
        emptyArray()
    }

    // 1. Check current directory
    for (item in list) {
        if (item.equals(targetName, ignoreCase = true)) {
            return if (root.isEmpty()) item else "$root/$item"
        }
    }

    // 2. Check subdirectories
    for (item in list) {
        // Optimisation: Skip files with extensions to avoid treating them as dirs
        if (!item.contains(".")) {
            val nextRoot = if (root.isEmpty()) item else "$root/$item"
            val result = findAssetPathRecursive(context, targetName, nextRoot)
            if (result != null) return result
        }
        // Special case: The generated resource folder might have dots!
        // e.g. "openword.composeapp.generated.resources"
        else if (item.contains("generated.resources")) {
            val nextRoot = if (root.isEmpty()) item else "$root/$item"
            val result = findAssetPathRecursive(context, targetName, nextRoot)
            if (result != null) return result
        }
    }

    return null
}

fun logAllAssets(context: android.content.Context, root: String) {
    val list = try { context.assets.list(root) ?: emptyArray() } catch (e: Exception) { emptyArray() }
    for (item in list) {
        val path = if (root.isEmpty()) item else "$root/$item"
        if (item.contains(".")) {
            println("Asset: $path")
        } else {
            println("Folder: $path")
            logAllAssets(context, path)
        }
    }
}