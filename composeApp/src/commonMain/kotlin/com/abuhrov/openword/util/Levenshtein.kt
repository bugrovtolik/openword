package com.abuhrov.openword.util

import kotlin.math.min

/**
 * Calculates the Levenshtein distance between two strings using a memory-efficient
 * two-row array swapping technique to minimize allocation overhead.
 */
fun levenshteinDistance(s1: String, s2: String): Int {
    if (s1 == s2) return 0
    if (s1.isEmpty()) return s2.length
    if (s2.isEmpty()) return s1.length

    var v0 = IntArray(s2.length + 1)
    var v1 = IntArray(s2.length + 1)

    for (i in 0..s2.length) {
        v0[i] = i
    }

    for (i in 0 until s1.length) {
        v1[0] = i + 1

        for (j in 0 until s2.length) {
            val cost = if (s1[i] == s2[j]) 0 else 1
            v1[j + 1] = min(
                min(v1[j] + 1, v0[j + 1] + 1),
                v0[j] + cost
            )
        }

        // Swap v0 and v1
        val temp = v0
        v0 = v1
        v1 = temp
    }

    return v0[s2.length]
}
