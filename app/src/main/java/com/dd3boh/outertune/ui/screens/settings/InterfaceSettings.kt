/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ContentCountryKey
import com.dd3boh.outertune.constants.ContentLanguageKey
import com.dd3boh.outertune.constants.CountryCodeToName
import com.dd3boh.outertune.constants.DEFAULT_ENABLED_FILTERS
import com.dd3boh.outertune.constants.DEFAULT_ENABLED_TABS
import com.dd3boh.outertune.constants.DefaultOpenTabKey
import com.dd3boh.outertune.constants.EnabledFiltersKey
import com.dd3boh.outertune.constants.EnabledTabsKey
import com.dd3boh.outertune.constants.LanguageCodeToName
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.SYSTEM_DEFAULT
import com.dd3boh.outertune.constants.SwipeToQueueKey
import com.dd3boh.outertune.constants.SwipeToSkip
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.ui.component.ActionPromptDialog
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.InfoLabel
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.screens.Screens
import com.dd3boh.outertune.ui.screens.Screens.LibraryFilter
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // these are to be arranged in order as they appear
    val (enabledTabs, onEnabledTabsChange) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    val (enabledFilters, onEnabledFiltersChange) = rememberPreference(
        EnabledFiltersKey,
        defaultValue = DEFAULT_ENABLED_FILTERS
    )
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberPreference(DefaultOpenTabKey, defaultValue = "home")

    val (swipeToSkip, onSwipeToSkipChange) = rememberPreference(SwipeToSkip, defaultValue = true)
    val (swipe2Queue, onSwipe2QueueChange) = rememberPreference(SwipeToQueueKey, defaultValue = true)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(
        key = ContentLanguageKey,
        defaultValue = "system"
    )
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")


    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    /**
     * ---------------------------
     * Configurable tabs
     * ---------------------------
     */

    var showTabArrangement by rememberSaveable {
        mutableStateOf(false)
    }
    val mutableTabs = remember { mutableStateListOf<Pair<Screens, Boolean>>() }
    val lazyTabsListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyTabsListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(
                top = ListItemHeight,
                bottom = ListItemHeight
            )
        ).asPaddingValues()
    ) { from, to ->
        val currentDragInfo = dragInfo
        dragInfo = if (currentDragInfo == null) {
            from.index to to.index
        } else {
            currentDragInfo.first to to.index
        }
        mutableTabs.move(from.index, to.index)
    }

    fun updateTabs() {
        mutableTabs.apply {
            clear()

            val enabled = Screens.getScreens(enabledTabs)
            addAll(enabled.map { it to true })
            addAll(
                Screens.getAllScreens().filterNot { it in enabled }.map { it to false }
            )
        }
    }

    LaunchedEffect(showTabArrangement, enabledTabs) {
        updateTabs()
    }


    /**
     * ---------------------------
     * Configurable filters
     * ---------------------------
     */


    var showFilterArrangement by rememberSaveable {
        mutableStateOf(false)
    }
    val mutableFilters = remember { mutableStateListOf<Pair<LibraryFilter, Boolean>>() }
    val lazyFiltersListState = rememberLazyListState()
    val filtersReorderableState = rememberReorderableLazyListState(
        lazyListState = lazyFiltersListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(
                top = ListItemHeight,
                bottom = ListItemHeight
            )
        ).asPaddingValues()
    ) { from, to ->
        val currentDragInfo = dragInfo
        dragInfo = if (currentDragInfo == null) {
            from.index to to.index
        } else {
            currentDragInfo.first to to.index
        }
        mutableFilters.move(from.index, to.index)
    }

    fun updateFilters() {
        mutableFilters.apply {
            clear()

            val enabled = Screens.getFilters(enabledFilters)
            addAll(enabled.map { it to true })
            addAll(
                LibraryFilter.entries.filterNot { it in enabled }.map { it to false }
                    .filterNot { it.first == LibraryFilter.ALL })
        }
    }

    LaunchedEffect(showFilterArrangement, enabledFilters) {
        updateFilters()
    }


    /**
     * ---------------------------
     * Preference keys
     * ---------------------------
     */

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.grp_layout)
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.tab_arrangement)) },
            icon = { Icon(Icons.Rounded.Reorder, null) },
            onClick = {
                showTabArrangement = true
            }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.filter_arrangement)) },
            icon = { Icon(Icons.Rounded.Reorder, null) },
            onClick = {
                showFilterArrangement = true
            }
        )
        ListPreference(
            title = { Text(stringResource(R.string.default_open_tab)) },
            icon = { Icon(Icons.Rounded.Tab, null) },
            selectedValue = Screens.getAllScreens().find { it.route == defaultOpenTab } ?: Screens.Home,
            onValueSelected = { screen ->
                onDefaultOpenTabChange(screen.route)
            },
            values = Screens.getAllScreens().filter { Screens.getScreens(enabledTabs).contains(it) },
            valueText = { stringResource(it.titleId) }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_behavior)
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.swipe2Queue)) },
            description = stringResource(R.string.swipe2Queue_description),
            icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            checked = swipe2Queue,
            onCheckedChange = onSwipe2QueueChange
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.swipe_to_skip_title)) },
            description = stringResource(R.string.swipe_to_skip_description),
            icon = { Icon(Icons.Rounded.Swipe, null) },
            checked = swipeToSkip,
            onCheckedChange = onSwipeToSkipChange
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_localization)
        )
        ListPreference(
            title = { Text(stringResource(R.string.content_language)) },
            icon = { Icon(Icons.Rounded.Language, null) },
            selectedValue = contentLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = {
                LanguageCodeToName.getOrElse(it) {
                    stringResource(R.string.system_default)
                }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()
                val languageTag = locale.toLanguageTag().replace("-Hant", "")

                YouTube.locale = YouTube.locale.copy(
                    hl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.language.takeIf { it in LanguageCodeToName }
                        ?: languageTag.takeIf { it in LanguageCodeToName }
                        ?: "en"
                )

                onContentLanguageChange(newValue)
            }
        )
        ListPreference(
            title = { Text(stringResource(R.string.content_country)) },
            icon = { Icon(Icons.Rounded.LocationOn, null) },
            selectedValue = contentCountry,
            values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
            valueText = {
                CountryCodeToName.getOrElse(it) {
                    stringResource(R.string.system_default)
                }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()

                YouTube.locale = YouTube.locale.copy(
                    gl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.country.takeIf { it in CountryCodeToName }
                        ?: "US"
                )

                onContentCountryChange(newValue)
            }
        )
    }


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showTabArrangement) {
        ActionPromptDialog(
            title = stringResource(R.string.tab_arrangement),
            onDismiss = { showTabArrangement = false },
            onConfirm = {
                var encoded = Screens.encodeScreens(mutableTabs.filter { it.second }.map { it.first })

                // reset defaultOpenTab if it got disabled
                if (Screens.getScreens(encoded).find { it.route == defaultOpenTab } == null)
                    onDefaultOpenTabChange(Screens.Home.route)

                // home is required
                if (!encoded.contains('H')) {
                    encoded += "H"
                }

                onEnabledTabsChange(encoded)
                showTabArrangement = false
            },
            onReset = {
                onEnabledTabsChange(DEFAULT_ENABLED_TABS)
                updateTabs()
            },
            onCancel = {
                showTabArrangement = false
            }
        ) {
            // tabs list
            LazyColumn(
                state = lazyTabsListState,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(ThumbnailCornerRadius)
                    )
            ) {
                itemsIndexed(
                    items = mutableTabs,
                    key = { _, item -> item.hashCode() }
                ) { index, tab ->
                    ReorderableItem(
                        state = reorderableState,
                        key = tab.hashCode()
                    ) {
                        fun onChecked() {
                            mutableTabs[mutableTabs.indexOf(tab)] = tab.copy(second = !tab.second)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                .fillMaxWidth()
                                .clickable { onChecked() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(if (tab.second) MaterialTheme.colorScheme.primary else Color.Transparent)
                            ) {
                                Row(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Text(
                                        text = stringResource(tab.first.titleId),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = null,
                                modifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }

            InfoLabel(stringResource(R.string.tab_arrangement_home_required))
        }
    }

    if (showFilterArrangement) {
        ActionPromptDialog(
            title = stringResource(R.string.filter_arrangement),
            onDismiss = { showFilterArrangement = false },
            onConfirm = {
                val encoded = Screens.encodeFilters(mutableFilters.filter { it.second }.map { it.first })

                onEnabledFiltersChange(encoded)
                showFilterArrangement = false
            },
            onReset = {
                onEnabledFiltersChange(DEFAULT_ENABLED_FILTERS)
                updateFilters()
            },
            onCancel = {
                showFilterArrangement = false
            }
        ) {
            // tabs list
            LazyColumn(
                state = lazyFiltersListState,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(ThumbnailCornerRadius)
                    )
            ) {
                itemsIndexed(
                    items = mutableFilters,
                    key = { _, item -> item.hashCode() }
                ) { index, filter ->
                    ReorderableItem(
                        state = filtersReorderableState,
                        key = filter.hashCode()
                    ) {
                        fun onChecked() {
                            mutableFilters[mutableFilters.indexOf(filter)] = filter.copy(second = !filter.second)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                .fillMaxWidth()
                                .clickable { onChecked() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(if (filter.second) MaterialTheme.colorScheme.primary else Color.Transparent)
                            ) {
                                Row(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Text(
                                        text = when (filter.first) {
                                            LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                            LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                            LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                            LibraryFilter.SONGS -> stringResource(R.string.songs)
                                            LibraryFilter.FOLDERS -> stringResource(R.string.folders)
                                            else -> {
                                                // TODO: Do we even need this?
                                                stringResource(R.string.tab_arrangement_disable_tip)
                                            }
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = null,
                                modifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }
        }
    }


    TopAppBar(
        title = { Text(stringResource(R.string.grp_interface)) },
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
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}

