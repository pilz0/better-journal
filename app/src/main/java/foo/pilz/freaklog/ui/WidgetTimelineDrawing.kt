/*
 * Copyright (c) 2024-2025.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.shapeAlpha
import foo.pilz.freaklog.ui.theme.md_theme_dark_primary
import foo.pilz.freaklog.ui.theme.md_theme_light_primary
import java.io.File
import java.io.FileOutputStream

/**
 * Constants shared by the widgets that draw the timeline graph onto a
 * [android.graphics.Canvas]. Kept here (rather than inline) so the heatmap
 * widget can reuse the exact same rendering as the timeline widget.
 */
internal object WidgetTimelineDrawingConstants {
    const val PEAK_HEIGHT_FRACTION = 0.85f
    const val STROKE_WIDTH = 5f
    const val CORNER_PATH_EFFECT_RADIUS = 15f
    const val INGESTION_DOT_RADIUS = 7f
}

/**
 * Render a [WidgetTimelineModel] to a PNG file at `cacheDir/<fileName>` and
 * return the absolute path. Returns `null` if the model has no content.
 *
 * This is the same renderer used by the timeline widget, extracted so the
 * heatmap widget can also display a timeline graph of substances currently
 * being ingested.
 */
internal fun renderWidgetTimelineToFile(
    context: Context,
    model: WidgetTimelineModel,
    fileName: String,
    isDarkMode: Boolean,
    width: Int = 1200,
    height: Int = 320,
): String? {
    if (!model.hasContent) return null

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    drawWidgetTimeline(
        canvas = canvas,
        model = model,
        isDarkMode = isDarkMode,
        width = width.toFloat(),
        height = height.toFloat(),
    )

    val file = File(context.cacheDir, fileName)
    return try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    } catch (_: Throwable) {
        null
    } finally {
        bitmap.recycle()
    }
}

/**
 * Draw the timeline graph onto an existing [Canvas]. Extracted so callers
 * that already manage their own bitmap (e.g. the heatmap widget which stacks
 * the timeline beneath the heatmap) can reuse the exact same rendering.
 */
