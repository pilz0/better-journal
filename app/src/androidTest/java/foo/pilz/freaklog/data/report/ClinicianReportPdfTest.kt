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

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith

/** PdfDocument is native, so this needs a device; Robolectric cannot create one. */
@RunWith(AndroidJUnit4::class)
class ClinicianReportPdfTest {

  private fun report(style: ReportStyle): ClinicianReport {
    val start = Instant.parse("2026-01-01T20:00:00Z")
    val ingestions =
        (0 until 60).map {
          ReportIngestion(
              time = start.plusSeconds(it * 86_400L / 2),
              substanceName =
                  listOf("Cannabis", "Alcohol", "Methylphenidate", "Psilocybin")[it % 4],
              dose = "${10 + it} mg",
              route = "oral",
          )
        }
    return ClinicianReport(
        style = style,
        from = LocalDate.of(2026, 1, 1),
        to = LocalDate.of(2026, 1, 31),
        generatedAt = start,
        patientName = "Test Patient",
        zone = ZoneOffset.UTC,
        substances =
            ingestions
                .groupBy { it.substanceName }
                .map { (name, uses) ->
                  SubstanceSummary(
                      name,
                      uses.size,
                      uses.size,
                      listOf("oral"),
                      "20 mg",
                      "10–69 mg",
                      0,
                      uses.last().time,
                  )
                },
        days =
            ingestions
                .groupBy { it.time.atZone(ZoneOffset.UTC).toLocalDate() }
                .mapValues { (_, uses) -> uses.map { it.substanceName } }
                .toSortedMap(),
        ingestions = ingestions,
        experiences =
            ingestions.chunked(6).mapIndexed { i, uses ->
              ReportExperience(
                  title = "Experience $i",
                  start = uses.first().time,
                  ingestions = uses,
                  ratings = listOf("++"),
                  notes = "A long note that has to wrap. ".repeat(30),
                  timedNotes = listOf(ReportTimedNote(uses.first().time, "felt calm")),
              )
            },
    )
  }

  @Test
  fun writesAMultiPagePdfForBothStyles() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    ReportStyle.entries.forEach { style ->
      val file = File(context.cacheDir, "report-test-$style.pdf")
      file.outputStream().use { writeClinicianReportPdf(report(style), it) }
      assertThat(file.readBytes().decodeToString(0, 5)).isEqualTo("%PDF-")
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { assertThat(it.pageCount).isGreaterThan(1) }
      }
      file.delete()
    }
  }
}
