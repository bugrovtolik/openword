package com.abuhrov.openword.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual suspend fun createDriver(dbName: String): SqlDriver {
        return NativeSqliteDriver(NoOpSchema, dbName)
    }
}

object NoOpSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1
    override fun create(driver: SqlDriver) = QueryResult.Value(Unit)
    override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long) = QueryResult.Value(Unit)
}