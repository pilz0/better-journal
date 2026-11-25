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
import androidx.core.graphics.ColorUtils
import foo.pilz.freaklog.data.substanceshare.sharedSnapshot
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A4 in PostScript points.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val FOOTER_HEIGHT = 28f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
private const val CELL_HEIGHT = 50f
private const val NAMES_PER_CELL = 3
private const val CELL_PADDING = 4f
private const val FIELD_PADDING = 5f

// ponytail: the calendar stops at 12 months so an "everything" report stays printable; the
// summary, pattern and log still cover the whole period.
private const val MAX_CALENDAR_MONTHS = 12

// One square per day up to four months, otherwise several days per square.
private const val MAX_PATTERN_BUCKETS = 120

// Black on white with grey rules, like a lab report: it has to survive a black-and-white
// printer or fax, so nothing depends on colour.
private val INK = Color.rgb(26, 26, 26)
private val MUTED = Color.rgb(95, 95, 95)
private val FAINT = Color.rgb(158, 158, 158)
private val HAIRLINE = Color.rgb(190, 190, 190)
private val FILL = Color.rgb(236, 236, 236)
private val FILL_LIGHT = Color.rgb(247, 247, 247)

private fun textPaint(size: Float, bold: Boolean = false, color: Int = INK) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = size
      typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
      this.color = color
    }

private fun solid(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun stroke(color: Int, width: Float = 0.5f) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      style = Paint.Style.STROKE
      strokeWidth = width
    }

private val titlePaint = textPaint(15f, bold = true)
private val headingPaint = textPaint(9.5f, bold = true).apply { letterSpacing = 0.06f }
private val fieldLabelPaint =
    textPaint(6.5f, bold = true, color = MUTED).apply { letterSpacing = 0.08f }
private val fieldValuePaint = textPaint(9.5f, bold = true)
private val figurePaint = textPaint(12f, bold = true)
private val bodyPaint = textPaint(9f)
private val boldPaint = textPaint(9f, bold = true)
private val tableHeadPaint = textPaint(8f, bold = true)
private val smallPaint = textPaint(7.5f, color = MUTED)
private val faintPaint = textPaint(7.5f, color = FAINT)
private val dayNumberPaint = textPaint(7.5f, bold = true)
private val experienceDatePaint = textPaint(8f, bold = true, color = MUTED)
private val experienceTitlePaint = textPaint(10.5f, bold = true)
private val linePaint = stroke(HAIRLINE)
private val rulePaint = stroke(INK, 0.75f)

/** Writes [report] as an A4 PDF. Page 1 always starts with the header and key figures. */
fun writeClinicianReportPdf(report: ClinicianReport, out: OutputStream) {
  // "Page x of y" needs the total, so the first pass only counts pages.
  val total = render(report, totalPages = null) {}
  render(report, total) { it.writeTo(out) }
}

