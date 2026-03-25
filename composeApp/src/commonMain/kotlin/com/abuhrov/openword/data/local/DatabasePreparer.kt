package com.abuhrov.openword.data.local

import com.abuhrov.openword.data.config.availableCommentaries
import com.abuhrov.openword.data.config.availableTranslations
import com.abuhrov.openword.data.platform.checkDatabaseFile
import com.abuhrov.openword.data.platform.deleteDatabaseFile
import com.abuhrov.openword.data.platform.installDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.data.repository.CommentaryRepository
import com.abuhrov.openword.util.Constants
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

suspend fun clearAllLocalData() = withContext(ioDispatcher) {
    deleteDatabaseFile(Constants.LEXICON_DB_NAME.substringAfterLast('/'))
    deleteDatabaseFile(Constants.WORDS_DEFINITIONS_DB_NAME.substringAfterLast('/'))
    availableTranslations.forEach { deleteDatabaseFile(it.fileName.substringAfterLast('/')) }
    availableCommentaries.forEach { deleteDatabaseFile(it.fileName.substringAfterLast('/')) }
    CommentaryRepository.clearCache()
    deleteDatabaseFile(Constants.CROSS_REFERENCE_DB_NAME.substringAfterLast('/'))
}

suspend fun prepareDatabaseFile(fileName: String) {
    val simpleName = fileName.substringAfterLast('/')
    withContext(ioDispatcher) {
        if (!checkDatabaseFile(simpleName)) {
            withTimeout(Constants.DATABASE_PREPARE_TIMEOUT_MS) {
                try {
                    installDatabaseFile(simpleName, "files/$fileName")
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to prepare database '$simpleName'.", e)
                }
            }
        }
    }
}
