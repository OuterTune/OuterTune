package com.dd3boh.outertune.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5
const val CONTENT_TYPE_FOLDER = 6

val NavigationBarHeight = 80.dp
val MiniPlayerHeight = 64.dp
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SuggestionItemHeight = 56.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 48.dp
val GridThumbnailHeight = 128.dp
val AlbumThumbnailSize = 144.dp
@OptIn(ExperimentalMaterial3Api::class)
val SettingsTopBarHeight = TopAppBarDefaults.TopAppBarExpandedHeight + 32.dp

val ThumbnailCornerRadius = 6.dp
val MenuCornerRadius = 16.dp
val DialogCornerRadius = 32.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec = spring<Dp>(stiffness = Spring.StiffnessMediumLow)
