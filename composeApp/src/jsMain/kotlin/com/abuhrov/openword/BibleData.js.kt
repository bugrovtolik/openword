package com.abuhrov.openword

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import openword.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.khronos.webgl.Uint8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

val jsDatabaseCache = mutableMapOf<String, Uint8Array>()
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual suspend fun checkDatabaseFile(name: String): Boolean {
    if (jsDatabaseCache.containsKey(name)) return true
    return try {
        val data = loadFromIdb(name)
        if (data != null) {
            jsDatabaseCache[name] = data
            true
        } else false
    } catch (e: Throwable) {
        false
    }
}

@OptIn(ExperimentalResourceApi::class)
actual suspend fun installDatabaseFile(name: String) {
    // 1. Load bytes using Compose Resources
    val bytes = Res.readBytes("files/$name")

    // 2. Convert to JS Uint8Array
    val uint8Array = Uint8Array(bytes.toTypedArray())

    // 3. Cache in Memory & IndexedDB
    jsDatabaseCache[name] = uint8Array
    try {
        saveToIdb(name, uint8Array)
        println("JS: Saved $name to IndexedDB.")
    } catch (e: Throwable) {
        println("JS: Failed to save to IDB: $e")
    }
}

private suspend fun saveToIdb(name: String, data: Uint8Array): Unit = suspendCoroutine { cont ->
    val req = js("indexedDB.open('openword_db', 1)")
    req.onupgradeneeded = { e: dynamic ->
        val db = e.target.result
        if (!db.objectStoreNames.contains("files")) db.createObjectStore("files")
    }
    req.onsuccess = { e: dynamic ->
        val db = e.target.result
        val tx = db.transaction("files", "readwrite")
        tx.objectStore("files").put(data, name)
        tx.oncomplete = { cont.resume(Unit) }
        tx.onerror = { err: dynamic -> cont.resumeWithException(Exception("IDB Write Error: $err")) }
    }
    req.onerror = { cont.resumeWithException(Exception("IDB Open Error")) }
}

private suspend fun loadFromIdb(name: String): Uint8Array? = suspendCoroutine { cont ->
    val req = js("indexedDB.open('openword_db', 1)")
    req.onupgradeneeded = { e: dynamic ->
        val db = e.target.result
        if (!db.objectStoreNames.contains("files")) db.createObjectStore("files")
    }
    req.onsuccess = { e: dynamic ->
        val db = e.target.result
        val tx = db.transaction("files", "readonly")
        val getReq = tx.objectStore("files").get(name)
        getReq.onsuccess = {
            val result = getReq.result
            if (result != undefined) cont.resume(result.unsafeCast<Uint8Array>())
            else cont.resume(null)
        }
        getReq.onerror = { cont.resume(null) }
    }
    req.onerror = { cont.resume(null) }
}