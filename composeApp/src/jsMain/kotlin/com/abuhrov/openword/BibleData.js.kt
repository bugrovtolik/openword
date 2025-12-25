package com.abuhrov.openword

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import openword.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object BibleDataCache {
    val map = mutableMapOf<String, Uint8Array>()
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

@OptIn(ExperimentalResourceApi::class)
actual suspend fun loadAppFont(): FontFamily? {
    return try {
        val bytes = Res.readBytes("font/OpenSans.ttf")
        val int8Array = Int8Array(bytes.size)
        for (i in bytes.indices) {
            int8Array[i] = bytes[i]
        }
        val font = Font(identity = "OpenSans", data = int8Array.unsafeCast<ByteArray>())
        FontFamily(font)
    } catch (e: Throwable) {
        null
    }
}

actual suspend fun checkDatabaseFile(name: String): Boolean {
    if (BibleDataCache.map.containsKey(name)) return true
    return try {
        val data = loadFromIdb(name)
        if (data != null) {
            BibleDataCache.map[name] = data
            true
        } else {
            false
        }
    } catch (_: Throwable) {
        false
    }
}

@OptIn(ExperimentalResourceApi::class)
actual suspend fun installDatabaseFile(name: String, resourcePath: String) {
    val bytes = Res.readBytes(resourcePath)
    val uint8Array = Uint8Array(bytes.toTypedArray())

    BibleDataCache.map[name] = uint8Array
    saveToIdb(name, uint8Array)
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
    req.onerror = { e: dynamic -> cont.resumeWithException(Exception("IDB Open Error")) }
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
        val store = tx.objectStore("files")
        val getReq = store.get(name)
        getReq.onsuccess = {
            val result = getReq.result
            if (result != undefined) cont.resume(result.unsafeCast<Uint8Array>())
            else cont.resume(null)
        }
        getReq.onerror = { cont.resume(null) }
    }
    req.onerror = { cont.resume(null) }
}