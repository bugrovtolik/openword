package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.abuhrov.openword.db.lexicon.Lexicon
import com.abuhrov.openword.db.LexiconDb
import com.abuhrov.openword.util.normalizeStrongCode

suspend fun getLexicon(db: LexiconDb, rawCode: String): Lexicon? {
    val code = normalizeStrongCode(rawCode)
    var def = db.lexiconQueries.getLexiconDefinition(code).awaitAsOneOrNull()

    if (def == null && code.length > 1 && code.last().isLetter()) {
        val lastChar = code.last()
        val swappedLast =
            if (lastChar.isUpperCase()) lastChar.lowercaseChar() else lastChar.uppercaseChar()
        val altCode = code.dropLast(1) + swappedLast
        def = db.lexiconQueries.getLexiconDefinition(altCode).awaitAsOneOrNull()
    }
    if (def == null && code.length > 1 && code.last().isLetter()) {
        val baseCode = code.dropLast(1)
        def = db.lexiconQueries.getLexiconDefinition(baseCode).awaitAsOneOrNull()
    }
    return def
}
