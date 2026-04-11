package com.divehub.app.data.remote.dto

data class RegisterPushTokenRequest(
    val token: String,
    val platform: String = "android",
)