internal fun drawWidgetTimeline(
    canvas: Canvas,
    model: WidgetTimelineModel,
    isDarkMode: Boolean,
    width: Float,
    height: Float,
) {
    val paddingLeft = 24f
    val paddingRight = 24f
    val paddingTop = 18f
    val labelHeight = 36f
    val baselineY = height - paddingTop - labelHeight
    val graphTop = paddingTop
    val graphHeight = baselineY - graphTop
    val graphLeft = paddingLeft
    val graphRight = width - paddingRight
    val graphWidth = graphRight - graphLeft

    val widthInSeconds = model.widthInSeconds
    fun secToPixel(sec: Float): Float = graphLeft + (sec / widthInSeconds) * graphWidth

    // ---- background grid + hour ticks ----
    val gridColor = if (isDarkMode) {
        md_theme_dark_primary.toArgb()
    } else {
        md_theme_light_primary.toArgb()
    }
    val gridPaint = Paint().apply {
        color = Color.argb(45, Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    val baselinePaint = Paint().apply {
        color = Color.argb(120, Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    val axis = foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.AxisDrawable(
        startTime = model.windowStart,
        widthInSeconds = widthInSeconds,
    )
    val pixelsPerSec = graphWidth / widthInSeconds
    val fullHours = axis.getFullHours(pixelsPerSec = pixelsPerSec, widthInPixels = graphWidth)

    for (fullHour in fullHours) {
        val x = graphLeft + fullHour.distanceFromStart
        canvas.drawLine(x, graphTop, x, baselineY, gridPaint)
    }
    canvas.drawLine(graphLeft, baselineY, graphRight, baselineY, baselinePaint)

    // ---- effect curves ----
    val peakHeight = graphHeight * WidgetTimelineDrawingConstants.PEAK_HEIGHT_FRACTION

    for (group in model.groups) {
        if (group.points.size < 2) continue
        val androidColor = group.color.getComposeColor(isDarkMode).toArgb()

        val path = Path()
        var started = false
        var firstX = graphLeft
        var lastX = graphLeft
        for (p in group.points) {
            val x = secToPixel(p.secondsFromStart).coerceIn(graphLeft, graphRight)
            val y = baselineY - p.height * peakHeight
            if (!started) {
                path.moveTo(x, y)
                firstX = x
                started = true
            } else {
                path.lineTo(x, y)
            }
            lastX = x
        }
        if (!started) continue

        val strokePaint = Paint().apply {
            this.color = androidColor
            style = Paint.Style.STROKE
            strokeWidth = WidgetTimelineDrawingConstants.STROKE_WIDTH
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = if (group.isComplete) {
                android.graphics.CornerPathEffect(WidgetTimelineDrawingConstants.CORNER_PATH_EFFECT_RADIUS)
            } else {
                val dashLength = WidgetTimelineDrawingConstants.STROKE_WIDTH * 4
                val gapLength = WidgetTimelineDrawingConstants.STROKE_WIDTH * 3
                android.graphics.DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
            }
        }

        val fillPaint = Paint().apply {
            this.color = Color.argb(
                (shapeAlpha * 255).toInt(),
                Color.red(androidColor),
                Color.green(androidColor),
                Color.blue(androidColor)
            )
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val fillPath = Path(path)
        fillPath.lineTo(lastX, baselineY + strokePaint.strokeWidth / 2f)
        fillPath.lineTo(firstX, baselineY + strokePaint.strokeWidth / 2f)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, strokePaint)

        val dotPaint = Paint().apply {
            this.color = androidColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val dotOutlinePaint = Paint().apply {
            color = if (isDarkMode) Color.argb(180, 0, 0, 0) else Color.argb(180, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        for (sec in group.ingestionDotsSecondsFromStart) {
            if (sec < 0f || sec > widthInSeconds) continue
            val dx = secToPixel(sec).coerceIn(graphLeft, graphRight)
            canvas.drawCircle(dx, baselineY, WidgetTimelineDrawingConstants.INGESTION_DOT_RADIUS, dotPaint)
            canvas.drawCircle(dx, baselineY, WidgetTimelineDrawingConstants.INGESTION_DOT_RADIUS, dotOutlinePaint)
        }
    }

    // ---- "now" indicator ----
    val nowSec = model.nowSecondsFromStart
    if (nowSec in 0f..widthInSeconds) {
        val nowX = secToPixel(nowSec)
        val nowPaint = Paint().apply {
            color = if (isDarkMode) Color.WHITE else Color.BLACK
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        canvas.drawLine(nowX, graphTop, nowX, baselineY, nowPaint)
    }

    // ---- labels ----
    val textPaint = Paint().apply {
        color = if (isDarkMode) Color.argb(200, 255, 255, 255) else Color.argb(200, 0, 0, 0)
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val labelY = (height - paddingTop / 2f).coerceAtMost(height - 6f)
    for (fullHour in fullHours) {
        val x = graphLeft + fullHour.distanceFromStart
        canvas.drawText(fullHour.label, x, labelY, textPaint)
    }
    if (nowSec in 0f..widthInSeconds) {
        val nowX = secToPixel(nowSec)
        val nowLabelPaint = Paint(textPaint).apply {
            color = if (isDarkMode) Color.WHITE else Color.BLACK
            isFakeBoldText = true
        }
        canvas.drawText("now", nowX, labelY, nowLabelPaint)
    }
}

/**
 * Convenience: convert an [AdaptiveColor] to an Android [Int] colour, mirroring
 * the helper in [WidgetProvider].
 */
internal fun AdaptiveColor.toAndroidArgb(isDarkTheme: Boolean): Int =
    getComposeColor(isDarkTheme).toArgb()
