/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.component

import android.R.attr.checked
import android.R.attr.text
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dd3boh.outertune.App.Companion.toast
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.R
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.extensions.isUserLoggedIn
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.body.CreatePlaylistBody
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun EditPlaylistDialog(
    onDismiss: () -> Unit,
    playlist: PlaylistEntity
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(playlist.name) }
    var description by remember { mutableStateOf(playlist.description) }
    var privacyStatus by remember { mutableStateOf(playlist.privacyStatus) }

    fun onDone() {
        isLoading = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                playlist.browseId?.also { id ->
                    YouTube.updatePlaylist(
                        playlistId = id,
                        name = title.takeIf { title != playlist.name },
                        description = description.takeIf { description != playlist.description },
                        privacyStatus = privacyStatus.takeIf { privacyStatus != playlist.privacyStatus }
                    )
                }

                database.query {
                    update(playlist.copy(
                        name = title,
                        description = description,
                        privacyStatus = privacyStatus
                    ))
                }

                onDismiss()
            } catch (e: Exception) {
                reportException(e)
                toast("Failed to update an playlist!", 1)
                isLoading = false
            }
        }
    }

    AlertDialog(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Edit, contentDescription = null)
                Text(text = stringResource(R.string.edit_playlist))
            }
        },

        dismissButton = {
            TextButton(
                enabled = !isLoading,
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },

        confirmButton = {
            TextButton(
                enabled = !isLoading
                        && title.isNotBlank()
                        && !(description.contains("<") || description.contains(">")), // HTML tags are prohibited on YouTube and will return an error!

                onClick = ::onDone
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },

        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.playlist_name)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Description") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors()
                )

                if(!playlist.isLocal) {
                    val privacyStatuses = listOf(
                        "Public" to CreatePlaylistBody.PrivacyStatus.PUBLIC,
                        "Unlisted" to CreatePlaylistBody.PrivacyStatus.UNLISTED,
                        "Private" to CreatePlaylistBody.PrivacyStatus.PRIVATE
                    )

                    Spacer(Modifier.height(16.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        privacyStatuses.forEachIndexed { index, (name, value) ->
                            SegmentedButton(
                                selected = value == privacyStatus,
                                onClick = { privacyStatus = value },
                                label = { Text(name) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = privacyStatuses.size
                                )
                            )
                        }
                    }
                }
            }
        },

        onDismissRequest = {
            if (!isLoading) onDismiss()
        }
    )
}