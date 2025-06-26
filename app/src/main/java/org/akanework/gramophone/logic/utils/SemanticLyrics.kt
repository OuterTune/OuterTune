package org.akanework.gramophone.logic.utils

import android.util.Log
import android.util.Xml
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import org.akanework.gramophone.logic.forEachSupport
import org.akanework.gramophone.logic.utils.SemanticLyrics.LyricLine
import org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.UnsyncedLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.Word
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private const val TAG = "SemanticLyrics"

/*
 * Syntactic-semantic lyric parser.
 *   First parse lrc syntax into custom objects, then parse these into usable representation
 *   for playback. This should be more testable and stable than the old parser.
 *
 * Formats we have to consider in this parser are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
 *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
 *     - This implies multiple [offset:] tags are possible
 *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
 *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
 *  - Multiline format: This technically isn't part of any listed guidelines, however is allows for
 *      reading of otherwise discarded lyrics, all the lines between sync point A and B are read as
 *      lyric text of A
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Walaoke gender extension (ref Wikipedia)
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - [offset:] tag in header (ref Wikipedia)
 * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
 */

enum class SpeakerEntity(val isWalaoke: Boolean, val isVoice2: Boolean = false, val isGroup: Boolean = false, val isBackground: Boolean = false) {
    Male(true), // Walaoke
    Female(true), // Walaoke
    Duet(true), // Walaoke
    Voice1(false), // iTunes
    Background(false, isBackground = true), // iTunes
    Voice2(false, isVoice2 = true), // iTunes
    Voice2Background(false, isVoice2 = true, isBackground = true), // iTunes
    Group(false, isGroup = true),
    GroupBackground(false, isGroup = true, isBackground = true)
}

/*
 * Syntactic lyric parser. Parses lrc syntax into custom objects.
 *
 * Formats we have to consider in this component are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
 *  - Multiline format: This technically isn't part of any listed guidelines, however is allows for
 *      reading of otherwise discarded lyrics, all the lines between sync point A and B are read as
 *      lyric text of A
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Extended LRC without sync points ex: <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Walaoke gender extension (ref Wikipedia)
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - Metadata tags in header (ref Wikipedia)
 */
private sealed class SyntacticLrc {
    // all timestamps are in milliseconds ignoring offset
    data class SyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class SpeakerTag(val speaker: SpeakerEntity) : SyntacticLrc()
    data class WordSyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class Metadata(val name: String, val value: String) : SyntacticLrc()
    data class LyricText(val text: String) : SyntacticLrc()
    data class InvalidText(val text: String) : SyntacticLrc()
    open class NewLine() : SyntacticLrc() {
        class SyntheticNewLine : NewLine()
    }

