package com.abuhrov.openword.data.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import openword.composeapp.generated.resources.OpenSans
import openword.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font
import java.io.File
import java.io.FileOutputStream

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

@Composable
actual fun loadAppFont(): FontFamily? = FontFamily(Font(Res.font.OpenSans))

private val context: Context get() = com.abuhrov.openword.db.AndroidContext.applicationContext

actual suspend fun checkDatabaseFile(name: String): Boolean {
    val dbFile = File(context.filesDir, name)
    return dbFile.exists()
}

actual suspend fun installDatabaseFile(name: String, resourcePath: String) {
    val dbFile = File(context.filesDir, name)
    dbFile.parentFile?.mkdirs()
    val assets = context.assets
    val assetPath = findAssetPath(assets, name) ?: resourcePath
    assets.open(assetPath).use { input ->
        FileOutputStream(dbFile).use { output ->
            input.copyTo(output)
        }
    }
}

actual suspend fun deleteDatabaseFile(name: String) {
    val dbFile = File(context.filesDir, name)
    if (dbFile.exists()) {
        dbFile.delete()
    }
}

private fun findAssetPath(assets: android.content.res.AssetManager, targetName: String): String? {
    fun search(path: String): String? {
        val list = try {
            assets.list(path) ?: emptyArray()
        } catch (_: Exception) {
            return null
        }
        for (item in list) {
            val full = if (path.isEmpty()) item else "$path/$item"
            if (item == targetName) return full
            val subResult = search(full)
            if (subResult != null) return subResult
        }
        return null
    }
    return search("")
}
