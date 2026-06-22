package com.divehub.app.services

data class PhotoEnhancementJob(
    val id: String,
    val state: State,
    val sourceUri: String,
    val resultUri: String? = null,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null,
) {
    enum class State {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
    }
}
