package io.github.yuuichi_s.astertune

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.zionhuang.innertube.YouTube
import io.github.yuuichi_s.astertune.constants.AUTO_SCAN_COOLDOWN
import io.github.yuuichi_s.astertune.constants.AUTO_SCAN_SOFT_COOLDOWN
import io.github.yuuichi_s.astertune.constants.AutomaticScannerKey
import io.github.yuuichi_s.astertune.constants.ExcludedScanPathsKey
import io.github.yuuichi_s.astertune.constants.LastLocalScanKey
import io.github.yuuichi_s.astertune.constants.LocalLibraryEnableKey
import io.github.yuuichi_s.astertune.constants.OOBE_VERSION
import io.github.yuuichi_s.astertune.constants.OobeStatusKey
import io.github.yuuichi_s.astertune.constants.SCANNER_OWNER_LM
import io.github.yuuichi_s.astertune.constants.ScanPathsKey
import io.github.yuuichi_s.astertune.constants.ScannerMatchCriteria
import io.github.yuuichi_s.astertune.constants.ScannerSensitivityKey
import io.github.yuuichi_s.astertune.constants.ScannerStrictExtKey
import io.github.yuuichi_s.astertune.constants.ScannerStrictFilePathsKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.DownloadUtil
import io.github.yuuichi_s.astertune.playback.PlayerConnection
import io.github.yuuichi_s.astertune.playback.queues.ListQueue
import io.github.yuuichi_s.astertune.ui.utils.MEDIA_PERMISSION_LEVEL
import io.github.yuuichi_s.astertune.ui.utils.clearDtCache
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.enumPreference
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.reportException
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner.Companion.destroyScanner
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner.Companion.scannerState
import io.github.yuuichi_s.astertune.utils.scanners.ScannerAbortException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset


/**
 * Directly navigate to a YouTube page given an YouTube url
 */
fun youtubeNavigator(
    context: Context,
    navController: NavController,
    coroutineScope: CoroutineScope,
    playerConnection: PlayerConnection?,
    snackbarHostState: SnackbarHostState,
    uri: Uri
): Boolean {
    when (val path = uri.pathSegments.firstOrNull()) {
        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
            if (playlistId.startsWith("OLAK5uy_")) {
                coroutineScope.launch {
                    YouTube.albumSongs(playlistId).onSuccess { songs ->
                        songs.firstOrNull()?.album?.id?.let { browseId ->
                            navController.navigate("album/$browseId")
                        }
                    }.onFailure {
                        reportException(it)
                    }
                }
            } else {
                navController.navigate("online_playlist/$playlistId")
            }
        }

        "channel", "c" -> uri.lastPathSegment?.let { artistId ->
            navController.navigate("artist/$artistId")
        }

        else -> when {
            path == "watch" -> uri.getQueryParameter("v")
            uri.host == "youtu.be" -> path
            else -> return false
        }?.let { videoId ->
            val playlistId = uri.getQueryParameter("list")
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    YouTube.queue(listOf(videoId), playlistId)
                }.onSuccess {
                    val s = it.firstOrNull()
                    if (s == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.err_invalid_ytm_song),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long
                            )
                        }
                    } else {
                        playerConnection?.playQueue(
                            queue = ListQueue(
                                title = s.title,
                                items = listOf(s.toMediaMetadata())
                            )
                        )
                    }
                }.onFailure {
                    reportException(it)
                }
            }
        }
    }

    return true
}

