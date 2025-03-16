/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.playback

import android.util.Log
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import com.dd3boh.outertune.constants.PersistentQueueKey
import com.dd3boh.outertune.db.entities.QueueEntity
import com.dd3boh.outertune.extensions.currentMetadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okhttp3.internal.toImmutableList
import timber.log.Timber
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

const val QUEUE_DEBUG = false
const val MAX_QUEUES = 20

/**
 * This is for UI display purposes only. Do not modify externally.
 */
var isShuffleEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)

/**
 * Multiple queues manager. Methods will not automatically (re)load queues into the player unless
 * otherwise explicitly stated.
 */
class QueueBoard(queues: MutableList<MultiQueueObject> = ArrayList()) {
    private val TAG = QueueBoard::class.simpleName.toString()

    private val masterQueues: MutableList<MultiQueueObject> = queues
    private var masterIndex = masterQueues.size - 1 // current queue index
    var detachedHead = false
    var initialized = false


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
    private fun bubbleUp(item: MultiQueueObject, player: MusicService) = bubbleUp(masterQueues.indexOf(item), player)

    /**
     * Push this queue at index to top of the master queue list, and track set this as current queue.
     *
     * @param index
     */
    private fun bubbleUp(index: Int, player: MusicService) {
        if (index == masterQueues.size - 1) {
            return
        }

        val item = masterQueues[index]
        masterQueues.remove(item)
        masterQueues.add(item)
        masterIndex = masterQueues.size - 1

        regenerateIndexes()
        saveAllQueues(masterQueues, player)
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
        player: MusicService,
        shuffled: Boolean = false,
        forceInsert: Boolean = false,
        replace: Boolean = false,
        delta: Boolean = true,
        isRadio: Boolean = false,
        startIndex: Int = 0
    ): Boolean {
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d(
                "Adding to queue \"$title\". medialist size = ${mediaList.size}. " +
                        "forceInsert/replace/delta/startIndex = $forceInsert/$replace/$delta/$startIndex"
            )

        if (mediaList.isEmpty()) {
            return false
        }

        val match = masterQueues.firstOrNull { it.title == title } // look for matching queue. Title is uid
        if (match != null) { // found an existing queue
            // Titles ending in "+​" (u200B) signify a extension queue
            val anyExts = masterQueues.firstOrNull { it.title == match.title + " +\u200B" }
            if (replace) { // force replace
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: Replacing all queue items")

                mediaList.fastForEachIndexed { index, s ->
                    s?.shuffleIndex = index
                }

                match.replaceAll(mediaList.filterNotNull())
                match.queuePos = startIndex

                if (shuffled) {
                    shuffle(match, player, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                bubbleUp(match, player)  // move queue to end of list so it shows as most recent
                return true
            }

            // don't add songs to the queue if it's just one EXISTING song AND the new medialist is a subset of what we have
            // UNLESS forced to
            val containsAll = mediaList.all { s -> match.queue.any { s?.id == it.id } } // if is subset
            if (containsAll && match.getSize() == mediaList.size && !forceInsert) { // jump to song, don't add
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: jump only")
                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong)
                    // no need update index in db, onMediaItemTransition() has alread done it
                }
                if (shuffled) {
                    shuffle(match, player, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                bubbleUp(match, player)  // move queue to end of list so it shows as most recent
                return true
            } else if (delta) {
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: delta additive")

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
                    shuffle(match, player, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                saveQueueSongs(match, player)
                bubbleUp(match, player) // move queue to end of list so it shows as most recent
                return true
            } else if (match.title.endsWith("+\u200B") || anyExts != null) { // this queue is an already an extension queue
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: extension queue additive")
                // add items to existing queue unconditionally
                if (anyExts != null) {
                    addSongsToQueue(anyExts, Int.MAX_VALUE, mediaList.filterNotNull(), player, saveToDb = false)
                    if (shuffled) {
                        shuffle(anyExts, player, false, true)
                        match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                    }
                } else {
                    addSongsToQueue(match, Int.MAX_VALUE, mediaList.filterNotNull(), player, saveToDb = false)
                    if (shuffled) {
                        shuffle(match, player, false, true)
                        match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                    }
                }

                // rewrite queue
                saveQueueSongs(anyExts ?: match, player)

                // don't change index
                bubbleUp(match, player) // move queue to end of list so it shows as most recent
                return false
            } else { // make new extension queue
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: extension queue creation (and additive)")
                // add items to NEW queue unconditionally (add entirely new queue)
                if (masterQueues.size > MAX_QUEUES) {
                    deleteQueue(masterQueues.first(), player)
                }

                // create new queues
                val shufQueue = match.getCurrentQueueShuffled()
                shufQueue.addAll((mediaList.filterNotNull()))

                // queue is always created as un-shuffled
                shufQueue.fastForEachIndexed { index, s ->
                    s.shuffleIndex = index
                }

                val newQueue = MultiQueueObject(
                    QueueEntity.generateQueueId(),
                    "$title +\u200B",
                    shufQueue,
                    false,
                    match.getQueuePosShuffled(),
                    masterQueues.size
                )
                masterQueues.add(newQueue)
                if (shuffled) {
                    shuffle(newQueue, player, false, true)
                }

                saveQueue(newQueue, player)

                // don't change index, don't move match queue to end
                masterIndex = masterQueues.size - 1 // track the newly modified queue
                return true
            }
        } else {
            // add entirely new queue
            // Precondition(s): radio queues never include local songs
            if (masterQueues.size > MAX_QUEUES) {
                deleteQueue(masterQueues.first(), player)
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
                masterQueues.size,
                if (isRadio) q.lastOrNull()?.id else null
            )
            masterQueues.add(newQueue)
            if (shuffled) {
                shuffle(masterQueues.size - 1, player, false, true)
                newQueue.queuePos = newQueue.queue.indexOf(newQueue.queue.find { it.shuffleIndex == 0 })
            }

            saveQueue(newQueue, player)
            masterIndex = masterQueues.size - 1 // track the newly modified queue
            return true
        }
    }

    fun addQueue(
        title: String,
        mediaList: List<MediaMetadata?>,
        playerConnection: PlayerConnection,
        shuffled: Boolean = false,
        forceInsert: Boolean = false,
        replace: Boolean = false,
        delta: Boolean = true,
        startIndex: Int = 0,
        isRadio: Boolean = false,
    ) = addQueue(title, mediaList, playerConnection.service, shuffled, forceInsert, replace, delta, isRadio, startIndex)


    /**
     * Add songs to end of CURRENT QUEUE & update it in the player
     */
    fun enqueueEnd(mediaList: List<MediaMetadata>, player: MusicService, isRadio: Boolean = false) {
        getCurrentQueue()?.let {
            addSongsToQueue(it, Int.MAX_VALUE, mediaList, player, isRadio = isRadio)
        }
    }

    /**
     * Add songs to queue object & update it in the player, given an index to insert at
     */
    fun addSongsToQueue(
        q: MultiQueueObject,
        pos: Int,
        mediaList: List<MediaMetadata>,
        player: MusicService,
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

        setCurrQueue(q, player)
        if (isRadio) {
            q.playlistId = mediaList.lastOrNull()?.id
        }

        if (saveToDb) {
            saveQueueSongs(q, player)
        }

    }

    /**
     * Removes song from the current queue
     *
     * @param index Index of item
     */
    fun removeCurrentQueueSong(index: Int, player: MusicService): Boolean {
        val q = getCurrentQueue()
        if (q == null) {
            return false
        }
        return removeSong(q, index, player)
    }


    /**
     * Removes song from the queue
     *
     * @param item Queue
     * @param index Index of item
     */
    fun removeSong(item: MultiQueueObject, index: Int, player: MusicService): Boolean {
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

        saveQueueSongs(item, player)
        return ret
    }

    /**
     * Deletes a queue
     *
     * @param item
     */
    fun deleteQueue(item: MultiQueueObject, player: MusicService): Int {
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d("DELETING QUEUE ${item.title}")

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
        } else if (QUEUE_DEBUG) {
            Timber.tag(TAG).w("Cannot find queue ${item.title}")
        }

        return masterQueues.size
    }


    /**
     * Un-shuffles current queue
     *
     * @return New current position tracker
     */
    fun unShuffleCurrent(player: MusicService) = unShuffle(masterIndex, player)

    /**
     * Un-shuffles a queue
     *
     * @return New current position tracker
     */
    fun unShuffle(index: Int, player: MusicService): Int {
        val item = masterQueues[index]
        if (item.shuffled) {
            if (QUEUE_DEBUG)
                Timber.tag(TAG).d("Un-shuffling queue ${item.title}")

            item.shuffled = false
            isShuffleEnabled.value = false
        }
        saveQueueSongs(item, player)
        bubbleUp(item, player)
        return item.queuePos
    }

    /**
     * Shuffles current queue
     */
    fun shuffleCurrent(player: MusicService, preserveCurrent: Boolean = true, bypassSaveToDb: Boolean = false) =
        shuffle(masterIndex, player, preserveCurrent, bypassSaveToDb)


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
        player: MusicService,
        preserveCurrent: Boolean = true,
        bypassSaveToDb: Boolean = false
    ) = shuffle(masterQueues.indexOf(q), player, preserveCurrent, bypassSaveToDb)

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
        player: MusicService,
        preserveCurrent: Boolean = true,
        bypassSaveToDb: Boolean = false
    ): Int {
        if (index <= -1) {
            return 0
        }

        val item = masterQueues[index]
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d("Shuffling queue ${item.title}")

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
        }

