package com.ndm4.freakquery

data class FreakQueryConfig(
    val version: String = "3.2.1",
    val defaultFormat: String = "text",
    val renderSeparator: String = ", ",
    val renderParens: Boolean = true,
    val renderDose: Boolean = true,
    val renderUnit: Boolean = true,
    val renderLabels: Boolean = true,
    val renderCount: Boolean = false,
    val renderPercent: Boolean = true,
    val renderCompact: Boolean = false,
    val renderTimeEnabled: Boolean = false,
    val renderTimeFormat: String? = null,
    val ratioUnderOne: String = "<1%",
    val queryReverse: Boolean = false,
    val bingeGapHours: Int = 8,
    val limits: Map<String, Int> = mapOf(
        "default" to 10,
        "rows" to 100,
        "top_substances" to 10,
        "top_routes" to 10,
        "ratio" to 8,
        "sites" to 10,
        "binges" to 5,
        "streaks" to 5
    ),
    val fieldNames: Map<String, List<String>> = mapOf(
        "substance" to listOf("substance", "substanceName", "name", "drug", "compound"),
        "route" to listOf("route", "roa", "administrationRoute", "adminRoute"),
        "site" to listOf("site", "administrationSite", "bodySite", "siteName"),
        "dose" to listOf("dose", "amount", "qty", "quantity"),
        "unit" to listOf("unit", "units", "measurement"),
        "time" to listOf("time", "creationDate", "timestamp", "date")
    ),
    val aliases: Map<String, Map<String, String>> = DefaultAliases.all
) {
    fun limit(key: String): Int? = limits[key]
}
