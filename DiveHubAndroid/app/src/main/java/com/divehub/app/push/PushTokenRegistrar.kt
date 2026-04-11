package com.divehub.app.push

import android.util.Log
import com.divehub.app.AppGraph
import com.divehub.app.BuildConfig
import com.divehub.app.data.remote.dto.RegisterPushTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object PushTokenRegistrar {
    private const val TAG = "PushTokenRegistrar"

    /** After login / cold start: obtain FCM token and register if it changed since last POST. */
    suspend fun syncCurrentTokenIfNeeded(graph: AppGraph) {
        if (graph.tokenStore.getAccessToken().isNullOrBlank()) return
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "FCM token unavailable (use a real Firebase google-services.json)", e)
            return
        }
        if (token.isBlank()) return
        if (token == graph.tokenStore.getLastRegisteredFcmToken()) return
        registerKnownToken(graph, token)
    }

    /** Token rotation: always send to backend when logged in. */
    suspend fun registerKnownToken(graph: AppGraph, token: String) {
        if (token.isBlank()) return
        if (graph.tokenStore.getAccessToken().isNullOrBlank()) return
        runCatching {
            graph.usersApi().registerPushToken(RegisterPushTokenRequest(token = token))
            graph.tokenStore.setLastRegisteredFcmToken(token)
        }.onFailure { e ->
            if (BuildConfig.DEBUG) Log.w(TAG, "POST users/me/push-token failed", e)
        }
    }
}
