package foo.pilz.freaklog.ui.utils

fun evaluateNumericExpression(input: String): Double? {
    val text = input.trim().replace(',', '.')
    if (text.isEmpty()) return null
    text.toDoubleOrNull()?.let { return it }
    return try {
        val parser = ExpressionParser(text)
        val result = parser.parseExpression()
        if (parser.isAtEnd && !result.isNaN() && !result.isInfinite()) result else null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private class ExpressionParser(private val text: String) {
    private var index = 0
    val isAtEnd: Boolean get() = index >= text.length

    fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipWhitespace()
            when (currentChar()) {
                '+' -> {
                    index++
                    value += parseTerm()
                }

                '-' -> {
                    index++
                    value -= parseTerm()
                }

                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (true) {
            skipWhitespace()
            when (currentChar()) {
                '*' -> {
                    index++
                    value *= parseFactor()
                }

                '/' -> {
                    index++
                    value /= parseFactor()
                }

                else -> return value
            }
        }
    }

    private fun parseFactor(): Double {
        skipWhitespace()
        when (currentChar()) {
            '(' -> {
                index++
                val value = parseExpression()
                skipWhitespace()
                require(currentChar() == ')') { "Expected closing parenthesis" }
                index++
                return value
            }

            '-' -> {
                index++
                return -parseFactor()
            }

            '+' -> {
                index++
                return parseFactor()
            }
        }
        val start = index
        while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
        require(start != index) { "Expected number" }
        return requireNotNull(text.substring(start, index).toDoubleOrNull()) { "Invalid number" }
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun currentChar(): Char = if (index < text.length) text[index] else ' '
}
