package com.abuhrov.openword.model

data class Book(val id: Long, val name: String, val shortName: String, val chapterCount: Long, val color: String? = null)
