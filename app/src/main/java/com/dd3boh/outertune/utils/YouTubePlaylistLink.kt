package com.dd3boh.outertune.utils

import android.net.Uri

/**
 * Extracts a YouTube / YouTube Music playlist id from a share URL, or returns a bare id if pasted.
 * Supports e.g. `watch?v=…&list=RD…`, `playlist?list=…`, `youtu.be/…?list=…`.
 */
/** `watch?v=…` or youtu.be/… query `v` */
fun extractYouTubeWatchVideoId(input: String): String? {
    val s = input.trim()
    if (s.isEmpty()) return null
    Regex("""[?&]v=([^&]+)""").find(s)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val normalized = if (!s.contains("://")) "https://$s" else s
    runCatching { Uri.parse(normalized) }.getOrNull()?.getQueryParameter("v")?.trim()?.let {
        if (it.isNotEmpty()) return it
    }
    val youtuBe = Regex("""youtu\.be/([^?&#]+)""").find(s)?.groupValues?.getOrNull(1)?.trim()
    if (!youtuBe.isNullOrEmpty()) return youtuBe
    return null
}

fun extractYouTubePlaylistId(input: String): String? {
    val s = input.trim()
    if (s.isEmpty()) return null

    val listFromQuery = Regex("""[?&]list=([^&]+)""").find(s)?.groupValues?.getOrNull(1)?.trim()
    if (!listFromQuery.isNullOrEmpty()) return listFromQuery

    val normalized = if (!s.contains("://")) "https://$s" else s
    runCatching { Uri.parse(normalized) }.getOrNull()?.getQueryParameter("list")?.trim()?.let {
        if (it.isNotEmpty()) return it
    }

    // Bare playlist id (PL…, RD…, OLAK5uy_…, etc.)
    if (!s.contains("/") && !s.contains("?") && s.length >= 13 && s.matches(Regex("""[A-Za-z0-9_-]+"""))) {
        return s
    }
    return null
}
