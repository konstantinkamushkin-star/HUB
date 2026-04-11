package com.divehub.app.ui.diveeditor

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.divehub.app.R
import com.divehub.app.ui.theme.IosDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer

@Composable
fun DiveEditorRoute() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableIntStateOf(0) } // 0 photo, 1 video
    var compareMode by remember { mutableIntStateOf(0) } // 0 after, 1 before
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    var processedVideo by remember { mutableStateOf<Uri?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) } // -1..1
    var contrast by remember { mutableFloatStateOf(1f) } // 0.5..1.8
    var saturation by remember { mutableFloatStateOf(1f) } // 0..2
    var videoSpeed by remember { mutableFloatStateOf(1f) }
    var trimStartSec by remember { mutableFloatStateOf(0f) }
    var trimEndSec by remember { mutableFloatStateOf(100f) }
    var videoDurationSec by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.dive_editor_title)
    val modePhoto = stringResource(R.string.dive_editor_mode_photo)
    val modeVideo = stringResource(R.string.dive_editor_mode_video)
    val compareAfter = stringResource(R.string.dive_editor_compare_after)
    val compareBefore = stringResource(R.string.dive_editor_compare_before)
    val pickPhotoPrompt = stringResource(R.string.dive_editor_pick_photo_prompt)
    val pickVideoPrompt = stringResource(R.string.dive_editor_pick_video_prompt)
    val openGallery = stringResource(R.string.dive_editor_open_gallery)
    val openVideo = stringResource(R.string.dive_editor_open_video)
    val videoSelected = stringResource(R.string.dive_editor_video_selected)
    val processingEngine = stringResource(R.string.dive_editor_processing_engine)
    val brightnessLabel = stringResource(R.string.dive_editor_brightness)
    val contrastLabel = stringResource(R.string.dive_editor_contrast)
    val saturationLabel = stringResource(R.string.dive_editor_saturation)
    val speedLabel = stringResource(R.string.dive_editor_speed)
    val trimStartLabel = stringResource(R.string.dive_editor_trim_start)
    val trimEndLabel = stringResource(R.string.dive_editor_trim_end)
    val resetLabel = stringResource(R.string.dive_editor_reset)
    val saveLabel = stringResource(R.string.dive_editor_save)
    val selectPhotoFirst = stringResource(R.string.dive_editor_select_photo_first)
    val selectVideoFirst = stringResource(R.string.dive_editor_select_video_first)
    val openGalleryContentDescription = stringResource(R.string.dive_editor_open_gallery)
    val openVideoContentDescription = stringResource(R.string.dive_editor_open_video)
    val openImageError = stringResource(R.string.dive_editor_error_open_image)
    val decodeImageError = stringResource(R.string.dive_editor_error_decode_image)
    val createFileError = stringResource(R.string.dive_editor_error_create_file)
    val writeFileError = stringResource(R.string.dive_editor_error_write_file)
    val savedToGallery = stringResource(R.string.dive_editor_saved_to_gallery)
    val openVideoError = stringResource(R.string.dive_editor_error_open_video)
    val noSupportedTracksError = stringResource(R.string.dive_editor_error_no_supported_tracks)
    val saveAfterProcessingError = stringResource(R.string.dive_editor_error_save_after_processing)
    val writeResultError = stringResource(R.string.dive_editor_error_write_result)
    val processedSavedSuccess = stringResource(R.string.dive_editor_video_processed_saved)

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (mode == 0) {
            selectedImage = uri
            selectedVideo = null
            processedVideo = null
        } else {
            selectedVideo = uri
            selectedImage = null
            processedVideo = null
            if (uri != null) {
                val mmr = MediaMetadataRetriever()
                runCatching {
                    mmr.setDataSource(context, uri)
                    val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    videoDurationSec = (ms / 1000f).coerceAtLeast(1f)
                    trimStartSec = 0f
                    trimEndSec = videoDurationSec
                }
                runCatching { mmr.release() }
            }
        }
    }

    val matrix = remember(brightness, contrast, saturation) {
        val b = brightness * 255f
        val c = contrast * saturation
        val t = (1f - c) * 128f
        ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t + b,
                0f, c, 0f, 0f, t + b,
                0f, 0f, c, 0f, t + b,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = IosDesign.ScreenPadding, vertical = IosDesign.SectionSpacing)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { mode = 0; compareMode = 0 }) { Text(modePhoto) }
            OutlinedButton(onClick = { mode = 1; compareMode = 0 }) { Text(modeVideo) }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = compareMode == 0,
                onClick = { compareMode = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(compareAfter) }
            SegmentedButton(
                selected = compareMode == 1,
                onClick = { compareMode = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(compareBefore) }
        }
        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = IosDesign.BubbleCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
        ) {
            if (mode == 0 && selectedImage == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(pickPhotoPrompt)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = openGalleryContentDescription)
                        Spacer(Modifier.height(1.dp))
                        Text("  $openGallery")
                    }
                }
            } else if (mode == 0) {
                AsyncImage(
                    model = selectedImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (compareMode == 1) null else ColorFilter.colorMatrix(matrix),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else if (selectedVideo == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(pickVideoPrompt)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = openVideoContentDescription)
                        Spacer(Modifier.height(1.dp))
                        Text("  $openVideo")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(if (compareMode == 1 || processedVideo == null) selectedVideo else processedVideo)
                                setMediaController(MediaController(ctx))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    runCatching {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            mp.playbackParams = mp.playbackParams.setSpeed(videoSpeed)
                                        }
                                    }
                                    start()
                                }
                            }
                        },
                        update = { vv ->
                            runCatching {
                                vv.setVideoURI(if (compareMode == 1 || processedVideo == null) selectedVideo else processedVideo)
                                vv.start()
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(videoSelected, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = IosDesign.CardCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(processingEngine)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // Auto preset
                            brightness = 0.06f
                            contrast = 1.15f
                            saturation = 1.1f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Auto") }
                    OutlinedButton(
                        onClick = {
                            // SeaThru-like preset
                            brightness = 0.1f
                            contrast = 1.25f
                            saturation = 1.3f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("SeaThru") }
                    OutlinedButton(
                        onClick = {
                            // Cursor-like softer preset
                            brightness = 0.03f
                            contrast = 1.08f
                            saturation = 1.05f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Cursor") }
                }
                Spacer(Modifier.height(8.dp))
                if (mode == 0) {
                    Text("$brightnessLabel ${(brightness * 100).toInt()}%")
                    Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -1f..1f)
                    Text("$contrastLabel ${(contrast * 100).toInt()}%")
                    Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.8f)
                    Text("$saturationLabel ${(saturation * 100).toInt()}%")
                    Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f)
                } else {
                    Text("$speedLabel ${(videoSpeed * 100).toInt()}%")
                    Slider(value = videoSpeed, onValueChange = { videoSpeed = it }, valueRange = 0.5f..2f)
                    Text("$trimStartLabel ${trimStartSec.toInt()}s")
                    Slider(
                        value = trimStartSec,
                        onValueChange = { trimStartSec = it.coerceAtMost(trimEndSec) },
                        valueRange = 0f..videoDurationSec.coerceAtLeast(1f),
                    )
                    Text("$trimEndLabel ${trimEndSec.toInt()}s")
                    Slider(
                        value = trimEndSec,
                        onValueChange = { trimEndSec = it.coerceAtLeast(trimStartSec) },
                        valueRange = 0f..videoDurationSec.coerceAtLeast(1f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            brightness = 0f
                            contrast = 1f
                            saturation = 1f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(resetLabel) }
                    Button(
                        onClick = {
                            scope.launch {
                                status = if (mode == 0) {
                                    val uri = selectedImage
                                    if (uri == null) selectPhotoFirst else {
                                        saveEditedImage(
                                            context = context,
                                            source = uri,
                                            matrix = matrix,
                                            openImageError = openImageError,
                                            decodeImageError = decodeImageError,
                                            createFileError = createFileError,
                                            writeFileError = writeFileError,
                                            savedToGallery = savedToGallery,
                                        )
                                    }
                                } else {
                                    val uri = selectedVideo
                                    if (uri == null) selectVideoFirst else {
                                        exportProcessedVideo(
                                            context = context,
                                            source = uri,
                                            trimStartSec = trimStartSec,
                                            trimEndSec = trimEndSec,
                                            speed = videoSpeed,
                                            openVideoError = openVideoError,
                                            noSupportedTracksError = noSupportedTracksError,
                                            saveAfterProcessingError = saveAfterProcessingError,
                                            writeResultError = writeResultError,
                                            processedSavedSuccess = processedSavedSuccess,
                                        )
                                    }
                                }
                                if (mode == 1 && selectedVideo != null) {
                                    processedVideo = createLastSavedVideoUri(context)
                                }
                            }
                        },
                        enabled = if (mode == 0) selectedImage != null else selectedVideo != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(saveLabel) }
                }
                if (!status.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status ?: "")
                }
            }
        }
    }
}

