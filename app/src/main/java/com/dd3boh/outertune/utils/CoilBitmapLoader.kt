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
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.dd3boh.outertune.R
import com.dd3boh.outertune.di.ImageCache
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.math.min

class CoilBitmapLoader @Inject constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    @ImageCache private val imageCache: LmImageCacheMgr,
) : androidx.media3.common.util.BitmapLoader {

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
                return@future imageCache.getLocalThumbnail(uri.toString()) ?: imageCache.placeholderImage
            }
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false) // pixel access is not supported on Config#HARDWARE bitmaps
                    .build()
            )
            if (result is ErrorResult) {
                reportException(ExecutionException(result.throwable))
                return@future imageCache.placeholderImage
            }
            try {
                result.image!!.toBitmap()
            } catch (e: Exception) {
                reportException(ExecutionException(e))
                return@future imageCache.placeholderImage
            }
        }

    fun loadBitmapOrNull(uri: Uri): ListenableFuture<Bitmap?> =
        scope.future(Dispatchers.IO) {
            // local images
            if (uri.toString().startsWith("/storage/")) {
                return@future imageCache.getLocalThumbnail(uri.toString()) ?: imageCache.placeholderImage
            }
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false) // pixel access is not supported on Config#HARDWARE bitmaps
                    .build()
            )
            if (result is ErrorResult) {
                reportException(ExecutionException(result.throwable))
                return@future null
            }
            try {
                result.image!!.toBitmap()
            } catch (e: Exception) {
                reportException(ExecutionException(e))
                return@future null
            }
        }

    companion object {
        // TODO: re eval dimens after a few months
        /**
         * Draw a centered square app icon with the maximum possible size while maintaining aspect ratio.
         *
         * @param context
         * @param x Desired final x dimension
         * @param y Desired final y dimension
         * @param size Percentage size of valid draw frame. Must be a value between 0.0 and 1.0. For example, 0.8
         *      means that inner frame should be 80% of the size of the final frame, and centered within that frame.
         */
        fun drawPlaceholder(context: Context, x: Int = 2000, y: Int = 2000, size: Float = 0.8f): Bitmap {
            val padding = size.coerceIn(0f, 1f)
            val innerRecWidth = x * padding
            val innerRecHeight = y * padding

            val squareLength = min(innerRecWidth, innerRecHeight).toInt()
            val squareLeft = ((x - squareLength) / 2)
            val squareTop = ((y - squareLength) / 2)

            val drawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.placeholder_icon)
            val bitmap = Bitmap.createBitmap(x, y, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawable?.setBounds(squareLeft, squareTop, squareLeft + squareLength, squareTop + squareLength)
            drawable?.draw(canvas)
            return bitmap
        }
    }
}