/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.vitals

const val MIN_SYSTOLIC = 20
const val MAX_SYSTOLIC = 300
const val MIN_DIASTOLIC = 10
const val MAX_DIASTOLIC = 200
const val MIN_PULSE = 20
const val MAX_PULSE = 300

/** Per-field messages for the blood pressure form; null means the field is fine (or still empty). */
data class BloodPressureFieldErrors(val systolic: String?, val diastolic: String?, val pulse: String?)

private fun rangeError(text: String, min: Int, max: Int, unit: String): String? {
    val value = text.trim().toIntOrNull()
    return when {
        text.isBlank() -> null
        value == null -> "Enter a whole number"
        value !in min..max -> "Must be between $min and $max $unit"
        else -> null
    }
}

fun bloodPressureFieldErrors(systolic: String, diastolic: String, pulse: String): BloodPressureFieldErrors {
    val sys = systolic.trim().toIntOrNull()
    val dia = diastolic.trim().toIntOrNull()
    val systolicError = rangeError(systolic, MIN_SYSTOLIC, MAX_SYSTOLIC, "mmHg")
        ?: "Must be above diastolic".takeIf { sys != null && dia != null && sys <= dia }
    return BloodPressureFieldErrors(
        systolic = systolicError,
        diastolic = rangeError(diastolic, MIN_DIASTOLIC, MAX_DIASTOLIC, "mmHg"),
        pulse = rangeError(pulse, MIN_PULSE, MAX_PULSE, "bpm"),
    )
}

/** Systolic and diastolic are required; pulse is optional but must be valid when given. */
fun isBloodPressureInputValid(systolic: String, diastolic: String, pulse: String): Boolean {
    val errors = bloodPressureFieldErrors(systolic, diastolic, pulse)
    return systolic.isNotBlank() && diastolic.isNotBlank() &&
        errors.systolic == null && errors.diastolic == null && errors.pulse == null
}

/** Rough category after the 2017 ACC/AHA thresholds; informational only. */
fun bloodPressureCategory(systolic: Int, diastolic: Int): String = when {
    systolic >= CRISIS_SYSTOLIC || diastolic >= CRISIS_DIASTOLIC -> "Very high"
    systolic >= STAGE_2_SYSTOLIC || diastolic >= STAGE_2_DIASTOLIC -> "High"
    systolic >= STAGE_1_SYSTOLIC || diastolic >= STAGE_1_DIASTOLIC -> "Elevated (stage 1)"
    systolic >= ELEVATED_SYSTOLIC -> "Elevated"
    systolic < LOW_SYSTOLIC || diastolic < LOW_DIASTOLIC -> "Low"
    else -> "Normal"
}

private const val CRISIS_SYSTOLIC = 180
private const val CRISIS_DIASTOLIC = 120
private const val STAGE_2_SYSTOLIC = 140
private const val STAGE_2_DIASTOLIC = 90
private const val STAGE_1_SYSTOLIC = 130
private const val STAGE_1_DIASTOLIC = 80
private const val ELEVATED_SYSTOLIC = 120
private const val LOW_SYSTOLIC = 90
private const val LOW_DIASTOLIC = 60
