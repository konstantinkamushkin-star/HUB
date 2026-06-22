package com.divehub.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared by [EditProfileRoute] and [ProfileAvatarCropRoute] (same [androidx.navigation.NavBackStackEntry] store).
 * Holds the avatar pipeline: pick/capture → full-screen crop → [readyAvatarUri] for preview + save.
 */
class EditProfileViewModel : ViewModel() {
    private val _pendingAvatarCropUri = MutableStateFlow<Uri?>(null)
    val pendingAvatarCropUri: StateFlow<Uri?> = _pendingAvatarCropUri.asStateFlow()

    private val _readyAvatarUri = MutableStateFlow<Uri?>(null)
    val readyAvatarUri: StateFlow<Uri?> = _readyAvatarUri.asStateFlow()

    fun startAvatarCropFromSource(source: Uri) {
        _pendingAvatarCropUri.value = source
    }

    fun setReadyAvatarCropped(exportUri: Uri) {
        _readyAvatarUri.value = exportUri
        _pendingAvatarCropUri.value = null
    }

    fun clearPendingOnly() {
        _pendingAvatarCropUri.value = null
    }

    fun clearPickedAvatar() {
        _pendingAvatarCropUri.value = null
        _readyAvatarUri.value = null
    }
}
