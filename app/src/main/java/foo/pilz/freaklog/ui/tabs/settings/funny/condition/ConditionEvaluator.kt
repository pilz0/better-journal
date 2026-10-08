package foo.pilz.freaklog.ui.tabs.settings.funny.condition

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal sealed interface Value {
    data object Null : Value
    data class Bool(val v: Boolean) : Value
    data class Num(val v: Double) : Value
    data class Str(val v: String) : Value
    data class Sym(val name: String) : Value
    data class RouteV(val v: AdministrationRoute) : Value
    data class DoseV(val v: DoseClass) : Value
    data class IngestionV(val v: Ingestion) : Value
    data class ExperienceV(val v: ExperienceWithIngestions) : Value
    data class Coll(val items: List<Value>) : Value
}

internal class Frame(val ctx: EvalContext, val env: Map<String, Value>)

internal typealias Compiled = (Frame) -> Value

internal fun asBool(value: Value): Boolean = when (value) {
    is Value.Bool -> value.v
    else -> error("Expected a boolean but got $value")
}

internal class Compiler {

    fun compile(expr: Expr): Compiled = when (expr) {
        is NumLit -> constant(Value.Num(expr.value))
        is StrLit -> constant(Value.Str(expr.value))
        is Ident -> compileIdent(expr.name)
        is Unary -> compileUnary(expr)
        is Binary -> compileBinary(expr)
        is Member -> compileMember(expr)
        is Call -> compileCall(expr)
        is Lambda -> { _ -> error("Lambda cannot be evaluated directly") }
    }

    private fun constant(v: Value): Compiled = { _ -> v }

    private fun compileUnary(expr: Unary): Compiled {
        val operand = compile(expr.operand)
        return { f -> Value.Bool(!asBool(operand(f))) }
    }

    private fun compileMember(expr: Member): Compiled {
        val receiver = compile(expr.receiver)
        val name = expr.name
        return { f -> evalMember(receiver(f), name, f.ctx) }
    }

    private fun compileIdent(name: String): Compiled = when (name) {
        "ingestions" ->
            { f -> f.env[name] ?: Value.Coll(f.ctx.ingestions.map { Value.IngestionV(it) }) }

        "experiences" ->
            { f -> f.env[name] ?: Value.Coll(f.ctx.experiences.map { Value.ExperienceV(it) }) }

        else -> { f -> f.env[name] ?: Value.Sym(name) }
    }

    private fun compileBinary(expr: Binary): Compiled {
        val left = compile(expr.left)
        return when (expr.op) {
            TokType.ANDAND -> {
                val right = compile(expr.right)
                val c: Compiled = { f -> Value.Bool(asBool(left(f)) && asBool(right(f))) }
                c
            }

            TokType.OROR -> {
                val right = compile(expr.right)
                val c: Compiled = { f -> Value.Bool(asBool(left(f)) || asBool(right(f))) }
                c
            }

            TokType.EQ -> {
                val right = compile(expr.right)
                val c: Compiled = { f -> Value.Bool(valueEquals(left(f), right(f))) }
                c
            }

            TokType.NEQ -> {
                val right = compile(expr.right)
                val c: Compiled = { f -> Value.Bool(!valueEquals(left(f), right(f))) }
                c
            }

            TokType.IN -> {
                val right = compile(expr.right)
                val c: Compiled = { f ->
                    val l = left(f)
                    val r = right(f)
                    require(r is Value.Coll) { "'in' requires a collection on the right" }
                    Value.Bool(r.items.any { valueEquals(it, l) })
                }
                c
            }

            TokType.LT, TokType.LE, TokType.GT, TokType.GE -> {
                val right = compile(expr.right)
                val op = expr.op
                val c: Compiled = { f ->
                    val cmp = compareValues(left(f), right(f))
                    val result = cmp != null && when (op) {
                        TokType.LT -> cmp < 0
                        TokType.LE -> cmp <= 0
                        TokType.GT -> cmp > 0
                        else -> cmp >= 0
                    }
                    Value.Bool(result)
                }
                c
            }

            else -> { _ -> error("Unsupported operator ${expr.op}") }
        }
    }

    private fun compileCall(call: Call): Compiled {
        val callee = call.callee
        if (callee is Ident && callee.name in BUILTINS) return compileBuiltin(callee.name, call)
        if (callee is Member) return compileMethod(callee, call)
        return { _ -> error("Cannot call $callee") }
    }

