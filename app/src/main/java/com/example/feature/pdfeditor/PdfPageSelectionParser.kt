package com.example.feature.pdfeditor

/**
 * Parses one-based page numbers and ranges (for example, "1, 3-5")
 * into sorted, unique, zero-based page indices.
 */
internal fun parsePdfPageSelectionInput(input: String, pageCount: Int): List<Int>? {
    if (input.isBlank() || pageCount <= 0) return null
    val selected = sortedSetOf<Int>()
    return try {
        input.split(",").forEach { raw ->
            val token = raw.trim()
            require(token.isNotEmpty())
            val range = Regex("^([1-9][0-9]*)(?:\\s*-\\s*([1-9][0-9]*))?$")
                .matchEntire(token) ?: return null
            val start = range.groupValues[1].toInt()
            val end = range.groupValues[2].ifBlank { range.groupValues[1] }.toInt()
            require(start <= end && end <= pageCount)
            (start..end).forEach { selected.add(it - 1) }
        }
        selected.takeIf { it.isNotEmpty() }?.toList()
    } catch (_: Exception) {
        null
    }
}
