package com.dd3boh.outertune.utils.scanners

import com.dd3boh.outertune.models.SongTempData
import java.io.File

class FFmpegScanner() : MetadataScanner {
    override fun getAllMetadataFromFile(file: File): SongTempData {
        throw NotImplementedError()
    }

    companion object {
        const val VERSION_STRING = "N/A"
    }
}
