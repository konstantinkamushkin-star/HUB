package com.divehub.app.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * Short AAC/M4A recording for chat; output in app cache.
 */
class ChatVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var out: File? = null

    fun isRecording(): Boolean = recorder != null

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        if (recorder != null) return true
        return try {
            val f = File.createTempFile("chat_voice_", ".m4a", context.cacheDir)
            out = f
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            true
        } catch (_: Exception) {
            cancel()
            false
        }
    }

    /**
     * Stops and returns a [file://] URI, or null on failure.
     */
    fun stopAndReset(): Uri? {
        return try {
            val r = recorder
            recorder = null
            r?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                try {
                    release()
                } catch (_: Exception) {
                }
            }
            out?.let { f ->
                if (f.exists() && f.length() > 0) Uri.fromFile(f) else null
            }
        } catch (_: Exception) {
            null
        } finally {
            out = null
        }
    }

    fun cancel() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                try {
                    release()
                } catch (_: Exception) {
                }
            }
        } finally {
            recorder = null
            out?.delete()
            out = null
        }
    }
}
