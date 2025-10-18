/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AutomaticScannerKey
import com.dd3boh.outertune.constants.DEFAULT_ENABLED_FILTERS
import com.dd3boh.outertune.constants.DEFAULT_ENABLED_TABS
import com.dd3boh.outertune.constants.EnabledFiltersKey
import com.dd3boh.outertune.constants.EnabledTabsKey
import com.dd3boh.outertune.constants.LocalLibraryEnableKey
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.dialog.DefaultDialog
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.ui.screens.settings.fragments.LocalScannerExtraFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.LocalScannerFrag
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberPreference


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (autoScan, onAutoScanChange) = rememberPreference(AutomaticScannerKey, defaultValue = true)
    val (enabledFilters, onEnabledFiltersChange) = rememberPreference(EnabledFiltersKey, defaultValue = DEFAULT_ENABLED_FILTERS)
    val (enabledTabs, onEnabledTabsChange) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    val (localLibEnable, onLocalLibEnableChange) = rememberPreference(LocalLibraryEnableKey, defaultValue = true)

    LaunchedEffect(localLibEnable) {
        var containsFolders = enabledTabs.contains('F')
        if (!localLibEnable && containsFolders) {
            onEnabledTabsChange(enabledTabs.filterNot { it == 'F' })
        }

        containsFolders = enabledFilters.contains('F')
        if (!localLibEnable && containsFolders) {
            onEnabledFiltersChange(enabledFilters.filterNot { it == 'F' })
        }
    }

    var showLmDisableDialog by remember {
        mutableStateOf(false)
    }

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SwitchPreference(
            title = { Text(stringResource(R.string.local_library_enable_title)) },
            description = stringResource(R.string.local_library_enable_description),
            icon = { Icon(Icons.Rounded.SdCard, null) },
            checked = localLibEnable,
            onCheckedChange = {
                if (localLibEnable) {
                    showLmDisableDialog = true
                } else {
                    onLocalLibEnableChange(it)
                }
            }
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            // automatic scanner
            SwitchPreference(
                title = { Text(stringResource(R.string.auto_scanner_title)) },
                description = stringResource(R.string.auto_scanner_description),
                icon = { Icon(Icons.Rounded.Autorenew, null) },
                checked = autoScan,
                onCheckedChange = onAutoScanChange
            )
            InfoLabel(
                text = stringResource(R.string.auto_scanner_tooltip),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(localLibEnable) {
            Column {

                PreferenceGroupTitle(
                    title = stringResource(R.string.grp_manual_scanner)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LocalScannerFrag()
                }
                Spacer(modifier = Modifier.height(16.dp))

                PreferenceGroupTitle(
                    title = stringResource(R.string.grp_extra_scanner_settings)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LocalScannerExtraFrag()
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */
    if (showLmDisableDialog) {
        DefaultDialog(
            onDismiss = { showLmDisableDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.disable_lm_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showLmDisableDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showLmDisableDialog = false
                        onLocalLibEnableChange(false)
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.local_player_settings_title)) },
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
