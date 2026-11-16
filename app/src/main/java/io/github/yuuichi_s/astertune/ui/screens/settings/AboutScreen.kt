/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings

import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import io.github.yuuichi_s.astertune.BuildConfig
import io.github.yuuichi_s.astertune.LocalSnackbarHostState
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AutoUpdateCheckKey
import io.github.yuuichi_s.astertune.constants.ENABLE_FFMETADATAEX
import io.github.yuuichi_s.astertune.constants.LYRIC_FETCH_TIMEOUT
import io.github.yuuichi_s.astertune.constants.LatestKnownVersionKey
import io.github.yuuichi_s.astertune.constants.MAX_LM_SCANNER_JOBS
import io.github.yuuichi_s.astertune.constants.OOBE_VERSION
import io.github.yuuichi_s.astertune.constants.SNACKBAR_VERY_SHORT
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.constants.SettingsHorizontalPadding
import io.github.yuuichi_s.astertune.ui.component.ColumnWithContentPadding
import io.github.yuuichi_s.astertune.ui.component.ContributorCard
import io.github.yuuichi_s.astertune.ui.component.ContributorInfo
import io.github.yuuichi_s.astertune.ui.component.ContributorType.CUSTOM
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.SettingsClickToReveal
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.component.UpdateInstallState
import io.github.yuuichi_s.astertune.ui.component.UpdateInstallStatus
import io.github.yuuichi_s.astertune.ui.component.button.IconLabelButton
import io.github.yuuichi_s.astertune.ui.component.rememberUpdateInstaller
import io.github.yuuichi_s.astertune.utils.rememberPreference
import io.github.yuuichi_s.astertune.utils.updater.AppUpdater
import io.github.yuuichi_s.astertune.utils.updater.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (autoUpdateCheck, onAutoUpdateCheckChange) = rememberPreference(AutoUpdateCheckKey, defaultValue = true)
    val latestKnownVersion by rememberPreference(LatestKnownVersionKey, defaultValue = "")
    var checkingForUpdate by remember { mutableStateOf(false) }
    val installer = rememberUpdateInstaller()
    val updateVersion = latestKnownVersion.takeIf { AppUpdater.isNewer(it) }
    val installerBusy = installer.state is UpdateInstallState.Downloading ||
            installer.state == UpdateInstallState.Installing
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(installer) {
        installer.messages.collect {
            snackbarHostState.showSnackbar(
                message = it.text(context),
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
        }
    }

    val showDebugInfo = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "userdebug"

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.launcher_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground, BlendMode.SrcIn),
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation))
                .clickable { }
        )

        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "AsterTune",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.width(4.dp))

            if (showDebugInfo) {
                Spacer(Modifier.width(4.dp))

                Text(
                    text = BuildConfig.BUILD_TYPE.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 2.dp
                        )
                )
            }
        }

        UpdateInstallStatus(
            installer = installer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsHorizontalPadding)
                .padding(top = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            IconLabelButton(
                text = "GitHub",
                painter = painterResource(R.drawable.github),
                onClick = { uriHandler.openUri("https://github.com/yuuichi-s/AsterTune") },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = SettingsHorizontalPadding)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                PreferenceEntry(
                    title = {
                        Text(
                            stringResource(
                                if (checkingForUpdate) R.string.checking_for_update else R.string.check_for_update
                            )
                        )
                    },
                    description = when {
                        updateVersion != null -> stringResource(R.string.update_available_version, updateVersion)
                        latestKnownVersion.isNotEmpty() -> stringResource(R.string.no_updates_available)
                        else -> null
                    },
                    trailingContent = updateVersion?.let { version ->
                        {
                            when (installer.state) {
                                is UpdateInstallState.Downloading -> OutlinedButton(onClick = installer::cancel) {
                                    Text(stringResource(android.R.string.cancel))
                                }

                                // Android's install can no longer be withdrawn.
                                UpdateInstallState.Installing -> Unit

                                else -> Button(
                                    enabled = !checkingForUpdate,
                                    onClick = {
                                        installer.start {
                                            UpdateChecker.newerRelease?.takeIf { it.version == version }
                                                ?: UpdateChecker.check(context)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.update_install))
                                }
                            }
                        }
                    },
                    // Not disabled while busy, as that would also dim the button on the right.
                    // A check then would replace the version the install is for.
                    onClick = onClick@{
                        if (checkingForUpdate || installerBusy) return@onClick
                        checkingForUpdate = true
                        coroutineScope.launch {
                            val message = try {
                                if (UpdateChecker.check(context) == null) R.string.no_updates_available else null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w("AboutScreen", "Update check failed", e)
                                R.string.update_check_failed
                            } finally {
                                checkingForUpdate = false
                            }
                            if (message != null) {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(message),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                )
                SwitchPreference(
                    title = { Text(stringResource(R.string.auto_update_check)) },
                    description = stringResource(R.string.auto_update_check_description),
                    checked = autoUpdateCheck,
                    onCheckedChange = onAutoUpdateCheckChange
                )
            }
            Spacer(modifier = Modifier.height(SettingsGroupSpacing))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.attribution_title)) },
                    onClick = {
                        navController.navigate("settings/about/attribution")
                    }
                )
                PreferenceEntry(
                    title = { Text(stringResource(R.string.oss_licenses_title)) },
                    onClick = {
                        navController.navigate("settings/about/oss_licenses")
                    }
                )
            }
            Spacer(modifier = Modifier.height(SettingsGroupSpacing))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.help_bug_report_action)) },
                    onClick = {
                        uriHandler.openUri("https://github.com/yuuichi-s/AsterTune/issues")
                    }
                )
            }
            Spacer(modifier = Modifier.height(SettingsGroupSpacing))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsClickToReveal(stringResource(R.string.app_info_title)) {
                    val info = mutableListOf<String>(
                        "TagLib: ${BuildConfig.TAGLIB_VERSION}",
                        "FFmpeg decoder: $ENABLE_FFMETADATAEX",
                        "LM scanner concurrency: $MAX_LM_SCANNER_JOBS",
                        "LYRIC_FETCH_TIMEOUT: $LYRIC_FETCH_TIMEOUT",
                        "OOBE_VERSION: $OOBE_VERSION",
                        "LYRIC_FETCH_TIMEOUT: $LYRIC_FETCH_TIMEOUT",
                        "SNACKBAR_VERY_SHORT: $SNACKBAR_VERY_SHORT"
                    )
                    if (ENABLE_FFMETADATAEX) {
                        info.add("FFmpeg version: ${FfmpegLibrary.getVersion()}")
                        info.add("FFmpeg isAvailable: ${FfmpegLibrary.isAvailable()}")
                    }

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        info.forEach {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                SettingsClickToReveal(stringResource(R.string.device_info_title)) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val info = mutableListOf<String>(
                            "Device: ${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})",
                            "Manufacturer: ${Build.MANUFACTURER}",
                            "HW: ${Build.BOARD} (${Build.HARDWARE})",
                            "ABIs: ${Build.SUPPORTED_ABIS.joinToString()})",
                            "Android: ${Build.VERSION.SDK_INT} (${Build.ID})",
                            Build.DISPLAY,
                            Build.PRODUCT,
                            Build.FINGERPRINT,
                            Build.VERSION.SECURITY_PATCH
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            info.add("SOC: ${Build.SOC_MODEL} (${Build.SOC_MANUFACTURER})")
                            info.add("SKU: ${Build.SKU} (${Build.ODM_SKU})")
                        }

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            info.forEach {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(SettingsGroupSpacing))

            if (ENABLE_FFMETADATAEX) {
                ContributorCard(
                    contributor = ContributorInfo(
                        name = "FFmpeg",
                        description = stringResource(R.string.ffmpeg_lgpl),
                        type = listOf(CUSTOM),
                        url = "https://github.com/OuterTune/ffMetadataEx/blob/main/Modules.md"
                    )
                )
            }
        }

    }

    SettingsTopAppBar(
        title = stringResource(R.string.about),
        navController = navController,
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    SettingsPreview {
        AboutScreen(
            navController = rememberNavController(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        )
    }
}
