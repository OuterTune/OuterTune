package com.dd3boh.outertune.ui.screens.settings.fragments

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DownloadExtraPathKey
import com.dd3boh.outertune.constants.DownloadPathKey
import com.dd3boh.outertune.constants.MaxImageCacheSizeKey
import com.dd3boh.outertune.constants.MaxSongCacheSizeKey
import com.dd3boh.outertune.constants.ScanPathsKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.extensions.tryOrNull
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.SettingsClickToReveal
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.component.button.ResizableIconButton
import com.dd3boh.outertune.ui.dialog.ActionPromptDialog
import com.dd3boh.outertune.ui.dialog.DefaultDialog
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.utils.dlCoroutine
import com.dd3boh.outertune.utils.formatFileSize
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.scanners.absoluteFilePathFromUri
import com.dd3boh.outertune.utils.scanners.stringFromUriList
import com.dd3boh.outertune.utils.scanners.uriListFromString
import com.dd3boh.outertune.viewmodels.BackupRestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ColumnScope.BackupAndRestoreFrag(viewModel: BackupRestoreViewModel) {
    val context = LocalContext.current

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(uri)
            }
        }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.restore(uri)
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.action_backup)) },
            icon = { Icon(Icons.Rounded.Backup, null) },
            onClick = {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                backupLauncher.launch(
                    "${context.getString(R.string.app_name)}_${MusicDatabase.MUSIC_DATABASE_VERSION}_${
                        LocalDateTime.now().format(formatter)
                    }.backup"
                )
            }
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.action_restore)) },
            icon = { Icon(Icons.Rounded.Restore, null) },
            onClick = {
                restoreLauncher.launch(arrayOf("application/octet-stream"))
            }
        )
    }
}

