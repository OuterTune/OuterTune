package com.dd3boh.outertune.models

import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastSumBy

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
     * @return A copy of the Metadata list
     */
    fun getCurrentQueueShuffled(): MutableList<MediaMetadata> {
        return if (shuffled) {
            val shuffledQueue = ArrayList<MediaMetadata>()
            shuffledQueue.addAll(queue)
            shuffledQueue.sortBy { it.shuffleIndex }
            shuffledQueue
        } else {
            queue
        }
    }

    /**
     * Retrieve the song at current position in the queue
     */
    fun getCurrentSong(): MediaMetadata? {
        validateQueuePos()
        return queue[queuePos]
    }

    /**
     * Retrieve a song given a song ID. Returns null if no song is found
     */
    fun findSong(mediaId: String): MediaMetadata? {
        val currentSong = getCurrentSong()
        if (currentSong?.id == mediaId) {
            return currentSong
        }

        return queue.fastFirstOrNull { it.id == mediaId }
    }

    /**
     * Returns the index of current queue position considering shuffle state
     */
    fun getQueuePosShuffled(): Int {
        validateQueuePos()
        return if (shuffled) {
            queue[queuePos].shuffleIndex
        } else {
            queuePos
        }
    }

    fun setCurrentQueuePos(index: Int) {
        if (getQueuePosShuffled() != index) {

            /**
             * queuePos will always track the index of the song in the unsorted queue, *even* if queue is shuffled.
             * To get the real queuePos of the song, look at the shuffleIndex value that equals the index provided
             */
            val newQueuePos = if (shuffled) {
                queue.indexOf(queue.find { it.shuffleIndex == index })
            } else {
                index
            }

            queuePos = newQueuePos
        }
    }

    fun validateQueuePos() {
        if (queuePos < 0 || queuePos >= queue.size) { // I don't even...
            // possible issues with migrating some queues, notably from 0.7.4 to newer versions. Reset shuffle parts
            queue.fastForEachIndexed { index, s -> s.shuffleIndex = index }
            shuffled = false
            queuePos = 0
        }
    }

    /**
     * Retrieve the total duration of all songs
     *
     * @return Duration in seconds
     */
    fun getDuration(): Int {
        return queue.fastSumBy {
            it.duration // seconds
        }
    }

    /**
     * Get the length of the queue
     */
    fun getSize() = queue.size

    fun replaceAll(mediaList: List<MediaMetadata>) {
        queue.clear()
        queue.addAll(mediaList)
    }
}