package com.divehub.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.divehub.app.data.remote.dto.AdminBookingLocal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

private val HeatmapDowOrderMonday: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private val HeatmapDowOrderSunday: List<DayOfWeek> = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

/**
 * @param weekStartsOnSunday `true` — iOS admin home (Вс … Сб); `false` — Mon-first (календарь бронирований).
 * @param solidSelectedFill iOS-style filled primary chip for the selected day.
 */
@Composable
fun AdminBookingMonthGrid(
    yearMonth: YearMonth,
    bookingsByDate: Map<LocalDate, List<AdminBookingLocal>>,
    selectedDate: LocalDate,
    locale: Locale,
    onSelectDay: (LocalDate) -> Unit,
    weekStartsOnSunday: Boolean = false,
    solidSelectedFill: Boolean = false,
) {
    val first = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val offset = if (weekStartsOnSunday) {
        first.dayOfWeek.value % 7
    } else {
        (first.dayOfWeek.value + 6) % 7
    }
    val numWeeks = (offset + daysInMonth + 6) / 7
    val header = if (weekStartsOnSunday) HeatmapDowOrderSunday else HeatmapDowOrderMonday
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                header.forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.NARROW, locale),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            for (week in 0 until numWeeks) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (dow in 0 until 7) {
                        val dayIndex = week * 7 + dow - offset + 1
                        if (dayIndex in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayIndex)
                            val dayBookings = bookingsByDate[date].orEmpty()
                            AdminMonthDayCell(
                                dayNum = dayIndex,
                                count = dayBookings.size,
                                dominantStatus = adminDominantBookingStatus(dayBookings),
                                selected = date == selectedDate,
                                solidSelectedFill = solidSelectedFill,
                                onClick = { onSelectDay(date) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

fun adminBookingStatusColor(status: String): Color = when (status.lowercase()) {
    "pending" -> Color(0xFFF57C00)
    "confirmed" -> Color(0xFF1565C0)
    "completed" -> Color(0xFF2E7D32)
    "cancelled" -> Color(0xFFC62828)
    else -> Color(0xFF757575)
}

private fun adminDominantBookingStatus(list: List<AdminBookingLocal>): String? {
    if (list.isEmpty()) return null
    val order = listOf("pending", "confirmed", "completed", "cancelled")
    for (s in order) {
        if (list.any { it.status.equals(s, ignoreCase = true) }) return s
    }
    return list.first().status
}

@Composable
private fun AdminMonthDayCell(
    dayNum: Int,
    count: Int,
    dominantStatus: String?,
    selected: Boolean,
    solidSelectedFill: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val intensity = when {
        count <= 0 -> 0.08f
        count == 1 -> 0.22f
        count == 2 -> 0.35f
        else -> min(0.25f + count * 0.1f, 0.88f)
    }
    val fillSoft = primary.copy(alpha = if (selected && !solidSelectedFill) maxOf(0.2f, intensity) else intensity)
    val borderW = if (selected && !solidSelectedFill) 2.dp else 0.dp
    val bg = when {
        selected && solidSelectedFill -> primary
        else -> fillSoft
    }
    val labelColor = when {
        selected && solidSelectedFill -> onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val countColor = when {
        selected && solidSelectedFill -> onPrimary.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (borderW > 0.dp) {
                    Modifier.border(borderW, primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayNum.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected && solidSelectedFill) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
            )
            if (count > 0) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = countColor,
                )
            }
            if (!selected || !solidSelectedFill) {
                dominantStatus?.let {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(adminBookingStatusColor(it)),
                    )
                }
            }
        }
    }
}
