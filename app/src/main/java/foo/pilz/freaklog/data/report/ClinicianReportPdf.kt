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

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import foo.pilz.freaklog.data.substanceshare.sharedSnapshot
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A4 in PostScript points.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val FOOTER_HEIGHT = 24f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
private const val CELL_HEIGHT = 50f
private const val NAMES_PER_CELL = 3

// ponytail: the calendar stops at 12 months so an "everything" report stays printable; the
// summary and the log still cover the whole period.
private const val MAX_CALENDAR_MONTHS = 12

private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = size
      typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
      this.color = color
    }

private val titlePaint = textPaint(18f, bold = true)
private val headingPaint = textPaint(13f, bold = true)
private val bodyPaint = textPaint(9.5f)
private val boldPaint = textPaint(9.5f, bold = true)
private val smallPaint = textPaint(7.5f, color = Color.DKGRAY)
private val greyPaint = textPaint(7.5f, color = Color.GRAY)
private val dayNumberPaint = textPaint(7.5f, bold = true)
private val linePaint =
    Paint().apply {
      color = Color.LTGRAY
      style = Paint.Style.STROKE
      strokeWidth = 0.5f
    }
private val usedDayPaint =
    Paint().apply {
      color = Color.rgb(235, 240, 250)
      style = Paint.Style.FILL
    }

/** Writes [report] as an A4 PDF. Page 1 always starts with the header and substance summary. */
fun writeClinicianReportPdf(report: ClinicianReport, out: OutputStream) {
  val doc = PdfDocument()
  try {
    val pages = PageWriter(doc, "Generated ${report.date(report.generatedAt)} · $REPORT_DISCLAIMER")
    pages.header(report)
    when (report.style) {
      ReportStyle.MEDICAL -> {
        pages.summary(report)
        pages.calendar(report)
        pages.log(report)
      }
      ReportStyle.THERAPY -> {
        pages.summary(report)
        pages.experiences(report)
        pages.calendar(report)
      }
    }
    pages.finish()
    doc.writeTo(out)
  } finally {
    doc.close()
  }
}

