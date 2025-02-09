package com.dd3boh.outertune.lyrics

import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.Metadata
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.dd3boh.outertune.lyrics.SemanticLyrics.Word
import com.dd3boh.outertune.ui.component.animateScrollDuration
import java.io.File
import java.nio.charset.Charset
import kotlin.math.min

/**
 * Unless otherwise stated, this file is From Gramophone as of 9ca721e21a16bbfafdc15b8707b910edc5080c0c
 * This has been adapted for OuterTune
 * https://github.com/AkaneTan/Gramophone/blob/beta/app/src/main/kotlin/org/akanework/gramophone/logic/utils/LrcUtils.kt
 */
object LyricsUtils {

    private const val TAG = "LrcUtils"

    data class LrcParserOptions(val trim: Boolean, val multiLine: Boolean, val errorText: String)

    @VisibleForTesting
    fun parseLyrics(lyrics: String, parserOptions: LrcParserOptions): SemanticLyrics? {
        return (try {
            parseTtml(lyrics, parserOptions.trim)
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            SemanticLyrics.UnsyncedLyrics(listOf(parserOptions.errorText))
        } ?: try {
            parseSrt(lyrics, parserOptions.trim)
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            SemanticLyrics.UnsyncedLyrics(listOf(parserOptions.errorText))
        } ?: try {
            parseLrc(lyrics, parserOptions.trim, parserOptions.multiLine)
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            SemanticLyrics.UnsyncedLyrics(listOf(parserOptions.errorText))
        })?.let {
            if (it is SemanticLyrics.SyncedLyrics)
                splitBidirectionalWords(it)
            else it
        }
    }

    @OptIn(UnstableApi::class)
    fun extractAndParseLyrics(
        metadata: Metadata,
        parserOptions: LrcParserOptions
    ): SemanticLyrics? {
        for (i in 0..<metadata.length()) {
            val meta = metadata.get(i)
            // TODO https://id3.org/id3v2.4.0-frames implement SYLT
            // if (meta is BinaryFrame && meta.id == "SYLT") {
            //    val syltData = SyltFrameDecoder.decode(ParsableByteArray(meta.data))
            //    if (syltData != null) return syltData
            // }
            val plainTextData =
                if (meta is VorbisComment && meta.key == "LYRICS") // ogg / flac
                    meta.value
                else if (meta is BinaryFrame && (meta.id == "USLT" || meta.id == "SYLT")) // mp3 / other id3 based
                    UsltFrameDecoder.decode(ParsableByteArray(meta.data)) // SYLT is also used to store lrc lyrics encoded in USLT format
                else if (meta is TextInformationFrame && (meta.id == "USLT" || meta.id == "SYLT")) // m4a
                    meta.values.joinToString("\n")
                else null
            return plainTextData?.let { parseLyrics(it, parserOptions) } ?: continue
        }
        return null
    }

    // OuterTune helper
    @OptIn(UnstableApi::class)
    fun loadAndParseLyricsString(lyrics: String, parserOptions: LrcParserOptions): List<LyricsEntry> {
        return emptyList<LyricsEntry>() + parseLyrics(lyrics, parserOptions).convertForLegacy()
    }

