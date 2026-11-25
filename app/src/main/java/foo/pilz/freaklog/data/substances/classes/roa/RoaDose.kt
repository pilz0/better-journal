/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.substances.classes.roa

import kotlin.math.floor
import kotlin.math.roundToInt

data class RoaDose(
    val units: String,
    val lightMin: Double?,
    val commonMin: Double?,
    val strongMin: Double?,
    val heavyMin: Double?,
) {
  fun getDoseClass(ingestionDose: Double?, ingestionUnits: String? = units): DoseClass? {
    val dose = doseInOwnUnits(ingestionDose, ingestionUnits) ?: return null
    return if (lightMin != null && dose < lightMin) {
      DoseClass.THRESHOLD
    } else if (lightMin != null && commonMin != null && lightMin <= dose && dose < commonMin) {
      DoseClass.LIGHT
    } else if (commonMin != null && strongMin != null && commonMin <= dose && dose < strongMin) {
      DoseClass.COMMON
    } else if (strongMin != null && heavyMin != null && strongMin <= dose && dose < heavyMin) {
      DoseClass.STRONG
    } else if (heavyMin != null && heavyMin <= dose) {
      DoseClass.HEAVY
    } else {
      null
    }
  }

  fun getNumDots(ingestionDose: Double?, ingestionUnits: String? = units): Int? {
    val dose = doseInOwnUnits(ingestionDose, ingestionUnits) ?: return null
    if (heavyMin != null && (heavyMin <= 0.0 || !heavyMin.isFinite())) return null
    return when {
      lightMin != null && dose < lightMin -> 0
      lightMin != null && commonMin != null && lightMin <= dose && dose < commonMin -> 1
      commonMin != null && strongMin != null && commonMin <= dose && dose < strongMin -> 2
      strongMin != null && heavyMin != null && strongMin <= dose && dose < heavyMin -> 3
      heavyMin != null -> {
        val timesHeavy = floor(dose.div(heavyMin))
        val rest = dose.rem(heavyMin)
        val dots = (timesHeavy * 4) + getNumDotsUpTo4(dose = rest)
        if (dots in 0.0..Int.MAX_VALUE.toDouble()) dots.roundToInt() else null
      }
      else -> null
    }
  }

  /** [dose] in this scale's [units] (µg, mg and g convert), or null when they can't be compared. */
  fun doseInOwnUnits(dose: Double?, ingestionUnits: String?): Double? {
    if (dose == null || !dose.isFinite() || dose < 0.0) return null
    if (isMatchingUnit(ingestionUnits, units)) return dose
    val (fromBase, from) = DoseUnits.toBase(ingestionUnits) ?: return null
    val (toBase, to) = DoseUnits.toBase(units) ?: return null
    if (fromBase != toBase) return null
    return (dose * from / to).takeIf { it.isFinite() }
  }

  private fun getNumDotsUpTo4(dose: Double): Int {
    if (!dose.isFinite() || dose < 0.0) return 0
    return if (lightMin != null && dose < lightMin) {
      0
    } else if (lightMin != null && commonMin != null && lightMin <= dose && dose < commonMin) {
      1
    } else if (commonMin != null && strongMin != null && commonMin <= dose && dose < strongMin) {
      2
    } else if (strongMin != null && heavyMin != null && strongMin <= dose && dose < heavyMin) {
      3
    } else if (heavyMin != null && heavyMin > 0.0 && heavyMin.isFinite()) {
      floor(dose / heavyMin).roundToInt()
    } else {
      0
    }
  }

  val shouldUseVolumetricDosing: Boolean
    get() {
      val normUnits = normalizeUnitString(units)
      if (normUnits == "µg") return true
      return if (normUnits == "mg") {
        val sample = commonMin ?: strongMin
        sample != null && sample < 15
      } else {
        false
      }
    }

  val averageCommonDose: Double?
    get() {
      val c = commonMin?.takeIf { it.isFinite() && it > 0.0 } ?: return null
      val s = strongMin?.takeIf { it.isFinite() && it > 0.0 } ?: return null
      val avg = c / 2.0 + s / 2.0
      return avg.takeIf { it.isFinite() && it > 0.0 }
    }

  fun getStrengthRelativeToCommonDose(dose: Double): Double? {
    if (!dose.isFinite() || dose < 0.0) return null
    return averageCommonDose?.let {
      if (it > 0.0) {
        (dose / it).takeIf { ratio -> ratio.isFinite() && ratio >= 0.0 }
      } else {
        null
      }
    }
  }

  companion object {
    fun isMatchingUnit(ingestionUnits: String?, referenceUnits: String?): Boolean {
      if (ingestionUnits == referenceUnits) return true
      if (ingestionUnits == null || referenceUnits == null) return false
      val a = normalizeUnitString(ingestionUnits)
      val b = normalizeUnitString(referenceUnits)
      return a == b
    }

    fun normalizeUnitString(unit: String?): String {
      if (unit == null) return ""
      val trimmed = unit.trim().lowercase().replace('μ', 'µ')
      return when (trimmed) {
        "ug",
        "mcg",
        "µg",
        "microgram",
        "micrograms",
        "micro gram",
        "micro grams" -> "µg"
        "mg",
        "milligram",
        "milligrams",
        "miligram",
        "miligrams" -> "mg"
        "g",
        "gram",
        "grams" -> "g"
        "kg",
        "kilogram",
        "kilograms" -> "kg"
        "ml",
        "milliliter",
        "milliliters",
        "millilitre",
        "millilitres" -> "ml"
        "l",
        "liter",
        "liters",
        "litre",
        "litres" -> "l"
        else -> trimmed
      }
    }
  }
}
