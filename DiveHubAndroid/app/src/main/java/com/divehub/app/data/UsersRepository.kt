package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.CertificationDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.UserProfileSummaryDto

class UsersRepository(private val graph: AppGraph) {
    suspend fun getUser(id: String): UserDto = graph.usersApi().getUser(id)

    suspend fun getUserSummary(id: String): UserProfileSummaryDto =
        graph.usersApi().getUserSummary(id)

    suspend fun listCertifications(userId: String): List<CertificationDto> =
        graph.usersApi().listCertifications(userId)
}
