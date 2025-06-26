/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.utils

import android.Manifest
import android.os.Build
import com.dd3boh.outertune.constants.MAX_CONCURRENT_JOBS
import com.dd3boh.outertune.models.CulmSongs
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.utils.LmImageCacheMgr
import com.dd3boh.outertune.utils.fixFilePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

const val TAG = "LocalMediaUtils"

const val EXTRACTOR_TAG = "MetadataExtractor"

@OptIn(ExperimentalCoroutinesApi::class)
val scannerSession = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_JOBS)

// stuff to make this work
val MEDIA_PERMISSION_LEVEL =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
const val STORAGE_ROOT = "/storage/"
val ARTIST_SEPARATORS = Regex("\\s*;\\s*|\\s*ft\\.\\s*|\\s*feat\\.\\s*|\\s*&\\s*|\\s*,\\s*", RegexOption.IGNORE_CASE)
val uninitializedDirectoryTree = DirectoryTree(STORAGE_ROOT, CulmSongs(0))
private var cachedDirectoryTree: ArrayList<DirectoryTree> = ArrayList()
var imageCache: LmImageCacheMgr = LmImageCacheMgr()

/**
 * ==========================
 * Various misc helpers
 * ==========================
 */


/**
 * Get cached DirectoryTree
 */
fun getDirectoryTree(path: String): DirectoryTree {
    val yes = cachedDirectoryTree.firstOrNull { fixFilePath(it.getFullPath()) == fixFilePath(path) }

    if (yes != null) {
        return yes
    }
    return uninitializedDirectoryTree
}

/**
 * Cache a DirectoryTree
 */
fun cacheDirectoryTree(new: DirectoryTree) {
    // initiate with root's subdirs
    if (cachedDirectoryTree.isEmpty() && fixFilePath(new.getFullPath()) == STORAGE_ROOT) {
        val dirs = new.getFlattenedSubdirs(true)
        dirs.forEach { it.isSkeleton = !it.files.isEmpty() }
        cachedDirectoryTree.addAll(dirs)
        return
    }

    // update structure
    val match = cachedDirectoryTree.firstOrNull { fixFilePath(it.getFullPath()) == fixFilePath(new.getFullPath()) }
    if (match == null) {
        cachedDirectoryTree.add(new)
    } else {
        match.subdirs = new.subdirs
        match.files = new.files
        match.isSkeleton = new.isSkeleton
    }
}

fun clearDtCache() {
    cachedDirectoryTree.clear()
}