private fun render(report: ClinicianReport, totalPages: Int?, write: (PdfDocument) -> Unit): Int {
  val doc = PdfDocument()
  try {
    val pages = PageWriter(doc, report, totalPages)
    pages.header()
    pages.keyFigures()
    when (report.style) {
      ReportStyle.MEDICAL -> {
        pages.summary()
        pages.pattern()
        pages.calendar()
        pages.log()
      }
      ReportStyle.THERAPY -> {
        pages.summary()
        pages.pattern()
        pages.experiences()
        pages.calendar()
      }
    }
    pages.finish()
    write(doc)
    return pages.pageNumber
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

@Suppress("TooManyFunctions")
private class PageWriter(
    private val doc: PdfDocument,
    private val report: ClinicianReport,
    private val totalPages: Int?,
) {
  private var page: PdfDocument.Page? = null
  var pageNumber = 0
    private set

  private var y = MARGIN

  /** Redraws a table's header row at the top of each page the table continues on. */
  private var tableHeader: (() -> Unit)? = null

  private val canvas
    get() = checkNotNull(page) { "No active PDF page" }.canvas

  fun finish() {
    page?.let {
      val footerY = PAGE_HEIGHT - MARGIN + 8f
      it.canvas.drawLine(MARGIN, footerY - 11f, MARGIN + CONTENT_WIDTH, footerY - 11f, linePaint)
      val number = totalPages?.let { total -> "Page $pageNumber of $total" } ?: "Page $pageNumber"
      val numberWidth = smallPaint.measureText(number)
      val footer = "Generated ${report.date(report.generatedAt)} · $REPORT_DISCLAIMER"
      it.canvas.drawText(
          TextUtils.ellipsize(
                  footer,
                  smallPaint,
                  CONTENT_WIDTH - numberWidth - 12f,
                  TextUtils.TruncateAt.END,
              )
              .toString(),
          MARGIN,
          footerY,
          smallPaint,
      )
      it.canvas.drawText(number, PAGE_WIDTH - MARGIN - numberWidth, footerY, smallPaint)
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
    if (pageNumber > 1) {
      // Pages get separated in a paper file; each one says whose report it is.
      val left = listOf(REPORT_TITLE, report.patientName).filter { it.isNotEmpty() }
      canvas.drawText(left.joinToString(" · "), MARGIN, y, smallPaint)
      canvas.drawText(
          report.periodRange,
          PAGE_WIDTH - MARGIN - smallPaint.measureText(report.periodRange),
          y,
          smallPaint,
      )
      canvas.drawLine(MARGIN, y + 5f, MARGIN + CONTENT_WIDTH, y + 5f, linePaint)
      y += 16f
      tableHeader?.invoke()
    }
  }

  fun space(height: Float) {
    y += height
  }

  /** Wrapped text; long paragraphs continue on the next page. */
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

  /** An upper-case section title over a rule; [keepWith] is how much must fit below it. */
  fun heading(text: String, keepWith: Float = 40f) {
    space(16f)
    ensure(headingPaint.textSize + 10f + keepWith)
    canvas.drawText(
        text.uppercase(Locale.getDefault()),
        MARGIN,
        y + headingPaint.textSize,
        headingPaint,
    )
    y += headingPaint.textSize + 4f
    canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, rulePaint)
    y += 6f
  }

  /** A boxed row of labelled fields, like the identifier block of a lab report. */
  private fun fields(
      cells: List<Pair<String, String>>,
      valuePaint: TextPaint,
      weights: List<Float> = List(cells.size) { 1f },
  ) {
    val widths = weights.map { it / weights.sum() * CONTENT_WIDTH }
    val values = cells.mapIndexed { i, (_, value) ->
      layout(value, valuePaint, widths[i] - 2 * FIELD_PADDING)
    }
    val valueTop = FIELD_PADDING + fieldLabelPaint.textSize + 4f
    val height = valueTop + values.maxOf { it.height } + FIELD_PADDING
    ensure(height)
    var x = MARGIN
    cells.forEachIndexed { i, (label, _) ->
      if (i > 0) canvas.drawLine(x, y, x, y + height, linePaint)
      canvas.drawText(
          TextUtils.ellipsize(
                  label.uppercase(Locale.getDefault()),
                  fieldLabelPaint,
                  widths[i] - 2 * FIELD_PADDING,
                  TextUtils.TruncateAt.END,
              )
              .toString(),
          x + FIELD_PADDING,
          y + FIELD_PADDING + fieldLabelPaint.textSize,
          fieldLabelPaint,
      )
      val value = values[i]
      for (line in 0 until value.lineCount) {
        drawLine(value, line, x + FIELD_PADDING, y + valueTop + value.getLineTop(line))
      }
      x += widths[i]
    }
    canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + height, rulePaint)
    y += height
  }

  /** A table row; cells wrap within their column. */
  fun row(cells: List<String>, widths: List<Float>, paint: TextPaint, fill: Int? = null) {
    val layouts = cells.mapIndexed { i, cell -> layout(cell, paint, widths[i] - 2 * CELL_PADDING) }
    val height = layouts.maxOf { it.height }.toFloat() + 2 * CELL_PADDING
    ensure(height)
    fill?.let { canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + height, solid(it)) }
    var x = MARGIN
    layouts.forEachIndexed { i, layout ->
      for (line in 0 until layout.lineCount) {
        drawLine(layout, line, x + CELL_PADDING, y + CELL_PADDING + layout.getLineTop(line))
      }
      x += widths[i]
    }
    y += height
    canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
  }

  /** Draws [header] now and again on every page the rows spill onto. */
  private fun table(header: List<String>, widths: List<Float>, rows: () -> Unit) {
    val drawHeader = {
      canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, rulePaint)
      row(header, widths, tableHeadPaint, fill = FILL)
    }
    ensure(tableHeadPaint.textSize * 5)
    drawHeader()
    tableHeader = drawHeader
    rows()
    tableHeader = null
  }

  /** Title over a heavy rule, then the identifier block. */
  fun header() {
    ensure(0f)
    canvas.drawText(REPORT_TITLE, MARGIN, y + titlePaint.textSize, titlePaint)
    y += titlePaint.textSize + 7f
    canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, stroke(INK, 1.5f))
    y += 8f
    fields(
        listOf(
            "Patient" to report.patientName.ifEmpty { "Not stated" },
            "Reporting period" to report.periodRange,
            "Generated" to report.date(report.generatedAt),
        ),
        fieldValuePaint,
        weights = listOf(0.32f, 0.46f, 0.22f),
    )
    space(5f)
    text(REPORT_DISCLAIMER, smallPaint)
  }

  /** The numbers a clinician asks first: how often, and how long without. */
  fun keyFigures() {
    val period = report.periodDays
    val usedDays = report.days.keys.count { it in report.from..report.to }
    val useFreePercent = if (period > 0) report.useFreeDays * 100 / period else 0
    val longest = report.longestUseFreeStretch
    heading("Key figures")
    fields(
        listOf(
            "Days with use" to "$usedDays of $period",
            "Days without use" to "${report.useFreeDays} ($useFreePercent %)",
            "Longest use-free run" to "$longest ${if (longest == 1L) "day" else "days"}",
            "Substances" to "${report.substances.size}",
            "Entries" to "${report.ingestions.size}",
        ),
        figurePaint,
    )
  }

  fun summary() {
    heading("Substances")
    if (report.substances.isEmpty()) {
      text("No use recorded in this period.", bodyPaint)
      return
    }
    val widths = listOf(0.19f, 0.07f, 0.1f, 0.12f, 0.13f, 0.26f, 0.13f).map { it * CONTENT_WIDTH }
    var trends = false
    table(
        listOf(
            "Substance",
            "Days",
            "Times (per day)",
            "Days/week",
            "Route",
            "Dose per use",
            "Last use",
        ),
        widths,
    ) {
      report.substances.forEach { substance ->
        val perUseDay = substance.times.toDouble() / substance.daysUsed
        val halves = report.daysPerWeekByHalf(substance.name)
        trends = trends || halves != null
        row(
            listOf(
                substance.name,
                substance.daysUsed.toString(),
                if (perUseDay > 1.0) {
                  "${substance.times} (${perUseDay.oneDecimal()})"
                } else {
                  "${substance.times}"
                },
                halves?.let { (a, b) -> "${a.oneDecimal()} → ${b.oneDecimal()}" }
                    ?: (substance.daysUsed * 7.0 / report.periodDays).oneDecimal(),
                substance.routes.joinToString(", "),
                substance.doseText(),
                report.date(substance.lastUse),
            ),
            widths,
            bodyPaint,
        )
      }
    }
    space(3f)
    text(
        listOfNotNull(
                "Days: days with any use. Times (per day): entries, and entries per day with use.",
                "Days/week: first half → second half of the period.".takeIf { trends },
            )
            .joinToString(" "),
        smallPaint,
    )
  }

  /** One strip per substance across the period, so patterns and co-use stand out. */
  fun pattern() {
    if (report.substances.isEmpty()) return
    val pattern = report.usePattern(MAX_PATTERN_BUCKETS)
    val labelWidth = 110f
    val gridWidth = CONTENT_WIDTH - labelWidth
    val cell = gridWidth / pattern.any.size
    val rowHeight = 11f
    heading("Pattern over time", keepWith = rowHeight * 3 + 20f)
    text(
        if (pattern.bucketDays == 1) {
          "Each square is one day, filled on days with use."
        } else {
          "Each square is ${pattern.bucketDays} days; the darker, the more of them had use."
        },
        smallPaint,
    )
    fun strip(label: String, values: List<Float>, paint: TextPaint) {
      ensure(rowHeight + 3f)
      canvas.drawText(
          TextUtils.ellipsize(label, paint, labelWidth - 6f, TextUtils.TruncateAt.END).toString(),
          MARGIN,
          y + rowHeight / 2 + paint.textSize / 2 - 1f,
          paint,
      )
      val gap = (cell * 0.15f).coerceAtMost(1f)
      values.forEachIndexed { i, value ->
        val left = MARGIN + labelWidth + i * cell
        val fill =
            if (value > 0f) ColorUtils.blendARGB(FILL, INK, 0.3f + 0.7f * value) else FILL_LIGHT
        canvas.drawRect(left, y, left + cell - gap, y + rowHeight, solid(fill))
      }
      y += rowHeight + 3f
    }
    strip("Any substance", pattern.any, boldPaint)
    report.substances.forEach { strip(it.name, pattern.bySubstance[it.name].orEmpty(), bodyPaint) }
    // Month labels under the grid.
    ensure(smallPaint.textSize + 4f)
    var month = YearMonth.from(report.from).plusMonths(1)
    val months = YearMonth.from(report.from).until(YearMonth.from(report.to), ChronoUnit.MONTHS)
    val step = (months / 12 + 1).toInt()
    var index = 0
    val formatter = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
    while (month.atDay(1) <= report.to) {
      if (index % step == 0) {
        val offset =
            ChronoUnit.DAYS.between(report.from, month.atDay(1)).toFloat() / report.periodDays
        val x = MARGIN + labelWidth + offset * gridWidth
        canvas.drawLine(x, y - 2f, x, y + 2f, linePaint)
        canvas.drawText(
            month.atDay(1).format(formatter),
            x + 1.5f,
            y + smallPaint.textSize + 1f,
            smallPaint,
        )
      }
      index++
      month = month.plusMonths(1)
    }
    y += smallPaint.textSize + 4f
  }

  /** Timeline Followback style month grids: which substances were used on each day. */
  fun calendar() {
    if (report.days.isEmpty()) return
    val last = YearMonth.from(report.to)
    val first = maxOf(YearMonth.from(report.from), last.minusMonths(MAX_CALENDAR_MONTHS - 1L))
    heading("Calendar", keepWith = boldPaint.textSize * 3 + 5 * CELL_HEIGHT)
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
      val used = (1..month.lengthOfMonth()).count { report.days.containsKey(month.atDay(it)) }
      text(
          "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}" +
              "  ·  $used ${if (used == 1) "day" else "days"} with use",
          boldPaint,
      )
      for (i in 0 until 7) {
        val name =
            firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        canvas.drawText(name, MARGIN + i * cellWidth + 3f, y + smallPaint.textSize, smallPaint)
      }
      y += smallPaint.textSize + 4f
      for (dayOfMonth in 1..month.lengthOfMonth()) {
        calendarCell(month.atDay(dayOfMonth), offset + dayOfMonth - 1, cellWidth)
      }
      y += weeks * CELL_HEIGHT + 10f
      month = month.plusMonths(1)
    }
  }

  private fun calendarCell(date: LocalDate, index: Int, cellWidth: Float) {
    val left = MARGIN + (index % 7) * cellWidth
    val top = y + (index / 7) * CELL_HEIGHT
    val names = report.days[date].orEmpty()
    val outside = date < report.from || date > report.to
    when {
      outside -> canvas.drawRect(left, top, left + cellWidth, top + CELL_HEIGHT, solid(FILL_LIGHT))
      names.isNotEmpty() ->
          canvas.drawRect(left, top, left + cellWidth, top + CELL_HEIGHT, solid(FILL))
    }
    canvas.drawRect(left, top, left + cellWidth, top + CELL_HEIGHT, linePaint)
    canvas.drawText(
        date.dayOfMonth.toString(),
        left + 3f,
        top + smallPaint.textSize + 2f,
        if (outside) faintPaint else dayNumberPaint,
    )
    val shown =
        if (names.size > NAMES_PER_CELL) {
          names.take(NAMES_PER_CELL - 1) + "+${names.size - NAMES_PER_CELL + 1} more"
        } else {
          names
        }
    shown.forEachIndexed { i, name ->
      val baseline = top + (i + 2) * (smallPaint.textSize + 2.5f) + 2f
      canvas.drawText(
          TextUtils.ellipsize(name, smallPaint, cellWidth - 6f, TextUtils.TruncateAt.END)
              .toString(),
          left + 3f,
          baseline,
          smallPaint,
      )
    }
  }

  /** Every entry; the date is printed once per day and days alternate in shade. */
  fun log() {
    if (report.ingestions.isEmpty()) return
    heading("All entries")
    val widths = listOf(0.19f, 0.11f, 0.28f, 0.26f, 0.16f).map { it * CONTENT_WIDTH }
    table(listOf("Date", "Time", "Substance", "Dose", "Route"), widths) {
      report.ingestions
          .groupBy { it.time.atZone(report.zone).toLocalDate() }
          .entries
          .forEachIndexed { dayIndex, (day, entries) ->
            entries.forEachIndexed { i, entry ->
              row(
                  listOf(
                      if (i == 0) day.format(reportDayFormat) else "",
                      report.time(entry.time),
                      entry.substanceName,
                      entry.dose,
                      entry.route,
                  ),
                  widths,
                  bodyPaint,
                  fill = if (dayIndex % 2 == 1) FILL_LIGHT else null,
              )
            }
          }
    }
  }

  fun experiences() {
    if (report.experiences.isEmpty()) return
    heading("Experiences")
    report.experiences.forEachIndexed { i, exp ->
      if (i > 0) {
        space(4f)
        ensure(1f)
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
        space(8f)
      }
      ensure(
          experienceDatePaint.textSize + experienceTitlePaint.textSize * 2 + bodyPaint.textSize * 2
      )
      val rating = exp.ratings.takeIf { it.isNotEmpty() }?.joinToString(" ", "  ·  Shulgin rating ")
      text(
          "${exp.start.atZone(report.zone).toLocalDate().format(reportDayFormat)}${rating.orEmpty()}",
          experienceDatePaint,
      )
      text(exp.title.ifBlank { "Untitled" }, experienceTitlePaint)
      exp.ingestions.forEach {
        text(
            "${report.time(it.time)}   ${it.dose} ${it.substanceName}, ${it.route}",
            bodyPaint,
            indent = 10f,
        )
      }
      if (exp.notes.isNotEmpty()) {
        space(2f)
        text(exp.notes, bodyPaint)
      }
      exp.timedNotes.forEach {
        text("${report.dateTime(it.time)}   ${it.note}", smallPaint, indent = 10f)
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

private fun Double.oneDecimal(): String = String.format(Locale.getDefault(), "%.1f", this)

private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()
