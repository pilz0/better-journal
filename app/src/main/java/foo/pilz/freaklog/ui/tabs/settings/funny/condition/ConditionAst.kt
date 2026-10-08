package foo.pilz.freaklog.ui.tabs.settings.funny.condition

internal sealed interface Expr

internal data class NumLit(val value: Double) : Expr
internal data class StrLit(val value: String) : Expr
internal data class Ident(val name: String) : Expr
internal data class Unary(val op: TokType, val operand: Expr) : Expr
internal data class Binary(val op: TokType, val left: Expr, val right: Expr) : Expr
internal data class Member(val receiver: Expr, val name: String) : Expr
internal data class Lambda(val param: String, val body: Expr) : Expr
internal data class Call(val callee: Expr, val args: List<Expr>, val lambda: Lambda?) : Expr