/** Renders the PDF into a fresh share snapshot and opens the share sheet. */
suspend fun shareClinicianReportPdf(context: Context, report: ClinicianReport) {
  val uri =
      withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        val file = sharedSnapshot(context, dir, "Substance report", ".pdf")
        FileOutputStream(file).use { writeClinicianReportPdf(report, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      }
  val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, REPORT_TITLE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
  context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareClinicianReportText(context: Context, report: ClinicianReport) {
  val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, REPORT_TITLE)
        putExtra(Intent.EXTRA_TEXT, report.toText())
      }
  context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private class PageWriter(private val doc: PdfDocument, private val footer: String) {
  private var page: PdfDocument.Page? = null
  private var pageNumber = 0
  private var y = MARGIN

  private val canvas
    get() = page!!.canvas

  fun finish() {
    page?.let {
      val footerY = PAGE_HEIGHT - MARGIN + 8f
      it.canvas.drawText(
          TextUtils.ellipsize(footer, greyPaint, CONTENT_WIDTH - 50f, TextUtils.TruncateAt.END)
              .toString(),
          MARGIN,
          footerY,
          greyPaint,
      )
      val number = "Page $pageNumber"
      it.canvas.drawText(
          number,
          PAGE_WIDTH - MARGIN - greyPaint.measureText(number),
          footerY,
          greyPaint,
      )
      doc.finishPage(it)
    }
    page = null
  }

  /** Starts a new page unless [height] still fits on the current one. */
  fun ensure(height: Float) {
    if (page != null && y + height <= PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) return
    finish()
    pageNumber++
    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
    y = MARGIN
  }

  fun space(height: Float) {
    y += height
  }

  /** Wrapped text; long paragraphs continue on the next page line by line. */
  fun text(text: String, paint: TextPaint, indent: Float = 0f) {
    if (text.isEmpty()) return
    val layout = layout(text, paint, CONTENT_WIDTH - indent)
    for (line in 0 until layout.lineCount) {
      val height = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
      ensure(height)
      drawLine(layout, line, MARGIN + indent, y)
      y += height
    }
    y += 3f
  }

  fun heading(text: String) {
    space(8f)
    ensure(headingPaint.textSize * 3) // keep a heading with the lines below it
    text(text, headingPaint)
    space(2f)
  }

  /** A table row; cells wrap within their column. */
  fun row(cells: List<String>, widths: List<Float>, paint: TextPaint) {
    val layouts = cells.zip(widths) { cell, width -> layout(cell, paint, width - 4f) }
    val height = layouts.maxOf { it.height }.toFloat() + 4f
    ensure(height)
    var x = MARGIN
    layouts.zip(widths) { layout, width ->
      for (line in 0 until layout.lineCount) {
        drawLine(layout, line, x, y + 2f + layout.getLineTop(line))
      }
      x += width
    }
    y += height
    canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
  }

  fun header(report: ClinicianReport) {
    ensure(0f)
    text(REPORT_TITLE, titlePaint)
    if (report.patientName.isNotEmpty()) text("Name: ${report.patientName}", boldPaint)
    text("Period: ${report.periodLine}", bodyPaint)
    text(REPORT_DISCLAIMER, smallPaint)
  }

  fun summary(report: ClinicianReport) {
    heading("Substances")
    if (report.substances.isEmpty()) {
      text("No use recorded in this period.", bodyPaint)
      return
    }
    val widths = listOf(0.2f, 0.08f, 0.08f, 0.16f, 0.32f, 0.16f).map { it * CONTENT_WIDTH }
    row(
        listOf("Substance", "Days", "Times", "Route", "Dose per use", "Last use"),
        widths,
        boldPaint,
    )
    report.substances.forEach {
      row(
          listOf(
              it.name,
              it.daysUsed.toString(),
              it.times.toString(),
              it.routes.joinToString(", "),
              it.doseText(),
              report.date(it.lastUse),
          ),
          widths,
          bodyPaint,
      )
    }
  }

  /** Timeline Followback style month grids: which substances were used on each day. */
  fun calendar(report: ClinicianReport) {
    if (report.days.isEmpty()) return
    val last = YearMonth.from(report.to)
    val first = maxOf(YearMonth.from(report.from), last.minusMonths(MAX_CALENDAR_MONTHS - 1L))
    heading("Calendar")
    if (first != YearMonth.from(report.from)) {
      text("Shows the last $MAX_CALENDAR_MONTHS months of the period.", smallPaint)
    }
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val cellWidth = CONTENT_WIDTH / 7
    var month = first
    while (month <= last) {
      val offset = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
      val weeks = (offset + month.lengthOfMonth() + 6) / 7
      ensure(boldPaint.textSize * 4 + weeks * CELL_HEIGHT)
      text(
          "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
          boldPaint,
      )
      for (i in 0 until 7) {
        val name =
            firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        canvas.drawText(name, MARGIN + i * cellWidth + 2f, y + smallPaint.textSize, smallPaint)
      }
      y += smallPaint.textSize + 4f
      for (dayOfMonth in 1..month.lengthOfMonth()) {
        val date = month.atDay(dayOfMonth)
        val index = offset + dayOfMonth - 1
        val left = MARGIN + (index % 7) * cellWidth
        val top = y + (index / 7) * CELL_HEIGHT
        val names = report.days[date].orEmpty()
        if (names.isNotEmpty()) {
          canvas.drawRect(left, top, left + cellWidth, top + CELL_HEIGHT, usedDayPaint)
        }
        canvas.drawRect(left, top, left + cellWidth, top + CELL_HEIGHT, linePaint)
        val outside = date < report.from || date > report.to
        canvas.drawText(
            dayOfMonth.toString(),
            left + 2f,
            top + smallPaint.textSize + 1f,
            if (outside) greyPaint else dayNumberPaint,
        )
        val shown =
            if (names.size > NAMES_PER_CELL) {
              names.take(NAMES_PER_CELL - 1) + "+${names.size - NAMES_PER_CELL + 1} more"
            } else {
              names
            }
        shown.forEachIndexed { i, name ->
          canvas.drawText(
              TextUtils.ellipsize(name, smallPaint, cellWidth - 4f, TextUtils.TruncateAt.END)
                  .toString(),
              left + 2f,
              top + (i + 2) * (smallPaint.textSize + 2f) + 2f,
              smallPaint,
          )
        }
      }
      y += weeks * CELL_HEIGHT + 10f
      month = month.plusMonths(1)
    }
  }

  fun log(report: ClinicianReport) {
    if (report.ingestions.isEmpty()) return
    heading("All entries")
    val widths = listOf(0.24f, 0.26f, 0.32f, 0.18f).map { it * CONTENT_WIDTH }
    row(listOf("Time", "Substance", "Dose", "Route"), widths, boldPaint)
    report.ingestions.forEach {
      row(
          listOf(report.dateTime(it.time), it.substanceName, it.dose, it.route),
          widths,
          bodyPaint,
      )
    }
  }

  fun experiences(report: ClinicianReport) {
    if (report.experiences.isEmpty()) return
    heading("Experiences")
    report.experiences.forEach { exp ->
      space(6f)
      ensure(boldPaint.textSize * 4)
      val rating = exp.ratings.takeIf { it.isNotEmpty() }?.joinToString(" ", "  ·  rating ")
      text("${report.date(exp.start)}  ${exp.title}${rating.orEmpty()}", boldPaint)
      exp.ingestions.forEach {
        text(
            "${report.time(it.time)}  ${it.dose} ${it.substanceName}, ${it.route}",
            bodyPaint,
            indent = 8f,
        )
      }
      text(exp.notes, bodyPaint, indent = 8f)
      exp.timedNotes.forEach {
        text("${report.dateTime(it.time)}  ${it.note}", smallPaint, indent = 8f)
      }
    }
  }

  private fun drawLine(layout: StaticLayout, line: Int, x: Float, top: Float) {
    val paint = layout.paint
    canvas.drawText(
        layout.text,
        layout.getLineStart(line),
        layout.getLineVisibleEnd(line),
        x,
        top - layout.getLineTop(line) + layout.getLineBaseline(line),
        paint,
    )
  }
}

private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()
