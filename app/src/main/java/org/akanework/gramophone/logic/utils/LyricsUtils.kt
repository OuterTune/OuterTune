package org.akanework.gramophone.logic.utils

import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.Metadata
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
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
    fun parseLyrics(lyrics: String, parserOptions: LrcParserOptions, format: LyricFormat?): SemanticLyrics? {
        for (i in listOf({
            if (format == null || format == LyricFormat.TTML)
                parseTtml(lyrics)
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
                Log.e(TAG, Log.getStackTraceString(e))
                Log.e(TAG, "The lyrics are:\n$lyrics")
                SemanticLyrics.UnsyncedLyrics(listOf(parserOptions.errorText to null))
            }
        }
        return null
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
            return plainTextData?.let { parseLyrics(it, parserOptions, null) } ?: continue
        }
        return null
    }

    @OptIn(UnstableApi::class)
    fun loadAndParseLyricsFile(musicFile: File?, parserOptions: LrcParserOptions): SemanticLyrics? {
        return loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".ttml") },
            parserOptions.errorText
        )?.let { parseLyrics(it, parserOptions, LyricFormat.TTML) }
            ?: loadTextFile(
                musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".srt") },
                parserOptions.errorText
            )?.let { parseLyrics(it, parserOptions, LyricFormat.SRT) }
            ?: loadTextFile(
                musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".lrc") },
                parserOptions.errorText
            )?.let { parseLyrics(it, parserOptions, LyricFormat.LRC) }
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

    // begin OuterTune
    @OptIn(UnstableApi::class)
    fun loadLyricsFile(musicFile: File?): String? {
        return loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".ttml") },
            ""
        )?: loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".srt") },
            ""
        )?: loadTextFile(
            musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".lrc") },
            ""
        )
    }
    // end OuterTune
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