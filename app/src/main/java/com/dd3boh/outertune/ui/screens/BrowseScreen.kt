package com.dd3boh.outertune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.GridThumbnailHeight
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.YouTubeGridItem
import com.dd3boh.outertune.ui.component.shimmer.GridItemPlaceHolder
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.ui.menu.YouTubeAlbumMenu
import com.dd3boh.outertune.ui.menu.YouTubeArtistMenu
import com.dd3boh.outertune.ui.menu.YouTubePlaylistMenu
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.viewmodels.BrowseViewModel
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    browseId: String?,
    viewModel: BrowseViewModel = hiltViewModel(
        key = browseId,
    ),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val title by viewModel.title.collectAsState()
    val items by viewModel.items.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
        items?.let { items ->
            items(
                items = items,
                key = { it.id }
            ) { item ->
                YouTubeGridItem(
                    item = item,
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                    coroutineScope = coroutineScope,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is AlbumItem -> navController.navigate("album/${item.id}")
                                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                    is ArtistItem -> navController.navigate("artist/${item.id}")
                                    else -> {
                                        // Do nothing
                                    }
                                }
                            },
                            onLongClick = {
                                menuState.show {
                                    when (item) {
                                        is AlbumItem ->
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )

                                        is PlaylistItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss
                                            )
                                        }

                                        is ArtistItem -> {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = menuState::dismiss
                                            )
                                        }

                                        else -> {
                                            // Do nothing
                                        }
                                    }
                                }
                            }
                        )
                )
            }

            if (items.isEmpty()) {
                items(8) {
                    ShimmerHost {
                        GridItemPlaceHolder(fillMaxWidth = true)
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(title ?: "") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
