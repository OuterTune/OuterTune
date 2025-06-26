package com.dd3boh.outertune.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.ui.component.GridMenu
import com.dd3boh.outertune.ui.component.GridMenuItem
import com.dd3boh.outertune.ui.component.QueueListItem

@Composable
fun QueueMenu(
    navController: NavController,
    mq: MultiQueueObject?,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    if (mq == null) {
        onDismiss()
        return
    }
    val songs = mq.getCurrentQueueShuffled()

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }

    // dialogs
    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = { songs.map { it.id } },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    AddToQueueDialog(
        isVisible = showChooseQueueDialog,
        onAdd = { queueName ->
            playerConnection.service.queueBoard.addQueue(
                queueName,
                songs,
                forceInsert = true,
                delta = false
            )
            playerConnection.service.queueBoard.setCurrQueue()
        },
        onDismiss = {
            showChooseQueueDialog = false
            onDismiss() // here we dismiss since we switch to the queue anyways
        }
    )

    // queue item
    QueueListItem(queue = mq)

    HorizontalDivider()

    // menu options
    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
    }
}