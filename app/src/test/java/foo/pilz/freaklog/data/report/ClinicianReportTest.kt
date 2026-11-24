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

package foo.pilz.freaklog.data.report

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import foo.pilz.freaklog.data.export.ExportData
import foo.pilz.freaklog.data.export.ExportFilter
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRatingOption
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

class ClinicianReportTest {

  private val utc = ZoneOffset.UTC
  private val to = Instant.parse("2026-01-31T23:00:00Z")
  private val from = Instant.parse("2026-01-02T00:00:00Z")

  private fun at(day: Int, hour: Int = 20) =
      Instant.parse("2026-01-%02dT%02d:00:00Z".format(day, hour))

  private fun experience(
      id: Int,
      ingestions: List<Ingestion>,
      text: String = "",
      ratings: List<ShulginRating> = emptyList(),
      timedNotes: List<TimedNote> = emptyList(),
  ) =
      ExperienceWithIngestionsTimedNotesAndRatings(
          experience = EntityBuilders.experience(id = id, title = "Night $id", text = text),
          ingestions = ingestions.map { it.copy(experienceId = id) },
          timedNotes = timedNotes,
          ratings = ratings,
      )

  private fun report(
      vararg experiences: ExperienceWithIngestionsTimedNotesAndRatings,
      customUnits: List<CustomUnit> = emptyList(),
      filter: ExportFilter = ExportFilter(),
      style: ReportStyle = ReportStyle.MEDICAL,
      start: Instant? = from,
  ) =
      buildClinicianReport(
          data =
              ExportData(
                      experiences = experiences.toList(),
                      customUnits = customUnits,
                      substanceCompanions = emptyList(),
                      customSubstances = emptyList(),
                  )
                  .filtered(filter),
          from = start,
          to = to,
          style = style,
          zone = utc,
          generatedAt = to,
      )

  @Test
  fun `keeps only the user's own ingestions inside the period`() {
    val report =
        report(
            experience(
                1,
                listOf(
                    EntityBuilders.ingestion(substanceName = "Before", time = at(1, 23)),
                    EntityBuilders.ingestion(substanceName = "First", time = from),
                    EntityBuilders.ingestion(substanceName = "Last", time = to),
                    EntityBuilders.ingestion(substanceName = "After", time = to.plusSeconds(1)),
                    EntityBuilders.ingestion(
                        substanceName = "Friend",
                        time = at(10),
                        consumerName = "Alex",
                    ),
                ),
            )
        )

    assertThat(report.substances.map { it.name }).containsExactly("First", "Last")
    assertThat(report.from).isEqualTo(LocalDate.of(2026, 1, 2))
    assertThat(report.periodDays).isEqualTo(30L)
  }

  @Test
  fun `counts days, times, routes and last use`() {
    fun smoked(day: Int) =
        EntityBuilders.ingestion(
            substanceName = "Cannabis",
            time = at(day),
            administrationRoute = AdministrationRoute.SMOKED,
        )
    val report =
        report(
            experience(
                1,
                listOf(
                    EntityBuilders.ingestion(substanceName = "Cannabis", time = at(3, 18)),
                    EntityBuilders.ingestion(substanceName = "Cannabis", time = at(3, 22)),
                    smoked(5),
                    smoked(6),
                    smoked(9),
                ),
            )
        )

    val cannabis = report.substances.single()
    assertThat(cannabis.daysUsed).isEqualTo(4)
    assertThat(cannabis.times).isEqualTo(5)
    assertThat(cannabis.routes).containsExactly("smoked", "oral")
    assertThat(cannabis.lastUse).isEqualTo(at(9))
    assertThat(report.days.keys.size).isEqualTo(4)
  }

  @Test
  fun `dose median and range convert units and show them in the most used unit`() {
    val report =
        report(
            experience(
                1,
                listOf(
                        EntityBuilders.ingestion(substanceName = "LSD", dose = 100.0, units = "µg"),
                        EntityBuilders.ingestion(substanceName = "LSD", dose = 0.2, units = "mg"),
                        EntityBuilders.ingestion(substanceName = "LSD", dose = 150.0, units = "µg"),
                        EntityBuilders.ingestion(substanceName = "LSD", dose = null, units = "µg"),
                    )
                    .mapIndexed { i, ingestion -> ingestion.copy(time = at(i + 3)) },
            )
        )

    val lsd = report.substances.single()
    assertThat(lsd.usualDose).isEqualTo("150 µg")
    assertThat(lsd.doseRange).isEqualTo("100–200 µg")
    assertThat(lsd.undosedCount).isEqualTo(1)
    assertThat(lsd.doseText()).isEqualTo("usually 150 µg (100–200 µg); 1× without dose")
  }

  @Test
  fun `custom units count as their underlying dose`() {
    val tab =
        CustomUnit(
            id = 7,
            substanceName = "LSD",
            name = "Tab",
            administrationRoute = AdministrationRoute.SUBLINGUAL,
            dose = 100.0,
            estimatedDoseStandardDeviation = null,
            isEstimate = false,
            isArchived = false,
            unit = "tab",
            unitPlural = "tabs",
            originalUnit = "µg",
            note = "",
        )
    val report =
        report(
            experience(
                1,
                listOf(
                    EntityBuilders.ingestion(
                        substanceName = "LSD",
                        dose = 2.0,
                        units = "tab",
                        customUnitId = 7,
                        time = at(4),
                    )
                ),
            ),
            customUnits = listOf(tab),
        )

    assertThat(report.substances.single().usualDose).isEqualTo("200 µg")
    assertThat(report.substances.single().doseRange).isNull()
  }

