package com.divehub.app.ui.diveeditor

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import coil.compose.AsyncImage
import com.divehub.app.R
import com.divehub.app.diveHubApp
import com.divehub.app.data.LogbookRepository
import com.divehub.app.util.absoluteMediaUrl
import com.divehub.app.ui.theme.IosDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL

@Composable
fun DiveEditorRoute() {
    val context = LocalContext.current
    val graph = context.diveHubApp().graph
    val scope = rememberCoroutineScope()
    var compareMode by remember { mutableIntStateOf(0) } // 0 after, 1 before, 2 split
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var showAppGallery by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.dive_editor_title)
    val compareAfter = stringResource(R.string.dive_editor_compare_after)
    val compareBefore = stringResource(R.string.dive_editor_compare_before)
    val compareSplit = stringResource(R.string.dive_editor_compare_split)
    val pickPhotoPrompt = stringResource(R.string.dive_editor_pick_photo_prompt)
    val openGallery = stringResource(R.string.dive_editor_open_gallery)
    val processingEngine = stringResource(R.string.dive_editor_processing_engine)
    val brightnessLabel = stringResource(R.string.dive_editor_brightness)
    val contrastLabel = stringResource(R.string.dive_editor_contrast)
    val saturationLabel = stringResource(R.string.dive_editor_saturation)
    val resetLabel = stringResource(R.string.dive_editor_reset)
    val saveLabel = stringResource(R.string.dive_editor_save)
    val selectPhotoFirst = stringResource(R.string.dive_editor_select_photo_first)
    val openGalleryContentDescription = stringResource(R.string.dive_editor_open_gallery)
    val openImageError = stringResource(R.string.dive_editor_error_open_image)
    val decodeImageError = stringResource(R.string.dive_editor_error_decode_image)
    val createFileError = stringResource(R.string.dive_editor_error_create_file)
    val writeFileError = stringResource(R.string.dive_editor_error_write_file)
    val savedToGallery = stringResource(R.string.dive_editor_saved_to_gallery)
    val videoTeaserTitle = stringResource(R.string.dive_editor_video_coming_soon_title)
    val videoTeaserBody = stringResource(R.string.dive_editor_video_coming_soon_body)

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        selectedImage = uri
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = IosDesign.CardCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(videoTeaserTitle, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        videoTeaserBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = compareMode == 0,
                onClick = { compareMode = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) { Text(compareAfter) }
            SegmentedButton(
                selected = compareMode == 1,
                onClick = { compareMode = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) { Text(compareBefore) }
            SegmentedButton(
                selected = compareMode == 2,
                onClick = { compareMode = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) { Text(compareSplit) }
        }
        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = IosDesign.BubbleCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
        ) {
            if (selectedImage == null) {
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
                        Spacer(Modifier.width(8.dp))
                        Text(openGallery)
                    }
                }
            } else if (compareMode == 2) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    AsyncImage(
                        model = selectedImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
                    )
                    AsyncImage(
                        model = selectedImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(matrix),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            } else {
                AsyncImage(
                    model = selectedImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (compareMode == 1) null else ColorFilter.colorMatrix(matrix),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showAppGallery = true }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.dive_editor_open_app_gallery))
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
                            brightness = 0.06f
                            contrast = 1.15f
                            saturation = 1.1f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Auto") }
                    OutlinedButton(
                        onClick = {
                            brightness = 0.1f
                            contrast = 1.25f
                            saturation = 1.3f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("SeaThru") }
                    OutlinedButton(
                        onClick = {
                            brightness = 0.03f
                            contrast = 1.08f
                            saturation = 1.05f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Cursor") }
                }
                Spacer(Modifier.height(8.dp))
                Text("$brightnessLabel ${(brightness * 100).toInt()}%")
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -1f..1f)
                Text("$contrastLabel ${(contrast * 100).toInt()}%")
                Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.8f)
                Text("$saturationLabel ${(saturation * 100).toInt()}%")
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f)
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
                                status = run {
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
                                }
                            }
                        },
                        enabled = selectedImage != null,
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

    if (showAppGallery) {
        DiveEditorAppGallerySheet(
            graph = graph,
            onPick = { uri ->
                selectedImage = uri
                showAppGallery = false
            },
            onDismiss = { showAppGallery = false },
        )
    }
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
        val input: InputStream = when (source.scheme?.lowercase()) {
            "http", "https" -> runCatching { URL(source.toString()).openStream() }.getOrNull()
            else -> context.contentResolver.openInputStream(source)
        } ?: return@withContext openImageError
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiveEditorAppGallerySheet(
    graph: com.divehub.app.AppGraph,
    onPick: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<String>>(emptyList()) }
    var imageRoot by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        loadError = null
        imageRoot = runCatching { graph.tokenStore.getRootBaseUrl() }.getOrElse { "" }
        runCatching { LogbookRepository(graph).list() }
            .onSuccess { logs ->
                photos = logs.flatMap { it.photoUrls ?: emptyList() }.distinct()
                loading = false
            }
            .onFailure { e ->
                loadError = e.message
                loading = false
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.dive_editor_app_gallery_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                loadError != null -> Column {
                    Text(loadError ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        scope.launch {
                            loading = true
                            loadError = null
                            imageRoot = runCatching { graph.tokenStore.getRootBaseUrl() }.getOrElse { "" }
                            runCatching { LogbookRepository(graph).list() }
                                .onSuccess { logs ->
                                    photos = logs.flatMap { it.photoUrls ?: emptyList() }.distinct()
                                    loading = false
                                }
                                .onFailure { e ->
                                    loadError = e.message
                                    loading = false
                                }
                        }
                    }) { Text(stringResource(R.string.common_retry)) }
                }
                photos.isEmpty() -> Text(
                    stringResource(R.string.dive_editor_app_gallery_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(300.dp)) {
                        items(items = photos, key = { it }) { stored ->
                            val full = absoluteMediaUrl(imageRoot, stored)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                onClick = { onPick(Uri.parse(full)) },
                            ) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = full,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(56.dp)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stored.substringAfterLast('/'),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}
