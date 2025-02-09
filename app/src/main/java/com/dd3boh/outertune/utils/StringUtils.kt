package com.dd3boh.outertune.utils

import com.dd3boh.outertune.db.entities.Artist
import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest

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

fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")


/**
 * Whacko methods
 */

/**
 * Find the matching string, if not found the closest super string
 */
fun closestMatch(query: String, stringList: List<Artist>): Artist? {
    // Check for exact match first

    val exactMatch = stringList.find { query.lowercase() == it.artist.name.lowercase() }
    if (exactMatch != null) {
        return exactMatch
    }

    // Check for query as substring in any of the strings
    val substringMatches = stringList.filter { it.artist.name.contains(query) }
    if (substringMatches.isNotEmpty()) {
        return substringMatches.minByOrNull { it.artist.name.length }
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