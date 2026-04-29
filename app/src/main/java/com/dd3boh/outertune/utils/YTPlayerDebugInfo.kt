package com.dd3boh.outertune.utils

/**
 * Holds the last playback attempt's diagnostic state so the UI can display it
 * when ExoPlayer reports an error (including mid-stream 403s).
 */
object YTPlayerDebugInfo {
    @Volatile var videoId: String = ""
    @Volatile var winnerClient: String = "—"
    @Volatile var potStatus: String = "—"
    @Volatile var urlPrefix: String = "—"
    @Volatile var newPipeOk: Boolean? = null
    @Volatile var newPipeError: String? = null
    private val attempts = mutableListOf<String>()

    fun reset(vid: String) {
        videoId = vid
        winnerClient = "—"
        potStatus = "—"
        urlPrefix = "—"
        newPipeOk = null
        newPipeError = null
        synchronized(attempts) { attempts.clear() }
    }

    fun logAttempt(client: String, result: String) {
        synchronized(attempts) { attempts.add("$client → $result") }
    }

    fun format(): String = buildString {
        appendLine("=== Playback Debug ===")
        appendLine("videoId : $videoId")
        appendLine("poToken : $potStatus")
        appendLine("NewPipe : ${newPipeOk?.let { if (it) "OK" else "FAILED" } ?: "—"}")
        newPipeError?.let { appendLine("NP err  : $it") }
        appendLine("winner  : $winnerClient")
        appendLine("URL[50] : $urlPrefix")
        appendLine("--- client attempts ---")
        synchronized(attempts) { attempts.forEach { appendLine(it) } }
    }
}
