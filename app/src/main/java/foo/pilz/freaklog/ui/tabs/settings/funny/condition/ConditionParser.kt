package foo.pilz.freaklog.ui.tabs.settings.funny.condition

internal class Parser(private val tokens: List<Token>) {
    private var pos = 0

    private val comparisonOps = setOf(
        TokType.EQ, TokType.NEQ, TokType.LT, TokType.LE, TokType.GT, TokType.GE, TokType.IN,
    )

    fun parse(): Expr {
        val expr = parseOr()
        require(peek().type == TokType.EOF) { "Unexpected token '${peek().text}'" }
        return expr
    }

    private fun peek(): Token = tokens[pos]
    private fun advance(): Token = tokens[pos++]
    private fun check(type: TokType): Boolean = peek().type == type
    private fun match(type: TokType): Boolean {
        if (check(type)) {
            pos++; return true
        }
        return false
    }

    private fun expect(type: TokType): Token {
        require(check(type)) { "Expected $type but got '${peek().text}'" }
        return advance()
    }

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (match(TokType.OROR)) left = Binary(TokType.OROR, left, parseAnd())
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseComparison()
        while (match(TokType.ANDAND)) left = Binary(TokType.ANDAND, left, parseComparison())
        return left
    }

    private fun parseComparison(): Expr {
        val left = parseUnary()
        if (peek().type in comparisonOps) {
            val op = advance().type
            return Binary(op, left, parseUnary())
        }
        return left
    }

    private fun parseUnary(): Expr {
        if (match(TokType.BANG)) return Unary(TokType.BANG, parseUnary())
        return parsePostfix()
    }

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()
        while (true) {
            when (peek().type) {
                TokType.DOT -> {
                    advance()
                    val name = expect(TokType.IDENT).text
                    expr = Member(expr, name)
                }

                TokType.LPAREN -> {
                    advance()
                    val args = mutableListOf<Expr>()
                    if (!check(TokType.RPAREN)) {
                        args.add(parseOr())
                        while (match(TokType.COMMA)) args.add(parseOr())
                    }
                    expect(TokType.RPAREN)
                    val lambda = if (check(TokType.LBRACE)) parseLambda() else null
                    expr = Call(expr, args, lambda)
                }

                TokType.LBRACE -> expr = Call(expr, emptyList(), parseLambda())
                else -> break
            }
        }
        return expr
    }

    private fun parseLambda(): Lambda {
        expect(TokType.LBRACE)
        var param = "it"
        if (check(TokType.IDENT) && tokens[pos + 1].type == TokType.ARROW) {
            param = advance().text
            expect(TokType.ARROW)
        }
        val body = parseOr()
        expect(TokType.RBRACE)
        return Lambda(param, body)
    }

    private fun parsePrimary(): Expr {
        val token = peek()
        return when (token.type) {
            TokType.NUMBER -> {
                advance(); NumLit(token.text.toDouble())
            }

            TokType.STRING -> {
                advance(); StrLit(token.text)
            }

            TokType.IDENT -> {
                advance(); Ident(token.text)
            }

            TokType.LPAREN -> {
                advance()
                val expr = parseOr()
                expect(TokType.RPAREN)
                expr
            }

            else -> error("Unexpected token '${token.text}'")
        }
    }
}
