package com.dd3boh.outertune.models

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
     * Returns the index of current queue position considering shuffle state
     */
    fun getQueuePosShuffled(): Int {
        if (queuePos < 0) { // I don't even...
            queuePos = 0
            return 0
        }
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