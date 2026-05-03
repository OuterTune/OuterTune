package com.dd3boh.outertune.lyrics

import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Apple/iTunes TTML lyrics fetched from lyrics-api.boidu.dev and converts
 * them to Extended LRC format:
 *   [mm:ss.xx] <mm:ss.xx> word1 <mm:ss.xx> word2
 *
 * SemanticLyrics.parseLrc() already handles this format and converts the <timestamp>
 * markers into Word objects, which drives the karaoke animation in Lyrics.kt.
 */
object TTMLParser {

    private const val TAG = "TTMLParser"

    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val words: List<ParsedWord>,
        val backgroundLines: List<ParsedLine> = emptyList()
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean = true
    )

    private data class SpanInfo(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean
    )

    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                runCatching { isExpandEntityReferences = false }
            }
            val doc = factory.newDocumentBuilder().parse(ttml.byteInputStream())
            val root = doc.documentElement

            var globalOffset = 0.0
            findChild(root, "head")?.let { head ->
                findChild(head, "metadata")?.let { meta ->
                    findChild(meta, "audio")?.let { audio ->
                        globalOffset = audio.getAttribute("lyricOffset").toDoubleOrNull() ?: 0.0
                    }
                }
            }

            val body = findChild(root, "body")
            if (body != null) walk(body, lines, globalOffset, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TTML", e)
            return emptyList()
        }
        return lines
    }

    /**
     * Converts parsed lines to Extended LRC.
     * Lines with word timing:  [mm:ss.xx] <mm:ss.xx> word1 <mm:ss.xx> word2
     * Lines without:           [mm:ss.xx] line text
     */
    fun toExtendedLrc(lines: List<ParsedLine>): String {
        val sb = StringBuilder(lines.size * 100)
        for (line in lines) {
            appendLine(sb, line)
            for (bg in line.backgroundLines) appendLine(sb, bg)
        }
        return sb.toString().trimEnd()
    }

    private fun appendLine(sb: StringBuilder, line: ParsedLine) {
        sb.append('[').append(formatTs(line.startTime)).append(']')
        if (line.words.isNotEmpty()) {
            for (word in line.words) {
                sb.append(" <").append(formatTs(word.startTime)).append("> ").append(word.text)
            }
        } else {
            sb.append(' ').append(line.text)
        }
        sb.append('\n')
    }

    private fun formatTs(seconds: Double): String {
        val totalMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val m  = totalMs / 60_000L
        val s  = (totalMs % 60_000L) / 1_000L
        val cs = (totalMs % 1_000L) / 10L
        return buildString(8) {
            if (m < 10) append('0'); append(m); append(':')
            if (s < 10) append('0'); append(s); append('.')
            if (cs < 10) append('0'); append(cs)
        }
    }

    private fun parseTime(time: String): Double {
        val t = time.trim()
        val c1 = t.indexOf(':')
        if (c1 != -1) {
            val c2 = t.lastIndexOf(':')
            return if (c1 == c2) {
                (t.substring(0, c1).toIntOrNull() ?: 0) * 60.0 +
                        (t.substring(c1 + 1).toDoubleOrNull() ?: 0.0)
            } else {
                (t.substring(0, c1).toIntOrNull() ?: 0) * 3600.0 +
                        (t.substring(c1 + 1, c2).toIntOrNull() ?: 0) * 60.0 +
                        (t.substring(c2 + 1).toDoubleOrNull() ?: 0.0)
            }
        }
        if (t.endsWith("ms")) return (t.dropLast(2).toDoubleOrNull() ?: 0.0) / 1000.0
        val s = if (t.endsWith("s") || t.endsWith("m") || t.endsWith("h")) t.dropLast(1) else t
        val v = s.toDoubleOrNull() ?: 0.0
        return when {
            t.endsWith("m") -> v * 60.0
            t.endsWith("h") -> v * 3600.0
            else -> v
        }
    }

    private fun findChild(parent: Element, localName: String): Element? {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == localName) return child
            }
            child = child.nextSibling
        }
        return null
    }

    private fun getAttr(el: Element, localName: String): String {
        val ttm = el.getAttribute("ttm:$localName")
        if (ttm.isNotEmpty()) return ttm
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        return el.getAttributeNS("http://www.w3.org/ns/ttml#metadata", localName)
    }

    private fun timingAttr(el: Element, localName: String): String {
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        return el.getAttributeNS("http://www.w3.org/ns/ttml#parameter", localName)
    }

    private fun walk(element: Element, lines: MutableList<ParsedLine>, offset: Double, parentAgent: String?) {
        val name = element.localName ?: element.nodeName.substringAfterLast(':')
        if (name == "p") {
            parseP(element, lines, offset)
            return
        }
        var child = element.firstChild
        while (child != null) {
            if (child is Element) walk(child, lines, offset, parentAgent)
            child = child.nextSibling
        }
    }

    private fun findFirstSpanBegin(p: Element): String? {
        var child = p.firstChild
        var best: String? = null
        var bestSec = Double.POSITIVE_INFINITY
        while (child != null) {
            if (child is Element) {
                val b = timingAttr(child, "begin")
                if (b.isNotEmpty()) {
                    val s = parseTime(b)
                    if (s < bestSec) { bestSec = s; best = b }
                }
            }
            child = child.nextSibling
        }
        return best
    }

    private fun parseP(p: Element, lines: MutableList<ParsedLine>, offset: Double) {
        val begin = p.getAttribute("begin").ifEmpty { findFirstSpanBegin(p) ?: return }
        val startTime = parseTime(begin) + offset
        val spanInfos = mutableListOf<SpanInfo>()
        val backgroundLines = mutableListOf<ParsedLine>()

        var child = p.firstChild
        while (child != null) {
            if (child is Element) {
                val role = getAttr(child, "role")
                when (role) {
                    "x-bg" -> parseBackgroundSpan(child, startTime, offset)?.let { backgroundLines.add(it) }
                    "x-translation", "x-roman" -> { /* skip */ }
                    else -> parseWordSpan(child, offset, spanInfos, child)
                }
            }
            child = child.nextSibling
        }

        val words = mergeSpansIntoWords(spanInfos)
        val lineText = if (words.isEmpty()) getDirectText(p).trim() else buildLineText(words)

        if (lineText.isNotEmpty()) {
            val bgLines = if (backgroundLines.isNotEmpty()) listOf(
                ParsedLine(
                    text = backgroundLines.joinToString(" ") { it.text },
                    startTime = backgroundLines.minOf { it.startTime },
                    words = backgroundLines.flatMap { it.words },
                    backgroundLines = emptyList()
                )
            ) else emptyList()
            lines.add(ParsedLine(lineText, startTime, words, bgLines))
        }
    }

    private fun parseWordSpan(span: Element, offset: Double, spanInfos: MutableList<SpanInfo>, node: Node) {
        val begin = timingAttr(span, "begin")
        val end   = timingAttr(span, "end")
        if (begin.isNotEmpty() && end.isNotEmpty()) {
            val text = span.textContent ?: ""
            val next = node.nextSibling
            val space = text.lastOrNull()?.isWhitespace() == true ||
                    (next?.nodeType == Node.TEXT_NODE && next.textContent?.firstOrNull()?.isWhitespace() == true)
            spanInfos.add(SpanInfo(text, parseTime(begin) + offset, parseTime(end) + offset, space))
        }
    }

    private fun parseBackgroundSpan(span: Element, parentStart: Double, offset: Double): ParsedLine? {
        val begin = timingAttr(span, "begin")
        val start = if (begin.isNotEmpty()) parseTime(begin) + offset else parentStart
        val spanInfos = mutableListOf<SpanInfo>()
        var hasSpans = false
        var child = span.firstChild
        while (child != null) {
            if (child is Element) {
                hasSpans = true
                val role = getAttr(child, "role")
                if (role != "x-translation" && role != "x-roman") parseWordSpan(child, offset, spanInfos, child)
            }
            child = child.nextSibling
        }
        if (!hasSpans) {
            val text = span.textContent?.trim() ?: ""
            return if (text.isNotEmpty()) ParsedLine(text, start, emptyList()) else null
        }
        val words = mergeSpansIntoWords(spanInfos)
        val text = if (words.isEmpty()) getDirectText(span).trim() else buildLineText(words)
        return if (text.isNotEmpty()) ParsedLine(text, start, words) else null
    }

    private fun getDirectText(el: Element): String {
        val sb = StringBuilder()
        var child = el.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                sb.append(child.textContent)
            } else if (child is Element) {
                val role = getAttr(child, "role")
                if (role != "x-bg" && role != "x-translation" && role != "x-roman") {
                    sb.append(child.textContent)
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun mergeSpansIntoWords(spanInfos: List<SpanInfo>): List<ParsedWord> {
        if (spanInfos.isEmpty()) return emptyList()
        val words = mutableListOf<ParsedWord>()
        var text  = StringBuilder(spanInfos[0].text)
        var start = spanInfos[0].startTime
        var end   = spanInfos[0].endTime

        for (i in 1 until spanInfos.size) {
            val prev = spanInfos[i - 1]
            val curr = spanInfos[i]
            if (prev.hasTrailingSpace && !prev.text.trimEnd().endsWith('-')) {
                words.add(ParsedWord(text.toString(), start, end, true))
                text  = StringBuilder(curr.text)
                start = curr.startTime
                end   = curr.endTime
            } else {
                text.append(curr.text)
                end = curr.endTime
            }
        }
        words.add(ParsedWord(text.toString(), start, end, spanInfos.last().hasTrailingSpace))
        return words.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    }

    private fun buildLineText(words: List<ParsedWord>) = buildString {
        words.forEachIndexed { i, w ->
            append(w.text)
            if (w.hasTrailingSpace && !w.text.endsWith('-') && i < words.lastIndex) append(' ')
        }
    }.trim()
}
