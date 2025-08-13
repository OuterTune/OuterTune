/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.component

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.R
import com.dd3boh.outertune.ui.component.button.ResizableIconButton
import com.dd3boh.outertune.utils.reportException

@Composable
fun ContributorCard(
    contributor: ContributorInfo,
    titleColour: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColour: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth(1f)
            .clickable {
                contributor.url?.let {
                    try {
                        uriHandler.openUri(it)
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            ResizableIconButton(icon = Icons.Rounded.Person)
        }
        Column(
            modifier = Modifier
                .weight(1f, false)
                .padding(16.dp)
        ) {
            contributor.alias?.let {
                Text(
                    text = "($it)",
                    color = titleColour.copy(0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = contributor.name,
                color = titleColour,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )


            val descriptionText = if (contributor.type.none { it == ContributorType.CUSTOM }) {
                contributor.type.joinToString { type ->
                    ContributorType.getString(context, type, contributor.description)
                }
            } else {
                contributor.description
            }

            descriptionText?.let {
                Text(
                    text = it,
                    color = descriptionColour,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

data class ContributorInfo(
    val name: String,
    val alias: String? = null,
    val type: List<ContributorType>,
    val description: String? = null,
    val url: String? = null
)

enum class ContributorType {
    // General code contributor
    CONTRIBUTOR,
    // Main developers, in charge of the general project direction
    LEAD_DEVELOPER,
    // Maintains the project. (ex. pull requests, bug fixes, documentation, etc)
    MAINTAINER,
    // Non-code related contributions to the project
    PROJECT_SUPPORT,
    // Translation(s) contributor
    TRANSLATOR,
    // Miscellaneous contributions. Anything that does not fit into any of the above categories or requiring custom text
    CUSTOM;

    companion object {
        fun getString(context: Context, contributorType: ContributorType, extraContent: String? = null) =
            when (contributorType) {
                CONTRIBUTOR -> context.getString(R.string.att_contributor)
                LEAD_DEVELOPER -> context.getString(R.string.att_lead_developer)
                MAINTAINER -> context.getString(R.string.att_maintainer)
                PROJECT_SUPPORT -> context.getString(R.string.att_project_support)
                TRANSLATOR -> context.getString(R.string.att_translator, extraContent)
                CUSTOM -> ""
            }
    }
}