/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.models

import android.util.Log
import androidx.compose.ui.util.fastFirstOrNull
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.constants.SCANNER_DEBUG
import com.dd3boh.outertune.ui.utils.uninitializedDirectoryTree
import com.dd3boh.outertune.utils.fixFilePath
import java.time.ZoneOffset

/**
 * A tree representation of local audio files
 *
 * @param path root directory start
 */
class DirectoryTree(path: String, var culmSongs: CulmSongs) {
    companion object {
        const val TAG = "DirectoryTree"
        var directoryUID = 0
    }

    /**
     * Directory name
     */
    var currentDir = path // file name

    /**
     * Full parent directory path
     */
    var parent: String = ""

    // folder contents
    var subdirs = ArrayList<DirectoryTree>()
    var files = ArrayList<Song>()

    val uid = directoryUID

    var isSkeleton = true

    init {
        // increment uid
        directoryUID++
    }

    /**
     * Instantiate a directory tree directly with songs
     */
    constructor(path: String, culmSongs: CulmSongs, files: ArrayList<Song>) : this(path, culmSongs) {
        this.files = files
    }

    /**
     * Instantiate a directory tree directly with subdirectories and songs
     */
    constructor(
        path: String,
        culmSongs: CulmSongs,
        subdirs: ArrayList<DirectoryTree>,
        files: ArrayList<Song>
    ) : this(path, culmSongs) {
        this.subdirs = subdirs
        this.files = files
    }

    fun insert(path: String, song: Song) {

        // add a file
        if (path.indexOf('/') == -1) {
            files.add(song)
            culmSongs.value++
            if (SCANNER_DEBUG)
                Log.v(TAG, "Adding song with path: $path")
            return
        }

        // the first directory before the .
        var tmpPath = if (path.first() == '/') path.substring(1) else path// assume all paths start with /
        val subdirPath = tmpPath.substringBefore('/')

        // create subdirs if they do not exist, then insert
        var existingSubdir: DirectoryTree? = subdirs.fastFirstOrNull { it.currentDir == subdirPath }
        if (existingSubdir == null) {
            val tree = DirectoryTree(subdirPath, culmSongs)
            tree.parent = if (parent == "") {
                currentDir
            } else if (parent == "/") {
                "/$currentDir"
            } else {
                "$parent/$currentDir"
            }
            tree.insert(tmpPath.substringAfter('/'), song)
            subdirs.add(tree)

        } else {
            existingSubdir.insert(tmpPath.substringAfter('/'), song)
        }
    }


    /**
     * Get the name of the file from full path, without any extensions
     */
    private fun getFileName(path: String?): String? {
        if (path == null) {
            return null
        }
        return path.substringAfterLast('/').substringBefore('.')
    }

    /**
     * Retrieves song object at path
     *
     * @return song at path, or null if it does not exist
     */
    fun getSong(path: String): Song? {
        Log.v(TAG, "Searching for song, at path: $path")

        // search for song in current dir
        if (path.indexOf('/') == -1) {
            val foundSong: Song = files.first { getFileName(it.song.localPath) == getFileName(path) }
            Log.v(TAG, "Searching for song, found?: ${foundSong.id} Name: ${foundSong.song.title}")
            return foundSong
        }

        // there is still subdirs to process
        var tmpPath = path
        if (path[path.length - 1] == '/') {
            tmpPath = path.substring(0, path.length - 1)
        }

        // the first directory before the .
        val subdirPath = tmpPath.substringBefore('/')

        // scan for matching subdirectory
        var existingSubdir: DirectoryTree? = null
        subdirs.forEach { subdir ->
            if (subdir.currentDir == subdirPath) {
                existingSubdir = subdir
                return@forEach
            }
        }

        // explore the subdirectory if it exists in
        return existingSubdir?.getSong(tmpPath.substringAfter('/'))
    }


    /**
     * Retrieve a list of all the songs
     */
    fun toList(): List<Song> {
        val songs = ArrayList<Song>()

        fun traverseTree(tree: DirectoryTree, result: ArrayList<Song>) {
            result.addAll(tree.files)
            tree.subdirs.forEach { traverseTree(it, result) }
        }

        traverseTree(this, songs)
        return songs
    }

    /**
     * Retrieve a list of all the songs in the current directory, adhering to sort preferences.
     */
    fun toSortedList(sortType: SongSortType, sortDescending: Boolean): List<Song> {
        val songs = files.toMutableList()

        // sort songs. Ignore any subfolder structure
        songs.sortBy {
            when (sortType) {
                SongSortType.CREATE_DATE -> it.song.inLibrary?.toEpochSecond(ZoneOffset.UTC).toString()
                SongSortType.MODIFIED_DATE -> it.song.getDateModifiedLong().toString()
                SongSortType.RELEASE_DATE -> it.song.getDateLong().toString()
                SongSortType.NAME -> it.song.title
                SongSortType.ARTIST -> it.artists.firstOrNull()?.name
                SongSortType.PLAY_TIME -> it.song.totalPlayTime.toString()
                SongSortType.PLAY_COUNT -> it.playCount.toString()
            }
        }

        if (sortDescending) {
            songs.reverse()
        }
        return songs
    }

