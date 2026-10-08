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

package foo.pilz.freaklog.ui.tabs.search.custom.profile

import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.relations.CustomRoaInfo
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.utils.evaluateNumericExpression

/** The text typed for one duration phase, e.g. "20" – "40" minutes. */
data class DurationDraft(
    val min: String = "",
    val max: String = "",
    val units: DurationUnits = DurationUnits.MINUTES,
) {
    val isBlank get() = min.isBlank() && max.isBlank()

    /** A lone value is used for both ends; the range is null when empty or not a positive, ordered range. */
    fun toRange(): DurationRange? {
        val low = evaluateNumericExpression(min.ifBlank { max })?.toFloat() ?: return null
        val high = evaluateNumericExpression(max.ifBlank { min })?.toFloat() ?: return null
        return if (low < 0f || high < low) null else DurationRange(low, high, units)
    }

    val isValid get() = isBlank || toRange() != null

    companion object {
        fun from(range: DurationRange?) = if (range == null) {
            DurationDraft()
        } else {
            DurationDraft(
                min = range.min?.toDouble()?.toReadableString().orEmpty(),
                max = range.max?.toDouble()?.toReadableString().orEmpty(),
                units = range.units ?: DurationUnits.MINUTES,
            )
        }
    }
}

/** Editable text state for everything stored about one route of a custom substance. */
data class CustomRouteDraft(
    val route: AdministrationRoute,
    val doseUnits: String = "",
    val lightMin: String = "",
    val commonMin: String = "",
    val strongMin: String = "",
    val heavyMin: String = "",
    val onset: DurationDraft = DurationDraft(),
    val comeup: DurationDraft = DurationDraft(),
    val peak: DurationDraft = DurationDraft(),
    val offset: DurationDraft = DurationDraft(),
    val total: DurationDraft = DurationDraft(units = DurationUnits.HOURS),
    val bioavailabilityMin: String = "",
    val bioavailabilityMax: String = "",
) {
    private val thresholds get() = listOf(lightMin, commonMin, strongMin, heavyMin)
    private val durations get() = listOf(onset, comeup, peak, offset, total)

    /** Thresholds must be numbers and, where given, increase from light to heavy. */
    val areDosesValid: Boolean
        get() {
            val values = thresholds.filter { it.isNotBlank() }.map { evaluateNumericExpression(it) }
            return values.none { it == null || it < 0 } && values.filterNotNull().zipWithNext().all { (a, b) -> a <= b }
        }

    val areDurationsValid get() = durations.all { it.isValid }

    val isBioavailabilityValid: Boolean
        get() {
            val min = bioavailabilityMin.takeIf { it.isNotBlank() }?.let(::evaluateNumericExpression)
            val max = bioavailabilityMax.takeIf { it.isNotBlank() }?.let(::evaluateNumericExpression)
            val bothParse = (bioavailabilityMin.isBlank() || min != null) && (bioavailabilityMax.isBlank() || max != null)
            val inRange = listOfNotNull(min, max).all { it in 0.0..MAX_PERCENT }
            return bothParse && inRange && (min == null || max == null || min <= max)
        }

    val isValid get() = areDosesValid && areDurationsValid && isBioavailabilityValid

    fun toRoa() = CustomRoa(
        route = route,
        bioavailability = if (bioavailabilityMin.isBlank() && bioavailabilityMax.isBlank()) {
            null
        } else {
            Bioavailability(
                min = evaluateNumericExpression(bioavailabilityMin.ifBlank { bioavailabilityMax }),
                max = evaluateNumericExpression(bioavailabilityMax.ifBlank { bioavailabilityMin }),
            )
        },
    )

    /** Null when no threshold was entered, so the route keeps "no dose information". */
    fun toDose(): CustomRoaDose? =
        if (thresholds.all { it.isBlank() }) {
            null
        } else {
            CustomRoaDose(
                route = route,
                units = doseUnits.trim().ifBlank { null },
                lightMin = evaluateNumericExpression(lightMin),
                commonMin = evaluateNumericExpression(commonMin),
                strongMin = evaluateNumericExpression(strongMin),
                heavyMin = evaluateNumericExpression(heavyMin),
            )
        }

    fun toDuration(): CustomRoaDuration? =
        if (durations.all { it.isBlank }) {
            null
        } else {
            CustomRoaDuration(
                route = route,
                onset = onset.toRange(),
                comeup = comeup.toRange(),
                peak = peak.toRange(),
                offset = offset.toRange(),
                total = total.toRange(),
            )
        }

    companion object {
        private const val MAX_PERCENT = 100.0

        private fun Double?.text() = this?.toReadableString().orEmpty()

        fun from(info: CustomRoaInfo, defaultUnits: String) = CustomRouteDraft(
            route = info.route,
            doseUnits = info.dose?.units ?: defaultUnits,
            lightMin = info.dose?.lightMin.text(),
            commonMin = info.dose?.commonMin.text(),
            strongMin = info.dose?.strongMin.text(),
            heavyMin = info.dose?.heavyMin.text(),
            onset = DurationDraft.from(info.duration?.onset),
            comeup = DurationDraft.from(info.duration?.comeup),
            peak = DurationDraft.from(info.duration?.peak),
            offset = DurationDraft.from(info.duration?.offset),
            total = info.duration?.total?.let(DurationDraft::from) ?: DurationDraft(units = DurationUnits.HOURS),
            bioavailabilityMin = info.roa?.bioavailability?.min.text(),
            bioavailabilityMax = info.roa?.bioavailability?.max.text(),
        )
    }
}

/** "a, b ,, c" -> [a, b, c], without blanks or duplicates. */
fun parseCrossTolerances(text: String): List<String> =
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
