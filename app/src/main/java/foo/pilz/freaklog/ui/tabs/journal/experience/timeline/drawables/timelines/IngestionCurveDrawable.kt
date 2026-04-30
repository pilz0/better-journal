/*
 * Copyright (c) 2026.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import foo.pilz.freaklog.data.substances.classes.roa.curve.IngestionCurve
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.dottedStroke
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.TimelineDrawable
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.ingestionDotRadius
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.normalStroke
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.shapeAlpha
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.strokeWidth
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a stack of [IngestionCurve]s sharing the same graph origin.
 *
 * The drawable samples the summed curve at a fixed pixel resolution, so overlapping curves are
 * always combined at the highest visible point — eliminating the "missed peak between sample
 * points" bug of the previous polygonal renderer. The path is drawn with the existing rounded
 * stroke (which already implies smooth corners through `cornerPathEffect`).
 *
 * The stroke is dotted when any of the contributing curves is not "certain" (i.e. PsychonautWiki
 * data was incomplete for at least one ingestion), matching the convention of the previous renderer.
 */
data class IngestionCurveDrawable(
    val curves: List<IngestionCurve>,
) : TimelineDrawable {

    override var referenceHeight: Float = 1f

    override val nonNormalisedHeight: Float = run {
        if (curves.isEmpty()) {
            0f
        } else {
            // Sum of individual peak heights is an upper bound; for visual scaling we use the
            // actual joint maximum found by sampling on a coarse grid.
            sampleJointMax(stepSec = max(1f, (curveSpan() / 600f)))
        }
    }

    override val endOfLineRelativeToStartInSeconds: Float =
        curves.maxOfOrNull { it.curveEndSec } ?: 0f

    private val firstCurveStart: Float = curves.minOfOrNull { it.ingestionStartSec } ?: 0f

    private val isCertain: Boolean = curves.isNotEmpty() && curves.all { it.isCertain }

    private fun curveSpan(): Float =
        max(1f, endOfLineRelativeToStartInSeconds - firstCurveStart)

    private fun sumAt(tSec: Float): Float {
        var sum = 0f
        for (c in curves) sum += c.valueAt(tSec)
        return sum
    }

    private fun sampleJointMax(stepSec: Float): Float {
        var bestVal = 0f
        var t = firstCurveStart
        val end = endOfLineRelativeToStartInSeconds
        while (t <= end) {
            val v = sumAt(t)
            if (v > bestVal) bestVal = v
            t += stepSec
        }
        return bestVal
    }

    override fun drawTimeLine(
        drawScope: DrawScope,
        canvasHeight: Float,
        pixelsPerSec: Float,
        color: Color,
        density: Density,
    ) {
        if (curves.isEmpty()) return
        val pixelStep = 2f // sample every 2 px of canvas width
        val secondsPerStep = if (pixelsPerSec > 0f) pixelStep / pixelsPerSec else 1f
        val firstStartX = firstCurveStart * pixelsPerSec
        val endX = endOfLineRelativeToStartInSeconds * pixelsPerSec
        val refHeight = if (referenceHeight > 0f) referenceHeight else 1f

        val curvePath = Path().apply {
            var t = firstCurveStart
            var first = true
            while (t <= endOfLineRelativeToStartInSeconds + secondsPerStep) {
                val tClamped = min(t, endOfLineRelativeToStartInSeconds)
                val x = tClamped * pixelsPerSec
                val normalised = (sumAt(tClamped) / refHeight).coerceIn(0f, 1f)
                val y = canvasHeight - normalised * canvasHeight
                if (first) {
                    moveTo(x, y)
                    first = false
                } else {
                    lineTo(x, y)
                }
                t += secondsPerStep
            }
        }
        drawScope.drawPath(
            path = curvePath,
            color = color,
            style = if (isCertain) density.normalStroke else density.dottedStroke
        )
        // Filled translucent area under the curve.
        val filledPath = Path().apply {
            addPath(curvePath)
            val alignedBottom = canvasHeight + drawScope.strokeWidth / 2
            lineTo(endX, alignedBottom)
            lineTo(firstStartX, alignedBottom)
            close()
        }
        drawScope.drawPath(
            path = filledPath,
            color = color.copy(alpha = shapeAlpha)
        )
        // Ingestion dots
        for (curve in curves) {
            drawScope.drawCircle(
                color = color,
                radius = density.ingestionDotRadius,
                center = Offset(
                    x = curve.ingestionStartSec * pixelsPerSec,
                    y = canvasHeight
                )
            )
        }
    }
}