        item.shuffled = true
        isShuffleEnabled.value = true

        if (!bypassSaveToDb) {
            saveQueueSongs(item, player)
        }
        bubbleUp(item, player)
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
    fun move(fromIndex: Int, toIndex: Int, player: MusicService) {
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
        saveAllQueues(masterQueues, player)
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
    fun moveSong(fromIndex: Int, toIndex: Int, player: MusicService) =
        getCurrentQueue()?.let { moveSong(it, fromIndex, toIndex, player) }

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
        player: MusicService
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

        saveQueueSongs(queue, player)

        if (QUEUE_DEBUG)
            Timber.tag(TAG).d("Moved item from $currentMediaItemIndex to ${queue.queuePos}")
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

    /**
     * Load the current queue into the media player
     *
     * @param playerConnection PlayerConnection link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(playerConnection: PlayerConnection, autoSeek: Boolean = true) =
        setCurrQueue(getCurrentQueue(), playerConnection.service, autoSeek)

    /**
     * Load the current queue into the media player
     *
     * @param player MusicService link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(player: MusicService, autoSeek: Boolean = true) = setCurrQueue(getCurrentQueue(), player, autoSeek)

    /**
     * Load a queue into the media player
     *
     * @param index Index of queue
     * @param playerConnection PlayerConnection link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(index: Int, playerConnection: PlayerConnection, autoSeek: Boolean = true): Int? {
        return try {
            setCurrQueue(masterQueues[index], playerConnection.service, autoSeek)
        } catch (e: IndexOutOfBoundsException) {
            -1
        }
    }

    /**
     * Load a queue into the media player
     *
     * @param index Index of queue
     * @param player MusicService link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(index: Int, player: MusicService, autoSeek: Boolean = true): Int? {
        return try {
            setCurrQueue(masterQueues[index], player, autoSeek)
        } catch (e: IndexOutOfBoundsException) {
            -1
        }
    }

    /**
     * Load a queue into the media player
     *
     * @param item Queue object
     * @param player MusicService link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(item: MultiQueueObject?, player: MusicService, autoSeek: Boolean = true): Int? {
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d(
                "Loading queue ${item?.title ?: "null"} into player. " +
                        "autoSeek = $autoSeek shuffle state = ${item?.shuffled}"
            )

        if (item == null) {
            player.player.setMediaItems(ArrayList())
            return null
        }

        // I have no idea why this value gets reset to 0 by the end... but ig this works
        val queuePos = item.getQueuePosShuffled()
        val realQueuePos = item.queuePos
        masterIndex = masterQueues.indexOf(item)

        val mediaItems: MutableList<MediaMetadata> = item.getCurrentQueueShuffled()

        Log.d(
            TAG, "Setting current queue. in bounds: ${queuePos >= 0 && queuePos < mediaItems.size}, " +
                    "queuePos: $queuePos, real queuePos: ${realQueuePos}, ids: ${player.player.currentMetadata?.id}, " +
                    "${mediaItems[queuePos].id}"
        )
        /**
         * current playing == jump target, do seamlessly
         */
        val seamlessSupported = (queuePos >= 0 && queuePos < mediaItems.size)
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
                player.player.replaceMediaItems(0, playerIndex,
                    mediaItems.subList(0, queuePos).map { it.toMediaItem() })
                player.player.replaceMediaItems(queuePos + 1, Int.MAX_VALUE,
                    mediaItems.subList(queuePos + 1, mediaItems.size).map { it.toMediaItem() })
            }
        } else {
            Log.d(TAG, "Seamless is not supported. Loading songs in directly")
            player.player.setMediaItems(mediaItems.map { it.toMediaItem() })
        }

        isShuffleEnabled.value = item.shuffled

        if (autoSeek && !seamlessSupported) {
            player.player.seekTo(queuePos, C.TIME_UNSET)
        }

        bubbleUp(item, player)
        return queuePos
    }

    /**
     * Update the current position index of the current queue
     *
     * @param index
     */
    fun setCurrQueuePosIndex(index: Int) {
        getCurrentQueue()?.setCurrentQueuePos(index)
    }


    /**
     * ========================
     * Database sync management
     * ========================
     */

    class PriorityJob(val priority: Int, val job: Job) : Comparable<PriorityJob> {
        override fun compareTo(other: PriorityJob): Int = other.priority - priority
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
            Log.d(TAG, "Database save task is already acive, aborting")
            return
        }

        jobActive.tryLock()
        while (queueEntity.isNotEmpty() || queueSongMap.isNotEmpty()) {
            runBlocking {
                delay(5000L)
            }
            Log.d(TAG, "Running database save task")

            // saving songs nukes the queue entity in the process, abut it shouldn't matter since are same queue object
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

    private fun saveQueueSongs(mq: MultiQueueObject, player: MusicService) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueSongMap.add(
                PriorityJob(0,
                    coroutineScope.launch(start = CoroutineStart.LAZY) {
                        player.database.rewriteQueue(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    private fun saveQueue(mq: MultiQueueObject, player: MusicService) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueEntity.add(
                PriorityJob(0,
                    coroutineScope.launch(start = CoroutineStart.LAZY) {
                        player.database.updateQueue(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    private fun saveAllQueues(mq: MutableList<MultiQueueObject>, player: MusicService) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueEntity.add(
                // we select most recent task, therefore "lower" priority works out to be "higher" priority
                PriorityJob(1,
                    coroutineScope.launch(start = CoroutineStart.LAZY) {
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