    companion object {
        // also eats space if present
        val timeMarksRegex = "\\[(\\d+):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeMarksAfterWsRegex = "([ \t]+)\\[(\\d+):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeWordMarksRegex = "<(\\d+):(\\d{2})([.:]\\d+)?>".toRegex()
        val metadataRegex = "\\[([a-zA-Z#]+):([^]]*)]".toRegex()

        private fun parseTime(match: MatchResult): ULong {
            val minute = match.groupValues[1].toULong()
            val milliseconds = ((match.groupValues[2] + match.groupValues[3]
                .replace(':', '.')).toDouble() * 1000L).toULong()
            return minute * 60u * 1000u + milliseconds
        }

        fun parseLrc(text: String, multiLineEnabled: Boolean): List<SyntacticLrc>? {
            if (text.isBlank()) return null
            var pos = 0
            val out = mutableListOf<SyntacticLrc>()
            var isBgSpeaker = false
            while (pos < text.length) {
                var pendingBgNewLine = false
                if (isBgSpeaker && text[pos] == ']') {
                    pos++
                    isBgSpeaker = false
                    pendingBgNewLine = true
                }
                if (pos < text.length && pos + 1 < text.length && text.regionMatches(
                        pos,
                        "\r\n",
                        0,
                        2
                    )
                ) {
                    out.add(NewLine())
                    pos += 2
                    pendingBgNewLine = false
                    continue
                }
                if (pos < text.length && (text[pos] == '\n' || text[pos] == '\r')) {
                    out.add(NewLine())
                    pos++
                    pendingBgNewLine = false
                    continue
                }
                if (pendingBgNewLine) {
                    out.add(NewLine.SyntheticNewLine())
                    pendingBgNewLine = false
                    continue
                }
                val tmMatch = timeMarksRegex.matchAt(text, pos)
                if (tmMatch != null) {
                    // Insert synthetic newlines at places where we'd expect one. This won't ever
                    // work with word lyrics without normal sync points at all for obvious reasons,
                    // but hey, we tried. Can't do much about it.
                    // If you want to write something that looks like a timestamp into your lyrics,
                    // you'll probably have to delete the following three lines.
                    if (!(out.lastOrNull() is NewLine? || out.lastOrNull() is SyncPoint))
                        out.add(NewLine.SyntheticNewLine())
                    out.add(SyncPoint(parseTime(tmMatch)))
                    pos += tmMatch.value.length
                    continue
                }
                // Skip spaces in between of compressed lyric sync points. They really are
                // completely useless information we can and should discard.
                val tmwMatch = timeMarksAfterWsRegex.matchAt(text, pos)
                if (out.lastOrNull() is SyncPoint && pos + 7 < text.length && tmwMatch != null) {
                    pos += tmwMatch.groupValues[1].length
                    continue
                }
                // Speaker points can only appear directly after a sync point
                if (out.lastOrNull() is SyncPoint) {
                    if (pos + 2 < text.length && text.regionMatches(pos, "v1:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, "v2:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, "v3:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Group))
                        pos += 3
                        continue
                    }
                    if (pos + 1 < text.length && text.regionMatches(pos, "F:", 0, 2)) {
                        out.add(SpeakerTag(SpeakerEntity.Female))
                        pos += 2
                        continue
                    }
                    if (pos + 1 < text.length && text.regionMatches(pos, "M:", 0, 2)) {
                        out.add(SpeakerTag(SpeakerEntity.Male))
                        pos += 2
                        continue
                    }
                    if (pos + 1 < text.length && text.regionMatches(pos, "D:", 0, 2)) {
                        out.add(SpeakerTag(SpeakerEntity.Duet))
                        pos += 2
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v1:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 4
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v2:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 4
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v3:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Group))
                        pos += 4
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, " F:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Female))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, " M:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Male))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, " D:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Duet))
                        pos += 3
                        continue
                    }
                }
                // Metadata (or the bg speaker, which looks like metadata) can only appear in the
                // beginning of a file or after newlines
                if (pos + 3 < text.length && text.regionMatches(pos, "[bg:", 0, 4)) {
                    // Insert synthetic newlines at places where we'd expect one.
                    // If you want to write [bg: into your lyrics, you'll probably have to add the
                    // conditional surrounding this comment into the
                    // if (out.isEmpty() || out.last() is NewLine) below.
                    if (out.isNotEmpty() && out.last() !is NewLine)
                        out.add(NewLine.SyntheticNewLine())
                    val lastSpeaker = if (out.isNotEmpty()) out.subList(0, out.size - 1)
                        .indexOfLast { it is NewLine }.let { if (it < 0) null else it }?.let {
                            (out.subList(it, out.size - 1).findLast { it is SpeakerTag }
                                    as SpeakerTag?)?.speaker
                        } else null
                    // TODO revisit this heuristic. iTunes bg lines are a child of the last main
                    //  line and there is no "opposite" flag for background lines, but reality does
                    //  not work that way. can main lines be empty in iTunes' lyrics?
                    out.add(SpeakerTag(when {
                        lastSpeaker?.isGroup == true -> SpeakerEntity.GroupBackground
                        lastSpeaker?.isVoice2 == true -> SpeakerEntity.Voice2Background
                        else -> SpeakerEntity.Background
                    }))
                    pos += 4
                    isBgSpeaker = true
                    continue
                }
                if (out.isEmpty() || out.last() is NewLine) {
                    val mmMatch = metadataRegex.matchAt(text, pos)
                    if (mmMatch != null) {
                        out.add(Metadata(mmMatch.groupValues[1], mmMatch.groupValues[2]))
                        pos += mmMatch.value.length
                        continue
                    }
                }
                // Word marks can be in any lyric text, and in some cases there are no sync points
                // but only word marks in a lrc file.
                val wmMatch = timeWordMarksRegex.matchAt(text, pos)
                if (wmMatch != null) {
                    out.add(WordSyncPoint(parseTime(wmMatch)))
                    pos += wmMatch.value.length
                    continue
                }
                val firstUnsafeCharPos = (text.substring(pos).indexOfFirst {
                    it == '[' ||
                            it == '<' || it == '\r' || it == '\n' || (isBgSpeaker && it == ']')
                } + pos)
                    .let { if (it == pos - 1) text.length else it }
                    .let { if (it == pos) it + 1 else it }
                val subText = text.substring(pos, firstUnsafeCharPos)
                val last = out.lastOrNull()
                // Only count lyric text as lyric text if there is at least one kind of timestamp
                // associated.
                if (out.indexOfLast { it is NewLine } <
                    out.indexOfLast { it is SyncPoint || it is WordSyncPoint }) {
                    if (last is LyricText) {
                        out[out.size - 1] = LyricText(last.text + subText)
                    } else {
                        out.add(LyricText(subText))
                    }
                } else {
                    if (last is InvalidText) {
                        out[out.size - 1] = InvalidText(last.text + subText)
                    } else {
                        out.add(InvalidText(subText))
                    }
                }
                pos = firstUnsafeCharPos
            }
            if (out.lastOrNull() is SyncPoint)
                out.add(InvalidText(""))
            if (out.isNotEmpty() && out.last() !is NewLine)
                out.add(NewLine.SyntheticNewLine())
            return out.let {
                // If there isn't a single sync point with timestamp over zero, that is probably not
                // a valid .lrc file.
                if (it.find {
                        it is SyncPoint && it.timestamp > 0u
                                || it is WordSyncPoint && it.timestamp > 0u
                    } == null)
                // Recover only text information to make the most out of this damaged file.
                    it.flatMap {
                        if (it is InvalidText)
                            listOf(it)
                        else if (it is SpeakerTag)
                            listOf(it)
                        else if (it is LyricText)
                            listOf(InvalidText(it.text))
                        else
                            listOf()
                    }
                else it
            }.let {
                if (multiLineEnabled) {
                    val a = AtomicReference<String?>(null)
                    it.flatMap {
                        val aa = a.get()
                        when {
                            it is LyricText -> {
                                if (aa == null)
                                    a.set(it.text)
                                else
                                    a.set(aa + it.text)
                                listOf()
                            }
                            // make sure InvalidText that can't be lyric text isn't saved as lyric
                            it is InvalidText && aa != null -> {
                                a.set(aa + it.text)
                                listOf()
                            }

                            it is NewLine && aa != null -> {
                                a.set(aa + "\n")
                                listOf()
                            }

                            aa != null -> {
                                a.set(null)
                                var aaa: String = aa
                                var i = 0
                                while (aaa.last() == '\n') {
                                    i++
                                    aaa = aaa.dropLast(1)
                                }
                                listOf(LyricText(aaa)).let {
                                    var aaaa: List<SyntacticLrc> = it
                                    while (i-- > 0)
                                        aaaa = aaaa + listOf(NewLine())
                                    aaaa
                                } + it
                            }

                            else -> listOf(it)
                        }
                    }.let {
                        if (a.get() != null)
                            it + if (a.get()!!.last() == '\n')
                                listOf(LyricText(a.get()!!.dropLast(1)), NewLine())
                            else
                                listOf(LyricText(a.get()!!))
                        else it
                    }
                } else it
            }
        }
    }
}