    @OptIn(UnstableApi::class)
    fun loadAndParseLyricsFile(musicFile: File?, parserOptions: LrcParserOptions): SemanticLyrics? {
        val lrcFile = musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".lrc") }
        return loadTextFile(lrcFile, parserOptions.errorText)?.let {
            parseLyrics(
                it,
                parserOptions
            )
        }
    }

    private fun loadTextFile(lrcFile: File?, errorText: String?): String? {
        return try {
            if (lrcFile?.exists() == true)
                lrcFile.readBytes().toString(Charset.defaultCharset())
            else null
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            return errorText
        }
    }

    private fun splitBidirectionalWords(syncedLyrics: SemanticLyrics.SyncedLyrics): SemanticLyrics.SyncedLyrics {
        return SemanticLyrics.SyncedLyrics(syncedLyrics.text.map { line ->
            if (line.lyric.words.isNullOrEmpty()) return@map line
            val bidirectionalBarriers = findBidirectionalBarriers(line.lyric.text)
            val wordsWithBarriers = line.lyric.words.toMutableList()
            var lastWasRtl = false
            bidirectionalBarriers.forEach { barrier ->
                val evilWordIndex =
                    if (barrier.first == -1) -1 else wordsWithBarriers.indexOfFirst {
                        it.charRange.contains(barrier.first) && it.charRange.start != barrier.first
                    }
                if (evilWordIndex == -1) {
                    // Propagate the new direction (if there is a barrier after that, direction will
                    // be corrected after it).
                    val wordIndex = if (barrier.first == -1) 0 else
                        wordsWithBarriers.indexOfFirst { it.charRange.start == barrier.first }
                    wordsWithBarriers.replaceAllSupport(skipFirst = wordIndex) {
                        if (it.isRtl != barrier.second) it.copy(isRtl = barrier.second) else it
                    }
                    lastWasRtl = barrier.second
                    return@forEach
                }
                val evilWord = wordsWithBarriers[evilWordIndex]
                // Estimate how long this word will take based on character to time ratio. To avoid
                // this estimation, add a word sync point to bidirectional barriers :)
                val barrierTime = min(evilWord.timeRange.first + ((line.lyric.words.map {
                    it.timeRange.count() / it.charRange.count().toFloat()
                }.average().let { if (it.isNaN()) 100.0 else it } * (barrier.first -
                        evilWord.charRange.first))).toULong(), evilWord.timeRange.last - 1uL)
                val firstPart = Word(
                    charRange = evilWord.charRange.first..<barrier.first,
                    timeRange = evilWord.timeRange.first..<barrierTime, isRtl = lastWasRtl
                )
                val secondPart = Word(
                    charRange = barrier.first..evilWord.charRange.last,
                    timeRange = barrierTime..evilWord.timeRange.last, isRtl = barrier.second
                )
                wordsWithBarriers[evilWordIndex] = firstPart
                wordsWithBarriers.add(evilWordIndex + 1, secondPart)
                lastWasRtl = barrier.second
            }
            line.copy(line.lyric.copy(words = wordsWithBarriers))
        })
    }

    private val ltr =
        arrayOf(
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
        )
    private val rtl =
        arrayOf(
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
        )

    private fun findBidirectionalBarriers(text: String): List<Pair<Int, Boolean>> {
        val barriers = mutableListOf<Pair<Int, Boolean>>()
        if (text.isEmpty()) return barriers
        var previousDirection = text.find {
            val dir = Character.getDirectionality(it)
            dir in ltr || dir in rtl
        }?.let { Character.getDirectionality(it) in rtl } == true
        barriers.add(Pair(-1, previousDirection))
        for (i in 0 until text.length) {
            val currentDirection = Character.getDirectionality(text[i])
            val isRtl = currentDirection in rtl
            if (currentDirection !in ltr && !isRtl)
                continue
            if (previousDirection != isRtl)
                barriers.add(Pair(i, isRtl))
            previousDirection = isRtl
        }
        return barriers
    }

    @OptIn(UnstableApi::class)
    fun extractAndParseLyricsLegacy(
        metadata: Metadata,
        parserOptions: LrcParserOptions
    ): MutableList<LyricsEntry>? {
        for (i in 0..<metadata.length()) {
            val meta = metadata.get(i)
            val data =
                if (meta is VorbisComment && meta.key == "LYRICS") // ogg / flac
                    meta.value
                else if (meta is BinaryFrame && (meta.id == "USLT" || meta.id == "SYLT")) // mp3 / other id3 based
                    UsltFrameDecoder.decode(ParsableByteArray(meta.data))
                else if (meta is TextInformationFrame && (meta.id == "USLT" || meta.id == "SYLT")) // m4a
                    meta.values.joinToString("\n")
                else null
            val lyrics = data?.let {
                try {
                    parseLrcStringLegacy(it, parserOptions)
                } catch (e: Exception) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    mutableListOf(LyricsEntry(timeStamp = 0L, content = parserOptions.errorText))
                }
            }
            return lyrics ?: continue
        }
        return null
    }

    @OptIn(UnstableApi::class)
    fun loadAndParseLyricsFileLegacy(
        musicFile: File?,
        parserOptions: LrcParserOptions
    ): MutableList<LyricsEntry>? {
        val lrcFile = musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".lrc") }
        return loadTextFile(lrcFile, parserOptions.errorText)?.let {
            try {
                parseLrcStringLegacy(it, parserOptions)
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
                null
            }
        }
    }

    private fun parseLrcStringLegacy(
        lrcContent: String,
        parserOptions: LrcParserOptions
    ): MutableList<LyricsEntry>? {
        if (lrcContent.isBlank()) return null

        val timeMarksRegex = "\\[(\\d{2}:\\d{2})([.:]\\d+)?]".toRegex()
        val lyricsList = mutableListOf<LyricsEntry>()
        var foundNonNull = false
        var lyricsText: StringBuilder? = StringBuilder()

        lrcContent.lines().forEach { line ->
            val matches = timeMarksRegex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            val lyricContent =
                line.substring(matches.last().range.last + 1)
                    .let { if (parserOptions.trim) it.trim() else it }

            matches.forEach { match ->
                val timeString = match.groupValues[1] + match.groupValues[2]
                val timestamp = parseTime(timeString)

                if (!foundNonNull && timestamp > 0) {
                    foundNonNull = true
                    lyricsText = null
                }

                val lyricLine = if (parserOptions.multiLine) {
                    val startIndex = lrcContent.indexOf(line) + match.value.length
                    val endIndex = findEndIndex(lrcContent, startIndex, timeMarksRegex)
                    lrcContent.substring(startIndex, endIndex)
                        .let { if (parserOptions.trim) it.trim() else it }
                } else {
                    lyricContent
                }

                lyricsText?.append("$lyricLine\n")
                lyricsList.add(LyricsEntry(timestamp, lyricLine))
            }
        }

        markTranslations(lyricsList)

        lyricsList.takeWhile { it.content.isEmpty() }
            .forEach { _ -> lyricsList.removeAt(0) }

        if (lyricsList.isEmpty() && lrcContent.isNotEmpty()) {
            lyricsList.add(LyricsEntry(-1, lrcContent, false))
        } else if (!foundNonNull) {
            lyricsList.clear()
            lyricsList.add(LyricsEntry(-1, lyricsText.toString(), false))
        }

        return lyricsList
    }

    private fun findEndIndex(
        lrcContent: String,
        startIndex: Int,
        timeMarksRegex: Regex
    ): Int {
        val nextSyncMatch = timeMarksRegex.find(lrcContent, startIndex)
        return nextSyncMatch?.range?.first?.minus(1) ?: lrcContent.length
    }

    private fun markTranslations(lyricsList: MutableList<LyricsEntry>) {
        lyricsList.sortBy { it.timeStamp }
        var previousTimestamp: Long? = null
        lyricsList.forEach {
            it.isTranslation = (it.timeStamp == previousTimestamp)
            previousTimestamp = it.timeStamp
        }
    }

    private fun parseTime(timeString: String): Long {
        val timeRegex = "(\\d{2}):(\\d{2})[.:](\\d+)".toRegex()
        val matchResult = timeRegex.find(timeString)
        val minutes = matchResult?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val seconds = matchResult?.groupValues?.get(2)?.toLongOrNull() ?: 0
        val millisecondsString = matchResult?.groupValues?.get(3)
        val milliseconds = millisecondsString?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0

        return minutes * 60000 + seconds * 1000 + milliseconds
    }

    // this was from OuterTune
    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        for (index in lines.indices) {
            if (lines[index].timeStamp >= position + animateScrollDuration) {
                return index - 1
            }
        }
        return lines.lastIndex
    }
}


