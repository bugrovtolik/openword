package com.abuhrov.openword.util

/**
 * Centralized constants for the OpenWord application.
 */
object Constants {

    // --- Regex Patterns ---
    val STRONGS_PATTERN = Regex("[HG]\\d+[A-Za-z]*")
    val ROOT_WORD_PATTERN = Regex("\\{([^}]+)\\}")

    // --- Database ---
    const val LEXICON_DB_NAME = "vocabulary/lexicon.SQLite3"
    const val DICTIONARY_DB_NAME = "vocabulary/GRM.dictionary.SQLite3"
    const val DATABASE_PREPARE_TIMEOUT_MS = 15_000L

    // --- Network ---
    const val GEMINI_PROXY_URL = "https://openword-api.bugrovtolik.workers.dev"

    // --- Settings Keys ---
    object SettingsKeys {
        const val FONT_SCALE = "font_scale"
        const val AUTO_TRANSLATE = "auto_translate"
        const val LAST_TRANSLATION = "last_translation"
        const val LAST_BOOK = "last_book"
        const val LAST_CHAPTER = "last_chapter"
        const val LAST_VERSE = "last_verse"
        const val NAV_VIEW_MODE = "nav_view_mode"
    }

    // --- Settings Defaults ---
    const val DEFAULT_FONT_SCALE = "1.0"
    const val DEFAULT_AUTO_TRANSLATE = "true"
    const val DEFAULT_BOOK_ID = 1L
    const val DEFAULT_CHAPTER = 1L
    const val DEFAULT_VERSE = 1L
    const val DEFAULT_NAV_VIEW_MODE = "LIST"
}
