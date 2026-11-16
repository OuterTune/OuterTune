/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.updater

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import io.github.yuuichi_s.astertune.BuildConfig
import io.github.yuuichi_s.astertune.constants.AutoUpdateCheckKey
import io.github.yuuichi_s.astertune.constants.DismissedUpdateVersionKey
import io.github.yuuichi_s.astertune.constants.LastUpdateCheckTimeKey
import io.github.yuuichi_s.astertune.constants.LatestKnownVersionKey
import io.github.yuuichi_s.astertune.constants.UpdateInstallPermissionRequestedKey
import io.github.yuuichi_s.astertune.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val AUTO_CHECK_INTERVAL = 24L * 60 * 60 * 1000

    /** The release found by the last successful check in this process, if it is newer than this build. */
    @Volatile
    var newerRelease: AppRelease? = null
        private set

    /**
     * Asks GitHub for the latest release and records the result.
     *
     * Returns the release when it is newer than this build and its APK is available, otherwise null.
     * Throws when the request fails.
     */
    suspend fun check(context: Context): AppRelease? {
        val release = AppUpdater.fetchLatestRelease()
        val newer = release?.takeIf { AppUpdater.isNewer(it.version) }
        newerRelease = newer
        context.dataStore.edit {
            it[LatestKnownVersionKey] = newer?.version ?: BuildConfig.VERSION_NAME
            // A release without the APK yet is checked again on the next app start.
            if (release != null) it[LastUpdateCheckTimeKey] = System.currentTimeMillis()
        }
        return newer
    }

    /**
     * The check run on app start. Returns a release to announce, or null when there is nothing to
     * announce, the check is disabled or not due, or the request failed.
     *
     * Android 11 and earlier end the app when the install permission changes, so after the user
     * was sent to that setting, the next start checks again regardless of the interval and
     * of the version the user chose to skip.
     */
    suspend fun checkOnStart(context: Context): AppRelease? {
        val prefs = context.dataStore.data.first()
        val resumeInstall = prefs[UpdateInstallPermissionRequestedKey] == true
        if (resumeInstall) {
            context.dataStore.edit { it.remove(UpdateInstallPermissionRequestedKey) }
        } else {
            if (prefs[AutoUpdateCheckKey] == false) return null
            val lastCheck = prefs[LastUpdateCheckTimeKey] ?: 0L
            if (System.currentTimeMillis() - lastCheck in 0 until AUTO_CHECK_INTERVAL) return null
        }
        val release = try {
            check(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            return null
        }
        return release?.takeIf { resumeInstall || it.version != prefs[DismissedUpdateVersionKey] }
    }

    /** Records that the user was sent to the install permission setting. */
    suspend fun markInstallPermissionRequested(context: Context) {
        context.dataStore.edit { it[UpdateInstallPermissionRequestedKey] = true }
    }

    suspend fun dismiss(context: Context, release: AppRelease) {
        context.dataStore.edit { it[DismissedUpdateVersionKey] = release.version }
    }
}
