/*
 * Copyright (c) 2026.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.substances.classes.roa.curve

/**
 * A continuous, pure-Kotlin model of a single ingestion's projected effect curve.
 *
 * All times are expressed in seconds relative to a shared "graph origin" (typically the earliest
 * ingestion in a journal entry). This allows multiple curves to be summed on a common sample grid.
 *
 * The curve is zero before [ingestionStartSec] and decays asymptotically to zero after the peak;
 * [curveEndSec] is the time at which the value is considered visually negligible (default 5 % of
 * the peak). The maximum value of [valueAt] is approximately [peakHeight].
 */
interface IngestionCurve {
    /** Time at which the dose is administered (point ingestion) or the infusion begins. */
    val ingestionStartSec: Float

    /** End of the infusion window. Equal to [ingestionStartSec] for point ingestions. */
    val ingestionEndSec: Float

    /** Time at which the curve falls (and stays) below the visual threshold of the peak. */
    val curveEndSec: Float

    /** Peak (maximum) value of the curve. */
    val peakHeight: Float

    /**
     * Whether the underlying PsychonautWiki data was complete enough to plot a confident curve.
     * When false, callers should render the curve with a dotted style to signal uncertainty.
     */
    val isCertain: Boolean

    /** Curve value at the given absolute (graph-relative) time in seconds. Always >= 0. */
    fun valueAt(tSec: Float): Float
}
