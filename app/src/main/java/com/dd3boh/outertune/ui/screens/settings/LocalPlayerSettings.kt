package com.dd3boh.outertune.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AutomaticScannerKey
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
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.InfoLabel
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.utils.DEFAULT_SCAN_PATH
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.ui.utils.cacheDirectoryTree
import com.dd3boh.outertune.utils.isPackageInstalled
import com.dd3boh.outertune.utils.purgeCache
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


val MEDIA_PERMISSION_LEVEL =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

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
    val (scannerSensitivity, onScannerSensitivityChange) = rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerMatchCriteria.LEVEL_2
    )
    val (scannerImpl, onScannerImplChange) = rememberEnumPreference(
        key = ScannerImplKey,
        defaultValue = ScannerImpl.TAGLIB
    )
    val (strictExtensions, onStrictExtensionsChange) = rememberPreference(ScannerStrictExtKey, defaultValue = false)
    val (autoScan, onAutoScanChange) = rememberPreference(AutomaticScannerKey, defaultValue = false)
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

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        // automatic scanner
        SwitchPreference(
            title = { Text(stringResource(R.string.auto_scanner_title)) },
            description = stringResource(R.string.auto_scanner_description),
            icon = { Icon(Icons.Rounded.Autorenew, null) },
            checked = autoScan,
            onCheckedChange = onAutoScanChange
        )
        // file path selector
        PreferenceEntry(
            title = { Text(stringResource(R.string.scan_paths_title)) },
            onClick = {
                showAddFolderDialog = true
            },
        )

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
                    if (uri?.path != null && !tempScanPaths.contains(uri.path!!)) {
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
                            Row(modifier = Modifier
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
                                    onClick = { tempScanPaths = tempScanPaths.replace("$it\n", "") },
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

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_manual_scanner)
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

                        purgeCache()
                        cacheDirectoryTree(null)

                        onLastLocalScanChange(LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond())
                        scannerFinished.value = true
                    }
                }
            ) {
                Text(
                    text = if (isScannerActive || showLoading) {
                        "Cancel"
                    } else if (scannerFailure) {
                        "An Error Occurred"
                    } else if (isScanFinished) {
                        "Scan complete"
                    } else if (!mediaPermission) {
                        "No Permission"
                    } else {
                        "Scan"
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

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_extra_scanner_settings)
        )
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
        scrollBehavior = scrollBehavior
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