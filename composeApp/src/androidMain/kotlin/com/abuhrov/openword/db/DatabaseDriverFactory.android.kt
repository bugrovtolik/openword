package com.abuhrov.openword.db

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

object AndroidContext {
    lateinit var applicationContext: Context
}

actual class DatabaseDriverFactory {
    actual suspend fun createDriver(dbName: String): SqlDriver {
        return AndroidSqliteDriver(
            schema = NoOpSchema,
            context = AndroidContext.applicationContext,
            name = dbName
        )
    }
}

object NoOpSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1
    override fun create(driver: SqlDriver) = QueryResult.Value(Unit)
    override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion) = QueryResult.Value(Unit)
}