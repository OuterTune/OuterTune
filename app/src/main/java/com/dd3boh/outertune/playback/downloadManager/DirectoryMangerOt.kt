package com.dd3boh.outertune.playback.downloadManager

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.utils.reportException
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scanDfRecursive
import com.dd3boh.outertune.utils.scanners.documentFileFromUri
import java.io.IOException
import java.io.InputStream

class DownloadDirectoryManagerOt(private val context: Context, private var dir: Uri, extraDirs: List<Uri>) {
    val TAG = DownloadDirectoryManagerOt::class.simpleName.toString()
    var mainDir: DocumentFile? = null
    var allDirs: List<DocumentFile> = mutableListOf()

    init {
        Log.i(TAG, "Initializing download manager: $dir")
        try {
            mainDir = documentFileFromUri(context, dir)
            if (mainDir == null || !mainDir!!.isDirectory) {
                throw IOException("Invalid directory")
            }

            if (!mainDir!!.listFiles().any { it.name == ".nomedia" }) {
                documentFileFromUri(context, dir)?.createFile("audio/mka", ".nomedia")
            }

            val newAllDirs = mutableListOf<DocumentFile>()
            newAllDirs.add(mainDir!!)
            if (extraDirs.isNotEmpty()) {
                newAllDirs.addAll(
                    documentFileFromUri(context, extraDirs.filterNot { it == dir }).filter { it.isDirectory }
                )
            }
            allDirs = newAllDirs.toList()
            Log.i(TAG, "Download manager initialized successfully. ${allDirs.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate download manager: " + e.message)
            mainDir = null
            allDirs = mutableListOf()
            reportException(e)
//            Toast.makeText(context, "Failed to initiate download manager: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    fun deleteFile(mediaId: String): Boolean {
        val file = isExists(mediaId)
        return file?.delete() == true
    }

    fun saveFile(mediaId: String, input: InputStream, displayName: String?): Uri? {
        val resolver = context.contentResolver
        val directory = DocumentFile.fromTreeUri(context, dir)

        if (directory == null || !directory.isDirectory) {
            throw IOException("Invalid directory")
        }

        val fileName = "$displayName [$mediaId].mka"
        val newFile = directory.createFile("audio/mka", fileName)

        newFile?.uri?.let { uri ->
            resolver.openOutputStream(uri)?.use { out ->
                input.copyTo(out)
            }
            return uri
        }

        return null
    }

    fun isExists(mediaId: String): DocumentFile? {
        val result = ArrayList<DocumentFile>()
        for (dir in allDirs) {
            scanDfRecursive(dir, result, true) { it.substringAfterLast('[').substringBeforeLast(']') == mediaId }
        }
        return result.firstOrNull()
    }

    fun getFilePathIfExists(mediaId: String): Uri? {
        var existingFile: DocumentFile? = isExists(mediaId)
        return existingFile?.uri
    }

    fun getMissingFiles(mediaId: List<Song>): List<Song> {
        val missingFiles = mediaId.toMutableSet()
        val result = getAvailableFiles()
        missingFiles.removeIf { f -> result.any { it.key == f.id } }
        return missingFiles.toList()
    }

    fun getAvailableFiles(): Map<String, Uri> {
        val availableFiles = HashMap<String, Uri>()
        val result = ArrayList<DocumentFile>()
        for (dir in allDirs) {
            scanDfRecursive(dir, result, true)
        }

        for (file in result) {
            val path = file.name ?: continue
            availableFiles.put(path.substringAfterLast('[').substringBeforeLast(']'), file.uri)
        }
        return availableFiles
    }

    fun getMainDlStorageUsage(): Long {
        if (mainDir == null) return -1L
        val result = ArrayList<DocumentFile>()
        scanDfRecursive(mainDir!!, result, true)

        return result.filter { it.name != null }.sumOf { it.length() }
    }

    fun getTotalDlStorageUsage(): Long {
        if (allDirs.isEmpty()) return 0
        val result = ArrayList<DocumentFile>()
        for (dir in allDirs) {
            scanDfRecursive(dir, result, true)
        }

        return result.filter { it.name != null }.sumOf { it.length() }
    }

    fun getExtraDlStorageUsage(): Long {
        val dirs = allDirs.filter { it != mainDir }
        if (dirs.isEmpty()) return 0
        val result = ArrayList<DocumentFile>()
        for (dir in dirs) {
            scanDfRecursive(dir, result, true)
        }

        return result.filter { it.name != null }.sumOf { it.length() }
    }
}
