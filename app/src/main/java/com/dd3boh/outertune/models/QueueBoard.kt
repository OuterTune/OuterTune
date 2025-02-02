package com.dd3boh.outertune.models

import androidx.media3.common.C
import com.dd3boh.outertune.constants.PersistentQueueKey
import com.dd3boh.outertune.db.entities.QueueEntity
import com.dd3boh.outertune.extensions.currentMetadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.playback.MusicService
import com.dd3boh.outertune.playback.PlayerConnection
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.internal.toImmutableList
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

const val QUEUE_DEBUG = false
const val MAX_QUEUES = 20

/**
 * This is for UI display purposes only. Do not modify externally.
 */
var isShuffleEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)

/**
 * @param title Queue title (and UID)
 * @param queue List of media items
 */
data class MultiQueueObject(
    val id: Long,
    val title: String,
    /**
     * The order of songs are dynamic. This should not be accessed form outside QueueBoard.
     */
    val queue: MutableList<MediaMetadata>,
    /**
     * The order of songs stays in the order songs are added in. This should not be accessed form outside QueueBoard.
     */
    val unShuffled: MutableList<MediaMetadata>,
    var shuffled: Boolean = false,
    var queuePos: Int = -1, // position of current song
    var index: Int, // order of queue
    /**
     * Song id to start watch endpoint
     * TODO: change this in database too
     */
    var playlistId: String? = null,
) {

    /**
     * Retrieve the current queue in list form, with shuffle state taken in account
     *
     * @return Queue object (entire object)
     */
    fun getCurrentQueueShuffled(): MutableList<MediaMetadata> {
        return if (shuffled) {
            queue
        } else {
            unShuffled
        }
    }

    /**
     * Retrieve the total duration of all songs
     *
     * @return Duration in seconds
     */
    fun getDuration(): Long {
        var duration = 0L
        getCurrentQueueShuffled().forEach {
            duration += it.duration // seconds
        }
        return duration
    }
}

/**
 * Multiple queues manager. Methods will not automatically (re)load queues into the player unless
 * otherwise explicitly stated.
 */
class QueueBoard(queues: MutableList<MultiQueueObject> = ArrayList()) {
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
        var count = 0
        masterQueues.forEach { it.index = count++ }
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
        CoroutineScope(Dispatchers.IO).launch {
            player.database.updateAllQueues(masterQueues)
        }
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
     * @param queue Queue object for song continuation et al
     * @param forceInsert When mediaList contains one item, force an insert instead of jumping to an
     *      item if it exists
     * @param replace Replace all items in the queue. This overrides forceInsert, delta
     * @param delta Takes not effect if forceInsert is false. Setting this to true will add only new
     *      songs, false will add all songs
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
                match.queue.clear()
                match.queue.addAll(mediaList.filterNotNull())
                match.unShuffled.clear()
                match.unShuffled.addAll(mediaList.filterNotNull())
                match.queuePos = startIndex

