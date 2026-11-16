/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.utils.updater.AppRelease
import io.github.yuuichi_s.astertune.utils.updater.AppUpdater
import io.github.yuuichi_s.astertune.utils.updater.InstallResult
import io.github.yuuichi_s.astertune.utils.updater.UpdateChecker
import io.github.yuuichi_s.astertune.utils.updater.UpdateVerificationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data object NeedPermission : UpdateInstallState
    data class Downloading(val progress: Float?) : UpdateInstallState
    data object Installing : UpdateInstallState
}

/** The outcome of a run, shown once. [detail] fills the string's argument when it has one. */
data class UpdateInstallMessage(@StringRes val message: Int, val detail: String? = null) {
    fun text(context: Context): String = context.getString(message, detail.orEmpty()).trim()
}

/**
 * Downloads and installs a release. Work runs in [scope], so it stops when the screen that
 * created this leaves the composition.
 */
class UpdateInstaller(private val context: Context, private val scope: CoroutineScope) {
    var state by mutableStateOf<UpdateInstallState>(UpdateInstallState.Idle)
        private set

    private var job: Job? = null

    private val _messages = Channel<UpdateInstallMessage>(Channel.BUFFERED)

    /** Outcomes of runs, each delivered once. */
    val messages: Flow<UpdateInstallMessage> = _messages.receiveAsFlow()

    private fun finish(message: UpdateInstallMessage) {
        state = UpdateInstallState.Idle
        _messages.trySend(message)
    }

    /**
     * Starts the download and install. [resolve] supplies the release to install, and returns
     * null if there is none.
     *
     * Does nothing while a run started here or from another screen, including its cleanup,
     * has not finished, as runs share the download folder.
     */
    fun start(resolve: suspend () -> AppRelease?) {
        if (isRunning) return

        if (!AppUpdater.canInstallPackages(context)) {
            state = UpdateInstallState.NeedPermission
            return
        }

        job = scope.launch {
            try {
                run(resolve)
            } catch (e: CancellationException) {
                state = UpdateInstallState.Idle
                throw e
            }
        }.also { runningJob = it }
    }

    private suspend fun run(resolve: suspend () -> AppRelease?) {
        state = UpdateInstallState.Downloading(null)
        val release = try {
            resolve()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            null
        }
        if (release == null) {
            finish(UpdateInstallMessage(R.string.update_check_failed))
            return
        }

        val apk = try {
            AppUpdater.download(context, release) { state = UpdateInstallState.Downloading(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateVerificationException) {
            Log.w(TAG, "Update verification failed", e)
            finish(UpdateInstallMessage(R.string.update_verify_failed))
            return
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed", e)
            finish(UpdateInstallMessage(R.string.update_download_failed))
            return
        }

        state = UpdateInstallState.Installing
        val result = try {
            coroutineScope {
                val result = async(start = CoroutineStart.UNDISPATCHED) { AppUpdater.installResults.first() }
                AppUpdater.install(context, apk)
                result.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update install failed", e)
            InstallResult.Failure(e.message)
        }

        finish(
            when (result) {
                InstallResult.Success -> UpdateInstallMessage(R.string.update_installed)
                InstallResult.Aborted -> UpdateInstallMessage(R.string.update_install_aborted)
                is InstallResult.Failure -> UpdateInstallMessage(R.string.update_install_failed, result.message)
            }
        )
    }

    /**
     * Stops a download. The state returns to idle once the download has stopped and its file
     * is removed. An install already handed to Android cannot be withdrawn, so this does
     * nothing after the download.
     */
    fun cancel() {
        if (state !is UpdateInstallState.Downloading) return
        job?.cancel()
    }

    fun openInstallPermissionSettings() {
        state = UpdateInstallState.Idle
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Written before leaving, as the app may be ended while the setting is open.
        runBlocking { UpdateChecker.markInstallPermissionRequested(context) }
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open install permission settings", e)
        }
    }

    fun dismissPermission() {
        state = UpdateInstallState.Idle
    }

    companion object {
        private const val TAG = "UpdateInstaller"

        /** The latest run in this process, whichever screen started it. */
        @Volatile
        private var runningJob: Job? = null

        /** Whether a download or install is in progress, including its cleanup. */
        val isRunning: Boolean
            get() = runningJob?.isCompleted == false
    }
}

@Composable
fun rememberUpdateInstaller(): UpdateInstaller {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateInstaller(context.applicationContext, scope) }
}

/**
 * Progress of [installer] while downloading or installing, or nothing otherwise. Also shows the
 * install permission dialog when it is needed.
 */
@Composable
fun UpdateInstallStatus(installer: UpdateInstaller, modifier: Modifier = Modifier) {
    when (val state = installer.state) {
        UpdateInstallState.Idle -> Unit
        UpdateInstallState.NeedPermission -> InstallPermissionDialog(installer)

        is UpdateInstallState.Downloading -> Column(modifier) {
            val percent = state.progress?.let { (it * 100).toInt() }
            Text(
                text = if (percent != null) {
                    stringResource(R.string.update_downloading_percent, percent)
                } else {
                    stringResource(R.string.update_downloading)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(8.dp))
            if (state.progress != null) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        UpdateInstallState.Installing -> Column(modifier) {
            Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun InstallPermissionDialog(installer: UpdateInstaller) {
    AlertDialog(
        onDismissRequest = installer::dismissPermission,
        title = { Text(stringResource(R.string.update_install_permission_title)) },
        text = { Text(stringResource(R.string.update_install_permission_message)) },
        confirmButton = {
            TextButton(onClick = installer::openInstallPermissionSettings) {
                Text(stringResource(R.string.update_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = installer::dismissPermission) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

enum class UpdateAnnouncement { INSTALL, CLOSED, TIMED_OUT }

private const val ANNOUNCEMENT_DURATION = 10_000L

/**
 * Announces [release] with a snackbar that has an install action and a close button.
 *
 * The snackbar is dismissed after [ANNOUNCEMENT_DURATION] once shown, which is reported as
 * [UpdateAnnouncement.TIMED_OUT] so it can be told apart from the close button.
 */
suspend fun announceUpdate(
    context: Context,
    snackbarHostState: SnackbarHostState,
    release: AppRelease,
): UpdateAnnouncement = coroutineScope {
    val message = context.getString(R.string.update_available_version, release.version)
    var timedOut = false
    val timer = launch {
        // Snackbars are queued, so the time counts from when this one is shown.
        val shown = snapshotFlow { snackbarHostState.currentSnackbarData }
            .filterNotNull()
            .first { it.visuals.message == message }
        delay(ANNOUNCEMENT_DURATION)
        timedOut = true
        shown.dismiss()
    }
    val result = snackbarHostState.showSnackbar(
        message = message,
        actionLabel = context.getString(R.string.update_install),
        withDismissAction = true,
        duration = SnackbarDuration.Indefinite,
    )
    timer.cancel()
    when {
        result == SnackbarResult.ActionPerformed -> UpdateAnnouncement.INSTALL
        timedOut -> UpdateAnnouncement.TIMED_OUT
        else -> UpdateAnnouncement.CLOSED
    }
}
