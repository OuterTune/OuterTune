/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Receives the status of a [PackageInstaller] session committed by [AppUpdater.install].
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "Install status $status: $message")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    AppUpdater.emitInstallResult(InstallResult.Failure(message))
                    return
                }
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open the install confirmation", e)
                    AppUpdater.emitInstallResult(InstallResult.Failure(e.message))
                }
            }

            PackageInstaller.STATUS_SUCCESS -> AppUpdater.emitInstallResult(InstallResult.Success)
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdater.emitInstallResult(InstallResult.Aborted)
            else -> AppUpdater.emitInstallResult(InstallResult.Failure(message))
        }
    }

    private companion object {
        const val TAG = "UpdateInstallReceiver"
    }
}
