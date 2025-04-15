/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.screens.settings.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerImplKey
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.constants.ScannerSensitivityKey
import com.dd3boh.outertune.constants.ScannerStrictExtKey
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.utils.isPackageInstalled
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.LocalScannerExtraFrag() {
    val context = LocalContext.current

    val (scannerSensitivity, onScannerSensitivityChange) = rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerMatchCriteria.LEVEL_2
    )
    val (scannerImpl, onScannerImplChange) = rememberEnumPreference(
        key = ScannerImplKey,
        defaultValue = ScannerImpl.TAGLIB
    )
    val (strictExtensions, onStrictExtensionsChange) = rememberPreference(ScannerStrictExtKey, defaultValue = false)


    // scanner sensitivity
    EnumListPreference(
        title = { Text(stringResource(R.string.scanner_sensitivity_title)) },
        icon = { Icon(Icons.Rounded.GraphicEq, null) },
        selectedValue = scannerSensitivity,
        onValueSelected = onScannerSensitivityChange,
        valueText = {
            when (it) {
                ScannerMatchCriteria.LEVEL_1 -> stringResource(R.string.scanner_sensitivity_L1)
                ScannerMatchCriteria.LEVEL_2 -> stringResource(R.string.scanner_sensitivity_L2)
                ScannerMatchCriteria.LEVEL_3 -> stringResource(R.string.scanner_sensitivity_L3)
            }
        }
    )
    // strict file ext
    SwitchPreference(
        title = { Text(stringResource(R.string.scanner_strict_file_name_title)) },
        description = stringResource(R.string.scanner_strict_file_name_description),
        icon = { Icon(Icons.Rounded.TextFields, null) },
        checked = strictExtensions,
        onCheckedChange = onStrictExtensionsChange
    )
    // scanner type
    val isFFmpegInstalled = rememberFFmpegAvailability()

    // if plugin is not found, although we reset if a scan is run, ensure the user is made aware if in settings page
    LaunchedEffect(isFFmpegInstalled) {
        if (scannerImpl == ScannerImpl.FFMPEG_EXT && !isFFmpegInstalled) {
            onScannerImplChange(ScannerImpl.TAGLIB)
        }
    }

    EnumListPreference(
        title = { Text(stringResource(R.string.scanner_type_title)) },
        icon = { Icon(Icons.Rounded.Speed, null) },
        selectedValue = scannerImpl,
        onValueSelected = {
            if (it == ScannerImpl.FFMPEG_EXT && isFFmpegInstalled) {
                onScannerImplChange(it)
            } else {
                Toast.makeText(context, context.getString(R.string.scanner_missing_ffmpeg), Toast.LENGTH_LONG)
                    .show()
                // Explicitly revert to TagLib if FFmpeg is not available
                onScannerImplChange(ScannerImpl.TAGLIB)
            }
        },
        valueText = {
            when (it) {
                ScannerImpl.TAGLIB -> stringResource(R.string.scanner_type_taglib)
                ScannerImpl.FFMPEG_EXT -> stringResource(R.string.scanner_type_ffmpeg_ext)
            }
        },
        values = ScannerImpl.entries,
        disabled = { it == ScannerImpl.FFMPEG_EXT && !isFFmpegInstalled }
    )
}

@Composable
fun rememberFFmpegAvailability(): Boolean {
    val context = LocalContext.current
    var isFFmpegInstalled by remember {
        mutableStateOf(isPackageInstalled("wah.mikooomich.ffMetadataEx", context.packageManager))
    }

    DisposableEffect(context) {
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_ADDED -> {
                        isFFmpegInstalled = context?.packageManager?.let {
                            isPackageInstalled(
                                "wah.mikooomich.ffMetadataEx",
                                it
                            )
                        } == true
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }

        context.registerReceiver(packageReceiver, filter)

        onDispose {
            context.unregisterReceiver(packageReceiver)
        }
    }

    return isFFmpegInstalled
}