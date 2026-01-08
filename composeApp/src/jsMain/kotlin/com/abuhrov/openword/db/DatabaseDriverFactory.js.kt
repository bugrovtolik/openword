package com.abuhrov.openword.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.Query
import com.abuhrov.openword.BibleDataCache
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array

actual class DatabaseDriverFactory {
    actual suspend fun createDriver(dbName: String): SqlDriver {

        // 1. Initialize SQL.js (Main Thread)
        val SQL = try {
            initSqlJsSafe().await()
        } catch (e: Throwable) {
            console.error("JS: initSqlJsSafe() FAILED", e)
            throw RuntimeException("Failed to initialize SQL.js. Ensure index.html uses version 1.12.0", e)
        }

        // 2. Load Database Bytes
        val dbBytes = BibleDataCache.map[dbName]

        // 3. Create Database Object
        val db = try {
            if (dbBytes != null && dbBytes.length > 0) {
                createNewDatabase(SQL, dbBytes)
            } else {
                createNewDatabase(SQL, null)
            }
        } catch (e: Throwable) {
            console.error("JS: Failed to open/create DB object", e)
            throw RuntimeException("Failed to open DB $dbName", e)
        }

        // 4. Return Custom Main-Thread Driver
        return MainThreadSqlDriver(db)
    }
}

private fun createNewDatabase(SQL: dynamic, data: Uint8Array?): dynamic = js("""
    new SQL.Database(data)
""")

private fun initSqlJsSafe(): Promise<dynamic> = js("""
    new Promise(function(resolve, reject) {
        if (typeof window.initSqlJs !== 'function') {
             reject(new Error("window.initSqlJs is not a function. Check index.html script tag."));
             return;
        }
        
        try {
            window.initSqlJs({
                locateFile: function(file) {
                    return 'https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.12.0/sql-wasm.wasm';
                },
                printErr: function(text) {
                    console.error("SQL.js stderr:", text);
                }
            }).then(function(sql) {
                resolve(sql);
            }).catch(function(e) {
                console.error("JS [initSqlJsSafe]: Promise rejected.", e);
                reject(e);
            });
        } catch (e) {
            reject(e);
        }
    })
""")

class MainThreadSqlDriver(private val db: dynamic) : SqlDriver {

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {}
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {}
    override fun notifyListeners(vararg queryKeys: String) {}

    override fun currentTransaction(): Transacter.Transaction? = null
    override fun newTransaction(): QueryResult<Transacter.Transaction> = QueryResult.AsyncValue {
        object : Transacter.Transaction() {
            override val enclosingTransaction: Transacter.Transaction? = null
            override fun endTransaction(successful: Boolean) = if (successful) db.run("COMMIT") else db.run("ROLLBACK")
        }
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> = QueryResult.AsyncValue {
        val stmt = db.prepare(sql)
        if (binders != null) {
            val binder = JsPreparedStatement(stmt)
            binder.binders()
            stmt.bind(binder.parameters.toTypedArray())
        } else {
            stmt.bind()
        }

        stmt.step()
        stmt.free()

        val changes = db.exec("SELECT changes()")[0].values[0][0] as Int
        changes.toLong()
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> = QueryResult.AsyncValue {
        val stmt = db.prepare(sql)
        if (binders != null) {
            val binder = JsPreparedStatement(stmt)
            binder.binders()
            stmt.bind(binder.parameters.toTypedArray())
        } else {
            stmt.bind()
        }

        val cursor = JsSqlCursor(stmt)
        val result = mapper(cursor).await()
        stmt.free()
        result
    }

    override fun close() {
        db.close()
    }
}

class JsPreparedStatement(val stmt: dynamic) : SqlPreparedStatement {
    val parameters = mutableListOf<Any?>()

    override fun bindBoolean(index: Int, boolean: Boolean?) { parameters.add(boolean) }
    override fun bindBytes(index: Int, bytes: ByteArray?) { parameters.add(bytes) }
    override fun bindDouble(index: Int, double: Double?) { parameters.add(double) }
    override fun bindLong(index: Int, long: Long?) { parameters.add(long?.toDouble()) }
    override fun bindString(index: Int, string: String?) { parameters.add(string) }
}

class JsSqlCursor(private val stmt: dynamic) : SqlCursor {
    private var currentRow: Array<dynamic>? = null

    override fun next(): QueryResult<Boolean> = QueryResult.AsyncValue {
        val hasNext = stmt.step() as Boolean
        if (hasNext) {
            currentRow = stmt.get() as Array<dynamic>
        } else {
            currentRow = null
        }
        hasNext
    }

    override fun getString(index: Int): String? = currentRow?.get(index) as? String
    override fun getLong(index: Int): Long? = (currentRow?.get(index) as? Number)?.toLong()
    override fun getBytes(index: Int): ByteArray? = (currentRow?.get(index) as? Int8Array)?.unsafeCast<ByteArray>()
    override fun getDouble(index: Int): Double? = (currentRow?.get(index) as? Number)?.toDouble()
    override fun getBoolean(index: Int): Boolean? = currentRow?.get(index) as? Boolean
}