    /**
     * Retrieve a list of all the songs in the current directory including subdirectories, adhering to sort preferences.
     * Subfolder structure will be completely ignored.
     */
    fun toSortedListRecursive(sortType: SongSortType, sortDescending: Boolean): List<Song> {
        val songs = ArrayList<Song>()

        fun traverseTree(tree: DirectoryTree, result: ArrayList<Song>) {
            result.addAll(tree.files)
            tree.subdirs.forEach { traverseTree(it, result) }
        }

        traverseTree(this, songs)

        // sort songs. Ignore any subfolder structure
        songs.sortBy {
            when (sortType) {
                SongSortType.CREATE_DATE -> it.song.inLibrary?.toEpochSecond(ZoneOffset.UTC).toString()
                SongSortType.MODIFIED_DATE -> it.song.getDateModifiedLong().toString()
                SongSortType.RELEASE_DATE -> it.song.getDateLong().toString()
                SongSortType.NAME -> it.song.title
                SongSortType.ARTIST -> it.artists.firstOrNull()?.name
                SongSortType.PLAY_TIME -> it.song.totalPlayTime.toString()
                SongSortType.PLAY_COUNT -> it.playCount.toString()
            }
        }

        if (sortDescending) {
            songs.reverse()
        }
        return songs
    }

    /**
     * Retrieves a modified version of this DirectoryTree.
     * All folders are recognized to be top level folders
     */
    fun getFlattenedSubdirs(includeEmpty: Boolean = false): List<DirectoryTree> {
        val result = ArrayList<DirectoryTree>()
        getSubdirsRecursive(this, result, includeEmpty = includeEmpty)
        return result
    }

    fun getSubDir(path: String): DirectoryTree {
        val result = ArrayList<DirectoryTree>()
        getSubdirsRecursive(this, result, includeEmpty = true)
        return result.firstOrNull { fixFilePath(it.getFullPath()) == fixFilePath(path) } ?: uninitializedDirectoryTree
    }

    /**
     * Migrate emulated/0 path to "Internal" within the DirectoryTree.
     * This operation makes these edits directly to this object.
     * Calling this method on a tree that already has been migrated does nothing.
     *
     *
     * Why is this even necessary? Android internal volume is stored under "storage/emulated/0",
     * however for external volumes (like ext. sdcards), they are stored under "storage/<id>".
     * Flatten this to make the UI require less pointless clicks.
     *
     * @return This object, after migrating
     */
    fun androidStorageWorkaround(): DirectoryTree {
        if (currentDir == "/" && subdirs.size == 1 && files.isEmpty()) {
            return DirectoryTree("/storage", culmSongs, subdirs.first().subdirs, subdirs.first().files)
        }

        return this
    }

    /**
     * Remove any single empty branches of the tree, aka. DirectoryTrees with no files,
     * but only one subdirectory.
     */
    fun trimRoot(): DirectoryTree {
        var pointer = this
        while (pointer.subdirs.size == 1 && pointer.files.isEmpty()) {
            pointer = pointer.subdirs[0]
        }

        this.currentDir = pointer.currentDir
        this.files = pointer.files
        this.subdirs = pointer.subdirs

        return this
    }

    fun getSquashedDir(): String {
        // get full path of blank folders
        // subdir size is 1, and filesize is 0

        if (subdirs.size != 1 && files.size != 0) {
            return currentDir
        } else {
            var ret = ""
            var isEmpty = true
            fun exploreSubdirs(dt: DirectoryTree) {
                if (!isEmpty || dt.subdirs.size != 1 || dt.files.isNotEmpty()) {
                    if (isEmpty) {
                        ret += "/${dt.currentDir}"
                    }
                    isEmpty = false
                    return
                } else {
                    ret += "/${dt.currentDir}"
                    dt.subdirs.forEach {
                        exploreSubdirs(it)
                    }
                }
            }
            exploreSubdirs(this)

            return ret.trimStart() { it == '/' }.trimEnd { it == '/' }
        }
    }

    fun getFullSquashedDir(): String {
        return fixFilePath((parent + "/" + getSquashedDir()))
    }

    fun getFullPath(): String = "$parent/$currentDir"

    /**
     * Crawl the directory tree, add the subdirectories with songs to the list
     * @param it
     * @param result
     */
    private fun getSubdirsRecursive(
        it: DirectoryTree,
        result: ArrayList<DirectoryTree>,
        includeEmpty: Boolean = false
    ) {
        if (includeEmpty || it.files.isNotEmpty()) {
            result.add(it)
        }

        if (it.subdirs.isNotEmpty()) {
            it.subdirs.forEach { getSubdirsRecursive(it, result, includeEmpty) }
        }
    }
}


data class CulmSongs(var value: Int)
