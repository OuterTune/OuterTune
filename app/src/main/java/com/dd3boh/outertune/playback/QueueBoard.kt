/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.playback

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import com.dd3boh.outertune.constants.PersistentQueueKey
import com.dd3boh.outertune.constants.QUEUE_DEBUG
import com.dd3boh.outertune.db.entities.QueueEntity
import com.dd3boh.outertune.extensions.currentMetadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min


/**
 * Multiple queues manager. Methods will not automatically (re)load queues into the player unless
 * otherwise explicitly stated.
 */
class QueueBoard(
    private val player: MusicService,
    queues: MutableList<MultiQueueObject> = ArrayList(),
    private var maxQueues: Int
) {
    private val TAG = QueueBoard::class.simpleName.toString()

    val masterQueues: SnapshotStateList<MultiQueueObject> = mutableStateListOf()
    private var masterIndex = masterQueues.size - 1 // current queue index
    var detachedHead = false
    var initialized = false

    init {
        if (maxQueues < 0) {
            maxQueues = 1
        }
        if (!queues.isEmpty()) {
            masterQueues.addAll(queues.subList(0, min(queues.size, maxQueues)))
        }
    }

    /**
     * ========================
     * Data structure management
     * ========================
     */


    /**
     * Regenerate indexes of queues to reflect their position
     */
    private fun regenerateIndexes() {
        masterQueues.fastForEachIndexed { index, q -> q.index = index }
    }

    /**
     * Push this queue to top of the master queue list, and track set this as current queue
     *
     * @param item
     */
    private fun bubbleUp(item: MultiQueueObject) = bubbleUp(masterQueues.indexOf(item))

    /**
     * Push this queue at index to top of the master queue list, and track set this as current queue.
     *
     * @param index
     */
    private fun bubbleUp(index: Int) {
        if (index < 0 || index >= masterQueues.size) {
            Log.w(TAG, "Bubble up index out of bounds")
            return
        }

        val item = masterQueues[index]
        masterQueues.remove(item)
        masterQueues.add(item)
        masterIndex = masterQueues.size - 1

        regenerateIndexes()
        saveAllQueues(masterQueues)
    }

    /**
     * Add a new queue to the QueueBoard, or add to a queue if it exists.
     *
     * Depending on the circumstances, there can be varying behaviour.
     * 1. Queue does not exist: Queue is added as a new queue.
     * 2. Queue exists, and the contents are a perfect match (by songID): Current position (queuePos)
     *      index is updated. Queue itself is not modified.
     * 3. Queue exists, contents are different:
     *      delta is true: Extra items are added to the old queue. Current position is updated.
     *      delta is false: Items are added to the end of the queue, see 4.
     * 4. Items are purely added into the queue: Current position is NOT updated.
     *      When delta is false, this is "add mode". A new "+" suffix queue is spawned if it doesn't
     *      exist, and items are added to the end of the queue. We want queues with titles to represent
     *      the source (title), while the "+" suffix denotes a custom user queue where "anything goes".
     *
     * or add songs to queue it exists (and forceInsert is not true).
     *
     * @param title Title (id) of the queue
     * @param mediaList List of items to add
     * @param player Player object
     * @param shuffled Whether to load a shuffled queue into the player
     * @param forceInsert When mediaList contains one item, force an insert instead of jumping to an
     *      item if it exists
     * @param replace Replace all items in the queue. This overrides forceInsert, delta
     * @param delta Takes not effect if forceInsert is false. Setting this to true will add only new
     *      songs, false will add all songs
     * @param isRadio Specify if this is a queue that supports continuation
     * @param startIndex Index/position to instantiate the new queue with. This value takes no effect
     * if the queue already exists
     *
     * @return Boolean whether a full reload of player items should be done. In some cases it may be possible to enqueue
     *      without interrupting playback. Currently this is only supported when adding to extension queues
     */
    fun addQueue(
        title: String,
        mediaList: List<MediaMetadata?>,
        shuffled: Boolean = false,
        forceInsert: Boolean = false,
        replace: Boolean = false,
        delta: Boolean = true,
        isRadio: Boolean = false,
        startIndex: Int = 0
    ): MultiQueueObject? {
        if (QUEUE_DEBUG)
            Log.d(
                TAG,
                "Adding to queue \"$title\". medialist size = ${mediaList.size}. forceInsert/replace/delta/startIndex = $forceInsert/$replace/$delta/$startIndex"
            )

        if (mediaList.isEmpty()) {
            return null
        }

        val match = masterQueues.firstOrNull { it.title == title } // look for matching queue. Title is uid
        if (match != null) { // found an existing queue
            // Titles ending in "+​" (u200B) signify a extension queue
            val anyExts = masterQueues.firstOrNull { it.title == match.title + " +\u200B" }
            if (replace) { // force replace
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: Replacing all queue items")

                mediaList.fastForEachIndexed { index, s ->
                    s?.shuffleIndex = index
                }

                match.replaceAll(mediaList.filterNotNull())
                match.queuePos = startIndex

                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                saveQueueSongs(match)
                return match
            }

            // don't add songs to the queue if it's just one EXISTING song AND the new medialist is a subset of what we have
            // UNLESS forced to
            val containsAll = mediaList.all { s -> match.queue.any { s?.id == it.id } } // if is subset
            if (containsAll && match.getSize() == mediaList.size && !forceInsert) { // jump to song, don't add
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: jump only")
                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong)
                    // no need update index in db, onMediaItemTransition() has alread done it
                }
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                saveQueue(match)
                return match
            } else if (delta) {
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: delta additive")

                mediaList.fastForEachIndexed { index, s ->
                    s?.shuffleIndex = index
                }

                // add only the songs that are not already in the queue
                match.queue.addAll(mediaList.filter { s -> match.queue.none { s?.id == it.id } }.filterNotNull())

                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong) // track the index we jumped to
                }
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                saveQueueSongs(match)
                return match
            } else if (match.title.endsWith("+\u200B") || anyExts != null) { // this queue is an already an extension queue
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: extension queue additive")
                // add items to existing queue unconditionally
                if (anyExts != null) {
                    addSongsToQueue(anyExts, Int.MAX_VALUE, mediaList.filterNotNull(), saveToDb = false)
                    if (shuffled) {
                        shuffle(anyExts, false, true)
                        match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                    }
                } else {
                    addSongsToQueue(match, Int.MAX_VALUE, mediaList.filterNotNull(), saveToDb = false)
                    if (shuffled) {
                        shuffle(match, false, true)
                        match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                    }
                }

                // rewrite queue
                saveQueueSongs(anyExts ?: match)

                return match
            } else { // make new extension queue
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: extension queue rename + extension queue additive")
                // add items to existing queue unconditionally
                addSongsToQueue(match, Int.MAX_VALUE, mediaList.filterNotNull(), saveToDb = false)
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                match.title = "${match.title} +\u200B"

                // rewrite queue
                saveQueueSongs(match)

                return match
            }
        } else {
            // add entirely new queue
            // Precondition(s): radio queues never include local songs
            if (masterQueues.size > maxQueues) {
                deleteQueue(masterQueues.first())
            }
            val q = ArrayList(mediaList.filterNotNull())
            q.fastForEachIndexed { index, s ->
                s?.shuffleIndex = index
            }

            val newQueue = MultiQueueObject(
                QueueEntity.generateQueueId(),
                title,
                q,
                false,
                startIndex,
                -1,
                masterQueues.size,
                if (isRadio) q.lastOrNull()?.id else null
            )
            masterQueues.add(newQueue)
            if (shuffled) {
                shuffle(masterQueues.size - 1, false, true)
                newQueue.queuePos = newQueue.queue.indexOf(newQueue.queue.find { it.shuffleIndex == 0 })
            }

            saveQueueSongs(newQueue)
            return newQueue
        }
    }


    /**
     * Add songs to end of CURRENT QUEUE & update it in the player
     */
    fun enqueueEnd(mediaList: List<MediaMetadata>, isRadio: Boolean = false) {
        getCurrentQueue()?.let {
            addSongsToQueue(it, Int.MAX_VALUE, mediaList, isRadio = isRadio)
        }
    }

    /**
     * Add songs to queue object & update it in the player, given an index to insert at
     */
    fun addSongsToQueue(
        q: MultiQueueObject,
        pos: Int,
        mediaList: List<MediaMetadata>,
        saveToDb: Boolean = true,
        isRadio: Boolean = false
    ) {
        val listPos = if (pos < 0) {
            0
        } else if (pos > q.getSize()) {
            q.getSize()
        } else {
            pos
        }

        Log.d(TAG, "Inserting at position: $listPos")

        // assign new indexes to items affected by inserted items
        if (q.shuffled) {
            val songsAfter = q.getCurrentQueueShuffled()
            songsAfter.subList(listPos, songsAfter.size).forEach {
                it.shuffleIndex += mediaList.size
            }
        }

        // add new items
        mediaList.fastForEachIndexed { index, s ->
            s.shuffleIndex = listPos + index
        }

        if (q.shuffled) {
            q.queue.addAll(mediaList)
        } else {
            q.queue.addAll(listPos, mediaList)
        }

        // adding before current playing song requires tracking new index
        if (q.getQueuePosShuffled() >= listPos) {
            if (q.shuffled) {
                // shuffle index current song + add size
                val newIndex = q.queue[q.queuePos].shuffleIndex + mediaList.size
                q.queuePos = q.queue.indexOf(q.queue.fastFirst { it.shuffleIndex == newIndex })
            } else {
                q.queuePos += mediaList.size
            }
        }

        setCurrQueue(q, false)
        if (isRadio) {
            q.playlistId = mediaList.lastOrNull()?.id
        }

        if (saveToDb) {
            saveQueueSongs(q)
        }

    }

    /**
     * Removes song from the current queue
     *
     * @param index Index of item
     */
    fun removeCurrentQueueSong(index: Int): Boolean {
        val q = getCurrentQueue()
        if (q == null) {
            return false
        }
        return removeSong(q, index)
    }


    /**
     * Removes song from the queue
     *
     * @param item Queue
     * @param index Index of item
     */
    fun removeSong(item: MultiQueueObject, index: Int): Boolean {
        var ret = false
        val currentMediaItemIndex = player.player.currentMediaItemIndex
        var newQueuePos = item.getQueuePosShuffled()

        if (item.shuffled) {
            Log.d(TAG, "Trying remove song at index: $index")
            val s = item.queue.find { it.shuffleIndex == index }
            if (s != null) {
                ret = item.queue.remove(s)
                Log.d(TAG, "Removing song: ${s.title}, $ret")
            }
        } else {
            item.queue.removeAt(index)
            ret = true
        }
        item.getCurrentQueueShuffled().fastForEachIndexed { index, s -> s.shuffleIndex = index }

        // update current position only if the move will affect it
        if (index < currentMediaItemIndex) {
            newQueuePos--
        } else if (index == currentMediaItemIndex) {
            newQueuePos++
        } else {
            // no need to adjust
        }

        if (newQueuePos >= item.getSize()) {
            newQueuePos = item.getSize() - 1
        } else if (newQueuePos < 0) {
            newQueuePos = 0
        }
        item.queuePos = newQueuePos

        saveQueueSongs(item)
        return ret
    }

    /**
     * Deletes a queue
     *
     * @param item
     */
    fun deleteQueue(item: MultiQueueObject): Int {
        if (QUEUE_DEBUG)
            Log.d(TAG, "DELETING QUEUE ${item.title}")

        val match = masterQueues.firstOrNull { it.title == item.title }
        if (match != null) {
            masterQueues.remove(match)
            if (masterQueues.isNotEmpty()) {
                masterIndex -= 1
            } else {
                masterIndex = -1
            }

            CoroutineScope(Dispatchers.IO).launch {
                player.database.deleteQueue(match.id)
            }
        } else {
            Log.w(TAG, "Cannot find queue to delete: ${item.title}")
        }

        return masterQueues.size
    }


    /**
     * Un-shuffles current queue
     *
     * @return New current position tracker
     */
    fun unShuffleCurrent() = unShuffle(masterIndex)

    /**
     * Un-shuffles a queue
     *
     * @return New current position tracker
     */
    fun unShuffle(index: Int): Int {
        val item = masterQueues[index]
        if (item.shuffled) {
            if (QUEUE_DEBUG)
                Log.d(TAG, "Un-shuffling queue ${item.title}")

            item.shuffled = false
        }
        saveQueueSongs(item)
        bubbleUp(item)
        return item.queuePos
    }

    /**
     * Shuffles current queue
     */
    fun shuffleCurrent(preserveCurrent: Boolean = true, bypassSaveToDb: Boolean = false) =
        shuffle(masterIndex, preserveCurrent, bypassSaveToDb)


    /**
     * Shuffles a queue
     *
     * If shuffle is enabled, it will pull from the shuffled queue, if shuffle is not enabled, it pulls from the
     * un-shuffled queue
     *
     * @param index
     * @param preserveCurrent True will push the currently playing song to the top of the queue. False will
     *      fully shuffle everything.
     * @param bypassSaveToDb By default, the queue will be saved after shuffling. In some cases it may be necessary
     *      to avoid this behaviour
     *
     * @return New current position tracker
     */
    fun shuffle(
        q: MultiQueueObject,
        preserveCurrent: Boolean = true,
        bypassSaveToDb: Boolean = false
    ) = shuffle(masterQueues.indexOf(q), preserveCurrent, bypassSaveToDb)

    /**
     * Shuffles a queue
     *
     * If shuffle is enabled, it will pull from the shuffled queue, if shuffle is not enabled, it pulls from the
     * un-shuffled queue
     *
     * @param index
     * @param preserveCurrent True will push the currently playing song to the top of the queue. False will
     *      fully shuffle everything.
     * @param bypassSaveToDb By default, the queue will be saved after shuffling. In some cases it may be necessary
     *      to avoid this behaviour
     *
     * @return New current position tracker
     */
    fun shuffle(
        index: Int,
        preserveCurrent: Boolean = true,
        bypassSaveToDb: Boolean = false
    ): Int {
        if (index <= -1) {
            return 0
        }

        val item = masterQueues[index]
        if (QUEUE_DEBUG)
            Log.d(TAG, "Shuffling queue ${item.title}")

        val currentSong = item.queue[item.queuePos]

        // shuffle & push the current song to top if requested to
        shuffleInPlace(item.queue)
        if (preserveCurrent) {
            val s2 = item.queue.find { it.shuffleIndex == 0 }
            if (s2 != null && currentSong != s2) {
                currentSong.shuffleIndex = s2.shuffleIndex.also { s2.shuffleIndex = currentSong.shuffleIndex }
            }
            item.queuePos = item.queue.indexOf(currentSong)
        } else {
            item.queuePos = item.queue.indexOf(item.queue.fastFirstOrNull { it.shuffleIndex == 0 })
            item.lastSongPos = C.TIME_UNSET
        }

        item.shuffled = true

        if (!bypassSaveToDb) {
            saveQueueSongs(item)
        }
        bubbleUp(item)
        return item.queuePos
    }

    /**
     * Move a queue in masterQueues
     *
     * @param fromIndex Song to move
     * @param toIndex Destination
     *
     * @return New current position tracker
     */
    fun move(fromIndex: Int, toIndex: Int) {
        // update current position only if the move will affect it
        if (masterIndex >= min(fromIndex, toIndex) && masterIndex <= max(fromIndex, toIndex)) {
            if (fromIndex == masterIndex) {
                masterIndex = toIndex
            } else if (masterIndex == toIndex) {
                if (masterIndex < fromIndex) {
                    masterIndex++
                } else {
                    masterIndex--
                }
            } else if (toIndex > masterIndex) {
                masterIndex--
            } else {
                masterIndex++
            }
        }

        masterQueues.move(fromIndex, toIndex)
        regenerateIndexes()
        saveAllQueues(masterQueues)
    }


    /**
     * Move a song in the current queue
     *
     * @param fromIndex Song to move
     * @param toIndex Destination
     * @param currentMediaItemIndex Index of now playing song
     *
     * @return New current position tracker
     */
    fun moveSong(fromIndex: Int, toIndex: Int) =
        getCurrentQueue()?.let { moveSong(it, fromIndex, toIndex) }

    /**
     * Move a song, given a queue.
     *
     * @param queue Queue to operate on
     * @param fromIndex Song to move
     * @param toIndex Destination
     *
     * @return New current position tracker
     */
    private fun moveSong(
        queue: MultiQueueObject,
        fromIndex: Int,
        toIndex: Int,
    ): Int {
        val items = queue.getCurrentQueueShuffled()
        var newQueuePos = queue.getQueuePosShuffled()
        val currentMediaItemIndex = player.player.currentMediaItemIndex

        // update current position only if the move will affect it
        if (currentMediaItemIndex >= min(fromIndex, toIndex) && currentMediaItemIndex <= max(fromIndex, toIndex)) {
            if (fromIndex == currentMediaItemIndex) {
                newQueuePos = toIndex
            } else if (currentMediaItemIndex == toIndex) {
                if (currentMediaItemIndex < fromIndex) {
                    newQueuePos++
                } else {
                    newQueuePos--
                }
            } else if (toIndex > currentMediaItemIndex) {
                newQueuePos--
            } else {
                newQueuePos++
            }
        }
        queue.queuePos = newQueuePos

        // I like to move it move it
        if (queue.shuffled) {
            items.move(fromIndex, toIndex)
            items.fastForEachIndexed { index, s ->
                // items is a copy of queue.queue, assume all objects will exist *once* only
                queue.queue.find { it == s }?.shuffleIndex = index
            }
        } else {
            queue.queue.move(fromIndex, toIndex)
        }
        queue.getCurrentQueueShuffled().fastForEachIndexed { index, s -> s.shuffleIndex = index }

        saveQueueSongs(queue)

        if (QUEUE_DEBUG)
            Log.d(TAG, "Moved item from $currentMediaItemIndex to ${queue.queuePos}")
        return queue.queuePos
    }


    /**
     * =================
     * Player management
     * =================
     */

    /**
     * Get all copy of all queues
     */
    fun getAllQueues() = masterQueues.toImmutableList()


    /**
     * Get the index of the current queue
     */
    fun getMasterIndex() = masterIndex

    /**
     * Retrieve the current queue
     *
     * @return Queue object (entire object)
     */
    fun getCurrentQueue(): MultiQueueObject? {
        try {
            return masterQueues[masterIndex]
        } catch (e: IndexOutOfBoundsException) {
            masterIndex = masterQueues.size - 1 // reset var if invalid
            return null
        }
    }

    fun renameQueue(queue: MultiQueueObject, newName: String) {
        val found = masterQueues.any { it == queue }
        if (found) {
            val oldIndex = masterQueues.indexOf(queue)
            val q = masterQueues.removeAt(oldIndex)
            masterQueues.add(oldIndex, q.copy(title = newName))

            if (QUEUE_DEBUG)
                Log.d(TAG, "Renamed queue from \"${queue.title}\" to \"$newName\"")
        }
    }

    /**
     * Load a queue into the media player
     *
     * @param index Index of queue
     * @param shouldResume Set to true for the player should resume playing at the current song's last save position or
     * false to start from the beginning.
     * @return New current position tracker
     */
    fun setCurrQueue(index: Int, shouldResume: Boolean = true): MultiQueueObject? {
        return try {
            val q = masterQueues[index]
            setCurrQueue(q, shouldResume)
            return q
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    /**
     * Load the current queue into the media player
     *
     * @param shouldResume Set to true for the player should resume playing at the current song's last save position or
     * false to start from the beginning.
     * @return New current position tracker
     */
    fun setCurrQueue(shouldResume: Boolean = true): MultiQueueObject? {
        val q = getCurrentQueue()
        setCurrQueue(q, shouldResume)
        return q
    }

    /**
     * Load a queue into the media player. This should ran exclusively on the main thread.
     *
     * @param item Queue object
     * @param shouldResume Set to true for the player should resume playing at the current song's last save position or
     * false to start from the beginning.
     * @return New current position tracker
     */
    fun setCurrQueue(item: MultiQueueObject?, shouldResume: Boolean = true): Int? {
        Log.d(
            TAG,
            "Loading queue ${item?.title ?: "null"} into player. Shuffle state = ${item?.shuffled}"
        )

        if (item == null || item.queue.isEmpty()) {
            player.player.setMediaItems(ArrayList())
            return null
        }

        // I have no idea why this value gets reset to 0 by the end... but ig this works
        val queuePos = item.getQueuePosShuffled()
        val lastSongPos = if (shouldResume) item.lastSongPos else C.TIME_UNSET
        val realQueuePos = item.queuePos
        masterIndex = masterQueues.indexOf(item)

        val mediaItems: MutableList<MediaMetadata> = item.getCurrentQueueShuffled()

        Log.d(
            TAG, "Setting current queue. in bounds: ${queuePos >= 0 && queuePos < mediaItems.size}, " +
                    "queuePos: $queuePos, real queuePos: ${realQueuePos}, lastSongPos: $lastSongPos" +
                    "ids: ${player.player.currentMetadata?.id}, ${mediaItems[queuePos].id}"
        )
        /**
         * current playing == jump target, do seamlessly
         */
        val seamlessSupported = (queuePos < mediaItems.size)
                && player.player.currentMetadata?.id == mediaItems[queuePos].id
        if (seamlessSupported) {
            Log.d(TAG, "Trying seamless queue switch. Is first song?: ${queuePos == 0}")
            val playerIndex = player.player.currentMediaItemIndex

            if (queuePos == 0) {
                val playerItemCount = player.player.mediaItemCount
                // player.player.replaceMediaItems seems to stop playback so we
                // remove all songs except the currently playing one and then add the list of new items
                if (playerIndex < playerItemCount - 1) {
                    player.player.removeMediaItems(playerIndex + 1, playerItemCount)
                }
                if (playerIndex > 0) {
                    player.player.removeMediaItems(0, playerIndex)
                }
                // add all songs except the first one since it is already present and playing
                player.player.addMediaItems(mediaItems.drop(1).map { it.toMediaItem() })
            } else {
                // replace items up to current playing, then replace items after current
                player.player.replaceMediaItems(
                    0, playerIndex,
                    mediaItems.subList(0, queuePos).map { it.toMediaItem() })
                player.player.replaceMediaItems(
                    queuePos + 1, Int.MAX_VALUE,
                    mediaItems.subList(queuePos + 1, mediaItems.size).map { it.toMediaItem() })
            }
        } else {
            Log.d(TAG, "Seamless is not supported. Loading songs in directly")
            player.player.setMediaItems(mediaItems.map { it.toMediaItem() }, queuePos, lastSongPos)
        }

        bubbleUp(item)
        if (player.player.shuffleModeEnabled != item.shuffled) {
            player.player.shuffleModeEnabled = item.shuffled
        }
        return queuePos
    }

    /**
     * Update the current position index of the current queue
     *
     * @param index
     */
    fun setCurrQueuePosIndex(index: Int) {
        getCurrentQueue()?.let {
            it.setCurrentQueuePos(index)
            saveQueue(it)
        }
    }


    /**
     * ========================
     * Database sync management
     * ========================
     */

    class PriorityJob(val priority: Int, val job: Job) : Comparable<PriorityJob> {
        override fun compareTo(other: PriorityJob): Int = this.priority - other.priority
    }

    var queueEntity = PriorityQueue<PriorityJob>()
    var queueSongMap = PriorityQueue<PriorityJob>()
    var jobActive = Mutex()
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Execute the most recent save request, with a 5 second delay from function call
     */
    private fun databaseDispatcher() {
        Log.d(TAG, "Starting database save task")
        if (jobActive.isLocked) {
            Log.d(TAG, "Database save task is already active, aborting")
            return
        }

        jobActive.tryLock()
        while (queueEntity.isNotEmpty() || queueSongMap.isNotEmpty()) {
            runBlocking {
                delay(5000L)
            }
            Log.d(TAG, "Running database save task")

            // saving songs nukes the queue entity in the process, about it shouldn't matter since are same queue object
            if (!queueSongMap.isEmpty()) {
                queueSongMap.last().job.start()
                queueSongMap.clear()
                continue
            }

            if (!queueEntity.isEmpty()) {
                queueEntity.last().job.start()
                queueEntity.clear()
                continue
            }
        }
        jobActive.unlock()
        Log.d(TAG, "Exiting database save task")
    }

    fun shutdown() {
        queueSongMap.clear()
        queueEntity.clear()
    }

    private fun saveQueueSongs(mq: MultiQueueObject) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueSongMap.add(
                PriorityJob(
                    0,
                    coroutineScope.launch(start = CoroutineStart.DEFAULT) {
                        player.database.saveQueue(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    private fun saveQueue(mq: MultiQueueObject) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueEntity.add(
                PriorityJob(
                    0,
                    coroutineScope.launch(start = CoroutineStart.DEFAULT) {
                        player.database.updateQueue(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    private fun saveAllQueues(mq: MutableList<MultiQueueObject>) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueEntity.add(
                // we select most recent task, therefore "lowest" numeric priority at the end of the list == "highest" priority
                PriorityJob(
                    -1,
                    coroutineScope.launch(start = CoroutineStart.DEFAULT) {
                        player.database.updateAllQueues(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    companion object {
        val mutex = Mutex()

        fun shuffleInPlace(list: List<MediaMetadata>) {
            val rng = (0..(list.size - 1)).shuffled()

            list.forEachIndexed { index, s ->
                s.shuffleIndex = rng[index]
            }
        }
    }

}