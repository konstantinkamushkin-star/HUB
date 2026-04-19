package com.divehub.app.data

import android.os.Build
import com.divehub.app.AppGraph
import com.divehub.app.BuildConfig
import com.divehub.app.data.remote.dto.SupportTicketClientMetadata
import com.divehub.app.data.remote.dto.SupportTicketCreateRequest
import java.util.Locale

class SupportTicketsRepository(
    private val graph: AppGraph,
) {
    suspend fun submit(
        subject: String,
        body: String,
        category: String,
        conversationId: String? = null,
        localeTag: String = Locale.getDefault().toLanguageTag(),
    ) {
        val meta = SupportTicketClientMetadata(
            appVersion = BuildConfig.VERSION_NAME,
            build = BuildConfig.VERSION_CODE.toString(),
            os = "Android ${Build.VERSION.SDK_INT}",
            locale = localeTag,
        )
        graph.supportApi().createTicket(
            SupportTicketCreateRequest(
                subject = subject.trim(),
                body = body.trim(),
                category = category,
                conversationId = conversationId,
                metadata = meta,
            ),
        )
    }
}