/**
 * From Gramophone as of 0d46996db54a3360e832ed06bf18170bce85534f
 * This has been adapted for OuterTune
 * https://github.com/AkaneTan/Gramophone/blob/beta/app/src/main/kotlin/org/akanework/gramophone/logic/GramophoneExtensions.kt
 */
inline fun <reified T> MutableList<T>.replaceAllSupport(skipFirst: Int = 0, operator: (T) -> T) {
    val li = listIterator()
    var skip = skipFirst
    while (skip-- > 0) {
        li.next()
    }
    while (li.hasNext()) {
        li.set(operator(li.next()))
    }
}



// Class heavily based on MIT-licensed https://github.com/yoheimuta/ExoPlayerMusic/blob/77cfb989b59f6906b1170c9b2d565f9b8447db41/app/src/main/java/com/github/yoheimuta/amplayer/playback/UsltFrameDecoder.kt
// See http://id3.org/id3v2.4.0-frames
@OptIn(UnstableApi::class)
private class UsltFrameDecoder {
    companion object {
        private const val ID3_TEXT_ENCODING_ISO_8859_1 = 0
        private const val ID3_TEXT_ENCODING_UTF_16 = 1
        private const val ID3_TEXT_ENCODING_UTF_16BE = 2
        private const val ID3_TEXT_ENCODING_UTF_8 = 3

        fun decode(id3Data: ParsableByteArray): String? {
            if (id3Data.limit() < 4) {
                // Frame is malformed.
                return null
            }

            val encoding = id3Data.readUnsignedByte()
            val charset = getCharsetName(encoding)

            val lang = ByteArray(3)
            id3Data.readBytes(lang, 0, 3) // language
            val rest = ByteArray(id3Data.limit() - 4)
            id3Data.readBytes(rest, 0, id3Data.limit() - 4)

            val descriptionEndIndex = indexOfEos(rest, 0, encoding)
            val textStartIndex = descriptionEndIndex + delimiterLength(encoding)
            val textEndIndex = indexOfEos(rest, textStartIndex, encoding)
            return decodeStringIfValid(rest, textStartIndex, textEndIndex, charset)
        }

        private fun getCharsetName(encodingByte: Int): Charset {
            val name = when (encodingByte) {
                ID3_TEXT_ENCODING_UTF_16 -> "UTF-16"
                ID3_TEXT_ENCODING_UTF_16BE -> "UTF-16BE"
                ID3_TEXT_ENCODING_UTF_8 -> "UTF-8"
                ID3_TEXT_ENCODING_ISO_8859_1 -> "ISO-8859-1"
                else -> "ISO-8859-1"
            }
            return Charset.forName(name)
        }

        private fun indexOfEos(data: ByteArray, fromIndex: Int, encoding: Int): Int {
            var terminationPos = indexOfZeroByte(data, fromIndex)

            // For single byte encoding charsets, we're done.
            if (encoding == ID3_TEXT_ENCODING_ISO_8859_1 || encoding == ID3_TEXT_ENCODING_UTF_8) {
                return terminationPos
            }

            // Otherwise ensure an even index and look for a second zero byte.
            while (terminationPos < data.size - 1) {
                if (terminationPos % 2 == 0 && data[terminationPos + 1] == 0.toByte()) {
                    return terminationPos
                }
                terminationPos = indexOfZeroByte(data, terminationPos + 1)
            }

            return data.size
        }

        private fun indexOfZeroByte(data: ByteArray, fromIndex: Int): Int {
            for (i in fromIndex until data.size) {
                if (data[i] == 0.toByte()) {
                    return i
                }
            }
            return data.size
        }

        private fun delimiterLength(encodingByte: Int): Int {
            return if (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8)
                1
            else
                2
        }

        private fun decodeStringIfValid(
            data: ByteArray,
            from: Int,
            to: Int,
            charset: Charset
        ): String {
            return if (to <= from || to > data.size) {
                ""
            } else String(data, from, to - from, charset)
        }
    }
}