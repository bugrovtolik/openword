package com.abuhrov.openword

import androidx.compose.ui.text.font.FontFamily
import com.abuhrov.openword.db.AndroidContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.FileOutputStream
import java.io.IOException

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual suspend fun loadAppFont(): FontFamily? = null

actual suspend fun checkDatabaseFile(name: String): Boolean {
    val context = AndroidContext.applicationContext
    val dbFile = context.getDatabasePath(name)
    return dbFile.exists() && dbFile.length() > 0
}

actual suspend fun installDatabaseFile(name: String, resourcePath: String) {
    val context = AndroidContext.applicationContext
    val dbFile = context.getDatabasePath(name)
    dbFile.parentFile?.mkdirs()
    if (dbFile.exists() && dbFile.length() == 0L) dbFile.delete()

    try {
        val assetPath = findAssetPathRecursive(context, name) ?: throw Exception("Asset '$name' NOT FOUND in APK.")
        context.assets.open(assetPath).use { inputStream ->
            FileOutputStream(dbFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
        }
    } catch (e: Exception) {
        if (dbFile.exists()) dbFile.delete()
        throw e
    }
}

fun findAssetPathRecursive(context: android.content.Context, targetName: String, root: String = ""): String? {
    val list = try { context.assets.list(root) ?: emptyArray() } catch (_: IOException) { emptyArray() }
    for (item in list) {
        if (item.equals(targetName, ignoreCase = true)) return if (root.isEmpty()) item else "$root/$item"
    }
    for (item in list) {
        if (!item.contains(".")) {
            val nextRoot = if (root.isEmpty()) item else "$root/$item"
            val result = findAssetPathRecursive(context, targetName, nextRoot)
            if (result != null) return result
        } else if (item.contains("generated.resources")) {
            val nextRoot = if (root.isEmpty()) item else "$root/$item"
            val result = findAssetPathRecursive(context, targetName, nextRoot)
            if (result != null) return result
        }
    }
    return null
}