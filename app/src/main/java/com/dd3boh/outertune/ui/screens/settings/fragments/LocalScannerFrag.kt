/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens.settings.fragments

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ExcludedScanPathsKey
import com.dd3boh.outertune.constants.LastLocalScanKey
import com.dd3boh.outertune.constants.LookupYtmArtistsKey
import com.dd3boh.outertune.constants.ScanPathsKey
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerImplKey
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.constants.ScannerSensitivityKey
import com.dd3boh.outertune.constants.ScannerStrictExtKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.ui.component.ActionPromptDialog
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.InfoLabel
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.utils.DEFAULT_SCAN_PATH
import com.dd3boh.outertune.ui.utils.MEDIA_PERMISSION_LEVEL
import com.dd3boh.outertune.ui.utils.imageCache
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.destroyScanner
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.getScanner
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerActive
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerFinished
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerProgressCurrent
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerProgressTotal
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerRequestCancel
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner.Companion.scannerShowLoading
import com.dd3boh.outertune.utils.scanners.ScannerAbortException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.LocalScannerFrag() {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current

    // scanner vars
    val isScannerActive by scannerActive.collectAsState()
    val showLoading by scannerShowLoading.collectAsState()
    val isScanFinished by scannerActive.collectAsState()
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
    val (scanPaths, onScanPathsChange) = rememberPreference(ScanPathsKey, defaultValue = DEFAULT_SCAN_PATH)
    val (excludedScanPaths, onExcludedScanPathsChange) = rememberPreference(ExcludedScanPathsKey, defaultValue = "")

    var fullRescan by remember { mutableStateOf(false) }
    val (lookupYtmArtists, onlookupYtmArtistsChange) = rememberPreference(LookupYtmArtistsKey, defaultValue = true)

    // other vars
    var tempScanPaths by remember { mutableStateOf("") }
    val (lastLocalScan, onLastLocalScanChange) = rememberPreference(
        LastLocalScanKey,
        LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond()
    )


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
                if (isScannerActive) {
                    scannerRequestCancel = true
                }

                // check permission
                if (context.checkSelfPermission(MEDIA_PERMISSION_LEVEL)
                    != PackageManager.PERMISSION_GRANTED
                ) {

                    Toast.makeText(
                        context,
                        context.getString(R.string.scanner_missing_storage_perm),
                        Toast.LENGTH_SHORT
                    ).show()

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

                scannerFinished.value = false
                scannerFailure = false

                playerConnection?.player?.pause()

                coroutineScope.launch(Dispatchers.IO) {
                    // full rescan
                    if (fullRescan) {
                        try {
                            val scanner = getScanner(context, scannerImpl)
                            val directoryStructure =
                                scanner.scanLocal(
                                    database,
                                    scanPaths.split('\n'),
                                    excludedScanPaths.split('\n')
                                ).value

                            scanner.fullSync(
                                database, directoryStructure.toList(), scannerSensitivity,
                                strictExtensions
                            )

                            // start artist linking job
                            if (lookupYtmArtists && !isScannerActive) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    Looper.prepare()
                                    try {
                                        Toast.makeText(
                                            context, context.getString(R.string.scanner_ytm_link_start),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        scanner.localToRemoteArtist(database)
                                        Toast.makeText(
                                            context, context.getString(R.string.scanner_ytm_link_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: ScannerAbortException) {
                                        Looper.prepare()
                                        Toast.makeText(
                                            context,
                                            "${context.getString(R.string.scanner_ytm_link_success)}: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        } catch (e: ScannerAbortException) {
                            scannerFailure = true

                            Looper.prepare()
                            Toast.makeText(
                                context,
                                "${context.getString(R.string.scanner_scan_fail)}: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            destroyScanner()
                        }
                    } else {
                        // quick scan
                        try {
                            val scanner = getScanner(context, scannerImpl)
                            val directoryStructure = scanner.scanLocal(
                                database,
                                scanPaths.split('\n'),
                                excludedScanPaths.split('\n'),
                                pathsOnly = true
                            ).value
                            scanner.quickSync(
                                database, directoryStructure.toList(), scannerSensitivity,
                                strictExtensions
                            )

                            // start artist linking job
                            if (lookupYtmArtists && !isScannerActive) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    Looper.prepare()
                                    try {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.scanner_ytm_link_start),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        scanner.localToRemoteArtist(database)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.scanner_ytm_link_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: ScannerAbortException) {
                                        Toast.makeText(
                                            context,
                                            "${context.getString(R.string.scanner_ytm_link_fail)}: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        } catch (e: ScannerAbortException) {
                            scannerFailure = true

                            Looper.prepare()
                            Toast.makeText(
                                context,
                                "${context.getString(R.string.scanner_scan_fail)}: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            destroyScanner()
                        }
                    }

                    // post scan actions
                    imageCache.purgeCache()
                    playerConnection?.service?.initQueue()

                    onLastLocalScanChange(LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond())
                    scannerFinished.value = true
                }
            }
        ) {
            Text(
                text = if (isScannerActive || showLoading) {
                    stringResource(R.string.action_cancel)
                } else if (scannerFailure) {
                    stringResource(R.string.scanner_scan_fail)
                } else if (isScanFinished) {
                    stringResource(R.string.scanner_progress_complete)
                } else if (!mediaPermission) {
                    stringResource(R.string.scanner_missing_storage_perm)
                } else {
                    stringResource(R.string.scanner_btn_idle)
                }
            )
        }


        // progress indicator
        if (!showLoading) {
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

        if (scannerProgressTotal != -1) {
            Column {
                val isSyncing = scannerProgressCurrent > -1
                Text(
                    text = if (isSyncing) {
                        stringResource(R.string.scanner_progress_syncing)
                    } else {
                        stringResource(R.string.scanner_progress_scanning)
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "${if (isSyncing) scannerProgressCurrent else "—"}/${
                        pluralStringResource(
                            if (isSyncing) R.plurals.scanner_n_song_processed else R.plurals.scanner_n_song_found,
                            scannerProgressTotal,
                            scannerProgressTotal
                        )
                    }",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }
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
                onCheckedChange = onlookupYtmArtistsChange,
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
        if (tempScanPaths.isEmpty()) {
            tempScanPaths = if (showAddFolderDialog == true) scanPaths else excludedScanPaths
        }

        ActionPromptDialog(
            titleBar = {
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
                            tempScanPaths =
                                if (showAddFolderDialog == true) scanPaths else excludedScanPaths
                        },
                    )
                }
            },
            onDismiss = {
                showAddFolderDialog = null
                tempScanPaths = ""
            },
            onConfirm = {
                if (showAddFolderDialog as Boolean) {
                    onScanPathsChange(tempScanPaths)
                } else {
                    onExcludedScanPathsChange(tempScanPaths)
                }

                showAddFolderDialog = null
                tempScanPaths = ""
            },
            onReset = {
                // reset to whitespace so not empty
                tempScanPaths = if (showAddFolderDialog as Boolean) DEFAULT_SCAN_PATH else " "
            },
            onCancel = {
                showAddFolderDialog = null
                tempScanPaths = ""
            }
        ) {
            val dirPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri?.path != null && !("$tempScanPaths\u200B").contains(uri.path!! + "\u200B")) {
                    if (tempScanPaths.isBlank()) {
                        tempScanPaths = "${uri.path}\n"
                    } else {
                        tempScanPaths += "${uri.path}\n"
                    }
                }
            }

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
                tempScanPaths.split('\n').forEach {
                    if (it.isNotBlank())
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable { }) {
                            Text(
                                // I hate this but I'll do it properly... eventually
                                text = if (it.substringAfter("tree/")
                                        .substringBefore(':') == "primary"
                                ) {
                                    "Internal Storage/${it.substringAfter(':')}"
                                } else {
                                    "External (${
                                        it.substringAfter("tree/").substringBefore(':')
                                    })/${it.substringAfter(':')}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterVertically)
                            )
                            IconButton(
                                onClick = {
                                    tempScanPaths = if (tempScanPaths.substringAfter("\n").contains("\n")) {
                                        tempScanPaths.replace("$it\n", "")
                                    } else {
                                        " " // cursed bug
                                    }
                                },
                                onLongClick = {}
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { dirPickerLauncher.launch(null) }) {
                    Text(stringResource(R.string.scan_paths_add_folder))
                }

                InfoLabel(text = stringResource(R.string.scan_paths_tooltip))
            }
        }
    }
}