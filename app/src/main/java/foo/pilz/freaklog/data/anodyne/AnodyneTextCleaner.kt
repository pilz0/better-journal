package foo.pilz.freaklog.data.anodyne

object AnodyneTextCleaner {
  private val MACRO_PATTERN = Regex("""\{-?\{[\s\S]*?\}-?\}|\{-[^}]*-?\}|\{[^{}]*\}""")

  /** `{xeff 'name'}` or `{wkp 'article' 'shown text'}`: the wiki shows the last argument. */
  private val MACRO_WITH_TEXT = Regex("""\{(\w+)((?:\s+'[^']*')+)\s*\}""")
  private val MACRO_ARG = Regex("""'([^']*)'""")
  private val HTML_TAG_PATTERN = Regex("""<[^>]+>""")
  private val WHITESPACE = Regex("""\s+""")
  private val SUBSCRIPT_MAP =
      mapOf(
          '0' to '₀',
          '1' to '₁',
          '2' to '₂',
          '3' to '₃',
          '4' to '₄',
          '5' to '₅',
          '6' to '₆',
          '7' to '₇',
          '8' to '₈',
          '9' to '₉',
      )

  /** Replaces wiki macros by the text they show; macros without text are dropped. */
  fun expandMacros(text: String): String =
      text
          .replace(MACRO_WITH_TEXT) { m ->
            val args = MACRO_ARG.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
            // {xeff 'teeth-grinding'} shows "teeth grinding"
            if (m.groupValues[1] == "xeff" && args.size == 1) {
              args[0].replace('-', ' ')
            } else {
              args.last()
            }
          }
          // After the arguments: `'cotard{apos}s syndrome'` must stay one quoted argument.
          .replace("{apos}", "'")
          .replace(MACRO_PATTERN, " ")

  fun clean(text: String): String = cleanInline(text).trim()

  private fun cleanInline(text: String): String =
      expandMacros(text)
          .replace(HTML_TAG_PATTERN, "")
          .replace("&nbsp;", " ")
          .replace("&amp;", "&")
          .replace("&lt;", "<")
          .replace("&gt;", ">")
          .replace("&quot;", "\"")
          .replace("&#39;", "'")
          .replace("&apos;", "'")
          .replace(WHITESPACE, " ")

  /**
   * A run of userpage text: a link ([url]; [substanceTitle] too for `{sub 'name'}`, which the app
   * can open itself), a severity such as `{sev 'strong'}`, or plain.
   */
  data class Span(
      val text: String,
      val url: String? = null,
      val isSeverity: Boolean = false,
      val substanceTitle: String? = null,
  )

  // Link targets as anodyne-frontend's "user" template (util.html) builds them
  private val EXTERNAL_LINKS =
      mapOf(
          "wkp" to "https://en.wikipedia.org/wiki/",
          "icd" to "https://icd.who.int/browse10/2019/en#/",
          "xeff" to "https://www.effectindex.com/effects/",
      )
  private val INSTANCE_LINKS =
      mapOf(
          "a" to "/",
          "sub" to "/substance/",
          "roa" to "/administration/",
          "subst" to "/substituted/",
          "class" to "/class/",
          "user" to "/user/",
          "ieff" to "/effect/",
      )

  /**
   * Splits userpage markup into what the wiki shows: `{wkp 'article' 'label'}`, `{icd 'F41.2'}`,
   * `{xeff 'teeth-grinding'}` and the wiki's own links become [Span.url]s, `{sev '…'}` a severity.
   * Joining the texts gives [clean]'s result.
   */
  fun spans(text: String, instanceUrl: String): List<Span> {
    val spans = mutableListOf<Span>()
    var last = 0
    for (m in MACRO_WITH_TEXT.findAll(text)) {
      val tag = m.groupValues[1]
      val prefix = EXTERNAL_LINKS[tag] ?: INSTANCE_LINKS[tag]?.let { instanceUrl.trimEnd('/') + it }
      if (prefix == null && tag != "sev") continue // other macros are left to clean()
      val args =
          MACRO_ARG.findAll(m.groupValues[2])
              .map { it.groupValues[1].replace("{apos}", "'") }
              .toList()
      spans += Span(cleanInline(text.substring(last, m.range.first)))
      val target = args.first().trim()
      val label = if (tag == "xeff" && args.size == 1) target.replace('-', ' ') else args.last()
      spans +=
          if (prefix == null) {
            Span(label, isSeverity = true)
          } else {
            Span(
                label,
                prefix + target.replace(' ', if (tag == "xeff") '-' else '_'),
                substanceTitle = target.takeIf { tag == "sub" },
            )
          }
      last = m.range.last + 1
    }
    spans += Span(cleanInline(text.substring(last)))
    // Trim the whole text like clean() does, then drop the empty runs that leaves
    spans[0] = spans[0].copy(text = spans[0].text.trimStart())
    spans[spans.lastIndex] = spans.last().copy(text = spans.last().text.trimEnd())
    return spans.filter { it.text.isNotEmpty() }
  }

  fun subscript(formula: String): String {
    val result = StringBuilder()
    if (formula.contains("<sub>")) {
      var inSub = false
      var i = 0
      while (i < formula.length) {
        if (formula.startsWith("<sub>", i)) {
          inSub = true
          i += 5
        } else if (formula.startsWith("</sub>", i)) {
          inSub = false
          i += 6
        } else {
          val c = formula[i]
          result.append(if (inSub) SUBSCRIPT_MAP[c] ?: c else c)
          i++
        }
      }
    } else {
      var inNumberRun = false
      var i = 0
      while (i < formula.length) {
        val c = formula[i]
        if (c.isLetter()) {
          inNumberRun = true
          result.append(c)
        } else if (c.isDigit() && inNumberRun) {
          result.append(SUBSCRIPT_MAP[c] ?: c)
        } else {
          inNumberRun = false
          result.append(c)
        }
        i++
      }
    }
    return result.toString()
  }
}
