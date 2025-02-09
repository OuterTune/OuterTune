/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.models

import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.ui.utils.SCANNER_DEBUG
import com.dd3boh.outertune.ui.utils.STORAGE_ROOT
import timber.log.Timber
import java.time.ZoneOffset

/**
 * A tree representation of local audio files
 *
 * @param path root directory start
 */
class DirectoryTree(path: String) {
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

    init {
        // increment uid
        directoryUID++
    }

    /**
     * Instantiate a directory tree directly with songs
     */
    constructor(path: String, files: ArrayList<Song>) : this(path) {
        this.files = files
    }

    /**
     * Instantiate a directory tree directly with subdirectories and songs
     */
    constructor(path: String, subdirs: ArrayList<DirectoryTree>, files: ArrayList<Song>) : this(path) {
        this.subdirs = subdirs
        this.files = files
    }

    fun insert(path: String, song: Song) {
//        println("curr path =" + path)

        // add a file
        if (path.indexOf('/') == -1) {
            files.add(song)
            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("Adding song with path: $path")
            return
        }

        // there is still subdirs to process
        var tmpPath = path
        if (path[path.length - 1] == '/') {
            tmpPath = path.substring(0, path.length - 1)
        }

        // the first directory before the .
        val subdirPath = tmpPath.substringBefore('/')

        // create subdirs if they do not exist, then insert
        var existingSubdir: DirectoryTree? = null
        subdirs.forEach { subdir ->
            if (subdir.currentDir == subdirPath) {
                existingSubdir = subdir
                return@forEach
            }
        }

        if (existingSubdir == null) {
            val tree = DirectoryTree(subdirPath)
            tree.parent = "$parent/$currentDir"
            tree.insert(tmpPath.substringAfter('/'), song)
            subdirs.add(tree)

        } else {
            existingSubdir!!.insert(tmpPath.substringAfter('/'), song)
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
        Timber.tag(TAG).d("Searching for song, at path: $path")

        // search for song in current dir
        if (path.indexOf('/') == -1) {
            val foundSong: Song = files.first { getFileName(it.song.localPath) == getFileName(path) }
            Timber.tag(TAG).d("Searching for song, found?: ${foundSong.id} Name: ${foundSong.song.title}")
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
        if (existingSubdir == null) {
            return null
        } else {
            return existingSubdir!!.getSong(tmpPath.substringAfter('/'))
        }
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
     * Retrieve a list of all the songs, adhering to sort preferences. Subfolder structure will be completely ignored.
     */
    fun toSortedList(sortType: SongSortType, sortDescending: Boolean): List<Song> {
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
                SongSortType.PLAY_COUNT -> it.song.totalPlayTime.toString() // TODO: This properly
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
    fun toFlattenedTree(): DirectoryTree {
        val result = DirectoryTree(STORAGE_ROOT)
        getSubdirsRecursive(this, result.subdirs)
        return result
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
        var emulated: DirectoryTree? = null
        var zero: DirectoryTree? = null

        subdirs.forEach { subdir ->
            if (subdir.currentDir == "emulated") {
                emulated = subdir

                subdir.subdirs.forEach {
                    if (it.currentDir == "0") {
                        zero = it
                    }
                }
            }
        }

        // replace the emulated/0 path with "Internal"
        if (emulated != null && zero != null) {
            val newInternalStorage = DirectoryTree("Internal", zero!!.subdirs, zero!!.files)
            subdirs = ArrayList(subdirs.filterNot { it.currentDir == "emulated" })
            subdirs.add(newInternalStorage)
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

    /**
     * Crawl the directory tree, add the subdirectories with songs to the list
     * @param it
     * @param result
     */
    private fun getSubdirsRecursive(it: DirectoryTree, result: ArrayList<DirectoryTree>) {
        if (it.files.size > 0) {
            result.add(DirectoryTree(it.currentDir, it.files))
        }

        if (it.subdirs.size > 0) {
            it.subdirs.forEach { getSubdirsRecursive(it, result) }
        }
    }
}