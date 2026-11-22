package foo.pilz.freaklog.data.anodyne

object AnodyneTextCleaner {
    private val MACRO_PATTERN = Regex("""\{-?\{[\s\S]*?\}-?\}|\{-[^}]*-?\}|\{[^{}]*\}""")
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

    fun clean(text: String): String {
        return text.replace(MACRO_PATTERN, "")
            .replace(HTML_TAG_PATTERN, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(WHITESPACE, " ")
            .trim()
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
