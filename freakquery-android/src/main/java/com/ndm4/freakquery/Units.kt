package com.ndm4.freakquery

import java.util.Locale

internal object Units {
    private const val MICRO_SIGN = 'µ'
    private const val GREEK_MU = 'μ'

    private val massUnits = setOf("ug", "mcg", "${MICRO_SIGN}g", "mg", "g", "kg")

    // Both the micro sign (U+00B5) and Greek small mu (U+03BC) are typed as "µ"; normalize to the former.
    fun cleanUnit(unit: Any?): String =
        unit?.toString()?.trim()?.lowercase(Locale.ROOT)?.replace(GREEK_MU, MICRO_SIGN).orEmpty()

    fun isMassUnit(unit: Any?): Boolean = cleanUnit(unit) in massUnits

    /** Converts [value] in [unit] to milligrams, or null for invalid, negative or non-mass input. */
    fun toMgOrNull(value: Any?, unit: Any?): Double? {
        val x = value?.toString()?.trim()?.toDoubleOrNull() ?: return null
        if (!x.isFinite() || x < 0.0) return null
        val mg = when (cleanUnit(unit)) {
            "ug", "mcg", "${MICRO_SIGN}g" -> x / 1000.0
            "mg" -> x
            "g" -> x * 1000.0
            "kg" -> x * 1_000_000.0
            else -> return null
        }
        return mg.takeIf { it.isFinite() }
    }

    fun toMg(value: Any?, unit: Any?): Double =
        toMgOrNull(value, unit)
            ?: throw IllegalArgumentException("Invalid mass: $value $unit (normalized unit: '${cleanUnit(unit)}')")
}
