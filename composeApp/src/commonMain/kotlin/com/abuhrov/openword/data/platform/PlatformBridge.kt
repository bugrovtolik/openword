package com.abuhrov.openword.data.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineDispatcher

expect suspend fun checkDatabaseFile(name: String): Boolean
expect suspend fun installDatabaseFile(name: String, resourcePath: String)
expect suspend fun deleteDatabaseFile(name: String)

@Composable
expect fun loadAppFont(): FontFamily?

expect val ioDispatcher: CoroutineDispatcher
