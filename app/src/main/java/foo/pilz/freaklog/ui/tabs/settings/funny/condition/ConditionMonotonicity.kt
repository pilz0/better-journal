package foo.pilz.freaklog.ui.tabs.settings.funny.condition

/**
 * Whether a condition's truth value can only go false -> true as the dataset grows
 * (experiences / ingestions added over time). Monotonic conditions let attribution
 * binary-search the first satisfying prefix instead of scanning linearly.
 *
 * Conservative: returns false whenever monotonicity cannot be proven, so a linear
 * scan (always correct) is used as the fallback.
 */
internal fun conditionMonotonic(expr: Expr): Boolean {
    val p = polarity(expr, emptySet())
    return p == Pol.UP || p == Pol.CONST
}

/** How a node's value moves as the dataset grows. */
private enum class Pol { CONST, UP, DOWN, UNKNOWN }

private val BUILTINS = setOf("any", "all", "none", "count")

/**
 * [bound] holds identifiers bound to a lambda parameter. A parameter refers to a single
 * fixed item, so anything derived from it is CONST: prefixes only add whole experiences,
 * never mutate an already-included one. Only the global `ingestions` / `experiences`
 * collections grow.
 */
private fun polarity(expr: Expr, bound: Set<String>): Pol = when (expr) {
    is NumLit, is StrLit -> Pol.CONST
    is Ident -> when {
        expr.name in bound -> Pol.CONST
        expr.name == "ingestions" || expr.name == "experiences" -> Pol.UP
        else -> Pol.CONST
    }

    is Unary -> flip(polarity(expr.operand, bound))
    is Binary -> binaryPolarity(expr, bound)
    is Member -> memberPolarity(polarity(expr.receiver, bound), expr.name)
    is Call -> callPolarity(expr, bound)
    is Lambda -> Pol.CONST
}

private fun memberPolarity(receiver: Pol, name: String): Pol = when (name) {
    // numeric reductions and sub-collections grow with their source collection
    "count", "streak", "substances", "routes", "days", "categories", "ingestions" -> receiver
    // item-local scalar properties (substance, dose, doseLevel, hour, route, ...)
    else -> Pol.CONST
}

private fun callPolarity(call: Call, bound: Set<String>): Pol {
    val callee = call.callee
    if (callee is Ident && callee.name in BUILTINS) {
        return when (callee.name) {
            "any" -> {
                val coll = call.args.firstOrNull()?.let { polarity(it, bound) } ?: Pol.UNKNOWN
                val body = call.lambda?.let { polarity(it.body, bound + it.param) } ?: Pol.CONST
                anyPolarity(coll, body)
            }

            "count" -> call.args.firstOrNull()?.let { polarity(it, bound) } ?: Pol.UNKNOWN
            else -> Pol.UNKNOWN // all / none can flip true -> false when items are added
        }
    }
    if (callee is Member) {
        val receiver = polarity(callee.receiver, bound)
        return when (callee.name) {
            // The matching subset grows with the receiver only if the predicate is itself
            // non-decreasing; a predicate that references the growing global can drop items.
            "count", "filter" -> {
                val body = call.lambda?.let { polarity(it.body, bound + it.param) } ?: Pol.CONST
                if (body.nonDecreasing()) receiver else Pol.UNKNOWN
            }

            else -> Pol.CONST // has / hasCategory / interactionCount on a fixed item
        }
    }
    return Pol.UNKNOWN
}

private fun binaryPolarity(b: Binary, bound: Set<String>): Pol {
    val l = polarity(b.left, bound)
    val r = polarity(b.right, bound)
    return when (b.op) {
        TokType.ANDAND, TokType.OROR -> andOrPolarity(l, r)
        TokType.GT, TokType.GE -> comparePolarity(l, r)
        TokType.LT, TokType.LE -> comparePolarity(r, l)
        TokType.EQ, TokType.NEQ -> if (l == Pol.CONST && r == Pol.CONST) Pol.CONST else Pol.UNKNOWN
        TokType.IN -> inPolarity(l, r)
        else -> Pol.UNKNOWN
    }
}

// any(coll) { body }: non-decreasing if the collection and the per-item body are non-decreasing
private fun anyPolarity(coll: Pol, body: Pol): Pol = when {
    coll == Pol.CONST && body == Pol.CONST -> Pol.CONST
    coll.nonDecreasing() && body.nonDecreasing() -> Pol.UP
    else -> Pol.UNKNOWN
}

private fun andOrPolarity(l: Pol, r: Pol): Pol = when {
    l == Pol.CONST && r == Pol.CONST -> Pol.CONST
    l.nonDecreasing() && r.nonDecreasing() -> Pol.UP
    else -> Pol.UNKNOWN
}

// a (> | >=) b: non-decreasing when a is non-decreasing and b is non-increasing
private fun comparePolarity(a: Pol, b: Pol): Pol = when {
    a == Pol.CONST && b == Pol.CONST -> Pol.CONST
    a.nonDecreasing() && b.nonIncreasing() -> Pol.UP
    a.nonIncreasing() && b.nonDecreasing() -> Pol.DOWN
    else -> Pol.UNKNOWN
}

// x in coll: membership in a growing collection only flips false -> true
private fun inPolarity(left: Pol, right: Pol): Pol = when {
    left == Pol.CONST && right == Pol.CONST -> Pol.CONST
    right == Pol.UP && left.nonDecreasing() -> Pol.UP
    else -> Pol.UNKNOWN
}

private fun flip(p: Pol): Pol = when (p) {
    Pol.UP -> Pol.DOWN
    Pol.DOWN -> Pol.UP
    else -> p
}

private fun Pol.nonDecreasing(): Boolean = this == Pol.UP || this == Pol.CONST
private fun Pol.nonIncreasing(): Boolean = this == Pol.DOWN || this == Pol.CONST
