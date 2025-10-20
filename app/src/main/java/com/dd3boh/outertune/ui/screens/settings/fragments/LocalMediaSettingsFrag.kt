/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens.settings.fragments

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalSnackbarHostState
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DownloadExtraPathKey
import com.dd3boh.outertune.constants.DownloadPathKey
import com.dd3boh.outertune.constants.ENABLE_FFMETADATAEX
import com.dd3boh.outertune.constants.ExcludedScanPathsKey
import com.dd3boh.outertune.constants.LastLocalScanKey
import com.dd3boh.outertune.constants.LookupYtmArtistsKey
import com.dd3boh.outertune.constants.SCANNER_OWNER_LM
import com.dd3boh.outertune.constants.ScanPathsKey
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerImplKey
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.constants.ScannerSensitivityKey
import com.dd3boh.outertune.constants.ScannerStrictExtKey
import com.dd3boh.outertune.constants.ScannerStrictFilePathsKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.dialog.ActionPromptDialog
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.ui.utils.MEDIA_PERMISSION_LEVEL
import com.dd3boh.outertune.ui.utils.clearDtCache
import com.dd3boh.outertune.utils.lmScannerCoroutine
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.destroyScanner
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.getScanner
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerProgressCurrent
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerProgressTotal
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerRequestCancel
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerState
import com.dd3boh.outertune.utils.scanners.ScannerAbortException
import com.dd3boh.outertune.utils.scanners.absoluteFilePathFromUri
import com.dd3boh.outertune.utils.scanners.stringFromUriList
import com.dd3boh.outertune.utils.scanners.uriListFromString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset


