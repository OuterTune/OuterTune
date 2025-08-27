/*
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.menu

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DropdownItem(
    val title: String,
    val leadingIcon: @Composable (() -> Unit)?,
    val action: () -> Unit
)

@Composable
fun ActionDropdown(
    actions: List<DropdownItem>,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        IconButton(
            onClick = {
                menuExpanded = true
            },
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = 172.dp)
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    leadingIcon = action.leadingIcon,
                    text = {
                        Text(
                            text = action.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                    },
                    onClick = {
                        action.action()
                        menuExpanded = false
                    }
                )
            }
        }
    }
}