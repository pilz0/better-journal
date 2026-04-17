/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.journal.experience.redose

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import java.time.Instant

/**
 * User-configurable multipliers on the average onset/comeup/peak durations
 * that together define when the app suggests a redose is OK.
 *
 * Default: redoseAt = ingestionTime + onsetAvg + comeupAvg + 0.5 * peakAvg.
 * This corresponds to "shortly after the peak begins to decline" — a
 * reasonable heuristic in the absence of published data. Users can tune
 * this in Settings → Redose.
 */
data class RedoseParameters(
    val onsetFraction: Float,
    val comeupFraction: Float,
    val peakFraction: Float,
) {
    companion object {
        val Default = RedoseParameters(
            onsetFraction = 1.0f,
            comeupFraction = 1.0f,
            peakFraction = 0.5f
        )

        /** Clamp parameters into a sensible range to avoid NaN/weird UI. */
        fun sanitize(onset: Float, comeup: Float, peak: Float) = RedoseParameters(
            onsetFraction = onset.coerceIn(0f, 5f),
            comeupFraction = comeup.coerceIn(0f, 5f),
            peakFraction = peak.coerceIn(0f, 5f)
        )
    }
}

/**
 * Returns the recommended time for a next redose of the same substance/route,
 * or null if the substance's duration data is insufficient to compute one.
 */
fun computeRedoseTime(
    ingestionTime: Instant,
    duration: RoaDuration?,
    params: RedoseParameters
): Instant? {
    if (duration == null) return null
    val onset = avgSeconds(duration.onset)
    val comeup = avgSeconds(duration.comeup)
    val peak = avgSeconds(duration.peak)
    // Need at least some data for a meaningful suggestion.
    if (onset == null && comeup == null && peak == null) return null
    val offsetSec =
        (onset ?: 0f) * params.onsetFraction +
            (comeup ?: 0f) * params.comeupFraction +
            (peak ?: 0f) * params.peakFraction
    if (offsetSec <= 0f) return null
    return ingestionTime.plusSeconds(offsetSec.toLong())
}

/**
 * For a given substance (identified by name and route) find the latest
 * ingestion of that pair and return its redose time. Used by the experience
 * view to show a single chip per (substance, route).
 */
fun computeRedoseForLatest(
    ingestions: List<Ingestion>,
    duration: RoaDuration?,
    params: RedoseParameters
): Instant? {
    val latest = ingestions.maxByOrNull { it.time } ?: return null
    return computeRedoseTime(latest.time, duration, params)
}

private fun avgSeconds(range: DurationRange?): Float? {
    if (range == null) return null
    val min = range.minInSec
    val max = range.maxInSec
    return when {
        min != null && max != null -> (min + max) / 2f
        min != null -> min
        max != null -> max
        else -> null
    }
}
