package com.divehub.app.data

import android.content.Context
import android.net.Uri
import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.CertificationDto
import com.divehub.app.data.remote.dto.CreateCertificationRequest
import java.time.Instant
import java.time.format.DateTimeFormatter

class CertificationsRepository(private val graph: AppGraph) {

    private val feedRepo = FeedRepository(graph)

    suspend fun list(userId: String): List<CertificationDto> =
        graph.usersApi().listCertifications(userId)

    suspend fun delete(certificationId: String) {
        graph.usersApi().deleteCertification(certificationId).use { }
    }

    suspend fun create(
        userId: String,
        agency: String,
        level: String,
        instructorNumber: String?,
        issueDateIso: String?,
        cardImagePathOrUrl: String?,
        context: Context?,
    ): CertificationDto {
        val issue = issueDateIso?.trim()?.takeIf { it.isNotEmpty() }
            ?: DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        var cardUrl = cardImagePathOrUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (cardUrl != null && context != null && cardUrl.startsWith("content:")) {
            cardUrl = feedRepo.uploadMedia(context, Uri.parse(cardUrl))
        }
        val body = CreateCertificationRequest(
            agency = agency.trim(),
            level = level.trim(),
            issueDate = issue,
            instructorNumber = instructorNumber?.trim()?.takeIf { it.isNotEmpty() },
            cardImageUrl = cardUrl?.takeIf { !it.startsWith("content:") },
            verificationStatus = "PENDING",
        )
        return graph.usersApi().createCertification(userId, body)
    }
}
