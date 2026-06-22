package com.divehub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.divehub.app.services.PhotoEnhancementJob
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.photoEnhancementJobDataStore by preferencesDataStore("photo_enhancement_jobs")

class PhotoEnhancementJobStore(
    context: Context,
    private val gson: Gson = Gson(),
) {
    private val store = context.applicationContext.photoEnhancementJobDataStore
    private val listType = object : TypeToken<List<PhotoEnhancementJob>>() {}.type

    val jobs: Flow<List<PhotoEnhancementJob>> = store.data.map { prefs ->
        val raw = prefs[Keys.JOBS_JSON] ?: return@map emptyList()
        runCatching { gson.fromJson<List<PhotoEnhancementJob>>(raw, listType) }.getOrElse { emptyList() }
    }

    suspend fun saveAll(jobs: List<PhotoEnhancementJob>) {
        store.edit { prefs -> prefs[Keys.JOBS_JSON] = gson.toJson(jobs) }
    }

    suspend fun upsert(job: PhotoEnhancementJob) {
        store.edit { prefs ->
            val current = runCatching {
                gson.fromJson<List<PhotoEnhancementJob>>(prefs[Keys.JOBS_JSON] ?: "[]", listType)
            }.getOrElse { emptyList() }
            val next = listOf(job) + current.filter { it.id != job.id }
            prefs[Keys.JOBS_JSON] = gson.toJson(next)
        }
    }

    suspend fun update(jobId: String, transform: (PhotoEnhancementJob) -> PhotoEnhancementJob) {
        store.edit { prefs ->
            val current = runCatching {
                gson.fromJson<List<PhotoEnhancementJob>>(prefs[Keys.JOBS_JSON] ?: "[]", listType)
            }.getOrElse { emptyList() }
            val next = current.map { if (it.id == jobId) transform(it) else it }
            prefs[Keys.JOBS_JSON] = gson.toJson(next)
        }
    }

    suspend fun find(jobId: String): PhotoEnhancementJob? {
        val json = store.data.map { it[Keys.JOBS_JSON] }.first()
        val current = runCatching {
            gson.fromJson<List<PhotoEnhancementJob>>(json ?: "[]", listType)
        }.getOrElse { emptyList() }
        return current.find { it.id == jobId }
    }

    private object Keys {
        val JOBS_JSON = stringPreferencesKey("jobs_json")
    }
}
