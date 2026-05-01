package com.ndm4.freakquery

internal typealias Row = Map<String, Any?>
internal typealias MutableRow = MutableMap<String, Any?>
internal typealias Rows = List<Row>

internal object Types {
    const val LOG_ROWS = "log_rows"
    const val GROUP_ROWS = "group_rows"
    const val TEXT_ROWS = "text_rows"
    const val TOTAL_ROWS = "total_rows"
    const val TREND_ROWS = "trend_rows"
    const val SCALAR = "scalar"
    const val ANY = "any"
}

internal data class QueryPlan(
    var group: String? = null,
    var selector: String? = null,
    val filters: MutableList<String> = mutableListOf(),
    val metrics: MutableList<String> = mutableListOf(),
    val formats: MutableList<String> = mutableListOf(),
    val params: MutableMap<String, Any?> = mutableMapOf(),
    val display: MutableMap<String, Any?> = mutableMapOf(
        "dose" to true,
        "unit" to true,
        "route" to false,
        "site" to false,
        "parens" to true,
        "time" to false,
        "labels" to true,
        "count" to false,
        "percent" to true,
        "compact" to false,
        "sep" to ", "
    )
)

internal class Context(
    val logs: Rows,
    val config: FreakQueryConfig,
    val nowMs: Long = System.currentTimeMillis()
) {
    private val memory = mutableMapOf<String, Any?>()

    fun remember(key: String, value: Any?): Any? {
        memory[key] = value
        return value
    }

    fun recall(key: String, default: Any? = null): Any? =
        if (memory.containsKey(key)) memory[key] else default

    fun has(key: String): Boolean = key in memory
}
