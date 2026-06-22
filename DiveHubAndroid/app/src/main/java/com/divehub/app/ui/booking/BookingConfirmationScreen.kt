package com.divehub.app.ui.booking

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.diveHubApp
import com.divehub.app.ui.main.DiverTabIndices
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.theme.iosChromePageBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingConfirmationRoute(
    graph: AppGraph,
    innerNav: NavController,
) {
    val context = LocalContext.current
    val pending = remember { graph.consumePendingBookingConfirmation() }

    LaunchedEffect(pending) {
        if (pending == null) innerNav.popBackStack()
    }

    val summary = pending?.summary ?: return

    val shareTitle = stringResource(R.string.booking_confirmed_title)
    val shareBookingId = stringResource(R.string.booking_confirmed_booking_id, summary.bookingId)
    val shareCenter = stringResource(R.string.booking_confirmed_center, summary.centerName)
    val shareWhen = stringResource(R.string.booking_confirmed_when, summary.date, summary.time)
    val shareChooserTitle = stringResource(R.string.booking_share)

    Scaffold(
        containerColor = iosChromePageBackground(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.booking_confirmation_title)) })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF34C759),
                modifier = Modifier.size(80.dp),
            )
            Text(
                stringResource(R.string.booking_confirmed_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.booking_confirmed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = IosDesign.CardCorner,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.booking_confirmation_details),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HorizontalDivider()
                    ConfirmationRow(
                        label = stringResource(R.string.booking_detail_label_id),
                        value = summary.bookingId,
                    )
                    ConfirmationRow(
                        label = stringResource(R.string.booking_detail_label_center),
                        value = summary.centerName,
                    )
                    ConfirmationRow(
                        label = stringResource(R.string.booking_detail_label_service),
                        value = summary.serviceName,
                    )
                    ConfirmationRow(
                        label = stringResource(R.string.booking_detail_label_when),
                        value = "${summary.date} · ${summary.time}",
                    )
                    val paymentLabel = stringResource(
                        when (summary.paymentMethod) {
                            "on_site" -> R.string.booking_pay_onsite
                            "google_pay" -> R.string.booking_pay_google
                            else -> R.string.booking_pay_online
                        },
                    )
                    ConfirmationRow(
                        label = stringResource(R.string.booking_detail_label_payment),
                        value = paymentLabel,
                    )
                    ConfirmationRow(
                        label = stringResource(R.string.booking_detail_label_participants),
                        value = summary.participantCount.toString(),
                    )
                    summary.gearSummary?.let { gear ->
                        ConfirmationRow(
                            label = stringResource(R.string.booking_detail_label_gear),
                            value = gear,
                        )
                    }
                    summary.notes?.let { notes ->
                        ConfirmationRow(
                            label = stringResource(R.string.booking_detail_label_notes),
                            value = notes,
                        )
                    }
                }
            }

            if (pending.chatConversationId != null) {
                OutlinedButton(
                    onClick = {
                        val app = context.diveHubApp()
                        app.requestBusinessChatOpen("dive_center", summary.centerId)
                        app.emitDiverTab(DiverTabIndices.CHAT)
                        innerNav.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.booking_open_chat))
                }
            }

            OutlinedButton(
                onClick = {
                    val shareText = buildString {
                        append(shareTitle)
                        append("\n")
                        append(shareBookingId)
                        append("\n")
                        append(shareCenter)
                        append("\n")
                        append(shareWhen)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            shareChooserTitle,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(stringResource(R.string.booking_share))
                }
            }

            Button(
                onClick = { innerNav.popBackStack() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_ok))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfirmationRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}
