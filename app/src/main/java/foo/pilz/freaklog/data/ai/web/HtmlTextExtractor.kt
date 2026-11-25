package foo.pilz.freaklog.data.ai.web

import java.util.Locale

data class ExtractedHtml(val title: String, val text: String)

/**
 * Single-pass linear HTML-to-text extractor. Avoids backtracking regexes so malformed or huge HTML
 * inputs cannot stall the parser.
 */
object HtmlTextExtractor {

  private val SKIP_TAGS = setOf("script", "style", "noscript", "svg", "template")
  private val RAW_SKIP_TAGS = setOf("script", "style")
  private val PARAGRAPH_TAGS =
      setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "section", "article")
  private val LINE_BLOCK_TAGS = setOf("div", "li", "tr", "dt", "dd")
  private val BREAK_TAGS = setOf("br", "hr")
  private val CELL_TAGS = setOf("td", "th")
  private val INLINE_WHITESPACE = Regex("""[ \t\u00A0]+""")
  private val MULTI_NEWLINES = Regex("""\n{3,}""")

  private val NAMED_ENTITIES =
      mapOf(
          "amp" to "&",
          "lt" to "<",
          "gt" to ">",
          "quot" to "\"",
          "apos" to "'",
          "nbsp" to " ",
          "ndash" to "–",
          "mdash" to "—",
          "hellip" to "…",
          "lsquo" to "‘",
          "rsquo" to "’",
          "ldquo" to "“",
          "rdquo" to "”",
          "bull" to "•",
          "middot" to "·",
          "times" to "×",
          "deg" to "°",
          "micro" to "µ",
          "mu" to "µ",
          "plusmn" to "±",
      )

  private class ExtractState(capacity: Int) {
    val body = StringBuilder(capacity)
    val titleBuf = StringBuilder()
    var capturedTitle = ""
    var inTitle = false
    var skipTag: String? = null
    var skipDepth = 0
  }

  fun extract(html: String): ExtractedHtml {
    val state = ExtractState(minOf(html.length, 65_536))
    var i = 0
    while (i < html.length) {
      val skipTag = state.skipTag
      i =
          when {
            skipTag != null -> {
              val nextIndex = advanceInsideSkippedTag(html, i, skipTag, state.skipDepth)
              state.skipDepth = nextIndex.second
              if (state.skipDepth == 0) state.skipTag = null
              nextIndex.first
            }
            html[i] == '<' -> processOpenAngle(html, i, state)
            else -> appendPlainRun(html, i, state)
          }
    }
    val finalTitle =
        state.capturedTitle.ifEmpty {
          if (state.inTitle) normalizeInline(decodeEntities(state.titleBuf.toString())) else ""
        }
    val text = normalizeParagraphs(decodeEntities(state.body.toString()))
    return ExtractedHtml(finalTitle, text)
  }

  private fun appendPlainRun(html: String, start: Int, state: ExtractState): Int {
    val nextLt = html.indexOf('<', start).let { if (it < 0) html.length else it }
    if (state.inTitle) {
      state.titleBuf.append(html, start, nextLt)
    } else {
      state.body.append(html, start, nextLt)
    }
    return nextLt
  }

  private fun processOpenAngle(html: String, start: Int, state: ExtractState): Int {
    if (html.startsWith("<!--", start)) {
      val commentEnd = html.indexOf("-->", start + 4)
      return if (commentEnd >= 0) commentEnd + 3 else html.length
    }
    val tagEnd = findTagEnd(html, start)
    if (tagEnd < 0) {
      val nextLt = findNextOpenAngle(html, start + 1)
      val literal = html.substring(start, nextLt)
      if (state.inTitle) state.titleBuf.append(literal) else state.body.append(literal)
      return nextLt
    }
    val parsed = parseTag(html.substring(start + 1, tagEnd))
    if (parsed != null) applyParsedTag(parsed, state)
    return tagEnd + 1
  }

  private fun applyParsedTag(parsed: ParsedTag, state: ExtractState) {
    when {
      !parsed.isClosing && parsed.name in SKIP_TAGS && !parsed.isSelfClosing -> {
        state.skipTag = parsed.name
        state.skipDepth = 1
      }
      parsed.name == "title" -> {
        if (!parsed.isClosing && !parsed.isSelfClosing && state.capturedTitle.isEmpty()) {
          state.titleBuf.setLength(0)
          state.inTitle = true
        } else if (parsed.isClosing && state.inTitle) {
          state.capturedTitle = normalizeInline(decodeEntities(state.titleBuf.toString()))
          state.inTitle = false
        }
      }
      !state.inTitle -> appendSpacing(state.body, parsed)
    }
  }

  /** Strips HTML tags and decodes entities into a single normalized line. */
  internal fun stripTags(html: String): String =
      normalizeInline(extract(html).text.replace('\n', ' '))

  /** Decodes common named, decimal, and hexadecimal HTML entities. */
  internal fun decodeEntities(text: String): String {
    if (!text.contains('&')) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
      if (text[i] == '&') {
        val semi = text.indexOf(';', i + 1)
        if (semi > i + 1 && semi - i <= 12) {
          val decoded = decodeEntity(text.substring(i + 1, semi))
          if (decoded != null) {
            out.append(decoded)
            i = semi + 1
            continue
          }
        }
      }
      out.append(text[i])
      i++
    }
    return out.toString()
  }

  private fun decodeEntity(name: String): String? {
    NAMED_ENTITIES[name.lowercase(Locale.ROOT)]?.let {
      return it
    }
    if (!name.startsWith("#")) return null
    val codePoint =
        if (name.startsWith("#x", ignoreCase = true)) {
          name.substring(2).toIntOrNull(16)
        } else {
          name.substring(1).toIntOrNull(10)
        } ?: return null
    if (codePoint !in 1..Character.MAX_CODE_POINT || codePoint in 0xD800..0xDFFF) return null
    return String(Character.toChars(codePoint))
  }

  private fun advanceInsideSkippedTag(
      html: String,
      start: Int,
      tag: String,
      depth: Int,
  ): Pair<Int, Int> {
    if (tag in RAW_SKIP_TAGS) {
      val close = findClosingTag(html, start, tag)
      return if (close < 0) html.length to 0 else close to 0
    }
    val lt = html.indexOf('<', start)
    if (lt < 0) return html.length to 0
    val gt = findTagEnd(html, lt)
    if (gt < 0) return findNextOpenAngle(html, lt + 1) to depth
    val parsed = parseTag(html.substring(lt + 1, gt))
    val nextDepth =
        when {
          parsed == null || parsed.name != tag -> depth
          parsed.isClosing -> depth - 1
          !parsed.isSelfClosing -> depth + 1
          else -> depth
        }
    return (gt + 1) to nextDepth.coerceAtLeast(0)
  }

  private fun findClosingTag(html: String, start: Int, tag: String): Int {
    var pos = start
    while (pos < html.length) {
      val lt = html.indexOf("</", pos)
      if (lt < 0) return -1
      val nameStart = lt + 2
      val nameEnd = nameStart + tag.length
      if (
          nameEnd <= html.length &&
              html.regionMatches(nameStart, tag, 0, tag.length, ignoreCase = true)
      ) {
        val afterName = html.getOrNull(nameEnd)
        if (afterName == '>' || afterName?.isWhitespace() == true) {
          val gt = html.indexOf('>', nameEnd)
          return if (gt < 0) html.length else gt + 1
        }
      }
      pos = nameStart
    }
    return -1
  }

  private fun findTagEnd(html: String, openIndex: Int): Int {
    val first = html.getOrNull(openIndex + 1) ?: return -1
    if (!(first.isLetter() || first == '/' || first == '!' || first == '?')) return -1
    var j = openIndex + 1
    while (j < html.length) {
      when (html[j]) {
        '>' -> return j
        '<' -> return -1
      }
      j++
    }
    return -1
  }

  private fun findNextOpenAngle(html: String, fromIndex: Int): Int {
    val next = html.indexOf('<', fromIndex)
    return if (next < 0) html.length else next
  }

  private data class ParsedTag(val name: String, val isClosing: Boolean, val isSelfClosing: Boolean)

  private fun appendSpacing(body: StringBuilder, tag: ParsedTag) {
    when {
      tag.name in BREAK_TAGS -> body.append('\n')
      tag.name in PARAGRAPH_TAGS -> {
        if (!tag.isClosing && body.isNotEmpty() && !body.endsWith("\n\n")) {
          if (!body.endsWith('\n')) body.append('\n')
          body.append('\n')
        } else if (tag.isClosing && body.isNotEmpty() && !body.endsWith('\n')) {
          body.append('\n')
        }
      }
      tag.name in LINE_BLOCK_TAGS -> {
        if (body.isNotEmpty() && !body.endsWith('\n')) body.append('\n')
      }
      tag.name in CELL_TAGS -> body.append(' ')
    }
  }

  private fun parseTag(raw: String): ParsedTag? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("?")) return null
    val isClosing = trimmed.startsWith('/')
    val body = if (isClosing) trimmed.substring(1).trimStart() else trimmed
    var end = 0
    while (end < body.length && !body[end].isWhitespace() && body[end] != '/') end++
    if (end == 0) return null
    val name = body.substring(0, end).lowercase(Locale.ROOT)
    val isSelfClosing = !isClosing && trimmed.endsWith('/')
    return ParsedTag(name, isClosing, isSelfClosing)
  }

  private fun normalizeInline(text: String): String = INLINE_WHITESPACE.replace(text, " ").trim()

  private fun normalizeParagraphs(text: String): String {
    val cleanedLines =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { line ->
          INLINE_WHITESPACE.replace(line, " ").trim()
        }
    return MULTI_NEWLINES.replace(cleanedLines.joinToString("\n"), "\n\n").trim()
  }
}