@Composable
fun ColumnScope.LocalScannerFrag() {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val snackbarHostState = LocalSnackbarHostState.current

    // scanner vars
    val scannerState by scannerState.collectAsState()
    val scannerProgressTotal by scannerProgressTotal.collectAsState()
    val scannerProgressCurrent by scannerProgressCurrent.collectAsState()

    var scannerFailure = false
    var mediaPermission by remember { mutableStateOf(true) }

    /**
     * True = include folders
     * False = exclude folders
     * Null = don't show dialog
     */
    var showAddFolderDialog: Boolean? by remember {
        mutableStateOf(null)
    }

    // scanner prefs
    val scannerSensitivity by rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerMatchCriteria.LEVEL_2
    )
    val scannerImpl by rememberEnumPreference(
        key = ScannerImplKey,
        defaultValue = ScannerImpl.TAGLIB
    )
    val strictExtensions by rememberPreference(ScannerStrictExtKey, defaultValue = false)
    val strictFilePaths by rememberPreference(ScannerStrictFilePathsKey, defaultValue = false)
    val downloadPath by rememberPreference(DownloadPathKey, "")
    val (scanPaths, onScanPathsChange) = rememberPreference(ScanPathsKey, defaultValue = "")
    val (excludedScanPaths, onExcludedScanPathsChange) = rememberPreference(ExcludedScanPathsKey, defaultValue = "")
    val dlPathExtra by rememberPreference(DownloadExtraPathKey, "")

    var fullRescan by remember { mutableStateOf(false) }
    val (lookupYtmArtists, onLookupYtmArtistsChange) = rememberPreference(LookupYtmArtistsKey, defaultValue = false)

    val (lastLocalScan, onLastLocalScanChange) = rememberPreference(LastLocalScanKey, 0L)

    LaunchedEffect(scanPaths) {
        if (scanPaths.isBlank()) {
            showAddFolderDialog = true
        }
    }

    // scanner
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, // WHY WON'T YOU CENTER
    ) {
        Button(
            onClick = {
                // cancel button
                if (scannerState > 0) {
                    scannerRequestCancel = true
                }

                // check permission
                if (context.checkSelfPermission(MEDIA_PERMISSION_LEVEL)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.scanner_missing_storage_perm),
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                    }

                    requestPermissions(
                        context as Activity,
                        arrayOf(MEDIA_PERMISSION_LEVEL), PackageManager.PERMISSION_GRANTED
                    )

                    mediaPermission = false
                    return@Button
                } else if (context.checkSelfPermission(MEDIA_PERMISSION_LEVEL)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    mediaPermission = true
                }

                scannerFailure = false

                playerConnection?.player?.pause()

                coroutineScope.launch(lmScannerCoroutine) {
                    // full rescan
                    if (fullRescan) {
                        try {
                            val scanner = getScanner(context, scannerImpl, SCANNER_OWNER_LM)
                            val uris = scanner.scanLocal(scanPaths, excludedScanPaths)
                            scanner.fullSync(database, uris, scannerSensitivity, strictExtensions, strictFilePaths)

                            delay(1000)
                            // start artist linking job
                            if (lookupYtmArtists && scannerState <= 0) {
                                coroutineScope.launch(lmScannerCoroutine) {
                                    try {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.scanner_ytm_link_start),
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                        scanner.localToRemoteArtist(database)
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.scanner_ytm_link_success),
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                    } catch (e: ScannerAbortException) {
                                        snackbarHostState.showSnackbar(
                                            message = "${context.getString(R.string.scanner_ytm_link_success)}: ${e.message}",
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        } catch (e: ScannerAbortException) {
                            scannerFailure = true

                            snackbarHostState.showSnackbar(
                                message = "${context.getString(R.string.scanner_scan_fail)}: ${e.message}",
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            )
                        } finally {
                            destroyScanner(SCANNER_OWNER_LM)
                            clearDtCache()
                        }
                    } else {
                        // quick scan
                        try {
                            val scanner = getScanner(context, scannerImpl, SCANNER_OWNER_LM)
                            val uris = scanner.scanLocal(scanPaths, excludedScanPaths)
                            scanner.quickSync(database, uris, scannerSensitivity, strictExtensions,
                                strictFilePaths)

                            delay(1000)
                            // start artist linking job
                            if (lookupYtmArtists && scannerState <= 0) {
                                coroutineScope.launch(lmScannerCoroutine) {
                                    try {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.scanner_ytm_link_start),
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                        scanner.localToRemoteArtist(database)
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.scanner_ytm_link_success),
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                    } catch (e: ScannerAbortException) {
                                        snackbarHostState.showSnackbar(
                                            message = "${context.getString(R.string.scanner_ytm_link_fail)}: ${e.message}",
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        } catch (e: ScannerAbortException) {
                            scannerFailure = true

                            snackbarHostState.showSnackbar(
                                message = "${context.getString(R.string.scanner_scan_fail)}: ${e.message}",
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            )
                        } finally {
                            destroyScanner(SCANNER_OWNER_LM)
                            clearDtCache()
                        }
                    }

                    // post scan actions
                    playerConnection?.service?.initQueue()

                    onLastLocalScanChange(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli())
                }
            }
        ) {
            Text(
                text = if ((scannerState > 0 && scannerState < 4) || scannerState == 5) {
                    stringResource(R.string.action_cancel)
                } else if (scannerFailure) {
                    stringResource(R.string.scanner_scan_fail)
                } else if (scannerState >= 4) {
                    stringResource(R.string.scanner_progress_complete)
                } else if (!mediaPermission) {
                    stringResource(R.string.scanner_missing_storage_perm)
                } else {
                    stringResource(R.string.scanner_btn_idle)
                }
            )
        }


        // progress indicator
        if (scannerState <= 0) {
            return@Row
        }

        Spacer(Modifier.width(8.dp))

        CircularProgressIndicator(
            modifier = Modifier
                .size(32.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.width(8.dp))

        Column {
//            val isSyncing = scannerState > 3
            Text(
                text = when (scannerState) {
                    1 -> stringResource(R.string.scanner_progress_discovering)
                    3 -> stringResource(R.string.scanner_progress_syncing)
                    5 -> stringResource(R.string.scanner_ytm_link_start)
                    else -> stringResource(R.string.scanner_progress_processing)
                },
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp
            )
            Text(
                text = "${if (scannerProgressCurrent >= 0) "$scannerProgressCurrent" else "—"}/${
                    if (scannerProgressTotal >= 0) {
                        if (scannerState == 1) {
                            pluralStringResource(
                                R.plurals.scanner_n_song_found, scannerProgressTotal, scannerProgressTotal
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.scanner_n_song_processed, scannerProgressTotal, scannerProgressTotal
                            )
                        }
                    } else {
                        "—"
                    }
                }",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp
            )
        }
    }
    // scanner checkboxes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = fullRescan,
                onCheckedChange = { fullRescan = it }
            )
            Text(
                stringResource(R.string.scanner_variant_rescan), color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = lookupYtmArtists,
                onCheckedChange = onLookupYtmArtistsChange,
            )
            Text(
                stringResource(R.string.scanner_online_artist_linking), color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp
            )
        }
    }

    // file path selector
    PreferenceEntry(
        title = { Text(stringResource(R.string.scan_paths_title)) },
        onClick = {
            showAddFolderDialog = true
        },
    )

    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )

        Text(
            stringResource(R.string.scanner_warning),
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showAddFolderDialog != null) {
        var tempScanPaths = remember { mutableStateListOf<Uri>() }
        LaunchedEffect(showAddFolderDialog, scanPaths, excludedScanPaths) {
            tempScanPaths.clear()
            tempScanPaths.addAll(
                uriListFromString(if (showAddFolderDialog == true) scanPaths else excludedScanPaths)
            )
        }

        ActionPromptDialog(
            titleBar = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (showAddFolderDialog as Boolean) R.string.scan_paths_incl
                            else R.string.scan_paths_excl
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    // switch between include and exclude
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Switch(
                            checked = showAddFolderDialog!!,
                            onCheckedChange = {
                                showAddFolderDialog = !showAddFolderDialog!!
                            },
                        )
                    }
                }
            },
            onDismiss = {
                showAddFolderDialog = null
                tempScanPaths.clear()
            },
            onConfirm = {
                if (showAddFolderDialog as Boolean) {
                    onScanPathsChange(stringFromUriList(tempScanPaths.toList()))
                } else {
                    onExcludedScanPathsChange(stringFromUriList(tempScanPaths.toList()))
                }

                showAddFolderDialog = null
                tempScanPaths.clear()
            },
            onReset = {
                // clear all, let user select a new path on their own will
                tempScanPaths.clear()
            },
            onCancel = {
                showAddFolderDialog = null
                tempScanPaths.clear()
            },
            isInputValid = tempScanPaths.toList().all {
                // scan path cannot be the download directory or subdir of download directory
                !it.toString().contains(uriListFromString(downloadPath).firstOrNull().toString())
                        && uriListFromString(dlPathExtra).none { f -> it.toString().contains(f.toString()) }
            } || tempScanPaths.isEmpty()
        ) {
            val dirPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                if (tempScanPaths.any { it.toString() == uri.toString() }) return@rememberLauncherForActivityResult

                val contentResolver = context.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                tempScanPaths.add(uri)
            }

            Text(
                text = stringResource(R.string.scan_paths_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.padding(vertical = 8.dp))

            // folders list
            Column(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(ThumbnailCornerRadius)
                    )
            ) {
                tempScanPaths.forEach {
                    !it.toString().contains(uriListFromString(downloadPath).firstOrNull().toString())
                            && uriListFromString(dlPathExtra).none { f -> it.toString().contains(f.toString()) }
                    val valid = !it.toString().contains(uriListFromString(downloadPath).firstOrNull().toString())
                            && uriListFromString(dlPathExtra).none { f -> it.toString().contains(f.toString()) }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .background(if (valid) Color.Transparent else MaterialTheme.colorScheme.errorContainer)
                            .clickable { }) {
                        Text(
                            text = absoluteFilePathFromUri(context, it) ?: it.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        )
                        IconButton(
                            onClick = {
                                tempScanPaths.remove(it)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }

            // add folder button
            Column {
                Button(onClick = { dirPickerLauncher.launch(null) }) {
                    Text(stringResource(R.string.scan_paths_add_folder))
                }

                InfoLabel(
                    text = stringResource(R.string.scan_paths_tooltip),
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (tempScanPaths.toList().any {
                        it.toString() == uriListFromString(downloadPath).firstOrNull().toString()
                    }) {
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
    val (strictFilePaths, onStrictFilePathsChange) = rememberPreference(ScannerStrictFilePathsKey, defaultValue = false)


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
        },
        isEnabled = !strictFilePaths,
    )
    // strict file ext
    SwitchPreference(
        title = { Text(stringResource(R.string.scanner_strict_file_name_title)) },
        description = stringResource(R.string.scanner_strict_file_name_description),
        icon = { Icon(Icons.Rounded.TextFields, null) },
        isEnabled = !strictFilePaths,
        checked = strictExtensions,
        onCheckedChange = onStrictExtensionsChange
    )
    // compare file path only
    SwitchPreference(
        title = { Text(stringResource(R.string.scanner_strict_file_paths_title)) },
        description = stringResource(R.string.scanner_strict_file_paths_description),
        icon = { Icon(Icons.Rounded.MoreHoriz, null) },
        isEnabled = !strictFilePaths,
        checked = strictFilePaths,
        onCheckedChange = onStrictFilePathsChange,
    )
    // scanner type
    if (ENABLE_FFMETADATAEX) {
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_type_title)) },
            icon = { Icon(Icons.Rounded.Speed, null) },
            selectedValue = scannerImpl,
            onValueSelected = onScannerImplChange,
            valueText = {
                when (it) {
                    ScannerImpl.TAGLIB -> stringResource(R.string.scanner_type_taglib)
                    ScannerImpl.FFMPEG_EXT -> stringResource(R.string.scanner_type_ffmpeg_ext)
                }
            },
            values = ScannerImpl.entries,
        )
    }
}

