package com.dd3boh.outertune.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal internal logger that writes to app-private storage with simple rotation.
 * - Logs to filesDir/logs/ot-log-YYYYMMDD.txt (daily files)
 * - Keeps up to [maxDays] days
 * - Installs a global uncaught exception handler to capture crashes
 */
object InternalLog {
    private const val TAG = "InternalLog"
    private const val LOG_DIR_NAME = "logs"
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private lateinit var logDir: File
    private val initialized = AtomicBoolean(false)

    fun init(context: Context, maxDays: Int = 7) {
        if (initialized.compareAndSet(false, true)) {
            logDir = File(context.filesDir, LOG_DIR_NAME).apply { mkdirs() }
            pruneOldLogs(maxDays)
            installGlobalHandler()
            i(TAG, "InternalLog initialized; dir=${logDir.absolutePath}")
        }
    }

    private fun installGlobalHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                e(TAG, "Uncaught exception in thread ${t.name}")
                logException(TAG, e)
            } catch (ignored: Throwable) {
                // swallow
            } finally {
                prev?.uncaughtException(t, e)
            }
        }
    }

    private fun pruneOldLogs(maxDays: Int) {
        val files = logDir.listFiles()?.toList().orEmpty()
        if (files.isEmpty()) return
        val cutoff = System.currentTimeMillis() - maxDays * 24L * 60 * 60 * 1000
        files.sortedBy { it.lastModified() }
            .filter { it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    private fun logFileForToday(): File {
        val name = "ot-log-${dayFmt.format(Date())}.txt"
        return File(logDir, name)
    }

    private fun appendLine(level: String, tag: String, msg: String) {
        val line = "${tsFmt.format(Date())} [$level/$tag] $msg\n"
        try {
            FileWriter(logFileForToday(), true).use { it.write(line) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log file: ${e.message}")
        }
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        if (initialized.get()) appendLine("I", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        if (initialized.get()) appendLine("W", tag, msg)
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        if (initialized.get()) appendLine("E", tag, msg)
    }

    fun logException(tag: String, tr: Throwable) {
        Log.e(tag, tr.message, tr)
        if (!initialized.get()) return
        val sw = StringWriter()
        tr.printStackTrace(PrintWriter(sw))
        appendLine("E", tag, sw.toString())
    }
}
