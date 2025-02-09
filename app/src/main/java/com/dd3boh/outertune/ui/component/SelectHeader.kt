/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.R
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.ui.menu.SelectionMediaMetadataMenu

@Composable
fun RowScope.SelectHeader(
    selectedItems: List<MediaMetadata>,
    totalItemCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    menuState: MenuState,
    onDismiss: () -> Unit = {},
    onRemoveFromHistory: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp, bottom = 3.dp)
    ) {
        Text(
            text = "${selectedItems.size}/${context.resources.getQuantityString(R.plurals.n_selected, totalItemCount, totalItemCount)}",
            modifier = Modifier.weight(1f, false)
        )

        // option menu
        IconButton(
            onClick = {
                menuState.show {
                    SelectionMediaMetadataMenu(
                        selection = selectedItems,
                        onDismiss = menuState::dismiss,
                        clearAction = onDeselectAll,
                        onRemoveFromHistory = onRemoveFromHistory
                    )
                }
            }
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = LocalContentColor.current
            )
        }

        // select/deselect all
        val allSelected = selectedItems.size < totalItemCount

        IconButton(
            onClick = if (allSelected) onSelectAll else onDeselectAll
        ) {
            Icon(
                imageVector = if (allSelected) Icons.Rounded.SelectAll else Icons.Rounded.Deselect,
                contentDescription = null,
                tint = LocalContentColor.current
            )
        }

        // close selection mode
        IconButton(
            onClick = onDismiss,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null
            )
        }
    }
}