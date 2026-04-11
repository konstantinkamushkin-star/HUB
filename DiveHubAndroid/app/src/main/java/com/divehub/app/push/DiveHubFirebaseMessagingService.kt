package com.divehub.app.push

import com.divehub.app.diveHubApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

class DiveHubFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = applicationContext.diveHubApp()
        app.applicationWorkScope.launch {
            PushTokenRegistrar.registerKnownToken(app.graph, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Data-only payloads: handle here if needed. Notification payloads show in tray when app is backgrounded.
    }
}
