package com.divehub.app.ui.profile

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import com.divehub.app.R
import com.divehub.app.data.remote.dto.UserBookingDto
import com.divehub.app.data.remote.dto.manualCenterNoteFromNotes
import com.divehub.app.data.remote.dto.manualVerifiedPriceFromNotes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingDetailBottomSheet(
    booking: UserBookingDto,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BookingDetailSheetContent(
            booking = booking,
            onDismiss = onDismiss,
            onOpenChat = onOpenChat,
        )
    }
}

@Composable
private fun BookingDetailSheetContent(
    booking: UserBookingDto,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val ctx = LocalContext.current
    val verifiedPrice = booking.manualVerifiedPriceFromNotes()
    val centerNote = booking.manualCenterNoteFromNotes()
    val isPending = booking.status.equals("pending", ignoreCase = true)
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isPending) {
                stringResource(R.string.my_bookings_sheet_sent_title)
            } else {
                stringResource(R.string.my_bookings_sheet_done_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isPending) {
                stringResource(R.string.my_bookings_sheet_sent_message)
            } else {
                stringResource(R.string.my_bookings_sheet_done_message)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.my_bookings_sheet_details_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        if (verifiedPrice != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.my_bookings_sheet_verified),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        val shortId = booking.id.take(8).uppercase(Locale.ROOT)
        DetailRow(stringResource(R.string.my_bookings_sheet_row_id), shortId)
        DetailRow(stringResource(R.string.my_bookings_sheet_row_date), formatBookingDateLong(booking.date))
        DetailRow(stringResource(R.string.my_bookings_sheet_row_time), booking.startTime)
        DetailRow(
            stringResource(R.string.my_bookings_sheet_row_participants),
            booking.participantsCount?.toString() ?: stringResource(R.string.my_bookings_detail_unknown),
        )
        DetailRow(stringResource(R.string.my_bookings_sheet_row_status), localizedBookingStatus(booking.status))

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        if (verifiedPrice != null) {
            DetailRow(stringResource(R.string.my_bookings_sheet_row_verified_price), verifiedPrice)
        }
        val amount = booking.payment?.amount
        val currency = booking.payment?.currency?.takeIf { it.isNotBlank() } ?: "USD"
        val amountStr = if (amount != null) {
            String.format(Locale.US, "%.2f %s", amount, currency)
        } else {
            stringResource(R.string.my_bookings_amount_unknown)
        }
        DetailRow(stringResource(R.string.my_bookings_sheet_row_amount), amountStr)
        DetailRow(
            stringResource(R.string.my_bookings_sheet_row_payment_method),
            localizedPaymentMethod(booking.payment?.method),
        )
        if (centerNote != null) {
            DetailRow(stringResource(R.string.my_bookings_sheet_row_center_note), centerNote)
        }

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = {
                onDismiss()
                onOpenChat()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.my_bookings_sheet_open_chat))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { launchCalendarIntent(ctx, booking) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(R.string.my_bookings_sheet_add_calendar))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                ShareCompat.IntentBuilder(ctx)
                    .setType("text/plain")
                    .setText(buildBookingShareText(ctx, booking))
                    .startChooser()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.my_bookings_sheet_share))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.common_done))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun localizedBookingStatus(status: String): String = when (status.lowercase(Locale.ROOT)) {
    "pending" -> stringResource(R.string.admin_bookings_status_pending)
    "quoted" -> stringResource(R.string.my_bookings_status_quoted)
    "confirmed" -> stringResource(R.string.admin_bookings_status_confirmed)
    "completed" -> stringResource(R.string.admin_bookings_status_completed)
    "cancelled" -> stringResource(R.string.admin_bookings_status_cancelled)
    "refunded" -> stringResource(R.string.my_bookings_status_refunded)
    else -> status.replaceFirstChar { c ->
        if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
    }
}

@Composable
private fun localizedPaymentMethod(method: String?): String = when (method?.lowercase(Locale.ROOT)) {
    "online" -> stringResource(R.string.my_bookings_sheet_pay_online)
    "on_site" -> stringResource(R.string.my_bookings_sheet_pay_onsite)
    "apple_pay" -> stringResource(R.string.my_bookings_sheet_pay_apple)
    "google_pay" -> stringResource(R.string.my_bookings_sheet_pay_google)
    null, "" -> stringResource(R.string.my_bookings_detail_unknown)
    else -> method
}

