/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Coronavirus
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import coil3.imageLoader
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AudioGaplessOffloadKey
import com.dd3boh.outertune.constants.AudioOffloadKey
import com.dd3boh.outertune.constants.DevSettingsKey
import com.dd3boh.outertune.constants.MaxQueuesKey
import com.dd3boh.outertune.constants.OobeStatusKey
import com.dd3boh.outertune.constants.TabletUiKey
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.dialog.CounterDialog
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val uriHandler = LocalUriHandler.current

    // state variables and such
    val (audioGaplessOffload, onAudioGaplessOffloadChange) = rememberPreference(
        key = AudioGaplessOffloadKey,
        defaultValue = false
    )
    val (audioOffload, onAudioOffloadChange) = rememberPreference(key = AudioOffloadKey, defaultValue = false)
    val (maxQueues, onMaxQueuesChange) = rememberPreference(MaxQueuesKey, defaultValue = 19)
    val (tabletUi, onTabletUiChange) = rememberPreference(TabletUiKey, defaultValue = false)

    val (devSettings, onDevSettingsChange) = rememberPreference(DevSettingsKey, defaultValue = false)
    val (oobeStatus, onOobeStatusChange) = rememberPreference(OobeStatusKey, defaultValue = 0)

    var nukeEnabled by remember {
        mutableStateOf(false)
    }

    var showMaxQueuesDialog by remember {
        mutableStateOf(false)
    }

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.experimental_settings_title)
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.tablet_ui_title)) },
            description = stringResource(R.string.tablet_ui_title_description),
            icon = { Icon(Icons.Rounded.Devices, null) },
            checked = tabletUi,
            onCheckedChange = onTabletUiChange
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.max_queues_title)) },
            icon = { Icon(Icons.Rounded.Queue, null) },
            onClick = { showMaxQueuesDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (showMaxQueuesDialog) {
            CounterDialog(
                title = stringResource(R.string.max_queues_title),
                initialValue = maxQueues,
                upperBound = 30,
                lowerBound = 1,
                onDismiss = { showMaxQueuesDialog = false },
                onConfirm = {
                    showMaxQueuesDialog = false
                    coroutineScope.launch(Dispatchers.IO) {
                        onMaxQueuesChange(it)
                        delay(500)
                        // the queues get reloaded, but not cleared by the database
                        // this will allow the user to (immediately) revert any accidental changes
                        playerConnection?.service?.initQueue()
                    }
                },
                onCancel = {
                    showMaxQueuesDialog = false
                }
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.settings_debug)
        )

        PreferenceEntry(
            title = { Text("Flush local image cache") },
            icon = { Icon(Icons.Rounded.Delete, null) },
            onClick = {
                context.imageLoader.memoryCache?.clear()
            }
        )

        // dev settings
        SwitchPreference(
            title = { Text(stringResource(R.string.dev_settings_title)) },
            description = stringResource(R.string.dev_settings_description),
            icon = { Icon(Icons.Rounded.DeveloperMode, null) },
            checked = devSettings,
            onCheckedChange = onDevSettingsChange
        )

        if (devSettings) {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_offload)) },
                description = stringResource(R.string.audio_offload_description),
                icon = { Icon(Icons.Rounded.Bolt, null) },
                checked = audioOffload,
                onCheckedChange = onAudioOffloadChange
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_gapless_offload)) },
                description = stringResource(R.string.audio_gapless_offload_description),
                icon = { Icon(Icons.Rounded.Coronavirus, null) },
                checked = audioGaplessOffload,
                onCheckedChange = onAudioGaplessOffloadChange,
                isEnabled = audioOffload // media3 supports only > SDK 32
            )
            PreferenceEntry(
                title = { Text("Important: About audio offload compatibility and issues") },
                onClick = {
                    uriHandler.openUri("https://github.com/OuterTune/OuterTune/wiki/Audio-offload")
                }
            )

            Spacer(Modifier.height(20.dp))

            PreferenceEntry(
                title = { Text("Delete VisitorData: This may (or may not) help resolve \"Sign in to confirm you're not a bot\" issues. Not recommended for logged in users.") },
                onClick = {
                    runBlocking {
                        context.dataStore.edit { settings ->
                            settings.remove(VisitorDataKey)
                        }
                    }
                }
            )


            PreferenceEntry(
                title = { Text("Enter configurator") },
                icon = { Icon(Icons.Rounded.ConfirmationNumber, null) },
                onClick = {
                    onOobeStatusChange(0)
                    runBlocking { // hax. page loads before pref updates
                        delay(500)
                    }
                    navController.navigate("setup_wizard")
                }
            )


            Spacer(Modifier.height(100.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Material colours",
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(20.dp))

            Column {
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    text = "primary"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSecondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    text = "secondary"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onTertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary,
                    text = "tertiary"
                )
                Spacer(Modifier.height(60.dp))

                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    text = "surface"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    text = "surfaceVariant"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.inverseOnSurface,
                    backgroundColor = MaterialTheme.colorScheme.inverseSurface,
                    text = "inverseSurface"
                )
                Spacer(Modifier.height(60.dp))

                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceBright,
                    text = "surfaceBright"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceDim,
                    text = "surfaceDim"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.surfaceTint,
                    backgroundColor = MaterialTheme.colorScheme.onSurface,
                    text = "surfaceTint"
                )
                Spacer(Modifier.height(60.dp))

                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    text = "surfaceContainerHighest"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    text = "surfaceContainerHigh"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    text = "surfaceContainerLow"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    text = "surfaceContainerLowest"
                )
                Spacer(Modifier.height(60.dp))
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onPrimaryFixed,
                    backgroundColor = MaterialTheme.colorScheme.primaryFixed,
                    text = "primaryFixed"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onPrimaryFixedVariant,
                    backgroundColor = MaterialTheme.colorScheme.primaryFixedDim,
                    text = "primaryFixedDim / onPrimaryFixedVariant"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSecondaryFixed,
                    backgroundColor = MaterialTheme.colorScheme.secondaryFixed,
                    text = "secondaryFixed"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onSecondaryFixedVariant,
                    backgroundColor = MaterialTheme.colorScheme.secondaryFixedDim,
                    text = "secondaryFixedDim / onSecondaryFixedVariant"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onTertiaryFixed,
                    backgroundColor = MaterialTheme.colorScheme.tertiaryFixed,
                    text = "tertiaryFixed"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onTertiaryFixedVariant,
                    backgroundColor = MaterialTheme.colorScheme.tertiaryFixedDim,
                    text = "tertiaryFixedDim / onTertiaryFixedVariant"
                )
                Spacer(Modifier.height(60.dp))

                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onErrorContainer,
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    text = "errorContainer"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.scrim,
                    backgroundColor = Color.Transparent,
                    text = "scrim"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.onBackground,
                    backgroundColor = MaterialTheme.colorScheme.background,
                    text = "background"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.outline,
                    backgroundColor = Color.Transparent,
                    text = "outline"
                )
                ColorBox(
                    sampleColor = MaterialTheme.colorScheme.outlineVariant,
                    backgroundColor = Color.Transparent,
                    text = "outlineVariant"
                )

            }

            Spacer(Modifier.height(60.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Haptics test",
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(20.dp))

            Column {
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    text = "LongPress"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    text = "TextHandleMove"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    text = "ContextClick"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
                Spacer(Modifier.height(32.dp))


                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onSecondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    text = "SegmentTick"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onSecondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    text = "SegmentFrequentTick"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                Spacer(Modifier.height(32.dp))

                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onTertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary,
                    text = "Confirm"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onTertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary,
                    text = "Reject"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onTertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary,
                    text = "ToggleOn"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onTertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary,
                    text = "ToggleOff"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                }
                Spacer(Modifier.height(32.dp))

                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    text = "TextHandleMove"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    text = "VirtualKey"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    text = "GestureEnd"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    text = "GestureThresholdActivate"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                HapticBox(
                    sampleColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    text = "KeyboardTap"
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
            }

            // nukes
            Spacer(Modifier.height(100.dp))
            PreferenceEntry(
                title = { Text("Tap to show nuke options") },
                icon = { Icon(Icons.Rounded.ErrorOutline, null) },
                onClick = {
                    nukeEnabled = !nukeEnabled
                }
            )

            if (nukeEnabled) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "WARNING: These options have NO confirmation and will apply immediately!",
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.height(20.dp))

                PreferenceEntry(
                    title = { Text("DEBUG: Nuke local lib") },
                    icon = { Icon(Icons.Rounded.ErrorOutline, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking local files from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Log.i(SETTINGS_TAG, "Nuke database status:  ${database.nukeLocalData()}")
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke local artists") },
                    icon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking local artists from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Log.i(SETTINGS_TAG, "Nuke database status:  ${database.nukeLocalArtists()}")
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke dangling format entities") },
                    icon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking dangling format entities from database...", Toast.LENGTH_SHORT)
                            .show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Log.i(SETTINGS_TAG, "Nuke database status:  ${database.nukeDanglingFormatEntities()}")
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke local db lyrics") },
                    icon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking local lyrics from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Log.i(SETTINGS_TAG, "Nuke database status:  ${database.nukeLocalLyrics()}")
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke dangling db lyrics") },
                    icon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking dangling lyrics from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Log.i(SETTINGS_TAG, "Nuke database status:  ${database.nukeDanglingLyrics()}")
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke remote playlists") },
                    icon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking remote playlists from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Log.i(SETTINGS_TAG, "Nuke database status:  ${database.nukeRemotePlaylists()}")
                        }
                    }
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.experimental_settings_title)) },
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

@Composable
fun ColorBox(
    sampleColor: Color,
    backgroundColor: Color,
    text: String,
    padding: Dp = 10.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .background(sampleColor)
                .padding(padding)
                .width(140.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = backgroundColor,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(32.dp))
        Text(
            text = text,
            color = sampleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun HapticBox(
    sampleColor: Color,
    backgroundColor: Color,
    text: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 4.dp)
            .background(backgroundColor)
    ) {
        Text(
            text = text,
            color = sampleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable(
                    onClick = onClick
                )
        )
    }
}