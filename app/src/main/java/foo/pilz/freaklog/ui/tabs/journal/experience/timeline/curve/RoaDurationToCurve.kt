/*
 * Copyright (c) 2026.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.timeline.curve

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.data.substances.classes.roa.curve.BatemanCurve
import foo.pilz.freaklog.data.substances.classes.roa.curve.IngestionCurve
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.WeightedLine
import java.time.Duration
import java.time.Instant
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Threshold (fraction of peak) used in this file to derive `ke` from the requested total
 * duration. The same value is forwarded to [BatemanCurve.tailEpsilon] so the renderer agrees
 * on where the visual tail ends.
 */
private const val TAIL_EPSILON = 0.05

/**
 * Build an [IngestionCurve] for [weightedLine] using whichever of `onset` / `comeup` / `peak` /
 * `offset` / `total` are defined on this [RoaDuration].
 *
 * Degradation order:
 * 1. `total` is taken as the elimination time scale when present; otherwise the sum of
 *    onset+comeup+peak+offset (whichever subset is available) is used; otherwise `onset+comeup`
 *    (or `onset` alone) gets multiplied by a small factor so the curve has *some* tail.
 * 2. `tmax` (time of peak) defaults to `onset.center + comeup.center`. When neither is available
 *    we fall back to a quarter of the chosen total.
 * 3. The "isCertain" flag is true only when at least three of the four phases (onset, comeup,
 *    peak, offset) are known — i.e. when we are not extrapolating most of the shape.
 */
fun RoaDuration.toIngestionCurve(
    weightedLine: WeightedLine,
    graphStart: Instant,
): IngestionCurve? {
    val ingestionStartSec =
        Duration.between(graphStart, weightedLine.startTime).seconds.toFloat()
    val ingestionEndSec = weightedLine.endTime?.let {
        Duration.between(graphStart, it).seconds.toFloat()
    } ?: ingestionStartSec
    if (!ingestionStartSec.isFinite() || !ingestionEndSec.isFinite() || ingestionEndSec < ingestionStartSec) return null

    val w = weightedLine.horizontalWeight.coerceIn(0f, 1f).toDouble()
    val params = resolveParameters(w) ?: return null

    val totalSec = total?.interpolatedSec(w)
    val maxLength = totalSec?.let { (it * 1.2).toFloat() }
        ?: ((params.targetTotal * 1.5).toFloat())

    val peakHeight = if (weightedLine.height.isFinite()) max(0f, weightedLine.height) else 0f
    return BatemanCurve(
        ka = params.ka,
        ke = params.ke,
        ingestionStartSec = ingestionStartSec,
        ingestionEndSec = ingestionEndSec,
        peakHeight = peakHeight,
        isCertain = params.isCertain,
        tailEpsilon = TAIL_EPSILON,
        maxCurveLengthSec = maxLength,
    )
}

private data class BatemanParameters(
    val ka: Double,
    val ke: Double,
    val targetTotal: Double,
    val isCertain: Boolean,
)

private fun RoaDuration.resolveParameters(weight: Double): BatemanParameters? {
    val onsetSec = onset?.interpolatedSec(0.5)
    val comeupSec = comeup?.interpolatedSec(0.5)
    val peakSec = peak?.interpolatedSec(weight)
    val offsetSec = offset?.interpolatedSec(weight)
    val totalSec = total?.interpolatedSec(weight)

    val tmaxFromPhases = when {
        onsetSec != null && comeupSec != null -> onsetSec + comeupSec
        comeupSec != null -> comeupSec
        onsetSec != null -> onsetSec * 1.5
        peakSec != null -> peakSec * 0.5
        else -> null
    }

    val totalFromPhases = listOfNotNull(onsetSec, comeupSec, peakSec, offsetSec)
        .takeIf { it.isNotEmpty() }
        ?.sum()
    val targetTotal = totalSec
        ?: totalFromPhases
        ?: tmaxFromPhases?.let { it * 4.0 }
        ?: return null
    if (targetTotal <= 0.0 || !targetTotal.isFinite()) return null

    // A fixed 60 s floor would exceed the ceiling for short totals (e.g. inhaled DMT), so
    // scale it down with the total; capping it at the ceiling keeps coerceIn from throwing.
    val ke = (-ln(TAIL_EPSILON) / targetTotal).coerceAtLeast(1e-9)
    // A Bateman peak can't come later than 1/ke (≈ 0.33 * total here). Clamp just below that
    // limit instead of letting the solver fail, which used to collapse late peaks to 0.5/ke.
    val maxTmax = (0.98 / ke).coerceAtMost(targetTotal * 0.49)
    val minTmax = min(60.0, targetTotal * 0.1).coerceAtLeast(5.0).coerceAtMost(maxTmax)
    val tmax = (tmaxFromPhases ?: (targetTotal * 0.25)).coerceIn(minTmax, maxTmax)
    if (tmax <= 0.0 || !tmax.isFinite() || ke <= 0.0 || !ke.isFinite()) return null

    val ka = BatemanCurve.solveKaForTmax(ke = ke, tmaxSec = tmax)
        ?: BatemanCurve.solveKaForTmax(ke = ke, tmaxSec = 0.95 / ke)
        ?: BatemanCurve.solveKaForTmax(ke = ke, tmaxSec = 0.5 / ke)
        ?: return null
    if (ka <= 0.0 || ka <= ke || !ka.isFinite()) return null

    val phaseCount = listOf(onsetSec, comeupSec, peakSec, offsetSec).count { it != null }
    val isCertain = phaseCount >= 3

    return BatemanParameters(
        ka = ka,
        ke = ke,
        targetTotal = targetTotal,
        isCertain = isCertain,
    )
}

private fun DurationRange.interpolatedSec(weightZeroToOne: Double): Double? {
    val v = interpolateAtValueInSeconds(weightZeroToOne.toFloat()) ?: return null
    return v.toDouble()
}
