/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.BuildConfig
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AutomaticScannerKey
import com.dd3boh.outertune.constants.DEFAULT_ENABLED_FILTERS
import com.dd3boh.outertune.constants.DEFAULT_ENABLED_TABS
import com.dd3boh.outertune.constants.DownloadPathKey
import com.dd3boh.outertune.constants.EnabledFiltersKey
import com.dd3boh.outertune.constants.EnabledTabsKey
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.LibraryFilterKey
import com.dd3boh.outertune.constants.LocalLibraryEnableKey
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.MaxSongCacheSizeKey
import com.dd3boh.outertune.constants.NavigationBarHeight
import com.dd3boh.outertune.constants.OOBE_VERSION
import com.dd3boh.outertune.constants.OobeStatusKey
import com.dd3boh.outertune.constants.ScanPathsKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.button.IconLabelButton
import com.dd3boh.outertune.ui.dialog.ActionPromptDialog
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.ui.screens.Screens.LibraryFilter
import com.dd3boh.outertune.ui.screens.settings.fragments.AccountFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.LocalScannerFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.LocalizationFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.ThemeAppFrag
import com.dd3boh.outertune.utils.dlCoroutine
import com.dd3boh.outertune.utils.formatFileSize
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.scanners.stringFromUriList
import com.dd3boh.outertune.utils.scanners.uriListFromString
import com.zionhuang.innertube.utils.parseCookieString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizard(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val uriHandler = LocalUriHandler.current

    var oobeStatus by rememberPreference(OobeStatusKey, defaultValue = 0)

    // content prefs
    var filter by rememberEnumPreference(LibraryFilterKey, LibraryFilter.ALL)


    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (ytmSync, onYtmSyncChange) = rememberPreference(LyricTrimKey, defaultValue = true)

    // local media prefs
    val (localLibEnable, onLocalLibEnableChange) = rememberPreference(LocalLibraryEnableKey, defaultValue = true)
    val (autoScan, onAutoScanChange) = rememberPreference(AutomaticScannerKey, defaultValue = true)
    val (enabledTabs, onEnabledTabsChange) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    val (enabledFilters, onEnabledFiltersChange) = rememberPreference(EnabledFiltersKey, defaultValue = DEFAULT_ENABLED_FILTERS)

    LaunchedEffect(localLibEnable) {
        var containsFolders = enabledTabs.contains('F')
        if (localLibEnable && !containsFolders) {
            onEnabledTabsChange(enabledTabs + "F")
        } else if (!localLibEnable && containsFolders) {
            onEnabledTabsChange(enabledTabs.filterNot { it == 'F' })
        }

        containsFolders = enabledFilters.contains('F')
        if (!localLibEnable && containsFolders) {
            onEnabledFiltersChange(enabledFilters.filterNot { it == 'F' })
        }
    }

    BackHandler {
        if (oobeStatus > 0) {
            oobeStatus -= 1
        } else {
            // user may not dismiss via back
        }
    }

    val navBar = @Composable {
        // nav bar
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    if (oobeStatus > 0) {
                        oobeStatus -= 1
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            ) {
                Text(
                    text = stringResource(R.string.action_back),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                    contentDescription = null
                )
            }

            LinearProgressIndicator(
                progress = { oobeStatus.toFloat() / (OOBE_VERSION - 1) },
//                color = ProgressIndicatorDefaults.linearColor,
//                trackColor = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Butt,
                drawStopIndicator = {},
                modifier = Modifier
                    .weight(1f, false)
                    .height(8.dp)  // Height of the progress bar
                    .padding(2.dp),  // Add some padding at the top
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    if (oobeStatus == 1) {
                        filter = LibraryFilter.ALL // hax
                    }

                    if (oobeStatus < OOBE_VERSION) {
                        oobeStatus += 1
                    }

                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                    contentDescription = null
                )
                Text(
                    text = stringResource(R.string.action_next),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (oobeStatus > 0 && oobeStatus < OOBE_VERSION - 1) {
                Box(
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                        .fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        navBar()
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(
                    PaddingValues(
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        top = 0.dp,
                        end = paddingValues.calculateEndPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    )
                )
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 16.dp))

                when (oobeStatus) {
                    0 -> { // landing page
                        Image(
                            painter = painterResource(R.drawable.launcher_monochrome),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.SrcIn),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(
                                        NavigationBarDefaults.Elevation
                                    )
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }
                        )

                        Text(
                            text = stringResource(R.string.oobe_welcome_message),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            textAlign = TextAlign.Center
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 48.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            OobeFeatureRow(
                                title = stringResource(R.string.oobe_ytm_integration),
                                description = stringResource(R.string.oobe_ytm_integration_description),
                                icon = Icons.Rounded.MusicNote,
                                MaterialTheme.colorScheme.secondary
                            )
                            OobeFeatureRow(
                                title = stringResource(R.string.oobe_ad_free_exp),
                                description = stringResource(R.string.oobe_ad_free_exp_description),
                                icon = Icons.Rounded.Block,
                                Color.Red
                            )
                            OobeFeatureRow(
                                title = stringResource(R.string.oobe_cross_platform_sync),
                                description = stringResource(R.string.oobe_cross_platform_sync_description),
                                icon = Icons.Rounded.Sync,
                                MaterialTheme.colorScheme.tertiary
                            )
                            OobeFeatureRow(
                                title = stringResource(R.string.oobe_local_music_support),
                                description = stringResource(R.string.oobe_local_music_support_description),
                                icon = Icons.Rounded.SdCard,
                                MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        InfoLabel(
                            text = stringResource(R.string.oobe_welcome_tip),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    navController.navigate("settings/backup_restore")
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.oobe_use_backup),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            TextButton(
                                onClick = {
                                    oobeStatus = OOBE_VERSION
                                    navController.navigateUp()
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_skip),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // appearance
                    1 -> {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.grp_interface),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        Text(
                            text = stringResource(R.string.oobe_interface_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                        )


                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeAppFrag()
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LocalizationFrag()
                        }
                    }

                    // account
                    2 -> {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.oobe_ytm_logon_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        Text(
                            text = stringResource(R.string.oobe_ytm_logon_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                        )


                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AccountFrag(navController)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.ytm_sync)) },
                                icon = { Icon(Icons.Rounded.Lyrics, null) },
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange,
                                isEnabled = isLoggedIn
                            )
                        }
                    }

                    // local media
                    3 -> {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.oobe_local_media_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        Text(
                            text = stringResource(R.string.oobe_local_media_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                        )

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.local_library_enable_title)) },
                                description = stringResource(R.string.local_library_enable_description),
                                icon = { Icon(Icons.Rounded.SdCard, null) },
                                checked = localLibEnable,
                                onCheckedChange = onLocalLibEnableChange
                            )
                        }

                        AnimatedVisibility(localLibEnable) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SwitchPreference(
                                        title = { Text(stringResource(R.string.auto_scanner_title)) },
                                        description = stringResource(R.string.auto_scanner_description),
                                        icon = { Icon(Icons.Rounded.Autorenew, null) },
                                        checked = autoScan,
                                        onCheckedChange = onAutoScanChange
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    PreferenceGroupTitle(
                                        title = stringResource(R.string.grp_manual_scanner)
                                    )


                                    LocalScannerFrag()
                                }
                            }

                        }
                    }

                    // downloads
                    4 -> {
                        val downloadUtil = LocalDownloadUtil.current
                        val (downloadPath, onDownloadPathChange) = rememberPreference(DownloadPathKey, "")
                        val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(
                            key = MaxSongCacheSizeKey,
                            defaultValue = 0
                        )
                        val (scanPaths, onScanPathsChange) = rememberPreference(ScanPathsKey, defaultValue = "")

                        var showDlPathDialog: Boolean by remember {
                            mutableStateOf(false)
                        }


                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.oobe_downloads_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        Text(
                            text = stringResource(R.string.oobe_downloads_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                        )

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PreferenceEntry(
                                title = { Text(stringResource(R.string.dl_main_path_title)) },
                                onClick = {
                                    showDlPathDialog = true
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        InfoLabel(stringResource(R.string.dl_oobe_tooltip))

                        Spacer(Modifier.height(16.dp))
                        Icon(
                            imageVector = Icons.Rounded.Cached,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.song_cache), // TODO: oobe_cache_subtitle when localization is done
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.oobe_cache_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                        )

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListPreference(
                                title = { Text(stringResource(R.string.max_cache_size)) },
                                selectedValue = maxSongCacheSize,
                                values = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1),
                                valueText = {
                                    when (it) {
                                        0 -> stringResource(androidx.compose.ui.R.string.state_off)
                                        -1 -> stringResource(R.string.unlimited)
                                        else -> formatFileSize(it * 1024 * 1024L)
                                    }
                                },
                                onValueSelected = onMaxSongCacheSizeChange
                            )
                            InfoLabel(stringResource(R.string.restart_to_apply_changes))
                            Spacer(Modifier.height(12.dp))
                        }

                        if (showDlPathDialog) {
                            var tempFilePath by remember {
                                mutableStateOf<Uri?>(null)
                            }
                            LaunchedEffect(downloadPath) {
                                tempFilePath = uriListFromString(downloadPath).firstOrNull()
                            }

                            ActionPromptDialog(
                                titleBar = {
                                    Text(
                                        text = stringResource(R.string.dl_main_path_title),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                },
                                onDismiss = {
                                    showDlPathDialog = false
                                    tempFilePath = null
                                },
                                onConfirm = {
                                    tempFilePath?.let { f ->
                                        val uris = stringFromUriList(listOfNotNull(f))
                                        onDownloadPathChange(uris)
                                    }

                                    showDlPathDialog = false
                                    tempFilePath = null

                                    coroutineScope.launch(dlCoroutine) {
                                        delay(1000)
                                        downloadUtil.cd()
                                        downloadUtil.scanDownloads()
                                    }
                                },
                                onReset = {
                                    tempFilePath = null
                                },
                                onCancel = {
                                    showDlPathDialog = false
                                    tempFilePath = null
                                },
                                isInputValid = uriListFromString(scanPaths).none {
                                    // download path cannot a scan path, or a subdir of a scan path
                                    tempFilePath.toString().length <= it.toString().length && tempFilePath.toString()
                                        .contains(it.toString())
                                },
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState()),
                            ) {

                                val dirPickerLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.OpenDocumentTree()
                                ) { uri ->
                                    if (tempFilePath.toString() == uri.toString()) return@rememberLauncherForActivityResult
                                    if (uri?.path != null) {
                                        // Take persistable URI permission
                                        val contentResolver = context.contentResolver
                                        val takeFlags: Int =
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        contentResolver.takePersistableUriPermission(uri, takeFlags)

                                        tempFilePath = uri
                                    }
                                }

                                val valid = uriListFromString(scanPaths).none {
                                    // download path cannot a scan path, or a subdir of a scan path
                                    tempFilePath.toString().length <= it.toString().length && tempFilePath.toString()
                                        .contains(it.toString())
                                }

                                Text(
                                    text = stringResource(R.string.dl_main_path_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Spacer(Modifier.padding(vertical = 8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            RoundedCornerShape(ThumbnailCornerRadius)
                                        )
                                        .background(if (valid) Color.Transparent else MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    tempFilePath?.let {
                                        Text(
                                            text = it.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                // add folder button
                                Column {
                                    Button(onClick = { dirPickerLauncher.launch(null) }) {
                                        Text(stringResource(R.string.scan_paths_add_folder))
                                    }

                                    InfoLabel(
                                        text = stringResource(R.string.scan_paths_tooltip),
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )

                                    if (!valid) {
                                        InfoLabel(
                                            text = stringResource(R.string.scanner_rejected_dir),
                                            isError = true,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // exit page
                    5 -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .padding(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.oobe_complete_title),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.oobe_complete),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                IconLabelButton(
                                    text = "GitHub",
                                    icon = Icons.Rounded.Code,
                                    onClick = { uriHandler.openUri("https://github.com/OuterTune/OuterTune") },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconLabelButton(
                                    text = "Wiki",
                                    icon = Icons.Outlined.Info,
                                    onClick = { uriHandler.openUri("https://github.com/OuterTune/OuterTune/wiki") },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            Text(
                                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(NavigationBarHeight))
            }

            if (oobeStatus == 0 || oobeStatus == OOBE_VERSION - 1) {
                FloatingActionButton(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomEnd),
                    onClick = {
                        if (oobeStatus == 0) {
                            oobeStatus += 1
                        } else {
                            oobeStatus = OOBE_VERSION
                            navController.navigateUp()
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}


@Composable
private fun OobeFeatureRow(title: String, description: String?, icon: ImageVector, tint: Color) {
    val haptic = LocalHapticFeedback.current

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
