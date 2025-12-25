@file:OptIn(ExperimentalForeignApi::class)

package com.abuhrov.openword

import androidx.compose.ui.text.font.FontFamily
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import openword.composeapp.generated.resources.OpenSans
import openword.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font
import platform.Foundation.*

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual suspend fun loadAppFont(): FontFamily? = FontFamily(Font(Res.font.OpenSans))

actual suspend fun checkDatabaseFile(name: String): Boolean {
    val fileManager = NSFileManager.defaultManager
    val dbPath = getDatabasePath(name)
    return fileManager.fileExistsAtPath(dbPath)
}

actual suspend fun installDatabaseFile(name: String, resourcePath: String) {
    val dbPath = getDatabasePath(name)
    val fileManager = NSFileManager.defaultManager

    val bundlePath = NSBundle.mainBundle.resourcePath
    val fileEnumerator = fileManager.enumeratorAtPath(bundlePath!!)

    var foundPath: String? = null
    var file: String? = fileEnumerator?.nextObject() as? String
    while (file != null) {
        if (file.endsWith(name, ignoreCase = true)) {
            foundPath = "$bundlePath/$file"
            break
        }
        file = fileEnumerator?.nextObject() as? String
    }

    if (foundPath != null) {
        fileManager.copyItemAtPath(foundPath, dbPath, null)
    } else {
        throw Exception("Database resource $name not found in Bundle")
    }
}

private fun getDatabasePath(name: String): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
    val directory = paths.first() as String
    NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    return "$directory/$name"
}