    private fun compileBuiltin(name: String, call: Call): Compiled {
        require(call.args.size == 1) { "$name expects one collection argument" }
        val coll = compile(call.args[0])
        if (name == "count") {
            return { f ->
                val c = coll(f)
                require(c is Value.Coll) { "$name expects a collection" }
                Value.Num(c.items.size.toDouble())
            }
        }
        val lambda = call.lambda ?: error("$name requires a lambda")
        val body = compile(lambda.body)
        val param = lambda.param
        return when (name) {
            "any" -> { f ->
                val c = coll(f)
                require(c is Value.Coll) { "$name expects a collection" }
                Value.Bool(c.items.any { asBool(body(Frame(f.ctx, f.env + (param to it)))) })
            }

            "all" -> { f ->
                val c = coll(f)
                require(c is Value.Coll) { "$name expects a collection" }
                Value.Bool(c.items.all { asBool(body(Frame(f.ctx, f.env + (param to it)))) })
            }

            "none" -> { f ->
                val c = coll(f)
                require(c is Value.Coll) { "$name expects a collection" }
                Value.Bool(c.items.none { asBool(body(Frame(f.ctx, f.env + (param to it)))) })
            }

            else -> { _ -> error("Unknown builtin '$name'") }
        }
    }

    private fun compileMethod(callee: Member, call: Call): Compiled {
        val receiver = compile(callee.receiver)
        val name = callee.name
        val argCs = call.args.map { compile(it) }
        val lambda = call.lambda
        val body = lambda?.let { compile(it.body) }
        val param = lambda?.param
        return { f ->
            val recv = receiver(f)
            when {
                recv is Value.Coll && name == "count" -> {
                    val b = body ?: error("count {} requires a lambda")
                    Value.Num(recv.items.count {
                        asBool(b(Frame(f.ctx, f.env + (param!! to it))))
                    }.toDouble())
                }

                recv is Value.Coll && name == "filter" -> {
                    val b = body ?: error("filter {} requires a lambda")
                    Value.Coll(recv.items.filter {
                        asBool(b(Frame(f.ctx, f.env + (param!! to it))))
                    })
                }

                recv is Value.ExperienceV && name == "has" -> {
                    val arg = stringArg(argCs, f)
                    Value.Bool(recv.v.ingestions.any { it.substanceName == arg })
                }

                recv is Value.ExperienceV && name == "hasCategory" -> {
                    val arg = stringArg(argCs, f)
                    Value.Bool(recv.v.ingestions.any {
                        arg in f.ctx.substanceInfo.categories(it.substanceName)
                    })
                }

                recv is Value.ExperienceV && name == "interactionCount" -> {
                    val filter = filterArg(argCs, f)
                    Value.Num(f.ctx.interactionCount(recv.v.experience.id, filter).toDouble())
                }

                else -> error("Unknown method '$name' on $recv")
            }
        }
    }

    private fun stringArg(argCs: List<Compiled>, f: Frame): String {
        val value = argCs.first()(f)
        return (value as? Value.Str)?.v ?: error("Expected a string argument")
    }

    private fun filterArg(argCs: List<Compiled>, f: Frame): InteractionFilter {
        val value = argCs.first()(f)
        val name = (value as? Value.Sym)?.name ?: (value as? Value.Str)?.v
        ?: error("Expected an interaction filter")
        return InteractionFilter.valueOf(name.uppercase())
    }

    private fun evalMember(receiver: Value, name: String, ctx: EvalContext): Value = when (receiver) {
        is Value.IngestionV -> ingestionMember(receiver.v, name, ctx)
        is Value.ExperienceV -> experienceMember(receiver.v, name, ctx)
        is Value.Coll -> collectionMember(receiver, name, ctx)
        else -> error("Cannot access '.$name' on $receiver")
    }

    private fun ingestionMember(i: Ingestion, name: String, ctx: EvalContext): Value = when (name) {
        "substance" -> Value.Str(i.substanceName)
        "route" -> Value.RouteV(i.administrationRoute)
        "dose" -> i.dose?.let { Value.Num(it) } ?: Value.Null
        "units" -> i.units?.let { Value.Str(it) } ?: Value.Null
        "doseLevel" -> ctx.substanceInfo
            .doseClass(i.substanceName, i.administrationRoute, i.dose, i.units)
            ?.let { Value.DoseV(it) } ?: Value.Null

        "categories" -> Value.Coll(
            ctx.substanceInfo.categories(i.substanceName).map { Value.Str(it) })

        "hasNote" -> Value.Bool(!i.notes.isNullOrBlank())
        "note" -> i.notes?.let { Value.Str(it) } ?: Value.Null
        "stomach" -> i.stomachFullness?.let { Value.Str(it.name) } ?: Value.Null
        "consumer" -> i.consumerName?.let { Value.Str(it) } ?: Value.Null
        "hasConsumer" -> Value.Bool(!i.consumerName.isNullOrBlank())
        "isEstimate" -> Value.Bool(i.isDoseAnEstimate)
        "customUnit" -> Value.Bool(i.customUnitId != null)
        "hour" -> Value.Num(i.time.atZone(ctx.zone).hour.toDouble())
        else -> error("Unknown ingestion property '$name'")
    }

