package com.cmyksoda.savepoint

import java.text.Normalizer
import java.util.Locale

private val searchMarks = Regex("\\p{M}+")
private val searchSeparators = Regex("[^\\p{L}\\p{N}]+")

fun searchText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(searchMarks, "")
        .lowercase(Locale.ROOT)
        .replace(searchSeparators, " ")
        .trim()

private fun typoDistance(left: String, right: String, allowance: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > allowance) return allowance + 1
    val rows = Array(left.length + 1) { IntArray(right.length + 1) }
    for (i in rows.indices) rows[i][0] = i
    for (j in rows[0].indices) rows[0][j] = j
    for (i in 1..left.length) for (j in 1..right.length) {
        rows[i][j] =
            minOf(
                rows[i - 1][j] + 1,
                rows[i][j - 1] + 1,
                rows[i - 1][j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1,
            )
        if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1])
            rows[i][j] = minOf(rows[i][j], rows[i - 2][j - 2] + 1)
    }
    return rows.last().last()
}

fun searchMatchScore(title: String, query: String): Int? {
    val wanted = searchText(query)
    if (wanted.isEmpty()) return 0
    val text = searchText(title)
    if (text.contains(wanted)) return 0
    if (text.isEmpty()) return null
    val words = text.split(' ')
    var score = 0
    for (token in wanted.split(' ')) {
        val allowance =
            when {
                token.length >= 6 -> 2
                token.length >= 3 -> 1
                else -> 0
            }
        val closest =
            words.minOf { word ->
                when {
                    word.startsWith(token) || (token.length >= 3 && word.contains(token)) -> 0
                    allowance == 0 -> allowance + 1
                    else ->
                        minOf(
                            typoDistance(token, word, allowance),
                            typoDistance(token, word.take(token.length), allowance),
                        )
                }
            }
        if (closest > allowance) return null
        score += closest
    }
    return score
}
