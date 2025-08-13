/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.core.graphics.scale
import com.dd3boh.outertune.models.ImageCacheManager
import com.dd3boh.outertune.utils.CoilBitmapLoader.Companion.drawPlaceholder

class LmImageCacheMgr(context: Context, val placeholderImage: Bitmap = drawPlaceholder(context, 2000, 2000)) {

    private var localImageCache = ImageCacheManager(300)


    /**
     * Extract the album art from the audio file. The image is not resized
     *
     * @param path Full path of audio file
     */
    fun getLocalThumbnail(path: String?): Bitmap? = getLocalThumbnail(path, false)

    /**
     * Extract the album art from the audio file
     *
     * @param path Full path of audio file
     * @param resize Whether to resize the Bitmap to a thumbnail size (300x300)
     * @param fallback Use a default fallback image if no image could be resolved
     */
    fun getLocalThumbnail(path: String?, resize: Boolean): Bitmap {
        if (path == null) {
            return placeholderImage
        }
        // try cache lookup
        val cachedImage = if (resize) {
            localImageCache.retrieveImage(path)?.resizedImage
        } else {
            localImageCache.retrieveImage(path)?.image
        }

        if (cachedImage == null) {
//        Timber.tag(TAG).d("Cache miss on $path")
        } else {
            return cachedImage
        }

        val mData = MediaMetadataRetriever()

        var image: Bitmap = try {
            mData.setDataSource(path)
            val art = mData.embeddedPicture
            BitmapFactory.decodeByteArray(art, 0, art!!.size)
        } catch (e: Exception) {
            localImageCache.cache(path, placeholderImage, resize)
            null
        } ?: return placeholderImage

        if (resize) {
            image = image.scale(100, 100, false)
        }

        localImageCache.cache(path, image, resize)
        return image
    }

    fun purgeCache() {
        localImageCache.purgeCache()
    }
}