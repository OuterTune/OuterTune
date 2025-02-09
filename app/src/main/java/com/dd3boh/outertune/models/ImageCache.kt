/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.models

import android.graphics.Bitmap

/**
 * Cached image
 */
data class CachedBitmap(var path: String?, var image: Bitmap?, var resizedImage: Bitmap?)
class ImageCacheManager(val maxSize: Int) {
    private var bitmapCache = ArrayDeque<CachedBitmap>()

    /**
     * Retrieves an image from the cache
     */
    fun retrieveImage(path: String): CachedBitmap? {
        // do not remove the null check regardless of what kotlin or IDE tells you. it?.path
        return bitmapCache.firstOrNull { it?.path == path }
    }

    /**
     * Adds an image to the cache
     */
    fun cache(path: String, image: Bitmap?, resize: Boolean) {
        if (image == null) {
            return
        }

        // adhere to limit
        if (bitmapCache.size >= maxSize) {
            bitmapCache.removeFirst()
        }

        val existingCached = retrieveImage(path)
        if (existingCached == null) {
            // add the image
            if (resize) {
                bitmapCache.addLast(CachedBitmap(path, null, image))
            } else {
                bitmapCache.addLast(CachedBitmap(path, image, null))
            }
        } else {
            if (resize) {
                existingCached.resizedImage = image
            } else {
                existingCached.image = image
            }
        }
    }

    /**
     * Removes all cached images
     */
    fun purgeCache() {
        bitmapCache = ArrayDeque()
    }
}