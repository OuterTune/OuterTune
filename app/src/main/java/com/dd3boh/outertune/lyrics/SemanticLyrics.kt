package com.dd3boh.outertune.lyrics

import com.dd3boh.outertune.lyrics.SemanticLyrics.LyricLine
import com.dd3boh.outertune.lyrics.SemanticLyrics.LyricLineHolder
import com.dd3boh.outertune.lyrics.SemanticLyrics.SyncedLyrics
import com.dd3boh.outertune.lyrics.SemanticLyrics.UnsyncedLyrics
import com.dd3boh.outertune.lyrics.SemanticLyrics.Word
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.map

/**
 * Unless otherwise stated, this file is From Gramophone as of 0d46996db54a3360e832ed06bf18170bce85534f
 * This has been adapted for OuterTune
 * https://github.com/AkaneTan/Gramophone/blob/beta/app/src/main/kotlin/org/akanework/gramophone/logic/utils/SemanticLyrics.kt
 */

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

//@Parcelize
enum class SpeakerEntity(val isWalaoke: Boolean, val isVoice2: Boolean = false, val isBackground: Boolean = false) {
    Male(true), // Walaoke
    Female(true), // Walaoke
    Duet(true), // Walaoke
    Background(false, isBackground = true), // iTunes
    Voice1(false), // iTunes
    Voice2(false, isVoice2 = true), // iTunes
    Voice2Background(false, isVoice2 = true, isBackground = true) // iTunes
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
        val timeMarksRegex = "\\[(\\d{2}):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeMarksAfterWsRegex = "([ \t]+)\\[(\\d{2}):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeWordMarksRegex = "<(\\d{2}):(\\d{2})([.:]\\d+)?>".toRegex()
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
                    if (!(out.lastOrNull() is NewLine || out.lastOrNull() is SyncPoint))
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
                    val lastWasV2 = out.isNotEmpty() && out.subList(0, out.size - 1)
                        .indexOfLast { it is NewLine }.let { if (it < 0) null else it }?.let {
                            (out.subList(it, out.size - 1).findLast { it is SpeakerTag }
                                    as SpeakerTag?)?.speaker?.isVoice2
                        } == true
                    // TODO revisit this heuristic. iTunes bg lines are a child of the last main
                    //  line and there is no "opposite" flag for background lines, but reality does
                    //  not work that way. can main lines be empty in iTunes' lyrics?
                    out.add(SpeakerTag(if (lastWasV2) SpeakerEntity.Voice2Background else
                        SpeakerEntity.Background))
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
    abstract val unsyncedText: List<String>

    class UnsyncedLyrics(override val unsyncedText: List<String>) : SemanticLyrics()

    class SyncedLyrics(val text: List<LyricLineHolder>) : SemanticLyrics() {
        override val unsyncedText: List<String>
            get() = text.map { it.lyric.text }
    }

    data class LyricLine(
        val text: String,
        val start: ULong,
        val words: List<Word>?,
        val speaker: SpeakerEntity?
    )

    data class LyricLineHolder(
        val lyric: LyricLine,
        val isTranslated: Boolean
    )

    data class Word(
        val timeRange: ULongRange,
        val charRange: IntRange,
        val isRtl: Boolean
    )
}

fun parseLrc(lyricText: String, trimEnabled: Boolean, multiLineEnabled: Boolean): SemanticLyrics? {
    val lyricSyntax = SyntacticLrc.parseLrc(lyricText, multiLineEnabled)
        ?: return null
    if (lyricSyntax.find { it !is SyntacticLrc.InvalidText } == null)
        return UnsyncedLyrics(lyricSyntax.map { (it as SyntacticLrc.InvalidText).text })
    // Synced lyrics processing state machine starts here
    val out = mutableListOf<LyricLine>()
    var offset = 0L
    var lastSyncPoint: ULong? = null
    var lastWordSyncPoint: ULong? = null
    var speaker: SpeakerEntity? = null
    var hadLyricSinceWordSync = true
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
                    // if there's something in compressed at this point, would it be a bug?
                    compressed.clear()
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
            }

            element is SyntacticLrc.LyricText -> {
                hadLyricSinceWordSync = true
                currentLine.add(Pair(lastWordSyncPoint ?: lastSyncPoint!!, element.text))
            }

