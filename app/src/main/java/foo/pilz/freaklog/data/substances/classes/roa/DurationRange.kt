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

@kotlinx.serialization.Serializable
data class DurationRange(
    val min: Float?,
    val max: Float?,
    val units: DurationUnits?,
) {
  val text: String
    get() {
      val suffix = units?.shortText.orEmpty()
      val minClean = min?.toString()?.removeSuffix(".0")
      val maxClean = max?.toString()?.removeSuffix(".0")
      return when {
        minClean == null && maxClean == null -> ""
        minClean != null && maxClean == null -> "$minClean$suffix"
        minClean == null && maxClean != null -> "$maxClean$suffix"
        minClean == maxClean -> "$minClean$suffix"
        else -> "$minClean-$maxClean$suffix"
      }
    }

  // Derived values: kept out of Room columns and out of serialized output.
  @androidx.room.Ignore
  @kotlinx.serialization.Transient
  val minInSec: Float? =
      if (units != null) {
        min?.times(units.inSecondsMultiplier)?.takeIf { it.isFinite() && it >= 0f }
      } else {
        null
      }

  @androidx.room.Ignore
  @kotlinx.serialization.Transient
  val maxInSec: Float? =
      if (units != null) {
        max?.times(units.inSecondsMultiplier)?.takeIf { it.isFinite() && it >= 0f }
      } else {
        null
      }

  fun interpolateAtValueInSeconds(value: Float): Float? {
    if (units == null || !value.isFinite()) return null
    val effMin = (min ?: max)?.takeIf { it.isFinite() && it >= 0f } ?: return null
    val effMax = (max ?: min)?.takeIf { it.isFinite() && it >= 0f } ?: return null
    val diff = effMax - effMin
    val withUnit = effMin + diff.times(value)
    val sec = withUnit.times(units.inSecondsMultiplier)
    return sec.takeIf { it.isFinite() }?.coerceAtLeast(0f)
  }
}