private fun createLastSavedVideoUri(context: android.content.Context): Uri? {
    val projection = arrayOf(MediaStore.Video.Media._ID)
    val orderBy = "${MediaStore.Video.Media.DATE_ADDED} DESC"
    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        orderBy,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
        }
    }
    return null
}

private suspend fun saveEditedImage(
    context: android.content.Context,
    source: Uri,
    matrix: ColorMatrix,
    openImageError: String,
    decodeImageError: String,
    createFileError: String,
    writeFileError: String,
    savedToGallery: String,
): String {
    return withContext(Dispatchers.IO) {
        val input: InputStream = context.contentResolver.openInputStream(source)
            ?: return@withContext openImageError
        val src = BitmapFactory.decodeStream(input) ?: return@withContext decodeImageError
        input.close()

        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val androidMatrix = android.graphics.ColorMatrix(
            floatArrayOf(
                matrix.values[0], matrix.values[1], matrix.values[2], matrix.values[3], matrix.values[4],
                matrix.values[5], matrix.values[6], matrix.values[7], matrix.values[8], matrix.values[9],
                matrix.values[10], matrix.values[11], matrix.values[12], matrix.values[13], matrix.values[14],
                matrix.values[15], matrix.values[16], matrix.values[17], matrix.values[18], matrix.values[19],
            ),
        )
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(androidMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        val fileName = "dive_editor_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DiveHub")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext createFileError
        context.contentResolver.openOutputStream(uri)?.use { os ->
            out.compress(Bitmap.CompressFormat.JPEG, 95, os)
        } ?: return@withContext writeFileError
        savedToGallery
    }
}

