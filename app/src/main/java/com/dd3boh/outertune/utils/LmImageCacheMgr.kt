/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.dd3boh.outertune.models.ImageCacheManager
import javax.inject.Inject

class LmImageCacheMgr @Inject constructor(

) {

    private var localImageCache = ImageCacheManager(300)


    /**
     * Extract the album art from the audio file. The image is not resized
     * (did you mean to use getLocalThumbnail(path: String?, resize: Boolean)?).
     *
     * @param path Full path of audio file
     */
    fun getLocalThumbnail(path: String?): Bitmap? = getLocalThumbnail(path, false)

    /**
     * Extract the album art from the audio file
     *
     * @param path Full path of audio file
     * @param resize Whether to resize the Bitmap to a thumbnail size (300x300)
     */
    fun getLocalThumbnail(path: String?, resize: Boolean): Bitmap? {
        if (path == null) {
            return null
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
            localImageCache.cache(path, null, resize)
            null
        } ?: return null

        if (resize) {
            image = Bitmap.createScaledBitmap(image, 100, 100, false)
        }

        localImageCache.cache(path, image, resize)
        return image
    }

    fun purgeCache() {
        localImageCache.purgeCache()
    }
}