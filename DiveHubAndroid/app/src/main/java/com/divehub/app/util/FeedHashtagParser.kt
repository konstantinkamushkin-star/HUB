package com.divehub.app.util

/**
 * Mirrors iOS [FeedHashtagParser] — `#wreck`, `#nightdive` (Latin letters/digits/underscore).
 */
object FeedHashtagParser {
    private val hashtagPattern = Regex("#([A-Za-z][A-Za-z0-9_]*)")

    data class Segment(
        val text: String,
        val hashtag: String?,
    )

    fun normalize(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("#")) t = t.drop(1)
        return t.lowercase()
    }

    fun segments(content: String): List<Segment> {
        if (content.isEmpty()) return emptyList()
        val matches = hashtagPattern.findAll(content).toList()
        if (matches.isEmpty()) return listOf(Segment(content, null))
        val result = mutableListOf<Segment>()
        var cursor = 0
        for (match in matches) {
            val start = match.range.first
            if (start > cursor) {
                result += Segment(content.substring(cursor, start), null)
            }
            val token = match.value
            result += Segment(token, normalize(token))
            cursor = match.range.last + 1
        }
        if (cursor < content.length) {
            result += Segment(content.substring(cursor), null)
        }
        return result
    }

    fun deepLinkForTag(normalizedTag: String): String = "divehub://hashtag/$normalizedTag"
}
