package foo.pilz.freaklog.ui.tabs.settings.funny.condition

class AchievementEngine {
  private val cache = java.util.concurrent.ConcurrentHashMap<String, Compiled>()
  private val compiler = Compiler()

  private fun compiled(condition: String): Compiled =
      cache.getOrPut(condition) { compiler.compile(Parser(Lexer(condition).tokenize()).parse()) }

  fun evaluate(condition: String, context: EvalContext, checkActive: () -> Unit = {}): Boolean =
      asBool(compiled(condition)(Frame(context, emptyMap(), EvaluationBudget(checkActive))))
}