private fun formatBookingDateLong(raw: String): String =
    runCatching {
        val ld: LocalDate = when {
            raw.contains("T") -> OffsetDateTime.parse(raw).toLocalDate()
            else -> LocalDate.parse(raw.take(10))
        }
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()).format(ld)
    }.getOrElse { raw }

private fun eventStartMillis(booking: UserBookingDto): Long? {
    val date = runCatching {
        when {
            booking.date.contains("T") -> OffsetDateTime.parse(booking.date).toLocalDate()
            else -> LocalDate.parse(booking.date.take(10))
        }
    }.getOrNull() ?: return null
    val parts = booking.startTime.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val ldt = LocalDateTime.of(date, LocalTime.of(h, m))
    return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun localizedBookingStatusPlain(ctx: Context, status: String): String =
    when (status.lowercase(Locale.ROOT)) {
        "pending" -> ctx.getString(R.string.admin_bookings_status_pending)
        "quoted" -> ctx.getString(R.string.my_bookings_status_quoted)
        "confirmed" -> ctx.getString(R.string.admin_bookings_status_confirmed)
        "completed" -> ctx.getString(R.string.admin_bookings_status_completed)
        "cancelled" -> ctx.getString(R.string.admin_bookings_status_cancelled)
        "refunded" -> ctx.getString(R.string.my_bookings_status_refunded)
        else -> status.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
        }
    }

private fun localizedPaymentMethodPlain(ctx: Context, method: String?): String =
    when (method?.lowercase(Locale.ROOT)) {
        "online" -> ctx.getString(R.string.my_bookings_sheet_pay_online)
        "on_site" -> ctx.getString(R.string.my_bookings_sheet_pay_onsite)
        "apple_pay" -> ctx.getString(R.string.my_bookings_sheet_pay_apple)
        "google_pay" -> ctx.getString(R.string.my_bookings_sheet_pay_google)
        null, "" -> ctx.getString(R.string.my_bookings_detail_unknown)
        else -> method
    }

private fun buildBookingShareText(ctx: Context, booking: UserBookingDto): String {
    val verified = booking.manualVerifiedPriceFromNotes()
    val note = booking.manualCenterNoteFromNotes()
    val amount = booking.payment?.amount
    val currency = booking.payment?.currency?.takeIf { it.isNotBlank() } ?: "USD"
    val amountStr = if (amount != null) {
        String.format(Locale.US, "%.2f %s", amount, currency)
    } else {
        ctx.getString(R.string.my_bookings_amount_unknown)
    }
    val participantsStr = booking.participantsCount?.toString()
        ?: ctx.getString(R.string.my_bookings_detail_unknown)
    return buildString {
        appendLine(ctx.getString(R.string.my_bookings_share_doc_title))
        appendLine()
        appendLine(ctx.getString(R.string.my_bookings_share_line_full_id, booking.id))
        appendLine(
            ctx.getString(
                R.string.my_bookings_share_line_status,
                localizedBookingStatusPlain(ctx, booking.status),
            ),
        )
        appendLine(ctx.getString(R.string.my_bookings_share_line_date, formatBookingDateLong(booking.date)))
        appendLine(ctx.getString(R.string.my_bookings_share_line_time, booking.startTime))
        appendLine(ctx.getString(R.string.my_bookings_share_line_participants, participantsStr))
        appendLine(ctx.getString(R.string.my_bookings_share_line_center, booking.diveCenterId))
        appendLine(ctx.getString(R.string.my_bookings_share_line_service, booking.serviceId))
        appendLine(ctx.getString(R.string.my_bookings_share_line_amount, amountStr))
        appendLine(
            ctx.getString(
                R.string.my_bookings_share_line_payment,
                localizedPaymentMethodPlain(ctx, booking.payment?.method),
            ),
        )
        if (verified != null) {
            appendLine(ctx.getString(R.string.my_bookings_share_line_verified_price, verified))
        }
        if (note != null) {
            appendLine(ctx.getString(R.string.my_bookings_share_line_center_note, note))
        }
    }.trimEnd()
}

private fun launchCalendarIntent(ctx: android.content.Context, booking: UserBookingDto) {
    val start = eventStartMillis(booking) ?: return
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, ctx.getString(R.string.my_bookings_calendar_event_title))
        putExtra(
            CalendarContract.Events.DESCRIPTION,
            ctx.getString(R.string.my_bookings_calendar_event_description, booking.id),
        )
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 3 * 60 * 60 * 1000L)
    }
    runCatching {
        ctx.startActivity(Intent.createChooser(intent, null))
    }
}
