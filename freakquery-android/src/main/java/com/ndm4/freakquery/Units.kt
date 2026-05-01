package com.ndm4.freakquery

import java.util.Locale

internal object Units {
    private val massUnits = setOf("ug", "mcg", "μg", "µg", "mg", "g", "kg")

    fun cleanUnit(unit: Any?): String = unit?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()

    fun isMassUnit(unit: Any?): Boolean = cleanUnit(unit) in massUnits

    fun toMg(value: Any?, unit: Any?): Double {
        val x = value?.toString()?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid numeric value: $value")
        return when (val cleaned = cleanUnit(unit)) {
            "ug", "mcg", "μg", "µg" -> x / 1000.0
            "mg" -> x
            "g" -> x * 1000.0
            "kg" -> x * 1_000_000.0
            else -> throw IllegalArgumentException("Unknown mass unit: $unit (normalized: '$cleaned')")
        }
    }
}
