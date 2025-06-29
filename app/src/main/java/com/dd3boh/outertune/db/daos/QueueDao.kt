package com.dd3boh.outertune.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dd3boh.outertune.db.entities.QueueEntity
import com.dd3boh.outertune.db.entities.QueueSong
import com.dd3boh.outertune.db.entities.QueueSongMap
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.QueueBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

@Dao
interface QueueDao {

    // region Gets
    @Query("SELECT * from queue ORDER BY `index`")
    fun getAllQueues(): Flow<List<QueueEntity>>

    @Transaction
    @Query("SELECT song.*, queue_song_map.shuffledIndex from queue_song_map JOIN song ON queue_song_map.songId = song.id WHERE queueId = :queueId ORDER BY `index`")
    fun getQueueSongs(queueId: Long): Flow<List<QueueSong>>

    fun readQueue(): List<MultiQueueObject> {
        val resultQueues = ArrayList<MultiQueueObject>()
        val queues = runBlocking { getAllQueues().first() }

        queues.forEach { queue ->
            val shuffledSongs = runBlocking { getQueueSongs(queue.id).first() }
            if (shuffledSongs.isEmpty()) return@forEach
            resultQueues.add(
                MultiQueueObject(
                    id = queue.id,
                    title = queue.title,
                    queue = shuffledSongs.map {
                        val s = it.song.toMediaMetadata()
                        s.shuffleIndex = it.shuffledIndex
                        s
                    }.toMutableList(),
                    shuffled = queue.shuffled,
                    queuePos = queue.queuePos,
                    index = queue.index,
                    playlistId = queue.playlistId
                )
            )
        }

        return resultQueues
    }
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queue: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queueSong: QueueSongMap)
    // endregion

    // region Updates
    @Update
    fun update(queue: QueueEntity)

    @Transaction
    fun updateQueue(mq: MultiQueueObject) {
        update(
            QueueEntity(
                id = mq.id,
                title = mq.title,
                shuffled = mq.shuffled,
                queuePos = mq.queuePos,
                index = mq.index,
                playlistId = mq.playlistId
            )
        )
    }

    @Transaction
    fun updateAllQueues(mqs: List<MultiQueueObject>) {
        CoroutineScope(Dispatchers.IO).launch {
            QueueBoard.mutex.withLock { // possible ConcurrentModificationException
                mqs.forEach { updateQueue(it) }
            }
        }
    }

    // endregion

    // region Deletes
    @Delete
    fun delete(mq: QueueEntity)

    @Query("DELETE FROM queue")
    fun deleteAllQueues()

    @Query("DELETE FROM queue_song_map WHERE queueId = :id")
    fun deleteAllQueueSongs(id: Long)

    @Query("DELETE FROM queue WHERE id = :id")
    fun deleteQueue(id: Long)
    // endregion
}