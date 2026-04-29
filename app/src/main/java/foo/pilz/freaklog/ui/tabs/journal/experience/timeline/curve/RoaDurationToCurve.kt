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
    if (ingestionEndSec < ingestionStartSec) return null

    val w = weightedLine.horizontalWeight.coerceIn(0f, 1f).toDouble()
    val onsetSec = onset?.interpolatedSec(0.5)
    val comeupSec = comeup?.interpolatedSec(0.5)
    val peakSec = peak?.interpolatedSec(w)
    val offsetSec = offset?.interpolatedSec(w)
    val totalSec = total?.interpolatedSec(w)

    // Choose tmax (time of peak relative to ingestion start)
    val tmaxFromPhases = when {
        onsetSec != null && comeupSec != null -> onsetSec + comeupSec
        onsetSec != null -> onsetSec * 1.5
        else -> null
    }

    // Choose total duration (from ingestion start to negligible-effect)
    val totalFromPhases = listOfNotNull(onsetSec, comeupSec, peakSec, offsetSec)
        .takeIf { it.isNotEmpty() }
        ?.sum()
    val targetTotal = totalSec
        ?: totalFromPhases
        ?: tmaxFromPhases?.let { it * 4.0 }
        ?: return null

    val tmax = (tmaxFromPhases ?: (targetTotal * 0.25))
        .coerceAtLeast(60.0) // never less than one minute
        .coerceAtMost(targetTotal * 0.49) // tmax must be < total

    // ke chosen so the unit Bateman shape decays to TAIL_EPSILON at targetTotal.
    // The tail is dominated by ke, so e^(-ke·targetTotal) ≈ TAIL_EPSILON / unscaledPeak ≈ TAIL_EPSILON.
    val ke = (-ln(TAIL_EPSILON) / targetTotal).coerceAtLeast(1e-9)

    val ka = BatemanCurve.solveKaForTmax(ke = ke, tmaxSec = tmax)
        // If tmax is too close to 1/ke, shrink it until a solution exists.
        ?: BatemanCurve.solveKaForTmax(ke = ke, tmaxSec = 0.5 / ke)
        ?: return null

    // We require at least three of {onset, comeup, peak, offset} (i.e. enough phases to define
    // both the rise and the fall) to call the curve "certain". A `total`-only fallback is dotted.
    val phaseCount = listOf(onsetSec, comeupSec, peakSec, offsetSec).count { it != null }
    val isCertain = phaseCount >= 3

    // Cap on how far past the nominal total the renderer is allowed to draw the tail. We allow
    // a small overshoot so the dotted tail visibly approaches zero rather than being clipped at
    // exactly TAIL_EPSILON. The fallback (no `total`) gets a larger multiplier because the
    // estimated total is itself less reliable.
    val maxLength = totalSec?.let { (it * 1.2).toFloat() }
        ?: ((targetTotal * 1.5).toFloat())

    return BatemanCurve(
        ka = ka,
        ke = ke,
        ingestionStartSec = ingestionStartSec,
        ingestionEndSec = ingestionEndSec,
        peakHeight = max(0f, weightedLine.height),
        isCertain = isCertain,
        tailEpsilon = TAIL_EPSILON,
        maxCurveLengthSec = maxLength,
    )
}

private fun DurationRange.interpolatedSec(weightZeroToOne: Double): Double? {
    val v = interpolateAtValueInSeconds(weightZeroToOne.toFloat()) ?: return null
    return v.toDouble()
}
