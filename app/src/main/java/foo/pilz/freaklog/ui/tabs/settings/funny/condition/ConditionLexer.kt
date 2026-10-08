package foo.pilz.freaklog.ui.tabs.settings.funny.condition

internal enum class TokType {
    NUMBER, STRING, IDENT,
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA, DOT, ARROW,
    BANG, ANDAND, OROR, EQ, NEQ, LT, LE, GT, GE, IN,
    EOF,
}

internal data class Token(val type: TokType, val text: String)

internal class Lexer(private val src: String) {
    private var i = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> single(tokens, TokType.LPAREN, "(")
                c == ')' -> single(tokens, TokType.RPAREN, ")")
                c == '{' -> single(tokens, TokType.LBRACE, "{")
                c == '}' -> single(tokens, TokType.RBRACE, "}")
                c == ',' -> single(tokens, TokType.COMMA, ",")
                c == '.' -> single(tokens, TokType.DOT, ".")
                c == '"' -> tokens.add(string())
                c == '!' && peek(1) == '=' -> pair(tokens, TokType.NEQ, "!=")
                c == '!' -> single(tokens, TokType.BANG, "!")
                c == '&' && peek(1) == '&' -> pair(tokens, TokType.ANDAND, "&&")
                c == '|' && peek(1) == '|' -> pair(tokens, TokType.OROR, "||")
                c == '=' && peek(1) == '=' -> pair(tokens, TokType.EQ, "==")
                c == '<' && peek(1) == '=' -> pair(tokens, TokType.LE, "<=")
                c == '<' -> single(tokens, TokType.LT, "<")
                c == '>' && peek(1) == '=' -> pair(tokens, TokType.GE, ">=")
                c == '>' -> single(tokens, TokType.GT, ">")
                c == '-' && peek(1) == '>' -> pair(tokens, TokType.ARROW, "->")
                c.isDigit() -> tokens.add(number())
                c.isLetter() || c == '_' -> tokens.add(identifier())
                else -> error("Unexpected character '$c' at index $i")
            }
        }
        tokens.add(Token(TokType.EOF, ""))
        return tokens
    }

    private fun single(tokens: MutableList<Token>, type: TokType, text: String) {
        tokens.add(Token(type, text)); i++
    }

    private fun pair(tokens: MutableList<Token>, type: TokType, text: String) {
        tokens.add(Token(type, text)); i += 2
    }

    private fun peek(offset: Int): Char? = src.getOrNull(i + offset)

    private fun string(): Token {
        i++
        val sb = StringBuilder()
        while (i < src.length && src[i] != '"') {
            sb.append(src[i]); i++
        }
        require(i < src.length) { "Unterminated string literal" }
        i++
        return Token(TokType.STRING, sb.toString())
    }

    private fun number(): Token {
        val start = i
        while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
        return Token(TokType.NUMBER, src.substring(start, i))
    }

    private fun identifier(): Token {
        val start = i
        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
        val text = src.substring(start, i)
        return if (text == "in") Token(TokType.IN, text) else Token(TokType.IDENT, text)
    }
}
