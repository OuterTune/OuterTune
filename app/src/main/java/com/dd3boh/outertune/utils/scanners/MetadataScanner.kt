/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils.scanners

import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.models.SongTempData
import java.io.File


/**
 * Returns metadata information
 */
interface MetadataScanner {

    /**
     * Given a path to a file, extract necessary metadata.
     *
     * @param path Full file path
     */
    fun getAllMetadataFromPath(path: String): SongTempData


    /**
     * Given a path to a file, extract necessary metadata.
     *
     * @param file Full file path
     */
    fun getAllMetadataFromFile(file: File): SongTempData
}

/**
 * A wrapper containing extra raw metadata that MediaStore fails to read properly
 */
data class ExtraMetadataWrapper(val artists: String?, val genres: String?, val date: String?, var format: FormatEntity?)