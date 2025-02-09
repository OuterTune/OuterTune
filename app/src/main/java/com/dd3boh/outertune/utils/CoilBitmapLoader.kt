/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.dd3boh.outertune.ui.utils.imageCache
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import java.util.concurrent.ExecutionException

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : androidx.media3.common.util.BitmapLoader {
    private val placeholderImage = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: error("Could not decode image data")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            // local images
            if (uri.toString().startsWith("/storage/")) {
                return@future imageCache.getLocalThumbnail(uri.toString(), false)?: placeholderImage
            }
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
            )
            if (result is ErrorResult) {
                throw ExecutionException(result.throwable)
            }
            try {
                (result.drawable as BitmapDrawable).bitmap
            } catch (e: Exception) {
                throw ExecutionException(e)
            }
        }
}