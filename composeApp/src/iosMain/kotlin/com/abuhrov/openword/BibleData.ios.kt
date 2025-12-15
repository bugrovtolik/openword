package com.abuhrov.openword

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.*

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default // or IO if available

actual suspend fun checkDatabaseFile(name: String): Boolean {
    val fileManager = NSFileManager.defaultManager
    val dbPath = getDatabasePath(name)
    return fileManager.fileExistsAtPath(dbPath)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun installDatabaseFile(name: String) {
    val fileManager = NSFileManager.defaultManager
    val dbPath = getDatabasePath(name)

    // Find resource in the Bundle
    // Compose Multiplatform puts resources in "compose-resources/files"
    val resourcePath = NSBundle.mainBundle.resourcePath + "/compose-resources/files/$name"

    if (fileManager.fileExistsAtPath(resourcePath)) {
        // Direct file copy (Zero RAM usage)
        fileManager.copyItemAtPath(resourcePath, dbPath, null)
    } else {
        throw Exception("Database resource not found at $resourcePath")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun getDatabasePath(name: String): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
    val directory = paths.first() as String
    NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    return "$directory/$name"
}