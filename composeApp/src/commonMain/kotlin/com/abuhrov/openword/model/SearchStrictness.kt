package com.abuhrov.openword.model

enum class SearchStrictness(val maxDistance: Int, val allowPrefix: Boolean) {
    LOOSE(maxDistance = 1, allowPrefix = true),
    STRICT(maxDistance = 0, allowPrefix = false)
}
