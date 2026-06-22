package com.divehub.app.ui.chat

fun formatMessageTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    return runCatching {
        val instant = java.time.Instant.parse(createdAt)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(zoned)
    }.getOrElse {
        if (createdAt.length >= 16) createdAt.substring(11, 16) else ""
    }
}

fun formatMessageDateLabel(
    createdAt: String,
    todayLabel: String,
    yesterdayLabel: String,
): String {
    return runCatching {
        val instant = java.time.Instant.parse(createdAt)
        val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val today = java.time.LocalDate.now()
        when (date) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
        }
    }.getOrElse { "" }
}