private suspend fun saveVideoCopy(context: android.content.Context, source: Uri): String {
    return withContext(Dispatchers.IO) {
        val fileName = "dive_editor_video_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DiveHub")
            }
        }
        val target = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext "Не удалось создать видеофайл"
        val input = context.contentResolver.openInputStream(source)
            ?: return@withContext "Не удалось открыть видео"
        val output = context.contentResolver.openOutputStream(target)
            ?: return@withContext "Не удалось записать видео"
        input.use { ins -> output.use { outs -> ins.copyTo(outs) } }
        "Видео сохранено в галерею"
    }
}

private suspend fun exportProcessedVideo(
    context: android.content.Context,
    source: Uri,
    trimStartSec: Float,
    trimEndSec: Float,
    speed: Float,
    openVideoError: String,
    noSupportedTracksError: String,
    saveAfterProcessingError: String,
    writeResultError: String,
    processedSavedSuccess: String,
): String = withContext(Dispatchers.IO) {
    val startUs = (trimStartSec * 1_000_000L).toLong().coerceAtLeast(0L)
    val endUs = (trimEndSec * 1_000_000L).toLong().coerceAtLeast(startUs + 1L)
    val safeSpeed = speed.coerceIn(0.5f, 2f)

    val tempFile = java.io.File(
        context.cacheDir,
        "dive_editor_tmp_${System.currentTimeMillis()}.mp4",
    )

    val inFd = context.contentResolver.openAssetFileDescriptor(source, "r")
        ?: return@withContext openVideoError
    val extractor = MediaExtractor()
    extractor.setDataSource(inFd.fileDescriptor, inFd.startOffset, inFd.length)

    val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val trackMap = mutableMapOf<Int, Int>()
    for (i in 0 until extractor.trackCount) {
        val format: MediaFormat = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            extractor.selectTrack(i)
            trackMap[i] = muxer.addTrack(format)
        }
    }
    if (trackMap.isEmpty()) {
        extractor.release()
        muxer.release()
        return@withContext noSupportedTracksError
    }
    muxer.start()

    val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
    val info = MediaCodec.BufferInfo()
    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

    while (true) {
        val track = extractor.sampleTrackIndex
        if (track < 0) break
        val sampleTimeUs = extractor.sampleTime
        if (sampleTimeUs < startUs) {
            extractor.advance()
            continue
        }
        if (sampleTimeUs > endUs) {
            extractor.advance()
            continue
        }
        val targetTrack = trackMap[track]
        if (targetTrack != null) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.flags = extractor.sampleFlags
            info.presentationTimeUs = ((sampleTimeUs - startUs) / safeSpeed).toLong()
            muxer.writeSampleData(targetTrack, buffer, info)
        }
        extractor.advance()
    }

    runCatching { muxer.stop() }
    extractor.release()
    muxer.release()
    inFd.close()

    val fileName = "dive_editor_video_${System.currentTimeMillis()}.mp4"
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DiveHub")
        }
    }
    val target = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext saveAfterProcessingError
    val input = tempFile.inputStream()
    val output = context.contentResolver.openOutputStream(target)
        ?: return@withContext writeResultError
    input.use { ins -> output.use { outs -> ins.copyTo(outs) } }
    runCatching { tempFile.delete() }
    processedSavedSuccess
}