private fun splitBidirectionalWords(syncedLyrics: SyncedLyrics) {
    syncedLyrics.text.forEach { line ->
        if (line.words.isNullOrEmpty()) return@forEach
        val bidirectionalBarriers = findBidirectionalBarriers(line.text)
        var lastWasRtl = false
        bidirectionalBarriers.forEach { barrier ->
            val evilWordIndex =
                if (barrier.first == -1) -1 else line.words.indexOfFirst {
                    it.charRange.contains(barrier.first) && it.charRange.start != barrier.first
                }
            if (evilWordIndex == -1) {
                // Propagate the new direction (if there is a barrier after that, direction will
                // be corrected after it).
                val wordIndex = if (barrier.first == -1) 0 else
                    line.words.indexOfFirst { it.charRange.start == barrier.first }
                line.words.forEachSupport(skipFirst = wordIndex) {
                    it.isRtl = barrier.second
                }
                lastWasRtl = barrier.second
                return@forEach
            }
            val evilWord = line.words[evilWordIndex]
            // Estimate how long this word will take based on character to time ratio. To avoid
            // this estimation, add a word sync point to bidirectional barriers :)
            val barrierTime = min(evilWord.timeRange.first + ((line.words.map {
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
            line.words[evilWordIndex] = firstPart
            line.words.add(evilWordIndex + 1, secondPart)
            lastWasRtl = barrier.second
        }
    }
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

fun findBidirectionalBarriers(text: CharSequence): List<Pair<Int, Boolean>> {
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


/*
 * Syntactic lyric parser. Parse custom objects into usable representation for playback.
 *
 * Formats we have to consider in this component are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
 *     - This implies multiple [offset:] tags are possible
 *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Extended LRC without sync points ex: <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Walaoke gender extension (ref Wikipedia)
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - [offset:] tag in header (ref Wikipedia)
 * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
 */
sealed class SemanticLyrics {
    abstract val unsyncedText: List<Pair<String, SpeakerEntity?>>

    data class UnsyncedLyrics(override val unsyncedText: List<Pair<String, SpeakerEntity?>>) : SemanticLyrics()

    data class SyncedLyrics(val text: List<LyricLine>) : SemanticLyrics() {
        override val unsyncedText
            get() = text.map { it.text to it.speaker }
    }

    data class LyricLine(
        val text: String,
        val start: ULong,
        var end: ULong,
        val words: MutableList<Word>?,
        var speaker: SpeakerEntity?,
        var isTranslated: Boolean
    ) {
        val isClickable: Boolean
            get() = text.isNotBlank()
        val timeRange: ULongRange
            get() = start..end
    }

    data class Word(
        var timeRange: ULongRange,
        var charRange: IntRange,
        var isRtl: Boolean
    )
}

fun parseLrc(lyricText: String, trimEnabled: Boolean, multiLineEnabled: Boolean): SemanticLyrics? {
    val lyricSyntax = SyntacticLrc.parseLrc(lyricText, multiLineEnabled)
        ?: return null
    if (lyricSyntax.find { it is SyntacticLrc.SyncPoint || it is SyntacticLrc.WordSyncPoint } == null) {
        var lastSpeakerTag: SpeakerEntity? = null
        val out = mutableListOf<Pair<String, SpeakerEntity?>>()
        for (element in lyricSyntax) {
            when (element) {
                is SyntacticLrc.SpeakerTag -> {
                    lastSpeakerTag = element.speaker
                }
                is SyntacticLrc.InvalidText -> {
                    out += element.text to lastSpeakerTag
                    if (lastSpeakerTag?.isWalaoke != true)
                        lastSpeakerTag = null
                }
                else -> throw IllegalStateException("unexpected type ${element.javaClass.name}")
            }
        }
        val defaultIsWalaokeM = out.find { it.second?.isWalaoke == true } != null &&
                out.find { it.second?.isWalaoke == false } == null
        while (out.firstOrNull()?.first?.isBlank() == true)
            out.removeAt(0)
        //while (out.lastOrNull()?.first?.isBlank() == true)
        //    out.removeAt(out.lastIndex) TODO this breaks unit tests, but blank lines are useless
        return UnsyncedLyrics(out.map { lyric ->
            if (defaultIsWalaokeM && lyric.second == null)
                lyric.copy(second = SpeakerEntity.Male)
            else lyric
        })
    }
    // Synced lyrics processing state machine starts here
    val out = mutableListOf<LyricLine>()
    var offset = 0L
    var lastSyncPoint: ULong? = null
    var lastWordSyncPoint: ULong? = null
    var speaker: SpeakerEntity? = null
    var hadLyricSinceWordSync = true
    var hadWordSyncSinceNewLine = false
    var currentLine = mutableListOf<Pair<ULong, String?>>()
    var syncPointStreak = 0
    var compressed = mutableListOf<ULong>()
    for (element in lyricSyntax) {
        if (element is SyntacticLrc.SyncPoint)
            syncPointStreak++
        else
            syncPointStreak = 0
        when {
            element is SyntacticLrc.Metadata && element.name == "offset" -> {
                // positive offset means lyric played earlier in lrc, hence multiply with -1
                offset = element.value.toLong() * -1
            }

            element is SyntacticLrc.SyncPoint -> {
                val ts = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (syncPointStreak > 1) {
                    compressed.add(ts)
                } else {
                    if (compressed.isNotEmpty())
                        throw IllegalStateException("while parsing lrc, $compressed not empty but syncPointStreak is 1; lrc file: $lyricText")
                    lastSyncPoint = ts
                }
            }

            element is SyntacticLrc.SpeakerTag -> {
                speaker = element.speaker
            }

            element is SyntacticLrc.WordSyncPoint -> {
                if (!hadLyricSinceWordSync && lastWordSyncPoint != null)
                // add a dummy word for preserving end timing of previous word
                    currentLine.add(Pair(lastWordSyncPoint, null))
                lastWordSyncPoint = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (lastSyncPoint == null)
                    lastSyncPoint = lastWordSyncPoint
                hadLyricSinceWordSync = false
                hadWordSyncSinceNewLine = true
            }

            element is SyntacticLrc.LyricText -> {
                hadLyricSinceWordSync = true
                currentLine.add(Pair(lastWordSyncPoint ?: lastSyncPoint!!, element.text))
            }

            element is SyntacticLrc.NewLine -> {
                var words = if (currentLine.size > 1 || hadWordSyncSinceNewLine) {
                    val wout = mutableListOf<Word>()
                    var idx = 0
                    for (i in currentLine.indices) {
                        val current = currentLine[i]
                        if (current.second == null)
                            continue // skip dummy words that only exist to provide time
                        val oIdx = idx
                        idx += current.second!!.length
                        // Make sure we do NOT include whitespace as part of the word. Whitespaces
                        // do not have a strong bi-di flag assigned and hence ICU4J/AndroidBidi will
                        // set bi-di transition point before whitespace. Rendering relies on being
                        // able to change edge treatment with trailing flag in Layout which only
                        // works on bi-di transition points. Additionally, excluding whitespace
                        // allows us to scale gradient properly based on asking ourselves if the
                        // next char is even rendered (or whitespace).
                        val textWithoutStartWhitespace = current.second!!.trimStart()
                        val startWhitespaceLength =
                            current.second!!.length - textWithoutStartWhitespace.length
                        val textWithoutWhitespaces = textWithoutStartWhitespace.trimEnd()
                        val endWhitespaceLength =
                            textWithoutStartWhitespace.length - textWithoutWhitespaces.length
                        val startIndex = oIdx + startWhitespaceLength
                        val endIndex = idx - endWhitespaceLength
                        if (startIndex == endIndex)
                            continue // word contained only whitespace
                        val endInclusive = if (i + 1 < currentLine.size) {
                            // If we have a next word (with sync point), use its sync
                            // point minus 1ms as end point of this word
                            currentLine[i + 1].first - 1uL
                        } else if (lastWordSyncPoint != null &&
                            lastWordSyncPoint > current.first
                        ) {
                            // If we have a dedicated sync point just for the last word,
                            // use it. Similar to dummy words but for the last word only
                            lastWordSyncPoint - 1uL // minus 1ms for consistency
                        } else {
                            // Estimate how long this word will take based on character
                            // to time ratio. To avoid this estimation, add a last word
                            // sync point to the line after the text :)
                            current.first + (wout.map {
                                it.timeRange.count() /
                                        it.charRange.count().toFloat()
                            }.average().let {
                                if (it.isNaN()) 100.0 else it
                            } *
                                    textWithoutWhitespaces.length).toULong()
                        }
                        if (endInclusive > current.first)
                        // isRtl is filled in later in splitBidirectionalWords
                            wout.add(
                                Word(
                                    current.first..endInclusive,
                                    startIndex..<endIndex,
                                    isRtl = false
                                )
                            )
                    }
                    wout
                } else null
                if (currentLine.isNotEmpty() || lastWordSyncPoint != null || lastSyncPoint != null) {
                    var text = currentLine.joinToString("") { it.second ?: "" }
                    if (trimEnabled) {
                        val orig = text
                        text = orig.trimStart()
                        val startDiff = orig.length - text.length
                        text = text.trimEnd()
                        val iter = words?.listIterator()
                        iter?.forEach {
                            if (it.charRange.last.toLong() - startDiff < 0
                                || it.charRange.first.toLong() - startDiff >= text.length
                            )
                                iter.remove()
                            else
                                it.charRange = (it.charRange.first - startDiff)
                                    .coerceAtLeast(0)..(it.charRange.last - startDiff)
                                    .coerceAtMost(text.length - 1)
                        }
                    }
                    val start = if (currentLine.isNotEmpty()) currentLine.first().first
                    else lastWordSyncPoint ?: lastSyncPoint!!
                    out.add(LyricLine(text, start, 0uL /* filled later */, words, speaker, false /* filled later */))
                    compressed.forEach {
                        val diff = it - start
                        out.add(out.last().copy(start = it, words = words?.map {
                            it.copy(
                                it.timeRange.start + diff..it.timeRange.last + diff
                            )
                        }?.toMutableList()))
                    }
                }
                compressed.clear()
                currentLine.clear()
                lastSyncPoint = null
                lastWordSyncPoint = null
                hadWordSyncSinceNewLine = false
                // Walaoke extension speakers stick around unless another speaker is
                // specified. (The default speaker - before one is chosen - is male.)
                if (speaker?.isWalaoke != true)
                    speaker = null
                hadLyricSinceWordSync = true
            }
        }
    }
    out.sortBy { it.start }
    var previousTimestamp = ULong.MAX_VALUE
    val defaultIsWalaokeM = out.find { it.speaker?.isWalaoke == true } != null &&
            out.find { it.speaker?.isWalaoke == false } == null
    out.forEachIndexed { i, lyric ->
        if (defaultIsWalaokeM && lyric.speaker == null)
            lyric.speaker = SpeakerEntity.Male
        lyric.end = lyric.words?.lastOrNull()?.timeRange?.last
            ?: (if (lyric.start == previousTimestamp) out.find { it.start == lyric.start }
                ?.words?.lastOrNull()?.timeRange?.last else null)
                    ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                    ?: Long.MAX_VALUE.toULong()
        lyric.isTranslated = lyric.start == previousTimestamp
        previousTimestamp = lyric.start
    }
    while (out.firstOrNull()?.text?.isBlank() == true)
        out.removeAt(0)
    //while (out.lastOrNull()?.text?.isBlank() == true)
    //    out.removeAt(out.lastIndex) TODO this breaks unit tests, but blank lines are useless
    return SyncedLyrics(out).also { splitBidirectionalWords(it) }
}

private val tt = "http://www.w3.org/ns/ttml"
private val ttm = "http://www.w3.org/ns/ttml#metadata"
private val ttp = "http://www.w3.org/ns/ttml#parameter"
private val itunes = "http://itunes.apple.com/lyric-ttml-extensions"
private val itunesInternal = "http://music.apple.com/lyric-ttml-internal"
private fun XmlPullParser.skipToEndOfTag() {
    if (eventType != XmlPullParser.START_TAG)
        throw XmlPullParserException("expected start tag in skipToEndOfTag()")
    while (next() != XmlPullParser.END_TAG) {
        // we have a child tag!
        if (eventType == XmlPullParser.START_TAG)
            skipToEndOfTag()
        else if (eventType != XmlPullParser.TEXT)
            throw XmlPullParserException("expected start tag or text in skipToEndOfTag()")
        // else: we have some text, boring
    }
}
private fun XmlPullParser.nextAndThrowIfNotEnd() {
    if (next() != XmlPullParser.END_TAG)
        throw XmlPullParserException("expected end tag in nextAndThrowIfNotEnd()")
}
private fun XmlPullParser.nextAndThrowIfNotText() {
    if (next() != XmlPullParser.TEXT)
        throw XmlPullParserException("expected end tag in nextAndThrowIfNotText()")
}
private class TtmlTimeTracker(private val parser: XmlPullParser, private val isApple: Boolean) {
    private val effectiveFrameRate: Float
    private val subFrameRate: Int
    private val tickRate: Int
    init {
        val frameRate = parser.getAttributeValue(ttp, "frameRate")?.toInt() ?: 30
        val frameRateMultiplier = parser.getAttributeValue(ttp, "frameRateMultiplier")
            ?.split(" ")?.let { parts ->
                parts[0].toInt() / parts[1].toInt().toFloat()
            } ?: 1f
        effectiveFrameRate = frameRate * frameRateMultiplier
        subFrameRate = parser.getAttributeValue(ttp, "subFrameRate")?.toInt() ?: 1
        tickRate = parser.getAttributeValue(ttp, "tickRate")?.toInt() ?: 1
    }
    // of course someone didn't conform to spec again :D - business as usual
    private val appleTimeRegex = Regex("^(?:([0-9]+):)?(?:([0-9]+):)?([0-9]+\\.[0-9]+)?$")
    private val clockTimeRegex = Regex("^([0-9][0-9]+):([0-9][0-9]):([0-9][0-9])(?:(\\.[0-9]+)|:([0-9][0-9])(?:\\.([0-9]+))?)?$")
    private val offsetTimeRegex = Regex("^([0-9]+(?:\\.[0-9]+)?)(h|m|s|ms|f|t)$")
    var audioOffset: Long? = null
    fun parseTimestampMs(input: String?, offset: Long, negative: Boolean): Long? {
        if (input?.isEmpty() != false) return null
        val multiplier = if (negative && input.startsWith('-')) -1 else 1
        val input = if (multiplier == -1) input.substring(1) else input
        if (isApple) {
            val appleMatch = appleTimeRegex.matchEntire(input)
            if (appleMatch != null) {
                val hours = if (appleMatch.groupValues[2].isNotEmpty())
                    appleMatch.groupValues[1].toDoubleOrNull() ?: 0.0 else 0.0
                val minutes = if (appleMatch.groupValues[2].isNotEmpty())
                    appleMatch.groupValues[2].toDoubleOrNull() ?: 0.0 else
                    appleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val seconds = appleMatch.groupValues[3].toDouble()
                // Apple has no idea how a TTML file works. So omit offset just for their broken files
                return ((hours * 3600000 + minutes * 60000 + seconds * 1000).toLong() + (audioOffset ?: 0L)) * multiplier
            }
        } else {
            val clockMatch = clockTimeRegex.matchEntire(input)
            if (clockMatch != null) {
                val hours = clockMatch.groupValues[1].toDouble()
                val minutes = clockMatch.groupValues[2].toDouble()
                val seconds = (clockMatch.groupValues[3] + clockMatch.groupValues[4]).toDouble()
                val frameSecs = clockMatch.groupValues[5].toDoubleOrNull()
                    ?.div(effectiveFrameRate) ?: 0.0
                val subFrameSecs = clockMatch.groupValues[6].toDoubleOrNull()
                    ?.div(subFrameRate)?.div(effectiveFrameRate) ?: 0.0
                return ((hours * 3600000 + minutes * 60000 + (seconds + frameSecs +
                        subFrameSecs) * 1000).toLong() + offset + (audioOffset ?: 0L)) * multiplier
            }
            val offsetMatch = offsetTimeRegex.matchEntire(input)
            if (offsetMatch != null) {
                var time = offsetMatch.groupValues[1].toDouble()
                when (offsetMatch.groupValues[2]) {
                    "h" -> time *= 3600000.0
                    "m" -> time *= 60000.0
                    "s" -> time *= 1000.0
                    "ms" -> {}
                    "f" -> time /= effectiveFrameRate / 1000.0
                    "t" -> time /= tickRate / 1000.0
                }
                return (time.toLong() + offset + (audioOffset ?: 0L)) * multiplier
            }
        }
        throw XmlPullParserException("can't understand this TTML timestamp: $input")
    }
    private fun parseRange(offset: ULong): ULongRange? {
        var begin = parseTimestampMs(parser.getAttributeValue("", "begin"), offset.toLong(), false)?.toULong()
        var dur = parseTimestampMs(parser.getAttributeValue("", "dur"), 0L, false)?.toULong()
        var end = parseTimestampMs(parser.getAttributeValue("", "end"), offset.toLong(), false)?.toULong()
        if (begin == null && end == null || end == null && dur == null
            || begin == null && dur == null)
            return null
        if (begin == null && dur != null)
            begin = (end ?: 0uL) - dur
        else if (end == null && dur != null)
            end = begin!! + dur
        return begin!!..end!!
    }
    private class TtmlLevel(val time: ULongRange, val level: Int, var seq: ULong?)
    private val stack = mutableListOf<TtmlLevel>()
    fun beginBlock() {
        val isSeq = parser.getAttributeValue("", "timeContainer").let {
            when (it) {
                "par", null -> false
                "seq" -> true
                else -> throw XmlPullParserException("unknown timeContainer value $it")
            }
        }
        val last = stack.lastOrNull()
        val range = parseRange(last?.seq ?: last?.time?.first ?: 0uL)
        val frange = range ?: last?.time ?: 0uL..0uL
        stack.add(TtmlLevel(frange, (last?.level ?: 0) + if (range != null) 1 else 0, if (isSeq) frange.first else null))
    }
    fun getTime(): ULongRange? {
        return stack.lastOrNull()?.time
    }
    fun getLevel(): Int {
        return stack.lastOrNull()?.level ?: 0
    }
    fun endBlock() {
        val removed = stack.removeAt(stack.size - 1)
        stack.lastOrNull()?.let {
            it.seq = if (it.seq != null) removed.time.last else null
        }
    }
}
private class TtmlParserState(private val parser: XmlPullParser, private val timer: TtmlTimeTracker) {
    data class Text(val text: String, val time: ULongRange?, val role: String?)
    data class P(val texts: List<Text>, val time: ULongRange, val agent: String?,
                 val songPart: String?, val key: String?, val role: String?)
    private var texts: MutableList<Text>? = null
    val paragraphs = mutableListOf<P>()

    fun parse(time: ULongRange? = null, level: Int = 0, plevel: Int = 0, agent: String? = null, songPart: String? = null, key: String? = null, role: String? = null) {
        var time = time
        var agent = agent
        var songPart = songPart
        var key = key
        var role = role
        var level = level
        var plevel = plevel
        if (parser.eventType == XmlPullParser.TEXT) {
            if (parser.text.isBlank() && parser.text.contains("\n"))
                return // shrug
            if (texts == null) {
                if (parser.text.isNotBlank())
                    throw IllegalStateException("found TEXT \"${parser.text}\" but text isn't allowed here (forgot <p>?)")
                return
            }
            if (level == plevel)
                time = null
            texts!!.add(Text(parser.text, time, role))
            return
        }
        if (parser.eventType != XmlPullParser.START_TAG)
            throw IllegalStateException("expected START_TAG or TEXT, found ${parser.eventType}!")
        if (parser.name != "span")
            parser.getAttributeValue(ttm, "agent")?.let { agent = it }
        parser.getAttributeValue(ttm, "role")?.let { role = it }
        timer.beginBlock()
        time = timer.getTime()
        level = timer.getLevel()
        var isP = false
        when (parser.name) {
            "div" -> {
                // not even having consistent attribute naming is truly beautiful
                parser.getAttributeValue(itunes, "song-part")?.let { songPart = it }
                parser.getAttributeValue(itunesInternal, "songPart")?.let { songPart = it }
            }
            "p" -> {
                parser.getAttributeValue(itunesInternal, "key")?.let { key = it }
                texts = mutableListOf()
                isP = true
                plevel = level
            }
            "body", "span" -> {}
            else -> throw IllegalStateException("unknown tag ${parser.name}, wanted body/span/div/p")
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            parse(time, level, plevel, agent, songPart, key, role)
        }
        timer.endBlock()
        if (isP) {
            while (texts!!.isNotEmpty() && texts!![0].text.isBlank())
                texts!!.removeAt(0)
            while (texts!!.isNotEmpty() && texts!![texts!!.size - 1].text.isBlank())
                texts!!.removeAt(texts!!.size - 1)
            if (time == null)
                throw IllegalStateException("found a paragraph, why is time still null?")
            paragraphs.add(P(texts!!, time, agent, songPart, key, role))
            texts = null
        }
        if (parser.eventType != XmlPullParser.END_TAG)
            throw IllegalStateException("expected END_TAG, found ${parser.eventType}!")
    }
}

@OptIn(UnstableApi::class)
fun parseTtml(lyricText: String): SemanticLyrics? {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(StringReader(lyricText))
    try {
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, tt, "tt")
    } catch (_: XmlPullParserException) {
        return null // not ttml
    }
    // val lang = parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
    val timing = parser.getAttributeValue(itunesInternal, "timing")
    var hasItunesNamespace = timing != null
    if (!hasItunesNamespace) {
        for (i in 0..<parser.getNamespaceCount(parser.depth)) {
            if (parser.getNamespaceUri(i) == itunes || parser.getNamespaceUri(i) == itunesInternal) {
                hasItunesNamespace = true
                break
            }
        }
    }
    val peopleToType = hashMapOf<String, String>()
    val people = hashMapOf<String, MutableList<String>>()
    val timer = TtmlTimeTracker(parser, hasItunesNamespace)
    parser.nextTag()
    parser.require(XmlPullParser.START_TAG, tt, "head")
    // TODO parse and reject based on https://www.w3.org/TR/2018/REC-ttml2-20181108/#feature-profile-version-2 to be compliant
    while (parser.nextTag() != XmlPullParser.END_TAG) {
        if (parser.name == "metadata") {
            while (parser.nextTag() != XmlPullParser.END_TAG) {
                if (parser.namespace == ttm && parser.name == "agent") {
                    val id = parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "id")
                    val type = parser.getAttributeValue("", "type")
                    people.getOrPut(type) { mutableListOf() }.add(id)
                    peopleToType[id] = type
                    while (parser.nextTag() != XmlPullParser.END_TAG) {
                        if (parser.namespace == ttm && parser.name == "name") {
                            // val type = parser.getAttributeValue("", "type")
                            parser.nextAndThrowIfNotText()
                            // val name = parser.text
                            parser.nextAndThrowIfNotEnd()
                        } else {
                            throw XmlPullParserException(
                                "expected <ttm:name>, got " +
                                        "<${(parser.prefix?.plus(":") ?: "") + parser.name}> " +
                                        "in <ttm:agent> in <metadata>"
                            )
                        }
                    }
                } else if (parser.name == "iTunesMetadata") {
                    while (parser.nextTag() != XmlPullParser.END_TAG) {
                        if (parser.name == "songwriters") {
                            while (parser.nextTag() != XmlPullParser.END_TAG) {
                                if (parser.name == "songwriter") {
                                    parser.nextAndThrowIfNotText()
                                    // val songwriter = parser.text
                                    parser.nextAndThrowIfNotEnd()
                                } else {
                                    throw XmlPullParserException(
                                        "expected <songwriter>, got " +
                                                "<${(parser.prefix?.plus(":") ?: "") + parser.name}> " +
                                                "in <songwriters> in <iTunesMetadata>"
                                    )
                                }
                            }
                        } else if (parser.name == "audio") {
                            // There's a field named lyricOffset but lyrics are in sync without applying it.
                            // timer.audioOffset = timer.parseTimestampMs(parser.getAttributeValue(null, "lyricOffset"), 0L, true)
                            // val role = parser.getAttributeValue(null, "role")
                            parser.nextAndThrowIfNotEnd()
                        } else parser.skipToEndOfTag() // <translations> or other
                    }
                } else parser.skipToEndOfTag()
            }
        } else // probably <styling> or <layout>
            parser.skipToEndOfTag()
    }
    parser.require(XmlPullParser.END_TAG, tt, "head")
    parser.nextTag()
    parser.require(XmlPullParser.START_TAG, tt, "body")
    val state = TtmlParserState(parser, timer)
    state.parse()
    return SyncedLyrics(state.paragraphs.flatMap {
        /* x-bg can be anywhere in a line, let's split it out into
         * separate lines for now, that looks better */
        if (it.texts.isEmpty()) return@flatMap listOf(it)
        val out = mutableListOf<TtmlParserState.P>()
        var idx = it.texts.indexOfFirst { i -> i.role != it.texts[0].role }
        var cur = 0
        do {
            if (cur == 0 && idx == -1 && !(it.texts.firstOrNull()?.text?.startsWith('(') == true
                        && it.texts.lastOrNull()?.text?.endsWith(')') == true &&
                        (it.texts.firstOrNull()?.role ?: it.role) == "x-bg"))
                out.add(it.copy(role = it.texts.firstOrNull()?.role ?: it.role))
            else {
                val t = it.texts.subList(cur, idx.let { i -> if (i == -1) it.texts.size else i })
                    .toMutableList()
                if (t.firstOrNull()?.text?.startsWith('(') == true
                    && t.lastOrNull()?.text?.endsWith(')') == true) {
                    t[0] = t.first().copy(text = t.first().text.substring(1))
                    t[t.size - 1] = t.last().copy(text = t.last().text.substring(0, t.last().text.length - 1))
                }
                out.add(
                    it.copy(
                        t,
                        time = t.lastOrNull()?.time?.last?.let { other ->
                            t.firstOrNull()?.time?.first?.rangeTo(other)
                        } ?: it.time, role = t.firstOrNull()?.role ?: it.role))
            }
            cur = idx
            if (cur != -1)
                idx = it.texts.subList(cur, it.texts.size)
                    .indexOfFirst { i -> i.role != it.texts[cur].role }
        } while (cur != -1)
        out
    }.map {
        val text = StringBuilder()
        val words = mutableListOf<IntRange>()
        for (i in it.texts) {
            val start = text.length
            text.append(i.text)
            words += start..<text.length
        }
        val theWords = it.texts.mapIndexed { i, it -> it to words[i] }
            .filter { it.first.time != null }
            .map { Word(it.first.time!!, it.second, false) }
            .takeIf { it.isNotEmpty() }
            ?.toMutableList()
        val isBg = it.role == "x-bg"
        val isGroup = peopleToType[it.agent] == "group"
        val isVoice2 = it.agent != null && (people[peopleToType[it.agent]] ?: throw NullPointerException(
            "expected to find ${it.agent} (${peopleToType[it.agent]}) in $people")).indexOf(it.agent) % 2 == 1
        val speaker = when {
            isGroup && isBg -> SpeakerEntity.GroupBackground
            isGroup -> SpeakerEntity.Group
            isVoice2 && isBg -> SpeakerEntity.Voice2Background
            isVoice2 -> SpeakerEntity.Voice2
            isBg -> SpeakerEntity.Background
            else -> SpeakerEntity.Voice1
        }
        // TODO translations for ttml? does anyone actually do that? how could that work?
        LyricLine(text.toString(), it.time.first, it.time.last, theWords, speaker, false)
    }).also { splitBidirectionalWords(it) }
}

@OptIn(UnstableApi::class)
fun parseSrt(lyricText: String, trimEnabled: Boolean): SemanticLyrics? {
    if (!lyricText.startsWith("1\n") && !lyricText.startsWith("1\r")) return null // invalid SubRip
    val cues = mutableListOf<CuesWithTiming>()
    val parser = SubripParser()
    try {
        parser.parse(
            lyricText.toByteArray(),
            SubtitleParser.OutputOptions.allCues()
        ) { cues.add(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse something which looks like SRT: ${Log.getStackTraceString(e)}")
        return null
    }
    var lastTs: ULong? = null
    return SyncedLyrics(cues.map {
        val ts = (it.startTimeUs / 1000).toULong()
        val l = lastTs == ts
        lastTs = ts
        LyricLine(it.cues[0].text!!.toString().let {
            if (trimEnabled)
                it.trim()
            else it
        }, ts, (it.endTimeUs / 1000).toULong(), null, null, l)
    })
}

fun SemanticLyrics?.convertForLegacy(): MutableList<MediaStoreUtils.Lyric>? {
    if (this == null) return null
    if (this is SyncedLyrics) {
        return this.text.map {
            MediaStoreUtils.Lyric(it.start.toLong(), it.text, it.isTranslated)
        }.toMutableList()
    }
    return mutableListOf(MediaStoreUtils.Lyric(null,
        this.unsyncedText.joinToString("\n") { it.first }, false))
}