  @Test
  fun `incomparable units show varies`() {
    val report =
        report(
            experience(
                1,
                listOf(
                        EntityBuilders.ingestion(
                            substanceName = "MDMA",
                            dose = 1.0,
                            units = "pill",
                        ),
                        EntityBuilders.ingestion(substanceName = "MDMA", dose = 80.0, units = "mg"),
                    )
                    .mapIndexed { i, ingestion -> ingestion.copy(time = at(i + 3)) },
            )
        )

    assertThat(report.substances.single().usualDose).isEqualTo("varies")
  }

  @Test
  fun `therapy text lists experiences and the filter drops notes`() {
    val exp =
        experience(
            1,
            listOf(EntityBuilders.ingestion(substanceName = "Psilocybin", time = at(10))),
            text = "felt grief",
            ratings =
                listOf(
                    ShulginRating(
                        time = at(11),
                        option = ShulginRatingOption.THREE_PLUS,
                        experienceId = 1,
                    )
                ),
            timedNotes =
                listOf(
                    TimedNote(
                        time = at(12),
                        note = "calmer",
                        color = AdaptiveColor.BLUE,
                        experienceId = 1,
                        isPartOfTimeline = false,
                    )
                ),
        )

    val full = report(exp, style = ReportStyle.THERAPY)
    assertThat(full.experiences.single().notes).isEqualTo("felt grief")
    assertThat(full.experiences.single().timedNotes.single().note).isEqualTo("calmer")
    assertThat(full.toText()).contains("- 10 Jan 2026: Night 1 (+++)")

    val private =
        report(
            exp,
            style = ReportStyle.THERAPY,
            filter = ExportFilter(includeNotes = false, includeTimedNotes = false),
        )
    assertThat(private.experiences.single().notes).isEqualTo("")
    assertThat(private.experiences.single().timedNotes).isEmpty()
    assertThat(private.toText()).doesNotContain("grief")
  }

  @Test
  fun `medical text has one line per substance and no experiences`() {
    val text =
        report(
                experience(
                    1,
                    listOf(
                        EntityBuilders.ingestion(
                            substanceName = "Alcohol",
                            dose = 20.0,
                            units = "g",
                            time = at(20),
                        )
                    ),
                ),
                start = null,
            )
            .toText()

    assertThat(text)
        .contains("Period: 20 Jan 2026 – 31 Jan 2026 (12 days). Days with any use: 1 of 12.")
    assertThat(text).contains("Alcohol: 1 day, 1×, oral; usually 20 g; last 20 Jan 2026")
    assertThat(text).doesNotContain("Experiences:")
  }

  @Test
  fun `period presets end now and custom ranges cover whole days`() {
    val now = Instant.parse("2026-03-10T15:00:00Z")
    assertThat(ReportSettings(period = ReportPeriod.DAYS_30).range(now, utc))
        .isEqualTo(Instant.parse("2026-02-09T00:00:00Z") to now)
    assertThat(ReportSettings(period = ReportPeriod.ALL).range(now, utc)).isEqualTo(null to now)
    assertThat(
            ReportSettings(
                    period = ReportPeriod.CUSTOM,
                    customFrom = LocalDate.of(2026, 2, 1),
                    customTo = LocalDate.of(2026, 2, 3),
                )
                .range(now, utc)
        )
        .isEqualTo(
            Instant.parse("2026-02-01T00:00:00Z") to Instant.parse("2026-02-03T23:59:59.999Z")
        )
  }

  @Test
  fun `use-free stretch, halves and pattern buckets`() {
    // Period 2–31 Jan (30 days); use on 5, 6 and 25 Jan.
    val report =
        report(
            experience(
                1,
                listOf(5, 6, 25).map {
                  EntityBuilders.ingestion(substanceName = "Cannabis", time = at(it))
                },
            )
        )

    assertThat(report.useFreeDays).isEqualTo(27L)
    // 7–24 Jan.
    assertThat(report.longestUseFreeStretch).isEqualTo(18L)
    // Halves are 2–16 and 17–31 Jan.
    assertThat(report.daysPerWeekByHalf("Cannabis")).isEqualTo(2 * 7.0 / 15 to 1 * 7.0 / 15)

    val daily = report.usePattern(maxBuckets = 120)
    assertThat(daily.bucketDays).isEqualTo(1)
    assertThat(daily.any.size).isEqualTo(30)
    assertThat(daily.bySubstance.getValue("Cannabis")[3]).isEqualTo(1f)

    val weekly = report.usePattern(maxBuckets = 5)
    assertThat(weekly.bucketDays).isEqualTo(6)
    // 2–7 Jan holds 5 and 6 Jan.
    assertThat(weekly.any.first()).isEqualTo(2 / 6f)
  }

  @Test
  fun `no use means the whole period is use-free`() {
    val report = report()
    assertThat(report.useFreeDays).isEqualTo(30L)
    assertThat(report.longestUseFreeStretch).isEqualTo(30L)
  }
}
