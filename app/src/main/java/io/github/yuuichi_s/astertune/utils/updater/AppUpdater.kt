/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.zionhuang.innertube.YouTube
import io.github.yuuichi_s.astertune.BuildConfig
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * A published release that has an APK of the same flavor as the running build.
 */
data class AppRelease(
    val version: String,
    val apkName: String,
    val apkUrl: String,
    val size: Long,
    /** Lowercase hex SHA-256 published by GitHub for the asset, or null if GitHub did not publish one. */
    val sha256: String?,
)

sealed interface InstallResult {
    data object Success : InstallResult
    data object Aborted : InstallResult
    data class Failure(val message: String?) : InstallResult
}

class UpdateVerificationException : IOException("SHA-256 mismatch or missing digest")

object AppUpdater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/yuuichi-s/AsterTune/releases/latest"
    private const val UPDATE_DIR = "updates"

    private val _installResults = MutableSharedFlow<InstallResult>(extraBufferCapacity = 4)
    val installResults: SharedFlow<InstallResult> = _installResults.asSharedFlow()

    internal fun emitInstallResult(result: InstallResult) {
        _installResults.tryEmit(result)
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    /**
     * Runs the call and [block] on the current thread, and cancels the call when the coroutine
     * is cancelled so a blocked read returns. A failure caused by the cancellation is thrown as
     * a cancellation.
     */
    private suspend fun <T> Call.executeCancellable(block: (Response) -> T): T = coroutineScope {
        val call = this@executeCancellable
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use(block)
        } catch (e: Throwable) {
            ensureActive()
            throw e
        } finally {
            watcher.cancel()
        }
    }

    /**
     * Fetches the latest non-prerelease release from GitHub.
     *
     * Returns null when the release has no APK for the running flavor yet.
     */
    suspend fun fetchLatestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client().newCall(request).executeCancellable { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            (response.body ?: throw IOException("Empty body")).string()
        }
        parseRelease(body, BuildConfig.FLAVOR)
    }

    internal fun parseRelease(json: String, flavor: String): AppRelease? {
        val release = JSONObject(json)
        val version = release.getString("tag_name").removePrefix("v")
        val assets = release.optJSONArray("assets") ?: return null
        val candidates = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .filter {
                val name = it.getString("name")
                name.endsWith(".apk") && name.contains("-$flavor-")
            }
        val asset = candidates.firstOrNull { it.getString("name").contains("-universal-") }
            ?: candidates.firstOrNull()
            ?: return null
        val digest = asset.optString("digest", "")
        return AppRelease(
            version = version,
            apkName = asset.getString("name"),
            apkUrl = asset.getString("browser_download_url"),
            size = asset.optLong("size", -1L),
            sha256 = digest.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
        )
    }

    /**
     * Returns true when [remote] is a higher dotted version than [current].
     */
    fun isNewer(remote: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        val r = parseVersion(remote) ?: return false
        val c = parseVersion(current) ?: return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parseVersion(version: String): List<Int>? =
        version.removePrefix("v").substringBefore('-').split('.')
            .map { it.toIntOrNull() ?: return null }

    /**
     * Downloads the APK into the app cache and checks it against the published SHA-256.
     *
     * Older downloads are removed first. The partial file is removed on failure or cancellation,
     * before this returns.
     */
    suspend fun download(
        context: Context,
        release: AppRelease,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, release.apkName)
        try {
            val expected = release.sha256 ?: throw UpdateVerificationException()
            val digest = MessageDigest.getInstance("SHA-256")
            val job = currentCoroutineContext().job
            val call = client().newCall(Request.Builder().url(release.apkUrl).build())
            call.executeCancellable { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val total = body.contentLength().takeIf { it > 0 } ?: release.size
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var lastReported = -1
                        while (true) {
                            job.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress(read.toFloat() / total)
                                }
                            } else {
                                onProgress(null)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw UpdateVerificationException()
            file
        } catch (e: Throwable) {
            file.delete()
            currentCoroutineContext().ensureActive()
            throw e
        }
    }

    /**
     * Whether Android allows this app to install packages. Always true below Android 8.0,
     * where the setting is not per app.
     */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /**
     * Hands the APK to [PackageInstaller]. The result is delivered to [UpdateInstallReceiver]
     * and published on [installResults].
     *
     * If the coroutine is cancelled before the session is committed, the session is abandoned.
     * Once committed, Android carries on with the install and it cannot be withdrawn here.
     */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                currentCoroutineContext().ensureActive()
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Throwable) {
            installer.abandonSession(sessionId)
            throw e
        }
    }
}
