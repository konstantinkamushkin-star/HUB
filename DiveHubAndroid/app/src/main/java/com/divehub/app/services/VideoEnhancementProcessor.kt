package com.divehub.app.services

import com.divehub.app.data.diveeditor.CardLookProfile
import com.divehub.app.data.diveeditor.DiveEditorPhotoRepository

/** Cloud underwater video enhancement — iOS `UnderwaterVideoProcessing` / UVM `ai2`. */
object VideoEnhancementProcessor {

    suspend fun process(mp4: ByteArray, repo: DiveEditorPhotoRepository): ByteArray {
        val profile = CardLookProfile.default
        return repo.processVideoUnderwaterVisionModule(
            videoMp4 = mp4,
            engine = profile.engine,
        )
    }
}