@Composable
fun ColumnScope.DownloadsFrag() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return
    val downloadUtil = LocalDownloadUtil.current

    val (downloadPath, onDownloadPathChange) = rememberPreference(DownloadPathKey, "")
    val (scanPaths, onScanPathsChange) = rememberPreference(ScanPathsKey, defaultValue = "")

    // size stats
    var downloadCacheSize by remember {
        mutableLongStateOf(tryOrNull { downloadCache.cacheSpace } ?: 0)
    }
    var downloadMainPathSize by remember {
        mutableLongStateOf(-2L)
    }
    var downloadExtraPathSize by remember {
        mutableLongStateOf(-2L)
    }

    // downloads dialogs
    var showDlPathDialog: Boolean by remember {
        mutableStateOf(false)
    }
    var showClearConfirmDialog by remember {
        mutableStateOf(false)
    }
    var showDlInfoDialog by remember {
        mutableStateOf(false)
    }

    // advanced
    val (dlPathExtra, onDlPathExtraChange) = rememberPreference(DownloadExtraPathKey, "")
    val isLoading by downloadUtil.isProcessingDownloads.collectAsState()
    var showMigrationDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showImportDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showPathsDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(downloadCache) {
        while (isActive) {
            delay(2000)
            downloadCacheSize = tryOrNull { downloadCache.cacheSpace } ?: 0
        }
    }

    PreferenceEntry(
        title = { Text(stringResource(R.string.dl_main_path_title)) },
        description = if (downloadPath != "") uriListFromString(downloadPath).firstOrNull()
            ?.let { absoluteFilePathFromUri(context, it) } ?: downloadPath else null,
        onClick = {
            showDlPathDialog = true
        },
    )

    Text(
        text = stringResource(R.string.dl_size_used_cache, formatFileSize(downloadCacheSize)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )

    if (downloadMainPathSize == -2L && downloadExtraPathSize == -2L) {
        PreferenceEntry(
            title = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dl_calculate_size))
                    ResizableIconButton(
                        icon = Icons.Outlined.Info,
                        onClick = { showDlInfoDialog = true },
                    )
                }
            },
            onClick = {
                downloadMainPathSize = -1
                downloadCacheSize = -1
                coroutineScope.launch(Dispatchers.IO) {
                    downloadMainPathSize = downloadUtil.localMgr.getMainDlStorageUsage()
                    downloadExtraPathSize = downloadUtil.localMgr.getExtraDlStorageUsage()
                }
            },
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.dl_size_used_main,
                    formatFileSize(downloadMainPathSize.coerceIn(0, Long.MAX_VALUE))
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (downloadMainPathSize < 0L) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.dl_size_used_extra,
                    formatFileSize(downloadExtraPathSize.coerceIn(0, Long.MAX_VALUE))
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (downloadExtraPathSize < 0L) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }

    PreferenceEntry(
        title = { Text(stringResource(R.string.clear_all_downloads)) },
        onClick = {
            showClearConfirmDialog = true
        },
    )

    SettingsClickToReveal(stringResource(R.string.advanced)) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.dl_extra_path_title)) },
            description = stringResource(R.string.dl_extra_path_description),
            icon = { Icon(Icons.Rounded.FolderCopy, null) },
            onClick = {
                showPathsDialog = true
            },
            isEnabled = !isLoading
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.dl_rescan_title)) },
            description = stringResource(R.string.dl_rescan_description),
            icon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    Icon(Icons.Rounded.Sync, null)
                }
            },
            onClick = {
                showImportDialog = true
            },
            isEnabled = !isLoading && !(downloadPath.isEmpty() && dlPathExtra.isEmpty())
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.dl_migrate_title)) },
            description = stringResource(R.string.dl_migrate_description),
            icon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    Icon(Icons.Rounded.Downloading, null)
                }
            },
            onClick = {
                showMigrationDialog = true
            },
            isEnabled = !isLoading && !downloadPath.isEmpty()
        )
    }


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */
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
                val uris = stringFromUriList(listOfNotNull(tempFilePath))
                onDownloadPathChange(uris)

                showDlPathDialog = false
                tempFilePath = null

                coroutineScope.launch {
                    delay(1000)
                    downloadUtil.cd()
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
            }
        ) {

            val dirPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (tempFilePath.toString() == uri.toString()) return@rememberLauncherForActivityResult
                if (uri?.path != null) {
                    // Take persistable URI permission
                    val contentResolver = context.contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
                        text = absoluteFilePathFromUri(context, it) ?: it.toString(),
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

    if (showClearConfirmDialog) {
        DefaultDialog(
            onDismiss = { showClearConfirmDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_downloads_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearConfirmDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            // clear internal downloads
                            downloadCache.keys.forEach { key ->
                                downloadCache.removeResource(key)
                            }

                            // TODO: Delete external downloads. Rememebr to exclude extra paths
                            // clear external downloads
//                            database.downloadSongs(SongSortType.NAME, true).collect { songs ->
//                                songs.forEach { song ->
//                                    downloadUtil.delete(song)
//                                }
//                            }

                            downloadMainPathSize = downloadUtil.localMgr.getMainDlStorageUsage()
                            downloadExtraPathSize = downloadUtil.localMgr.getExtraDlStorageUsage()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showDlInfoDialog) {
        DefaultDialog(
            onDismiss = { showDlInfoDialog = false },
            content = {
                Column(
                    modifier = Modifier
                        .weight(1f, false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.dl_storage_tooltip),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDlInfoDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showPathsDialog) {
        var tempScanPaths = remember { mutableStateListOf<Uri>() }
        LaunchedEffect(dlPathExtra) {
            tempScanPaths.addAll(uriListFromString(dlPathExtra))
        }

        ActionPromptDialog(
            titleBar = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.scan_paths_incl),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            },
            onDismiss = {
                showPathsDialog = false
                tempScanPaths.clear()
            },
            onConfirm = {
                onDlPathExtraChange(stringFromUriList(tempScanPaths.toList()))
                coroutineScope.launch(dlCoroutine) {
                    delay(1000)
                    downloadUtil.cd()
                    downloadUtil.scanDownloads()
                }

                showPathsDialog = false
                tempScanPaths.clear()
            },
            onReset = {
                // reset to whitespace so not empty
                tempScanPaths.clear()
            },
            onCancel = {
                showPathsDialog = false
                tempScanPaths.clear()
            },
            isInputValid = uriListFromString(scanPaths).toList().none { scanPath ->
                // scan path cannot be contain any dl extras path
                tempScanPaths.toList().any { it.toString().contains(scanPath.toString()) }
            }
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
                tempScanPaths.forEach { tmpPath ->
                    val valid = uriListFromString(scanPaths).toList().none {
                        tmpPath.toString().contains(it.toString())
                    }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .background(if (valid) Color.Transparent else MaterialTheme.colorScheme.errorContainer)
                            .clickable { }) {
                        Text(
                            text = absoluteFilePathFromUri(context, tmpPath) ?: tmpPath.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        )
                        IconButton(
                            onClick = {
                                tempScanPaths.remove(tmpPath)
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

                if (uriListFromString(scanPaths).toList().any { scanPath ->
                        // scan path cannot be contain any dl extras path
                        tempScanPaths.toList().any { it.toString().contains(scanPath.toString()) }
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

    if (showImportDialog) {
        DefaultDialog(
            onDismiss = { showImportDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.dl_rescan_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showImportDialog = false
                        coroutineScope.launch(dlCoroutine) {
                            downloadUtil.scanDownloads()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showMigrationDialog) {
        DefaultDialog(
            onDismiss = { showMigrationDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.dl_migrate_confirm,
                        absoluteFilePathFromUri(context, downloadPath.toUri()) ?: downloadPath
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showMigrationDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showMigrationDialog = false

                        coroutineScope.launch(dlCoroutine) {
                            downloadUtil.migrateDownloads()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

}

@Composable
fun ColumnScope.SongCacheFrag() {
    val coroutineScope = rememberCoroutineScope()
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return

    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(key = MaxSongCacheSizeKey, defaultValue = 0)

    var playerCacheSize by remember {
        mutableLongStateOf(tryOrNull { playerCache.cacheSpace } ?: 0)
    }

    LaunchedEffect(playerCache) {
        while (isActive) {
            delay(2000)
            playerCacheSize = tryOrNull { playerCache.cacheSpace } ?: 0
        }
    }

    var showClearConfirmDialog by remember {
        mutableStateOf(false)
    }

    Spacer(modifier = Modifier.height(16.dp))
    if (maxSongCacheSize != 0) {
        if (maxSongCacheSize == -1) {
            Text(
                text = stringResource(R.string.size_used, formatFileSize(playerCacheSize)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        } else {
            LinearProgressIndicator(
                progress = { (playerCacheSize.toFloat() / (maxSongCacheSize * 1024 * 1024L)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Text(
                text = stringResource(
                    R.string.size_used,
                    "${formatFileSize(playerCacheSize)} / ${formatFileSize(maxSongCacheSize * 1024 * 1024L)}"
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }

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

    PreferenceEntry(
        title = { Text(stringResource(R.string.clear_song_cache)) },
        onClick = {
            showClearConfirmDialog = true
        },
    )


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */
    if (showClearConfirmDialog) {
        DefaultDialog(
            onDismiss = { showClearConfirmDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_song_cache_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearConfirmDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            playerCache.keys.forEach { key ->
                                playerCache.removeResource(key)
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun ColumnScope.ImageCacheFrag() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageDiskCache = context.imageLoader.diskCache ?: return

    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(
        key = MaxImageCacheSizeKey,
        defaultValue = 512
    )

    var imageCacheSize by remember {
        mutableLongStateOf(imageDiskCache.size)
    }

    LaunchedEffect(imageDiskCache) {
        while (isActive) {
            delay(500)
            imageCacheSize = imageDiskCache.size
        }
    }

    // clear caches when turning off
    LaunchedEffect(maxImageCacheSize) {
        if (maxImageCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                imageDiskCache.clear()
            }
        }
    }

    var showClearConfirmDialog by remember {
        mutableStateOf(false)
    }

    if (maxImageCacheSize > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { (imageCacheSize.toFloat() / imageDiskCache.maxSize).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Text(
            text = stringResource(
                R.string.size_used,
                "${formatFileSize(imageCacheSize)} / ${formatFileSize(imageDiskCache.maxSize)}"
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }

    ListPreference(
        title = { Text(stringResource(R.string.max_cache_size)) },
        selectedValue = maxImageCacheSize,
        values = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192),
        valueText = {
            when (it) {
                0 -> stringResource(androidx.compose.ui.R.string.state_off)
                else -> formatFileSize(it * 1024 * 1024L)
            }
        },
        onValueSelected = onMaxImageCacheSizeChange
    )
    InfoLabel(stringResource(R.string.restart_to_apply_changes))

    PreferenceEntry(
        title = { Text(stringResource(R.string.clear_image_cache)) },
        onClick = {
            showClearConfirmDialog = true
        },
    )


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */
    if (showClearConfirmDialog) {
        DefaultDialog(
            onDismiss = { showClearConfirmDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_image_cache_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearConfirmDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            imageDiskCache.clear()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }
}