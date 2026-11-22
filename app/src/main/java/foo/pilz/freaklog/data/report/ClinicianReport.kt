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

import foo.pilz.freaklog.data.export.ExportData
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.classes.roa.DoseUnits
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Who the report is laid out for. Both contain the same data; the order and depth differ. */
enum class ReportStyle(val label: String, val description: String) {
  MEDICAL(
      "Medical",
      "Substances, doses, routes, how often and when last used, with a day-by-day calendar.",
  ),
  THERAPY(
      "Therapy",
      "Experiences with notes and ratings first, then the substance summary and calendar.",
  ),
}

enum class ReportPeriod(val label: String, val days: Long?) {
  DAYS_30("30 days", 30),
  DAYS_90("90 days", 90),
  MONTHS_12("12 months", 365),
  ALL("Everything", null),
  CUSTOM("Custom", null),
}

/** What the user picked on the export screen. Not persisted. */
data class ReportSettings(
    val style: ReportStyle = ReportStyle.MEDICAL,
    val period: ReportPeriod = ReportPeriod.DAYS_30,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val patientName: String = "",
) {
  /** Start (null: since the first entry) and end of the period, both inclusive. */
  fun range(now: Instant, zone: ZoneId): Pair<Instant?, Instant> {
    val today = now.atZone(zone).toLocalDate()
    return when (period) {
      ReportPeriod.ALL -> null to now
      ReportPeriod.CUSTOM -> {
        val start = customFrom ?: today
        val end = maxOf(customTo ?: today, start)
        start.atStartOfDay(zone).toInstant() to
            end.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
      }
      else -> today.minusDays(period.days!! - 1).atStartOfDay(zone).toInstant() to now
    }
  }
}

data class SubstanceSummary(
    val name: String,
    val daysUsed: Int,
    val times: Int,
    val routes: List<String>,
    /** Median dose per use, "varies" when the units can't be compared, null without doses. */
    val usualDose: String?,
    val doseRange: String?,
    val undosedCount: Int,
    val lastUse: Instant,
)

data class ReportIngestion(
    val time: Instant,
    val substanceName: String,
    val dose: String,
    val route: String,
)

data class ReportTimedNote(val time: Instant, val note: String)

data class ReportExperience(
    val title: String,
    val start: Instant,
    val ingestions: List<ReportIngestion>,
    val ratings: List<String>,
    val notes: String,
    val timedNotes: List<ReportTimedNote>,
)

data class ClinicianReport(
    val style: ReportStyle,
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
    val patientName: String,
    val zone: ZoneId,
    val substances: List<SubstanceSummary>,
    /** Substances used on each day that had any use, in date order. */
    val days: Map<LocalDate, List<String>>,
    val ingestions: List<ReportIngestion>,
    val experiences: List<ReportExperience>,
) {
  val periodDays: Long
    get() = ChronoUnit.DAYS.between(from, to) + 1
}

/**
 * Builds the report from already filtered [data] (see `ExportData.filtered`). Only the user's own
 * ingestions between [from] (null: since the first one) and [to] are included.
 */
@Suppress("LongParameterList")
fun buildClinicianReport(
    data: ExportData,
    from: Instant?,
    to: Instant,
    style: ReportStyle,
    patientName: String = "",
    zone: ZoneId = ZoneId.systemDefault(),
    generatedAt: Instant = Instant.now(),
): ClinicianReport {
  val unitsById = data.customUnits.associateBy { it.id }
  val companionsByName = data.substanceCompanions.associateBy { it.substanceName }

  fun inPeriod(time: Instant) = (from == null || !time.isBefore(from)) && !time.isAfter(to)

  val experiences =
      data.experiences.mapNotNull { exp ->
        val ingestions =
            exp.ingestions
                .filter { it.consumerName == null && inPeriod(it.time) }
                .sortedBy { it.time }
                .map {
                  IngestionWithCompanionAndCustomUnit(
                      ingestion = it,
                      substanceCompanion = companionsByName[it.substanceName],
                      customUnit = it.customUnitId?.let(unitsById::get),
                  )
                }
        if (ingestions.isEmpty()) return@mapNotNull null
        exp to ingestions
      }
  val all = experiences.flatMap { it.second }.sortedBy { it.ingestion.time }
  fun day(time: Instant) = time.atZone(zone).toLocalDate()

  val substances =
      all.groupBy { it.ingestion.substanceName }
          .map { (name, uses) -> summarize(name, uses, ::day) }
          .sortedWith(compareByDescending<SubstanceSummary> { it.daysUsed }.thenBy { it.name })

  return ClinicianReport(
      style = style,
      from = from?.let(::day) ?: all.firstOrNull()?.ingestion?.time?.let(::day) ?: day(to),
      to = day(to),
      generatedAt = generatedAt,
      patientName = patientName.trim(),
      zone = zone,
      substances = substances,
      days =
          all.groupBy { day(it.ingestion.time) }
              .mapValues { (_, uses) -> uses.map { it.ingestion.substanceName }.distinct() }
              .toSortedMap(),
      ingestions = all.map(::toReportIngestion),
      experiences =
          experiences
              .map { (exp, ingestions) ->
                ReportExperience(
                    title = exp.experience.title,
                    start = ingestions.first().ingestion.time,
                    ingestions = ingestions.map(::toReportIngestion),
                    ratings = exp.ratings.map { it.option.sign },
                    notes = exp.experience.text.trim(),
                    timedNotes =
                        exp.timedNotes
                            .sortedBy { it.time }
                            .map { ReportTimedNote(it.time, it.note.trim()) },
                )
              }
              .sortedBy { it.start },
  )
}

