package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AppNotificationDto

class NotificationsRepository(private val graph: AppGraph) {
    suspend fun list(): List<AppNotificationDto> =
        graph.notificationsApi().list().notifications

    suspend fun markAllRead() {
        graph.notificationsApi().markAllRead()
    }

    suspend fun delete(id: String) {
        graph.notificationsApi().delete(id)
    }
}