    private fun experienceMember(
        e: ExperienceWithIngestions,
        name: String,
        ctx: EvalContext,
    ): Value = when (name) {
        "substances" -> Value.Coll(
            e.ingestions.map { it.substanceName }.distinct().map { Value.Str(it) })

        "categories" -> Value.Coll(
            e.ingestions.flatMap { ctx.substanceInfo.categories(it.substanceName) }
                .distinct().map { Value.Str(it) },
        )

        "ingestions" -> Value.Coll(e.ingestions.map { Value.IngestionV(it) })
        "isFavorite" -> Value.Bool(e.experience.isFavorite)
        "hasLocation" -> Value.Bool(e.experience.location != null)
        else -> error("Unknown experience property '$name'")
    }

    private fun collectionMember(coll: Value.Coll, name: String, ctx: EvalContext): Value =
        when (name) {
            "count" -> Value.Num(coll.items.size.toDouble())
            "substances" -> Value.Coll(
                coll.items.flatMap { substanceNamesOf(it) }.distinct().map { Value.Str(it) })

            "routes" -> Value.Coll(
                coll.items.mapNotNull { (it as? Value.IngestionV)?.v?.administrationRoute }
                    .distinct().map { Value.RouteV(it) },
            )

            "days" -> Value.Coll(daysOf(coll, ctx).map { Value.Str(it.toString()) })
            "streak" -> Value.Num(longestStreak(daysOf(coll, ctx)).toDouble())
            else -> error("Unknown collection property '$name'")
        }

    private fun substanceNamesOf(value: Value): List<String> = when (value) {
        is Value.IngestionV -> listOf(value.v.substanceName)
        is Value.ExperienceV -> value.v.ingestions.map { it.substanceName }
        else -> emptyList()
    }

    private fun daysOf(coll: Value.Coll, ctx: EvalContext): List<LocalDate> = coll.items
        .mapNotNull { (it as? Value.IngestionV)?.v?.time?.atZone(ctx.zone)?.toLocalDate() }
        .distinct()
        .sorted()

    private fun longestStreak(days: List<LocalDate>): Int {
        if (days.isEmpty()) return 0
        var longest = 1
        var current = 1
        for (k in 1 until days.size) {
            if (ChronoUnit.DAYS.between(days[k - 1], days[k]) == 1L) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        return longest
    }

    private fun valueEquals(left: Value, right: Value): Boolean {
        val lr = asRoute(left)
        val rr = asRoute(right)
        if (lr != null && rr != null) return lr == rr
        if (left is Value.DoseV || right is Value.DoseV) {
            val ld = asDose(left)
            val rd = asDose(right)
            if (ld != null && rd != null) return ld == rd
        }
        return when {
            left is Value.Str && right is Value.Str -> left.v == right.v
            left is Value.Num && right is Value.Num -> left.v == right.v
            left is Value.Bool && right is Value.Bool -> left.v == right.v
            else -> false
        }
    }

    private fun compareValues(left: Value, right: Value): Int? {
        if (left is Value.Num && right is Value.Num) return left.v.compareTo(right.v)
        val ld = asDose(left)
        val rd = asDose(right)
        if (ld != null && rd != null) return ld.ordinal.compareTo(rd.ordinal)
        return null
    }

    private fun asRoute(value: Value): AdministrationRoute? = when (value) {
        is Value.RouteV -> value.v
        is Value.Sym -> runCatching { AdministrationRoute.valueOf(value.name.uppercase()) }.getOrNull()
        else -> null
    }

    private fun asDose(value: Value): DoseClass? = when (value) {
        is Value.DoseV -> value.v
        is Value.Sym -> runCatching { DoseClass.valueOf(value.name.uppercase()) }.getOrNull()
        else -> null
    }

    private companion object {
        val BUILTINS = setOf("any", "all", "none", "count")
    }
}