private fun toReportIngestion(it: IngestionWithCompanionAndCustomUnit) =
    ReportIngestion(
        time = it.ingestion.time,
        substanceName = it.ingestion.substanceName,
        dose = if (it.ingestion.dose == null) "dose not recorded" else it.doseDescription,
        route = it.ingestion.administrationRoute.displayText.lowercase(),
    )

private fun summarize(
    name: String,
    uses: List<IngestionWithCompanionAndCustomUnit>,
    day: (Instant) -> LocalDate,
): SubstanceSummary {
  val withBase = uses.mapNotNull { use ->
    val base = DoseUnits.toBase(use.originalUnit)
    if (use.pureDose?.isFinite() == true && base != null) use to base else null
  }
  val dosed = withBase.map { it.first }
  val bases = withBase.map { it.second }
  val baseUnit = bases.map { it.first }.distinct().singleOrNull()
  var usual: String? = null
  var range: String? = null
  if (bases.isNotEmpty() && baseUnit == null) {
    usual = "varies"
  } else if (baseUnit != null) {
    val amounts = dosed.zip(bases) { use, base -> use.pureDose!! * base.second }.sorted()
    val median = amounts.median()
    // Show doses in the unit the user logs most, e.g. g instead of mg.
    val preferred = dosed.groupingBy { it.originalUnit }.eachCount().maxBy { it.value }.key
    val (unit, divisor) = DoseUnits.displayUnit(baseUnit, median, preferred)
    fun fmt(amount: Double) = (amount / divisor).toReadableString()
    usual = "${fmt(median)} $unit"
    if (amounts.first() != amounts.last()) {
      range = "${fmt(amounts.first())}–${fmt(amounts.last())} $unit"
    }
  }
  return SubstanceSummary(
      name = name,
      daysUsed = uses.map { day(it.ingestion.time) }.distinct().size,
      times = uses.size,
      routes =
          uses
              .groupingBy { it.ingestion.administrationRoute.displayText.lowercase() }
              .eachCount()
              .entries
              .sortedByDescending { it.value }
              .map { it.key },
      usualDose = usual,
      doseRange = range,
      undosedCount = uses.size - dosed.size,
      lastUse = uses.maxOf { it.ingestion.time },
  )
}

private fun List<Double>.median(): Double =
    if (size % 2 == 1) this[size / 2] else (this[size / 2 - 1] + this[size / 2]) / 2

internal val reportDateFormat: DateTimeFormatter
  get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

internal val reportTimeFormat: DateTimeFormatter
  get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

internal fun ClinicianReport.date(time: Instant): String =
    time.atZone(zone).toLocalDate().format(reportDateFormat)

internal fun ClinicianReport.time(time: Instant): String =
    time.atZone(zone).toLocalTime().format(reportTimeFormat)

internal fun ClinicianReport.dateTime(time: Instant): String = "${date(time)} ${time(time)}"

internal const val REPORT_TITLE = "Substance use summary (self-reported)"

internal const val REPORT_DISCLAIMER =
    "Recorded by the patient in the Freaklog app. Not verified by a clinician or by testing."

internal val ClinicianReport.periodLine: String
  get() =
      "${from.format(reportDateFormat)} – ${to.format(reportDateFormat)} ($periodDays days). " +
          "Days with any use: ${days.size} of $periodDays."

internal fun SubstanceSummary.doseText(): String =
    listOfNotNull(
            usualDose?.let { "usually $it" + (doseRange?.let { r -> " ($r)" } ?: "") },
            undosedCount.takeIf { it > 0 }?.let { "$it× without dose" },
        )
        .joinToString("; ")
        .ifEmpty { "no doses recorded" }

/** The plain-text version, for pasting into a message or email. */
fun ClinicianReport.toText(): String =
    buildString {
          appendLine(REPORT_TITLE)
          if (patientName.isNotEmpty()) appendLine("Name: $patientName")
          appendLine("Period: $periodLine")
          appendLine(REPORT_DISCLAIMER)
          appendLine()
          if (substances.isEmpty()) appendLine("No use recorded in this period.")
          substances.forEach {
            appendLine(
                "${it.name}: ${it.daysUsed} ${if (it.daysUsed == 1) "day" else "days"}, ${it.times}×, ${it.routes.joinToString("/")}; " +
                    "${it.doseText()}; last ${date(it.lastUse)}"
            )
          }
          if (style == ReportStyle.THERAPY && experiences.isNotEmpty()) {
            appendLine()
            appendLine("Experiences:")
            experiences.forEach {
              val rating = it.ratings.takeIf { r -> r.isNotEmpty() }?.joinToString(" ", " (", ")")
              appendLine("- ${date(it.start)}: ${it.title}${rating.orEmpty()}")
            }
          }
        }
        .trimEnd()
