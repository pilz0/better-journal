package com.ndm4.freakquery

import java.text.Normalizer
import java.util.Locale

object Aliases {
    fun norm(value: Any?): String {
        if (value == null) return ""
        var s = value.toString().trim().lowercase(Locale.ROOT)
        s = Normalizer.normalize(s, Normalizer.Form.NFKD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        val punctuation = charArrayOf('_', '-', '.', ',', ';', ':', '/', '\\', '|', '(', ')', '[', ']', '{', '}')
        punctuation.forEach { s = s.replace(it, ' ') }
        return s.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun fieldKeys(config: FreakQueryConfig, key: String): List<String> {
        val wanted = norm(key)
        for ((canonical, values) in config.fieldNames) {
            val names = if (canonical in values) values else listOf(canonical) + values
            if (names.any { norm(it) == wanted }) return names
        }
        return listOf(key)
    }

    fun aliasMap(config: FreakQueryConfig, field: String): Map<String, String> =
        config.aliases[norm(field)].orEmpty()

    fun canonicalValue(config: FreakQueryConfig, field: String, value: Any?): String {
        val raw = value?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return raw
        return aliasMap(config, field)[norm(raw)] ?: raw
    }

    fun sameValue(config: FreakQueryConfig, field: String, a: Any?, b: Any?): Boolean =
        canonicalValue(config, field, a) == canonicalValue(config, field, b)

    fun displayValue(config: FreakQueryConfig, field: String, value: Any?): String =
        canonicalValue(config, field, value)

    fun rowGet(config: FreakQueryConfig, row: Row, key: String): Any? {
        for (wanted in fieldKeys(config, key)) {
            val nw = norm(wanted)
            for (real in row.keys) {
                if (norm(real) == nw) return row[real]
            }
        }
        return null
    }
}
