package com.divehub.app.services

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.divehub.app.data.diveeditor.DiveEditorPhotoRepository
import com.divehub.app.data.local.PhotoEnhancementJobStore
import com.divehub.app.diveHubApp
import com.divehub.app.services.PhotoEnhancementJob.State
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PhotoEnhancementQueue {
    private const val KEY_JOB_ID = "job_id"

    fun enqueue(context: Context, sourceUri: Uri, store: PhotoEnhancementJobStore): String {
        val id = UUID.randomUUID().toString()
        val job = PhotoEnhancementJob(
            id = id,
            state = State.PENDING,
            sourceUri = sourceUri.toString(),
        )
        kotlinx.coroutines.runBlocking { store.upsert(job) }
        val request = OneTimeWorkRequestBuilder<PhotoEnhancementWorker>()
            .setInputData(Data.Builder().putString(KEY_JOB_ID, id).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
        return id
    }

    fun resumeIncompleteJobs(context: Context, store: PhotoEnhancementJobStore) {
        kotlinx.coroutines.runBlocking {
            val pending = store.jobs.first().filter { it.state == State.PENDING || it.state == State.RUNNING }
            pending.forEach { job ->
                val request = OneTimeWorkRequestBuilder<PhotoEnhancementWorker>()
                    .setInputData(Data.Builder().putString(KEY_JOB_ID, job.id).build())
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueue(request)
            }
        }
    }
}

class PhotoEnhancementWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString("job_id") ?: return Result.failure()
        val store = PhotoEnhancementJobStore(applicationContext)
        val jobs = store.jobs.first()
        val job = jobs.find { it.id == jobId } ?: return Result.failure()
        store.update(jobId) { it.copy(state = State.RUNNING) }
        return runCatching {
            val graph = applicationContext.diveHubApp().graph
            val repo = DiveEditorPhotoRepository(
                okHttpClient = graph.httpClient,
                tokenStore = graph.tokenStore,
                gson = graph.gson,
            )
            val sourceUri = Uri.parse(job.sourceUri)
            val input = applicationContext.contentResolver.openInputStream(sourceUri)
                ?: error("cannot open source")
            val bitmap = BitmapFactory.decodeStream(input) ?: error("decode failed")
            input.close()
            val jpeg = PhotoEnhancementProcessor.jpegData(bitmap)
            bitmap.recycle()
            val enhanced = PhotoEnhancementProcessor.process(jpeg, repo)
            val outDir = File(applicationContext.filesDir, "photo_enhancement").apply { mkdirs() }
            val outFile = File(outDir, "$jobId.jpg")
            FileOutputStream(outFile).use { fos -> fos.write(enhanced) }
            store.update(jobId) {
                it.copy(
                    state = State.COMPLETED,
                    resultUri = Uri.fromFile(outFile).toString(),
                    completedAtEpochMs = System.currentTimeMillis(),
                )
            }
            Result.success()
        }.getOrElse { e ->
            store.update(jobId) {
                it.copy(
                    state = State.FAILED,
                    errorMessage = e.message,
                    completedAtEpochMs = System.currentTimeMillis(),
                )
            }
            Result.failure()
        }
    }
}
