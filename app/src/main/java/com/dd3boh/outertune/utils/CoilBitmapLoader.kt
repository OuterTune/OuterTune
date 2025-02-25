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
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.dd3boh.outertune.R
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
    private val placeholderImage: Bitmap = drawPlaceholder()

    fun drawPlaceholder(l: Int = 1280, w: Int = 720): Bitmap {
        val drawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.small_icon)
        val bitmap = Bitmap.createBitmap(l, w, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // center without distortion. Expects square drawable
        if (l > w) {
            val start = (l - w) / 2
            drawable?.setBounds(start, 0, start + w, w)
        } else {
            val wStart = (w - l) / 2
            drawable?.setBounds(0, wStart, l, wStart + w)
        }

        drawable?.draw(canvas)
        return bitmap
    }

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
                return@future imageCache.getLocalThumbnail(uri.toString(), false) ?: placeholderImage
            }
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
            )
            if (result is ErrorResult) {
                reportException(ExecutionException(result.throwable))
                return@future placeholderImage
            }
            try {
                (result.drawable as BitmapDrawable).bitmap
            } catch (e: Exception) {
                reportException(ExecutionException(e))
                return@future placeholderImage
            }
        }
}