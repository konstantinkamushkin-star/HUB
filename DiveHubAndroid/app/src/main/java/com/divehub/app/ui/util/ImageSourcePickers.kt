package com.divehub.app.ui.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun fileProviderImageUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file,
    )