suspend fun scanInit(
    context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    coroutineScope: CoroutineScope,
    playerConnection: PlayerConnection?,
    snackbarHostState: SnackbarHostState
) {
    val MAIN_TAG = "MainOtActivity"
    val oobeStatus = context.dataStore.get(OobeStatusKey, defaultValue = 0)
    val localLibEnable = context.dataStore.get(LocalLibraryEnableKey, defaultValue = true)
    val ds = context.dataStore.data.first()[ScannerSensitivityKey]
//        .map { it[SkipSilenceKey] ?: false }
    // auto scanner
    val scannerSensitivity by enumPreference(
        context = context,
        key = ScannerSensitivityKey,
        defaultValue = ScannerMatchCriteria.LEVEL_2
    )
    val scanPaths = context.dataStore.get(ScanPathsKey, defaultValue = "")
    val excludedScanPaths = context.dataStore.get(ExcludedScanPathsKey, defaultValue = "")
    val strictExtensions = context.dataStore.get(ScannerStrictExtKey, defaultValue = false)
    val strictFilePaths = context.dataStore.get(ScannerStrictFilePathsKey, defaultValue = false)
    val autoScan = context.dataStore.get(AutomaticScannerKey, defaultValue = true)
    val lastLocalScan = context.dataStore.get(LastLocalScanKey, 0L)

    if (!autoScan || oobeStatus < OOBE_VERSION) {
        Log.i(MAIN_TAG, "Automatic scan is disabled, and/or user has not passed OOBE")
        return
    }
    val timeNow = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
    if (lastLocalScan + AUTO_SCAN_COOLDOWN > timeNow) {
        Log.i(MAIN_TAG, "Aborting automatic scan. Not enough time has passed since the last scan")
        downloadUtil.resumeDownloadsOnStart()
        return
    }
    Log.i(MAIN_TAG, "Starting local media and downloads auto scan")
    context.dataStore.edit { settings ->
        settings[LastLocalScanKey] =
            timeNow - AUTO_SCAN_COOLDOWN + AUTO_SCAN_SOFT_COOLDOWN // min cooldown to avoid crash loops
    }
    coroutineScope.launch {
        snackbarHostState.showSnackbar(
            message = context.getString(R.string.scanner_auto_start),
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
    }


    // scan download folders
    downloadUtil.scanDownloads()
    downloadUtil.resumeDownloadsOnStart()
    if (!localLibEnable) {
        context.dataStore.edit { settings ->
            settings[LastLocalScanKey] = timeNow
        }
        playerConnection?.service?.initQueue()
        Log.i(MAIN_TAG, "Downloads scan completed. Local media is disabled.")
    }


    // local media scan
    var localScanFailed = false
    val perms = context.checkSelfPermission(MEDIA_PERMISSION_LEVEL)
    // Check if the permissions for local media access
    if (scannerState.value <= 0 && localLibEnable) {
        if (perms == PackageManager.PERMISSION_GRANTED) {
            // equivalent to (quick scan)
            try {
                withContext(Dispatchers.Main) {
                    playerConnection?.player?.pause()
                }
                val scanner = LocalMediaScanner.getScanner(
                    context, SCANNER_OWNER_LM
                )
                val uris = scanner.scanLocal(scanPaths, excludedScanPaths)
                scanner.quickSync(database, uris, scannerSensitivity, strictExtensions, strictFilePaths)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ScannerAbortException) {
                localScanFailed = true
                Log.i(MAIN_TAG, "Automatic scan canceled: ${e.message}")
            } catch (e: Exception) {
                localScanFailed = true
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.scanner_scan_fail),
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
                reportException(e)
            } finally {
                clearDtCache()
                destroyScanner(SCANNER_OWNER_LM)
            }

            // post scan actions
            context.dataStore.edit { settings ->
                settings[LastLocalScanKey] = timeNow
            }
            playerConnection?.service?.initQueue()
            if (!localScanFailed) {
                Log.i(MAIN_TAG, "Local media and downloads scan completed")
            }
        } else if (perms == PackageManager.PERMISSION_DENIED) {
            // Request the permission using the permission launcher
            (context as MainActivity).permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
            Log.w(MAIN_TAG, "Not enough permission to perform local media scan")
        }
    } else if (localLibEnable) {
        Log.w(MAIN_TAG, "Cannot perform local media scan, scanner is in use")
    }

    if (!localScanFailed) {
        Log.i(MAIN_TAG, "Local media and downloads auto scan complete")
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.scanner_auto_end),
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
        }
    }

}
