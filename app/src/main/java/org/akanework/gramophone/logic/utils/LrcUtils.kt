package org.akanework.gramophone.logic.utils

import androidx.annotation.VisibleForTesting
import androidx.media3.common.Metadata
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics
import java.io.File
import java.nio.charset.Charset

object LrcUtils {

    private const val TAG = "LrcUtils"

    enum class LyricFormat {
        LRC,
        TTML,
        SRT
    }
    data class LrcParserOptions(val trim: Boolean, val multiLine: Boolean, val errorText: String?)

    @VisibleForTesting
    fun parseLyrics(lyrics: String, audioMimeType: String?, parserOptions: LrcParserOptions, format: LyricFormat?): SemanticLyrics? {
        for (i in listOf({
            if (format == null || format == LyricFormat.TTML)
                parseTtml(audioMimeType, lyrics)
            else null
        }, {
            if (format == null || format == LyricFormat.SRT)
                parseSrt(lyrics, parserOptions.trim)
            else null
        }, {
            if (format == null || format == LyricFormat.LRC)
                parseLrc(lyrics, parserOptions.trim, parserOptions.multiLine)
            else null
        })) {
            return try {
                i() ?: continue
            } catch (e: Exception) {
                if (parserOptions.errorText == null)
                    throw e
                Log.e(TAG, Log.getThrowableString(e)!!)
                Log.e(TAG, "The lyrics are:\n$lyrics")
                SemanticLyrics.UnsyncedLyrics(listOf(parserOptions.errorText to null))
            }
        }
        return null
    }

    // returns best lyrics first
    fun extractAndParseLyrics(
        sampleRate: Int,
        audioMimeType: String?,
        metadata: Metadata,
        parserOptions: LrcParserOptions
    ): List<SemanticLyrics> {
        val out = mutableListOf<SemanticLyrics>()
        for (i in 0..<metadata.length()) {
            val meta = metadata.get(i)
            if (meta is BinaryFrame && meta.id == "SYLT") {
                val syltData = UsltFrameDecoder.decodeSylt(sampleRate, ParsableByteArray(meta.data))
                if (syltData != null) {
                    if (syltData.contentType == 1 || syltData.contentType == 2) {
                        out.add(syltData.toSyncedLyrics(parserOptions.trim))
                    }
                    continue
                }
            }
            val plainTextData =
                if (meta is VorbisComment && meta.key == "LYRICS") // ogg / flac
                    meta.value
                else if (meta is BinaryFrame && (meta.id == "USLT" || meta.id == "SYLT" /* out-of-spec */)) // mp3 / other id3 based
                    UsltFrameDecoder.decode(ParsableByteArray(meta.data))?.text
                else if (meta is TextInformationFrame && (meta.id == "USLT" || meta.id == "SYLT")) // m4a
                    meta.values.joinToString("\n")
                else null
            if (plainTextData != null) {
                parseLyrics(plainTextData, audioMimeType, parserOptions, null)?.let {
                    out.add(it)
                    continue
                }
            }
        }
        out.sortBy {
            if (it !is SyncedLyrics) {
                return@sortBy -10
            }
            val hasWords = it.text.find { it.words != null } != null
            val hasTl = it.text.find { it.isTranslated } != null
            if (hasWords) 10 else 0 + if (hasTl) 1 else 0
        }
        return out
    }

    fun loadAndParseLyricsFile(musicFile: File?, audioMimeType: String?, parserOptions: LrcParserOptions): SemanticLyrics? {
        return loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".ttml") },
            parserOptions.errorText
        )?.let { parseLyrics(it, audioMimeType, parserOptions, LyricFormat.TTML) }
            ?: loadTextFile(
                musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".srt") },
                parserOptions.errorText
            )?.let { parseLyrics(it, audioMimeType, parserOptions, LyricFormat.SRT) }
            ?: loadTextFile(
                musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".lrc") },
                parserOptions.errorText
            )?.let { parseLyrics(it, audioMimeType, parserOptions, LyricFormat.LRC) }
    }

    private fun loadTextFile(lrcFile: File?, errorText: String?): String? {
        return try {
            if (lrcFile?.exists() == true)
                lrcFile.readBytes().toString(Charset.defaultCharset())
            else null
        } catch (e: Exception) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            return errorText
        }
    }

// begin OuterTune
    fun loadLyricsFile(musicFile: File?): String? {
        return loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".lrc") },
            ""
        )?: loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".ttml") },
            ""
        )?: loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".srt") },
            ""
        )
    }
// end OuterTune
}

// begin OuterTune
data class Lyric(
    val timeStamp: Long? = null,
    val content: String = "",
    var isTranslation: Boolean = false
) : Comparable<Lyric> {
    override fun compareTo(other: Lyric): Int = ((timeStamp ?: 0L) - (other.timeStamp ?: 0L)).toInt()
}
// end OuterTune
