/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.utils

import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.utils.LmImageCacheMgr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

const val TAG = "LocalMediaUtils"

/**
 * For easier debugging, set SCANNER_CRASH_AT_FIRST_ERROR to stop at first error
 */
const val SCANNER_CRASH_AT_FIRST_ERROR = false // crash at first FFmpeg scanner error. Currently not implemented
const val SYNC_SCANNER = false // true will not use multithreading for scanner
const val MAX_CONCURRENT_JOBS = 4
const val SCANNER_DEBUG = false

const val EXTRACTOR_DEBUG = false
const val DEBUG_SAVE_OUTPUT = false // ignored (will be false) when EXTRACTOR_DEBUG IS false
const val EXTRACTOR_TAG = "MetadataExtractor"

@OptIn(ExperimentalCoroutinesApi::class)
val scannerSession = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_JOBS)

// stuff to make this work
const val STORAGE_ROOT = "/storage/"
const val DEFAULT_SCAN_PATH = "/tree/primary:Music\n"
val ARTIST_SEPARATORS = Regex("\\s*;\\s*|\\s*ft\\.\\s*|\\s*feat\\.\\s*|\\s*&\\s*|\\s*,\\s*", RegexOption.IGNORE_CASE)
private var cachedDirectoryTree: DirectoryTree? = null

var imageCache: LmImageCacheMgr = LmImageCacheMgr()

/**
 * ==========================
 * Various misc helpers
 * ==========================
 */


/**
 * Get cached DirectoryTree
 */
fun getDirectoryTree(): DirectoryTree? {
    if (cachedDirectoryTree == null) {
        return null
    }
    return cachedDirectoryTree
}

/**
 * Cache a DirectoryTree
 */
fun cacheDirectoryTree(new: DirectoryTree?) {
    cachedDirectoryTree = new
}