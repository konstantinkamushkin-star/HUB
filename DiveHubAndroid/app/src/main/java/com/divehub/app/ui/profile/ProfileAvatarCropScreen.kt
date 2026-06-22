package com.divehub.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.R
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.theme.diveHubTopAppBarColors
import com.divehub.app.ui.util.fileProviderImageUri
import java.io.File
import kotlinx.coroutines.launch

private val AvatarCropEditSide: Dp = 320.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAvatarCropRoute(
    innerNav: NavController,
) {
    val parentEntry = remember(innerNav) {
        try {
            innerNav.getBackStackEntry(InnerRoutes.EditProfile)
        } catch (_: Exception) {
            null
        }
    }
    if (parentEntry == null) {
        LaunchedEffect(Unit) { innerNav.popBackStack() }
        return
    }
    val editVm: EditProfileViewModel = viewModel(
        viewModelStoreOwner = parentEntry,
    )
    val source by editVm.pendingAvatarCropUri.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var pan by remember(source) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(source) {
        scale = 1f
        pan = Offset.Zero
    }
    val nonNullSource = source
    if (nonNullSource == null) {
        LaunchedEffect(Unit) { innerNav.popBackStack() }
        return
    }
    val uri: Uri = nonNullSource

    fun exportAndApply(context: Context): Boolean {
        val previewPx = with(density) { AvatarCropEditSide.toPx() }
        val jpeg = ProfileAvatarCropMath.renderSquareJpeg(
            context = context,
            sourceUri = uri,
            previewSidePx = previewPx,
            userScale = scale,
            offset = pan,
        ) ?: return false
        val out = File(context.cacheDir, "profile_avatar_sq_${System.currentTimeMillis()}.jpg")
        out.writeBytes(jpeg)
        val outUri = fileProviderImageUri(context, out)
        editVm.setReadyAvatarCropped(outUri)
        return true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = diveHubTopAppBarColors(),
                title = { Text(stringResource(R.string.profile_avatar_crop_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            editVm.clearPendingOnly()
                            innerNav.popBackStack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (exportAndApply(ctx)) {
                                innerNav.popBackStack()
                            } else {
                                scope.launch { snackbar.showSnackbar(ctx.getString(R.string.profile_avatar_crop_error)) }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.common_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val side = AvatarCropEditSide
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ProfileAvatarCropEditor(
                    imageUri = uri,
                    side = side,
                    userScale = scale,
                    onUserScaleChange = { scale = it },
                    panOffset = pan,
                    onPanOffsetChange = { pan = it },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.profile_edit_avatar_crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
