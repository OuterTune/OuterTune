package com.dd3boh.outertune.utils

/**
 * Holds the last playback attempt's diagnostic state so the UI can display it
 * when ExoPlayer reports an error (including mid-stream 403s).
 */
object YTPlayerDebugInfo {
    @Volatile var videoId: String = ""
    @Volatile var clientUsed: String = "—"
    @Volatile var potStatus: String = "—"
    @Volatile var urlPrefix: String = "—"
    @Volatile var validateLog: String = ""
    @Volatile var newPipeOk: Boolean? = null

    fun reset(vid: String) {
        videoId = vid
        clientUsed = "—"
        potStatus = "—"
        urlPrefix = "—"
        validateLog = ""
        newPipeOk = null
    }

    fun format(): String = buildString {
        appendLine("=== Playback Debug ===")
        appendLine("videoId : $videoId")
        appendLine("client  : $clientUsed")
        appendLine("poToken : $potStatus")
        appendLine("NewPipe : ${newPipeOk?.let { if (it) "OK" else "FAILED" } ?: "—"}")
        appendLine("URL[50] : $urlPrefix")
        if (validateLog.isNotEmpty()) appendLine("validate: $validateLog")
    }
}
