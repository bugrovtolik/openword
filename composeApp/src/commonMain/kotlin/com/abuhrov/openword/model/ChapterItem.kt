package com.abuhrov.openword.model

sealed class ChapterItem {
    data class Header(val bookName: String, val chapter: Long) : ChapterItem()
    data class VerseItem(val verse: Verse, val chapter: Long) : ChapterItem()
}
