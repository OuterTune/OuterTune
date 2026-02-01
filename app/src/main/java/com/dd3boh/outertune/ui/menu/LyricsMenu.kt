package com.dd3boh.outertune.ui.menu

import android.app.SearchManager
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.MultilineLrcKey
import com.dd3boh.outertune.db.entities.LyricsEntity
import com.dd3boh.outertune.extensions.isInternetConnected
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SettingsClickToReveal
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.dialog.DefaultDialog
import com.dd3boh.outertune.ui.dialog.ListDialog
import com.dd3boh.outertune.ui.dialog.TextFieldDialog
import com.dd3boh.outertune.ui.screens.settings.fragments.LyricFormatFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.LyricParserFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.LyricSourceFrag
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LyricsMenuViewModel
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.parseLrc


@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
    onRefreshRequest: (SemanticLyrics?) -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current

    val multilineLrc by rememberPreference(MultilineLrcKey, defaultValue = true)
    val lyricTrim by rememberPreference(LyricTrimKey, defaultValue = false)

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = {
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadataProvider().id,
                            lyrics = it
                        )
                    )
                }
                onRefreshRequest(parseLrc(it, lyricTrim, multilineLrc))
            }
        )
    }

    var showSearchDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val searchMediaMetadata = remember(showSearchDialog) {
        mediaMetadataProvider()
    }
    val (titleField, onTitleFieldChange) = rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = mediaMetadataProvider().title
            )
        )
    }
    val (artistField, onArtistFieldChange) = rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = mediaMetadataProvider().artists.joinToString { it.name }
            )
        )
    }

    if (showSearchDialog) {
        DefaultDialog(
            onDismiss = { showSearchDialog = false },
            icon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
            title = { Text(stringResource(R.string.search_lyrics)) },
            buttons = {
                TextButton(
                    onClick = { showSearchDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        showSearchDialog = false
                        onDismiss()
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra(SearchManager.QUERY, "${artistField.text} ${titleField.text} lyrics")
                                }
                            )
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Text(stringResource(R.string.search_online))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        viewModel.search(
                            searchMediaMetadata.id,
                            titleField.text,
                            artistField.text,
                            searchMediaMetadata.duration
                        )
                        showSearchResultDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            OutlinedTextField(
                value = titleField,
                onValueChange = onTitleFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_title)) }
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = artistField,
                onValueChange = onArtistFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_artists)) }
            )
        }
    }

    if (showSearchResultDialog) {
        val results by viewModel.results.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        var expandedItemIndex by rememberSaveable {
            mutableIntStateOf(-1)
        }

        ListDialog(
            onDismiss = { showSearchResultDialog = false }
        ) {
            itemsIndexed(results) { index, result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            viewModel.cancelSearch()
                            database.query {
                                upsert(
                                    LyricsEntity(
                                        id = searchMediaMetadata.id,
                                        lyrics = result.lyrics
                                    )
                                )
                            }

                            onRefreshRequest(parseLrc(result.lyrics, lyricTrim, multilineLrc))
                        }
                        .padding(12.dp)
                        .animateContentSize()
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = result.lyrics,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (index == expandedItemIndex) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result.providerName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                            if (result.lyrics.startsWith("[")) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            expandedItemIndex = if (expandedItemIndex == index) -1 else index
                        }
                    ) {
                        Icon(
                            imageVector = if (index == expandedItemIndex) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!isLoading && results.isEmpty()) {
                item {
                    Text(
                        text = if (!context.isInternetConnected()) {
                            context.getString(R.string.error_no_internet)
                        } else {
                            context.getString(R.string.lyrics_not_found)
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    var showDeleteLyric by remember {
        mutableStateOf(false)
    }

    if (showDeleteLyric) {
        DefaultDialog(
            onDismiss = { showDeleteLyric = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_lyric_confirm, mediaMetadataProvider().title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeleteLyric = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeleteLyric = false
                        onDismiss()

                        lyricsProvider()?.let {
                            database.query {
                                delete(it)
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    var showSettings by remember {
        mutableStateOf(false)
    }
    if (showSettings) {
        DefaultDialog(
            onDismiss = { showSettings = false },
            content = {
                Column() {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    PreferenceGroupTitle(
                        title = stringResource(R.string.grp_lyrics_format)
                    )
                    LyricFormatFrag()

                    SettingsClickToReveal(stringResource(R.string.more_settings)) {
                        PreferenceGroupTitle(
                            title = stringResource(R.string.grp_lyrics_source)
                        )
                        LyricSourceFrag()

                        PreferenceGroupTitle(
                            title = stringResource(R.string.grp_lyrics_parser)
                        )
                        LyricParserFrag()
                    }
                }
            },
            buttons = {
                TextButton(
                    onClick = {
                        showSettings = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = Icons.Rounded.Edit,
            title = R.string.edit
        ) {
            showEditDialog = true
        }
        GridMenuItem(
            icon = Icons.Rounded.SyncAlt,
            title = R.string.refetch
        ) {
            onDismiss()
            viewModel.refetchLyrics(mediaMetadataProvider()) { onRefreshRequest(it) }
        }
        GridMenuItem(
            icon = Icons.Rounded.Search,
            title = R.string.search,
        ) {
            showSearchDialog = true
        }
        if (lyricsProvider() != null) {
            // TODO: hide this for when lrc exists and lyrics is not in the database
            GridMenuItem(
                icon = Icons.Rounded.Delete,
                title = R.string.delete,
            ) {
                showDeleteLyric = true
            }
        }

        GridMenuItem(
            icon = Icons.Rounded.Settings,
            title = R.string.settings,
        ) {
            showSettings = true
        }
    }
}
