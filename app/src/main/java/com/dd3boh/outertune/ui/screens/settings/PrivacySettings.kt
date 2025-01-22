package com.dd3boh.outertune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.PauseListenHistoryKey
import com.dd3boh.outertune.constants.PauseRemoteListenHistoryKey
import com.dd3boh.outertune.constants.PauseSearchHistoryKey
import com.dd3boh.outertune.constants.UseLoginForBrowse
import com.dd3boh.outertune.ui.component.DefaultDialog
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(key = PauseListenHistoryKey, defaultValue = false)
    val (pauseRemoteListenHistory, onPauseRemoteListenHistoryChange) = rememberPreference(key = PauseRemoteListenHistoryKey, defaultValue = true)
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(key = PauseSearchHistoryKey, defaultValue = false)
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(key = UseLoginForBrowse, defaultValue = false)

    var showClearListenHistoryDialog by remember {
        mutableStateOf(false)
    }

    if (showClearListenHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearListenHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_listen_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearListenHistoryDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearListenHistoryDialog = false
                        database.query {
                            clearListenHistory()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    var showClearSearchHistoryDialog by remember {
        mutableStateOf(false)
    }

    if (showClearSearchHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearSearchHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_search_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearSearchHistoryDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearSearchHistoryDialog = false
                        database.query {
                            clearSearchHistory()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        SwitchPreference(
            title = { Text(stringResource(R.string.pause_listen_history)) },
            icon = { Icon(Icons.Rounded.History, null) },
            checked = pauseListenHistory,
            onCheckedChange = onPauseListenHistoryChange
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.pause_listen_history)) },
            icon = { Icon(Icons.Rounded.History, null) },
            checked = pauseRemoteListenHistory,
            onCheckedChange = onPauseRemoteListenHistoryChange,
            isEnabled = pauseListenHistory
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_listen_history)) },
            icon = { Icon(Icons.Rounded.ClearAll, null) },
            onClick = { showClearListenHistoryDialog = true }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.pause_search_history)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.ManageSearch, null) },
            checked = pauseSearchHistory,
            onCheckedChange = onPauseSearchHistoryChange
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_search_history)) },
            icon = { Icon(Icons.Rounded.ClearAll, null) },
            onClick = { showClearSearchHistoryDialog = true }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.account)
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.use_login_for_browse)) },
            description = stringResource(R.string.use_login_for_browse_desc),
            icon = { Icon(Icons.Rounded.Person, null) },
            checked = useLoginForBrowse,
            onCheckedChange = {
                YouTube.useLoginForBrowse = it
                onUseLoginForBrowseChange(it)
            }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.privacy)) },
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
