package com.dd3boh.outertune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title", defaultValue = "")
    var title: String,
    var shuffled: Boolean = false,
    var queuePos: Int = -1, // position of current song
    @ColumnInfo(name = "index", defaultValue = 0.toString())
    val index: Int, // order of queue
    val playlistId: String? = null,
) {
    companion object {
        fun generateQueueId() = RandomStringUtils.insecure().next(8, false, true).toLong()
    }
}