package foo.pilz.freaklog.ui.tabs.settings.funny.condition

class AchievementEngine {
    private class Entry(val compiled: Compiled, val monotonic: Boolean)

    private val cache = HashMap<String, Entry>()
    private val compiler = Compiler()

    private fun entryFor(condition: String): Entry = cache.getOrPut(condition) {
        val expr = Parser(Lexer(condition).tokenize()).parse()
        Entry(compiler.compile(expr), conditionMonotonic(expr))
    }

    fun evaluate(condition: String, context: EvalContext): Boolean =
        asBool(entryFor(condition).compiled(Frame(context, emptyMap())))

    fun isMonotonic(condition: String): Boolean = entryFor(condition).monotonic
}
