/*
 * Copyright (c) 2023. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.journal.experience.components

import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import kotlin.math.max

fun precomputeAverageDoses(
    allIngestions: List<IngestionWithCompanionAndCustomUnit>,
): Map<IngestionDoseKey, Double> {
  val groups = allIngestions.groupBy {
    IngestionDoseKey(
        it.ingestion.substanceName.trim().lowercase(),
        it.ingestion.administrationRoute,
        RoaDose.normalizeUnitString(it.originalUnit),
    )
  }
  return groups.mapValues { (_, list) ->
    val valid = list.mapNotNull { it.pureDose?.takeIf { d -> d.isFinite() && d > 0.0 } }
    val sum = valid.sum()
    sum / max(1.0, valid.size.toDouble())
  }
}

fun getStrengthRelativeToCommonDose(
    ingestion: IngestionWithCompanionAndCustomUnit,
    averageDose: Double,
    roaDose: RoaDose?,
): Double {
  // Compare against the PsychonautWiki common dose when the units convert (0.1 g vs an mg scale),
  // otherwise against the average of this substance's ingestions logged in the same unit.
  val roaCommonDose = roaDose?.averageCommonDose?.takeIf { it.isFinite() && it > 0.0 }
  val doseInRoaUnits = roaDose?.doseInOwnUnits(ingestion.pureDose, ingestion.originalUnit)
  val relativeDose =
      if (roaCommonDose != null && doseInRoaUnits != null) {
        doseInRoaUnits / roaCommonDose
      } else {
        val commonDose = averageDose.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        ingestion.pureDose?.let { doseSnap ->
          if (doseSnap.isFinite() && doseSnap >= 0.0) doseSnap / commonDose else 1.0
        } ?: 1.0
      }

  return if (relativeDose.isFinite() && relativeDose >= 0.0) relativeDose else 1.0
}

fun getStrengthRelativeToCommonDose(
    ingestion: IngestionWithCompanionAndCustomUnit,
    allIngestions: List<IngestionWithCompanionAndCustomUnit>,
    roaDose: RoaDose?,
): Double {
  val key =
      IngestionDoseKey(
          ingestion.ingestion.substanceName.trim().lowercase(),
          ingestion.ingestion.administrationRoute,
          RoaDose.normalizeUnitString(ingestion.originalUnit),
      )
  val avg = precomputeAverageDoses(allIngestions)[key] ?: 0.0
  return getStrengthRelativeToCommonDose(ingestion, avg, roaDose)
}
