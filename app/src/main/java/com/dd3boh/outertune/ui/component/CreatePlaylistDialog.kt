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

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialTextFieldValue ?: "") }
    var description by remember { mutableStateOf("") }
    var privacyStatus by remember { mutableStateOf(CreatePlaylistBody.PrivacyStatus.PUBLIC) }
    var syncedPlaylist by remember { mutableStateOf(allowSyncing && context.isUserLoggedIn()) }

    val focusRequester = remember {
        FocusRequester()
    }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    fun onDone() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val browseId = if(!syncedPlaylist) null else YouTube.createPlaylist(
                    title = title,
                    description = description,
                    privacyStatus = privacyStatus
                )

                database.query {
                    insert(
                        PlaylistEntity(
                            name = title,
                            browseId = browseId,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                            isLocal = !syncedPlaylist // && check that all songs are non-local
                        )
                    )
                }
            } catch (e: Exception) {
                reportException(e)
                toast("Failed to create an playlist!", 1)
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
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Text(text = stringResource(R.string.create_playlist))
            }
        },

        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },

        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && // HTML tags are prohibited on YouTube and will return an error!
                        !(description.contains("<") || description.contains(">")),

                onClick = {
                    onDismiss()
                    onDone()
                }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.playlist_name)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                if(syncedPlaylist) {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Description") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                }

                if(allowSyncing && context.isUserLoggedIn()) {
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = syncedPlaylist,
                                onValueChange = { syncedPlaylist = it },
                                role = Role.Switch
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.create_sync_playlist),
                                style = MaterialTheme.typography.titleMedium,
                            )

                            Text(
                                text = stringResource(R.string.create_sync_playlist_description),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }

                        Switch(
                            modifier = Modifier.padding(top = 8.dp),
                            checked = syncedPlaylist,
                            onCheckedChange = null // null recommended for accessibility with screen readers
                        )
                    }
                }

                if(syncedPlaylist) {
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

        onDismissRequest = onDismiss
    )
}