                if (player.dataStore.get(PersistentQueueKey, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        player.database.updateQueue(match)
                    }
                }
                bubbleUp(match, player)  // move queue to end of list so it shows as most recent
                return true
            }

            // don't add songs to the queue if it's just one EXISTING song AND the new medialist is a subset of what we have
            // UNLESS forced to
            val containsAll = mediaList.all { s -> match.queue.any { s?.id == it.id } } // if is subset
            if (containsAll && match.queue.size == mediaList.size && !forceInsert) { // jump to song, don't add
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: jump only")
                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong)
                    // no need update index in db, onMediaItemTransition() has alread done it
                }

                bubbleUp(match, player)  // move queue to end of list so it shows as most recent
                return true
            } else if (delta) {
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: delta additive")
                // add only the songs that are not already in the queue
                match.queue.addAll(mediaList.filter { s -> match.queue.none { s?.id == it.id } }.filterNotNull())
                match.unShuffled.addAll(mediaList.filter { s -> match.queue.none { s?.id == it.id } }.filterNotNull())

                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong) // track the index we jumped to
                }

                if (player.dataStore.get(PersistentQueueKey, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        player.database.rewriteQueue(match)
                    }
                }

                bubbleUp(match, player) // move queue to end of list so it shows as most recent
                return true
            } else if (match.title.endsWith("+\u200B") || anyExts != null) { // this queue is an already an extension queue
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: extension queue additive")
                // add items to existing queue unconditionally
                if (anyExts != null) {
                    addSongsToQueue(anyExts, Int.MAX_VALUE, mediaList.filterNotNull(), player, saveToDb = false)
                } else {
                    addSongsToQueue(match, Int.MAX_VALUE, mediaList.filterNotNull(), player, saveToDb = false)
                }

                // rewrite queue
                if (player.dataStore.get(PersistentQueueKey, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        player.database.rewriteQueue(anyExts ?: match)
                    }
                }

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
                val shufQueue = ArrayList(match.queue.map { it })
                val unShufQueue = ArrayList(match.unShuffled.map { it })

                shufQueue.addAll((mediaList.filterNotNull()))
                unShufQueue.addAll((mediaList.filterNotNull()))

                val newQueue = MultiQueueObject(
                    QueueEntity.generateQueueId(),
                    "$title +\u200B",
                    shufQueue,
                    unShufQueue,
                    false,
                    match.queuePos,
                    masterQueues.size
                )
                masterQueues.add(newQueue)
                if (player.dataStore.get(PersistentQueueKey, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        player.database.saveQueue(newQueue)
                    }
                }

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
            val newQueue = MultiQueueObject(
                QueueEntity.generateQueueId(),
                title,
                q,
                ArrayList(mediaList.filterNotNull()),
                false,
                startIndex,
                masterQueues.size,
                if (isRadio) q.lastOrNull()?.id else null
            )
            masterQueues.add(newQueue)
            if (player.dataStore.get(PersistentQueueKey, true)) {
                CoroutineScope(Dispatchers.IO).launch {
                    player.database.saveQueue(newQueue)
                }
            }
            masterIndex = masterQueues.size - 1 // track the newly modified queue
            return true
        }
    }

    fun addQueue(
        title: String,
        mediaList: List<MediaMetadata?>,
        playerConnection: PlayerConnection,
        forceInsert: Boolean = false,
        replace: Boolean = false,
        delta: Boolean = true,
        startIndex: Int = 0,
        isRadio: Boolean = false,
    ) = addQueue(title, mediaList, playerConnection.service, forceInsert, replace, delta, isRadio, startIndex)


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
        } else if (pos > q.queue.size) {
            q.queue.size
        } else {
            pos
        }

        // Add to current queue at the position. For the other queue, just add to end
        if (q.shuffled) {
            q.queue.addAll(listPos, mediaList)
            q.unShuffled.addAll(q.queue.size, mediaList)
        } else {
            q.queue.addAll(q.queue.size, mediaList)
            q.unShuffled.addAll(listPos, mediaList)
        }

        setCurrQueue(q, player)
        if (isRadio) {
            q.playlistId = mediaList.lastOrNull()?.id
        }

        if (saveToDb && player.dataStore.get(PersistentQueueKey, true)) {
            CoroutineScope(Dispatchers.IO).launch {
                player.database.rewriteQueue(q)
            }
        }
    }

    /**
     * Removes song from the current queue
     *
     * @param index Index of item
     */
    fun removeCurrentQueueSong(index: Int, player: MusicService) =
        getCurrentQueue()?.let { removeSong(it, index, player) }

    /**
     * Removes song from the queue
     *
     * @param item Queue
     * @param index Index of item
     */
    fun removeSong(item: MultiQueueObject, index: Int, player: MusicService) {
        if (item.shuffled) {
            val removed = item.queue.removeAt(index)
            item.unShuffled.remove(removed)
        } else {
            val removed = item.unShuffled.removeAt(index)
            item.queue.remove(removed)
        }

        CoroutineScope(Dispatchers.IO).launch {
            player.database.rewriteQueue(item)
        }
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
            // re-track current position
            item.queuePos = item.unShuffled.indexOf(item.queue[item.queuePos])
            item.shuffled = false
            isShuffleEnabled.value = false
        }
        CoroutineScope(Dispatchers.IO).launch {
            player.database.rewriteQueue(item)
        }
        bubbleUp(item, player)
        return item.queuePos
    }

    /**
     * Shuffles current queue
     */
    fun shuffleCurrent(player: MusicService, preserveCurrent: Boolean = true) = shuffle(masterIndex, player, preserveCurrent)


    /**
     * Un-shuffles a queue
     *
     * If shuffle is enabled, it will pull from the shuffled queue, if shuffle is not enabled, it pulls from the
     * un-shuffled queue
     *
     * @param index
     * @param preserveCurrent True will push the currently playing song to the top of the queue. False will
     *      fully shuffle everything.
     * @return New current position tracker
     */
    fun shuffle(index: Int, player: MusicService, preserveCurrent: Boolean = true): Int {
        val item = masterQueues[index]
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d("Shuffling queue ${item.title}")

        val currentSong = if (item.shuffled) item.queue[item.queuePos] else item.unShuffled[item.queuePos]

        // shuffle & push the current song to top if requested to
        item.queue.shuffle()
        if (preserveCurrent) {
            item.queue.remove(currentSong)
            item.queue.add(0, currentSong)
        }
        item.queuePos = 0
        item.shuffled = true
        isShuffleEnabled.value = true

        CoroutineScope(Dispatchers.IO).launch {
            player.database.rewriteQueue(item)
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
        CoroutineScope(Dispatchers.IO).launch {
            player.database.updateAllQueues(masterQueues)
        }
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
    fun moveSong(fromIndex: Int, toIndex: Int, currentMediaItemIndex: Int, player: MusicService) =
        getCurrentQueue()?.let { moveSong(it, fromIndex, toIndex, currentMediaItemIndex, player) }

    /**
     * Move a song, given a queue.
     *
     * @param queue Queue to operate on
     * @param fromIndex Song to move
     * @param toIndex Destination
     * @param currentMediaItemIndex Index of now playing song
     *
     * @return New current position tracker
     */
    private fun moveSong(
        queue: MultiQueueObject,
        fromIndex: Int,
        toIndex: Int,
        currentMediaItemIndex: Int,
        player: MusicService
    ): Int {
        // update current position only if the move will affect it
        if (currentMediaItemIndex >= min(fromIndex, toIndex) && currentMediaItemIndex <= max(fromIndex, toIndex)) {
            if (fromIndex == currentMediaItemIndex) {
                queue.queuePos = toIndex
            } else if (currentMediaItemIndex == toIndex) {
                if (currentMediaItemIndex < fromIndex) {
                    queue.queuePos++
                } else {
                    queue.queuePos--
                }
            } else if (toIndex > currentMediaItemIndex) {
                queue.queuePos--
            } else {
                queue.queuePos++
            }
        }

        // I like to move it move it
        if (queue.shuffled) {
            queue.queue.move(fromIndex, toIndex)
        } else {
            queue.unShuffled.move(fromIndex, toIndex)
        }

        CoroutineScope(Dispatchers.IO).launch {
            player.database.rewriteQueue(queue)
        }

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

        val queuePos = item.queuePos // I have no idea why this value gets reset to 0 by the end... but ig this works
        masterIndex = masterQueues.indexOf(item)

        // if requested to get shuffled queue
        val mediaItems: MutableList<MediaMetadata>?
        if (item.shuffled) {
            mediaItems = item.queue
        } else {
            mediaItems = item.unShuffled
        }

        /**
         * current playing == jump target, do seamlessly
         */
        val seamlessSupported = player.player.currentMetadata?.id == mediaItems[queuePos].id
        if (seamlessSupported) {
            if (queuePos == 0) {
                val playerIndex = player.player.currentMediaItemIndex
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
                player.player.replaceMediaItems(0, queuePos,
                    mediaItems.subList(0, queuePos).map { it.toMediaItem() })
                player.player.replaceMediaItems(queuePos + 1, Int.MAX_VALUE,
                    mediaItems.subList(queuePos + 1, mediaItems.size).map { it.toMediaItem() })
            }
        } else {
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
    fun setCurrQueuePosIndex(index: Int, player: MusicService) {
        getCurrentQueue()?.let {
            if (it.queuePos != index) {
                it.queuePos = index
                if (player.dataStore.get(PersistentQueueKey, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        player.database.updateQueue(it)
                    }
                }
            }
        }
    }

    companion object {
        val mutex = Mutex()

        const val TAG = "QueueBoard"
    }

}