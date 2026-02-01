/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug log collector for AirPlay functionality.
 * Stores log entries in a circular buffer for in-app viewing.
 */
object AirPlayDebugLog {
    private const val MAX_ENTRIES = 500

    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        enum class Level { DEBUG, INFO, WARN, ERROR }

        fun formattedTime(): String {
            return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
        }

        fun formatted(): String {
            val levelStr = when (level) {
                Level.DEBUG -> "D"
                Level.INFO -> "I"
                Level.WARN -> "W"
                Level.ERROR -> "E"
            }
            return "${formattedTime()} $levelStr/$tag: $message"
        }
    }

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }

    fun debug(tag: String, message: String) {
        log(LogEntry.Level.DEBUG, tag, message)
    }

    fun info(tag: String, message: String) {
        log(LogEntry.Level.INFO, tag, message)
    }

    fun warn(tag: String, message: String) {
        log(LogEntry.Level.WARN, tag, message)
    }

    fun error(tag: String, message: String) {
        log(LogEntry.Level.ERROR, tag, message)
    }

    private fun log(level: LogEntry.Level, tag: String, message: String) {
        if (!_isEnabled.value) return

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )

        synchronized(this) {
            val current = _entries.value.toMutableList()
            current.add(entry)

            // Keep only the last MAX_ENTRIES
            if (current.size > MAX_ENTRIES) {
                _entries.value = current.takeLast(MAX_ENTRIES)
            } else {
                _entries.value = current
            }
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun getEntriesAsText(): String {
        return _entries.value.joinToString("\n") { it.formatted() }
    }
}
