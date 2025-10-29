package com.dd3boh.outertune.utils

import android.net.Uri
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.absoluteValue

/*
IMPORTANT: Put any string utils that require composable in outertne/ui/utils/StringUtils.kt
 */

fun makeTimeString(duration: Long?): String {
    if (duration == null || duration < 0) return ""
    var sec = duration / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60
    return when {
        day > 0 -> "%d:%02d:%02d:%02d".format(day, hour, minute, sec)
        hour > 0 -> "%d:%02d:%02d".format(hour, minute, sec)
        else -> "%d:%02d".format(minute, sec)
    }
}

fun md5(str: String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(str.toByteArray())).toString(16).padStart(32, '0')
}

fun joinByBullet(vararg str: String?) =
    str.filterNot {
        it.isNullOrEmpty()
    }.joinToString(separator = " • ")

fun String.urlEncode(): String = Uri.encode(this)

fun fixFilePath(path: String) = "/" + path.split('/').filterNot { it.isEmpty() }.joinToString("/") + "/"

fun formatFileSize(sizeBytes: Long): String {
    val prefix = if (sizeBytes < 0) "-" else ""
    var result: Long = sizeBytes.absoluteValue
    var suffix = "B"
    if (result > 900) {
        suffix = "KB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "MB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "GB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "TB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "PB"
        result /= 1024
    }
    return "$prefix$result $suffix"
}

/*
 * Whacko methods
 */

/**
 * Find the matching string, if not found the closest super string
 */
fun closestMatch(query: String, stringList: List<ArtistEntity>): ArtistEntity? {
    // Check for exact match first

    val exactMatch = stringList.find { query.equals(it.name.lowercase(), true) }
    if (exactMatch != null) {
        return exactMatch
    }

    // Check for query as substring in any of the strings
    val substringMatches = stringList.filter { it.name.contains(query) }
    if (substringMatches.isNotEmpty()) {
        return substringMatches.minByOrNull { it.name.length }
    }

    return null
}

/**
 * Find the matching string, if not found the closest super string
 */
fun closestAlbumMatch(query: String, stringList: List<AlbumEntity>): AlbumEntity? {
    // Check for exact match first

    val exactMatch = stringList.find { query.equals(it.title, true) }
    if (exactMatch != null) {
        return exactMatch
    }

    // Check for query as substring in any of the strings
    val substringMatches = stringList.filter { it.title.contains(query) }
    if (substringMatches.isNotEmpty()) {
        return substringMatches.minByOrNull { it.title.length }
    }

    return null
}

/**
 * Convert a number to a string representation
 *
 * The value of the number is denoted with characters from A through J. A being 0, and J being 9. This is prefixed by
 * number of digits the number has (always 2 digits, in the same representation) and succeeded with a null terminator "0"
 * In format:
 * <digit tens><digit ones><value in string form>0
 *
 *
 * For example:
 * 100          -> ADBAA0 ("AD" is "03", which represents this is a AD 3 digit number, "BAA" is "100")
 * 101          -> ADBAB0
 * 1013         -> AEBABD0
 * 9            -> ABJ0
 * 111222333444 -> BCBBBCCCDDDEEE0
 */
fun numberToAlpha(l: Long): String {
    val alphabetMap = ('A'..'J').toList()
    val weh = if (l < 0) "0" else l.toString()
    val lengthStr = if (weh.length.toInt() < 10) {
        "0" + weh.length.toInt()
    } else {
        weh.length.toInt().toString()
    }

    return (lengthStr + weh + "\u0000").map {
        if (it == '\u0000') {
            "0"
        } else {
            alphabetMap[it.digitToInt()]
        }
    }.joinToString("")
}

/**
 * Compare two version‐strings, returning:
 *   > 0 if v1 > v2
 *   < 0 if v1 < v2
 *   = 0 if they’re considered equal
 */
fun compareVersion(v1: String, v2: String): Int {
    fun normalize(v: String) = v.substringBefore('-')
    val parts1 = normalize(v1).split('.').map { it.toIntOrNull() ?: 0 }
    val parts2 = normalize(v2).split('.').map { it.toIntOrNull() ?: 0 }

    val max = maxOf(parts1.size, parts2.size)
    for (i in 0 until max) {
        val n1 = parts1.getOrElse(i) { 0 }
        val n2 = parts2.getOrElse(i) { 0 }
        if (n1 != n2) return n1 - n2
    }
    return 0
}