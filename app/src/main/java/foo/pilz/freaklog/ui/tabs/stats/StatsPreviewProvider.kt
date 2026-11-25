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

package foo.pilz.freaklog.ui.tabs.stats

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.AdministrationRoute
import java.time.Instant

class StatsPreviewProvider : PreviewParameterProvider<StatsModel> {
  override val values: Sequence<StatsModel> =
      sequenceOf(
          StatsModel(
              selectedOption = TimePickerOption.DAYS_7,
              areThereAnyIngestions = true,
              startDateText = "22 Jun 2022",
              consumerName = null,
              totalIngestions = 23,
              totalExperiences = 7,
              statItems =
                  listOf(
                      StatItem(
                          substanceName = "LSD",
                          color = AdaptiveColor.BLUE,
                          experienceCount = 3,
                          ingestionCount = 3,
                          routeCounts =
                              listOf(
                                  RouteCount(
                                      administrationRoute = AdministrationRoute.SUBLINGUAL,
                                      count = 3,
                                  )
                              ),
                          totalDose =
                              TotalDose(
                                  dose = 500.0,
                                  units = "µg",
                                  isEstimate = false,
                                  estimatedDoseStandardDeviation = null,
                              ),
                          lastUsed = Instant.parse("2026-04-29T12:00:00Z"),
                          unknownDoseCount = 0,
                          unitNames = listOf("µg"),
                      ),
                      StatItem(
                          substanceName = "MDMA",
                          color = AdaptiveColor.PINK,
                          ingestionCount = 8,
                          experienceCount = 2,
                          routeCounts =
                              listOf(
                                  RouteCount(
                                      administrationRoute = AdministrationRoute.ORAL,
                                      count = 6,
                                  ),
                                  RouteCount(
                                      administrationRoute = AdministrationRoute.INSUFFLATED,
                                      count = 2,
                                  ),
                              ),
                          totalDose =
                              TotalDose(
                                  dose = 950.0,
                                  units = "mg",
                                  isEstimate = true,
                                  estimatedDoseStandardDeviation = 50.0,
                              ),
                          lastUsed = Instant.parse("2026-04-29T12:00:00Z"),
                          unknownDoseCount = 0,
                          unitNames = listOf("mg"),
                      ),
                      StatItem(
                          substanceName = "Cocaine",
                          color = AdaptiveColor.ORANGE,
                          ingestionCount = 11,
                          experienceCount = 1,
                          routeCounts =
                              listOf(
                                  RouteCount(
                                      administrationRoute = AdministrationRoute.INSUFFLATED,
                                      count = 11,
                                  )
                              ),
                          totalDose = null,
                          lastUsed = Instant.parse("2026-04-29T12:00:00Z"),
                          unknownDoseCount = 1,
                          unitNames = listOf("mg"),
                          knownTotalDose =
                              TotalDose(
                                  dose = 420.0,
                                  units = "mg",
                                  isEstimate = false,
                                  estimatedDoseStandardDeviation = null,
                              ),
                      ),
                      StatItem(
                          substanceName = "Ketamine",
                          color = AdaptiveColor.PURPLE,
                          experienceCount = 1,
                          ingestionCount = 1,
                          routeCounts =
                              listOf(
                                  RouteCount(
                                      administrationRoute = AdministrationRoute.INSUFFLATED,
                                      count = 1,
                                  )
                              ),
                          totalDose =
                              TotalDose(
                                  dose = 30.0,
                                  units = "mg",
                                  isEstimate = true,
                                  estimatedDoseStandardDeviation = 5.0,
                              ),
                          lastUsed = Instant.parse("2026-04-29T12:00:00Z"),
                          unknownDoseCount = 0,
                          unitNames = listOf("mg"),
                      ),
                  ),
              chartBuckets =
                  listOf(
                      StatsChartBucket(
                          label = "Mon",
                          fullDateText = "22 Jun 2022",
                          items =
                              listOf(
                                  ColorCount(
                                      color = AdaptiveColor.BLUE,
                                      count = 3,
                                      substanceName = "LSD",
                                  ),
                                  ColorCount(
                                      color = AdaptiveColor.PINK,
                                      count = 2,
                                      substanceName = "MDMA",
                                  ),
                              ),
                      ),
                      StatsChartBucket("Tue", "23 Jun 2022", emptyList()),
                      StatsChartBucket("Wed", "24 Jun 2022", emptyList()),
                      StatsChartBucket("Thu", "25 Jun 2022", emptyList()),
                      StatsChartBucket("Fri", "26 Jun 2022", emptyList()),
                      StatsChartBucket(
                          label = "Sat",
                          fullDateText = "27 Jun 2022",
                          items =
                              listOf(
                                  ColorCount(
                                      color = AdaptiveColor.ORANGE,
                                      count = 1,
                                      substanceName = "Cocaine",
                                  )
                              ),
                      ),
                      StatsChartBucket(
                          label = "Sun",
                          fullDateText = "28 Jun 2022",
                          items =
                              listOf(
                                  ColorCount(
                                      color = AdaptiveColor.PURPLE,
                                      count = 1,
                                      substanceName = "Ketamine",
                                  )
                              ),
                      ),
                  ),
          )
      )
}