            element is SyntacticLrc.NewLine -> {
                var words = if (currentLine.size > 1) {
                    val wout = mutableListOf<Word>()
                    var idx = 0
                    for (i in currentLine.indices) {
                        val current = currentLine[i]
                        if (current.second == null)
                            continue // skip dummy words that only exist to provide time
                        val oIdx = idx
                        idx += current.second?.length ?: 0
                        val endInclusive = if (i + 1 < currentLine.size) {
                            // If we have a next word (with sync point), use its sync
                            // point minus 1ms as end point of this word
                            currentLine[i + 1].first - 1uL
                        } else if (lastWordSyncPoint != null &&
                            lastWordSyncPoint > current.first
                        ) {
                            // If we have a dedicated sync point just for the last word,
                            // use it. Similar to dummy words but for the last word only
                            lastWordSyncPoint
                        } else {
                            // Estimate how long this word will take based on character
                            // to time ratio. To avoid this estimation, add a last word
                            // sync point to the line after the text :)
                            current.first + (wout.map {
                                it.timeRange.count() /
                                        it.charRange.count().toFloat()
                            }.average()
                                .let { if (it.isNaN()) 100.0 else it } *
                                    (current.second?.length ?: 0)).toULong()
                        }
                        if (endInclusive > current.first)
                        // isRtl is filled in later in splitBidirectionalWords
                            wout.add(Word(current.first..endInclusive, oIdx..<idx, isRtl = false))
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
                        words = words?.flatMap {
                            if (it.charRange.last.toLong() - startDiff < 0
                                || it.charRange.first.toLong() - startDiff >= text.length
                            )
                                listOf()
                            else
                                listOf(
                                    it.copy(
                                        charRange = (it.charRange.first - startDiff)
                                            .coerceAtLeast(0)..(it.charRange.last - startDiff)
                                            .coerceAtMost(text.length - 1)
                                    )
                                )
                        }?.toMutableList()
                    }
                    val start = if (currentLine.isNotEmpty()) currentLine.first().first
                    else lastWordSyncPoint ?: lastSyncPoint!!
                    out.add(LyricLine(text, start, words, speaker))
                    compressed.forEach {
                        val diff = it - start
                        out.add(out.last().copy(start = it, words = words?.map {
                            it.copy(
                                it.timeRange.start + diff..it.timeRange.last + diff
                            )
                        }))
                    }
                }
                compressed.clear()
                currentLine.clear()
                lastSyncPoint = null
                lastWordSyncPoint = null
                // Walaoke extension speakers stick around unless another speaker is
                // specified. (The default speaker - before one is chosen - is male.)
                if (speaker?.isWalaoke != true)
                    speaker = null
                hadLyricSinceWordSync = true
            }
        }
    }
    out.sortBy { it.start }
    var sawNonBlank = false
    var previousTimestamp = 0uL
    val defaultIsWalaokeM = out.find { it.speaker?.isWalaoke == true } != null &&
            out.find { it.speaker?.isWalaoke == false } == null
    return SyncedLyrics(out.flatMap {
        if (sawNonBlank || it.text.isNotBlank()) {
            sawNonBlank = true
            listOf(it)
        } else listOf()
    }.map {
        if (defaultIsWalaokeM && it.speaker == null)
            it.copy(speaker = SpeakerEntity.Male)
        else it
    }.map {
        LyricLineHolder(it, it.start == previousTimestamp).also {
            previousTimestamp = it.lyric.start
        }
    })
}

fun parseTtml(lyricText: String, trimEnabled: Boolean): SemanticLyrics? {
    // TODO TTML support (and add unit tests for it)
    return null
}

fun parseSrt(lyricText: String, trimEnabled: Boolean): SemanticLyrics? {
    // TODO SRT support (and add unit tests for it)
    return null
}

fun SemanticLyrics?.convertForLegacy(): List<LyricsEntry> {
    if (this == null) return emptyList()
    if (this is SyncedLyrics) {
        return this.text.map {
            LyricsEntry(it.lyric.start.toLong(), it.lyric.text, it.isTranslated)
        }.toMutableList()
    }
    return mutableListOf(LyricsEntry(-1, this.unsyncedText.joinToString("\n"), false))
}
