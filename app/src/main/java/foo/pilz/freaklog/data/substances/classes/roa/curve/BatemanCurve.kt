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

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Bateman two-compartment pharmacokinetic curve.
 *
 * The unscaled shape for a unit point ingestion at time 0 is
 *
 *     f(τ) = e^(-ke·τ) - e^(-ka·τ)    for τ ≥ 0,    with ka > ke > 0.
 *
 * This produces a smooth absorption phase rising to a single maximum at
 * τ_peak = ln(ka/ke) / (ka − ke), followed by an exponential elimination tail.
 *
 * For an infusion over [ingestionStartSec, ingestionEndSec] the curve is the analytical
 * convolution of [f] with a rectangular pulse of duration T = end − start, again normalised so
 * the maximum equals [peakHeight].
 */
data class BatemanCurve(
    val ka: Double,
    val ke: Double,
    override val ingestionStartSec: Float,
    override val ingestionEndSec: Float,
    override val peakHeight: Float,
    override val isCertain: Boolean,
    /** Visual decay threshold (fraction of peak) used to compute [curveEndSec]. */
    private val tailEpsilon: Double = 0.05,
    /** Hard upper bound on [curveEndSec] in seconds (safety net). */
    private val maxCurveLengthSec: Float = Float.MAX_VALUE,
) : IngestionCurve {

    init {
        require(ka.isFinite() && ka > 0.0) { "ka must be finite and positive" }
        require(ke.isFinite() && ke > 0.0) { "ke must be finite and positive" }
        require(ka > ke) { "ka ($ka) must exceed ke ($ke)" }
        require(ingestionStartSec.isFinite()) { "ingestionStartSec must be finite" }
        require(ingestionEndSec.isFinite()) { "ingestionEndSec must be finite" }
        require(ingestionEndSec >= ingestionStartSec) {
            "ingestionEndSec ($ingestionEndSec) must be >= ingestionStartSec ($ingestionStartSec)"
        }
        require(peakHeight.isFinite() && peakHeight >= 0f) { "peakHeight must be finite and non-negative" }
        require(tailEpsilon.isFinite() && tailEpsilon > 0.0 && tailEpsilon < 1.0) {
            "tailEpsilon must be in (0, 1)"
        }
    }

    private val infusionDurationSec: Double = ingestionEndSec.toDouble() - ingestionStartSec.toDouble()

    /** Maximum of the unscaled point shape e^(-ke·τ) - e^(-ka·τ). */
    private val unscaledPointPeak: Double = run {
        val tPeak = ln(ka / ke) / (ka - ke)
        exp(-ke * tPeak) - exp(-ka * tPeak)
    }

    /** Maximum of the unscaled (point or infusion) shape, used for height normalisation. */
    private val unscaledPeak: Double = if (infusionDurationSec <= 0.0) {
        unscaledPointPeak
    } else {
        // Sample numerically: the analytical peak of the convolution depends on T and has no
        // closed form, but the curve is smooth and unimodal so a coarse scan + refinement is
        // robust and cheap.
        findMaxInfusionShapeValue(infusionDurationSec)
    }

    private val scale: Double = if (unscaledPeak > 0.0) peakHeight / unscaledPeak else 0.0

    override val curveEndSec: Float = run {
        val cap = min(maxCurveLengthSec.toDouble(), Float.MAX_VALUE.toDouble())
        // After ingestion ends, the tail decays approximately as e^(-ke·s) relative
        // to the peak. Its duration must not depend on the unscaled peak amplitude.
        val tailFromPeakEnd = if (ke > 0.0) -ln(tailEpsilon) / ke else 0.0
        val tau = max(0.0, tailFromPeakEnd) + infusionDurationSec
        val endSec = max(ingestionEndSec.toDouble(), ingestionStartSec.toDouble() + min(tau, cap))
        endSec.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }

    override fun valueAt(tSec: Float): Float {
        if (!tSec.isFinite()) return 0.0f
        val tau = tSec.toDouble() - ingestionStartSec.toDouble()
        if (tau <= 0.0) return 0.0f
        val raw = if (infusionDurationSec <= 0.0) {
            pointShape(tau)
        } else {
            infusionShape(tau, infusionDurationSec)
        }
        if (raw <= 0.0 || !raw.isFinite()) return 0.0f
        val scaled = scale * raw
        return if (scaled.isFinite() && scaled > 0.0) {
            scaled.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
        } else {
            0.0f
        }
    }

    private fun pointShape(tau: Double): Double = exp(-ke * tau) - exp(-ka * tau)

    private fun infusionShape(tau: Double, t: Double): Double {
        // g(τ) for 0 < τ ≤ T: ∫_0^τ f(u) du = (1-e^{-ke·τ})/ke - (1-e^{-ka·τ})/ka
        // g(τ) for τ  > T: ∫_{τ-T}^τ f(u) du
        //                = (e^{-ke·(τ-T)} - e^{-ke·τ})/ke - (e^{-ka·(τ-T)} - e^{-ka·τ})/ka
        return if (tau <= t) {
            (1.0 - exp(-ke * tau)) / ke - (1.0 - exp(-ka * tau)) / ka
        } else {
            val s = tau - t
            (exp(-ke * s) - exp(-ke * tau)) / ke - (exp(-ka * s) - exp(-ka * tau)) / ka
        }
    }

    /**
     * Returns the maximum *value* (not time) of [infusionShape] for an infusion of duration [t].
     * Used internally to normalise [scale] so the rendered peak equals [peakHeight].
     */
    private fun findMaxInfusionShapeValue(t: Double): Double {
        // Coarse scan over [0, T + 4/ke], then golden-section refinement.
        val safeTail = if (ke > 0.0) min(4.0 / ke, 1e9) else 1e9
        val end = min(t + safeTail, Double.MAX_VALUE / 4.0)
        val steps = 200
        var bestTau = t
        var bestVal = infusionShape(t, t)
        for (i in 0..steps) {
            val tau = end * i / steps
            if (tau <= 0.0) continue
            val v = infusionShape(tau, t)
            if (v > bestVal) {
                bestVal = v
                bestTau = tau
            }
        }
        // Golden-section refine around bestTau.
        val window = end / steps * 2.0
        var lo = max(1e-9, bestTau - window)
        var hi = bestTau + window
        val phi = (Math.sqrt(5.0) - 1.0) / 2.0
        var x1 = hi - phi * (hi - lo)
        var x2 = lo + phi * (hi - lo)
        var f1 = infusionShape(x1, t)
        var f2 = infusionShape(x2, t)
        repeat(40) {
            if (f1 < f2) {
                lo = x1
                x1 = x2; f1 = f2
                x2 = lo + phi * (hi - lo)
                f2 = infusionShape(x2, t)
            } else {
                hi = x2
                x2 = x1; f2 = f1
                x1 = hi - phi * (hi - lo)
                f1 = infusionShape(x1, t)
            }
        }
        return max(bestVal, max(f1, f2))
    }

    companion object {
        /**
         * Solve for the absorption rate constant `ka` such that the time of peak concentration
         * matches [tmaxSec] given the elimination rate [ke].
         *
         * Returns null when no `ka > ke` can produce the requested tmax (this happens when
         * `tmaxSec >= 1/ke`, the limit of `ln(ka/ke)/(ka-ke)` as ka → ke⁺). Callers should
         * lower [ke] (i.e. lengthen the total duration) or shorten the requested tmax.
         */
        fun solveKaForTmax(ke: Double, tmaxSec: Double): Double? {
            require(ke.isFinite() && ke > 0.0) { "ke must be finite and positive" }
            require(tmaxSec.isFinite() && tmaxSec > 0.0) { "tmaxSec must be finite and positive" }
            // tmax(ka) is strictly decreasing in ka with limit 1/ke as ka → ke⁺ and 0 as ka → ∞.
            val limit = 1.0 / ke
            if (!limit.isFinite() || tmaxSec >= limit * 0.999) return null
            var lo = ke * 1.0001
            var hi = min(ke * 1_000_000.0, Double.MAX_VALUE)
            // Ensure tmax(hi) < tmaxSec < tmax(lo). With the bounds above this nearly always
            // holds; clamp defensively.
            fun tmaxOf(ka: Double): Double {
                val delta = ka - ke
                if (delta <= 0.0) return 1.0 / ke
                return ln(ka / ke) / delta
            }
            if (tmaxOf(lo) <= tmaxSec) return lo
            if (tmaxOf(hi) >= tmaxSec) return hi
            repeat(80) {
                val ratio = hi / lo
                val mid = if (ratio.isFinite() && ratio > 0.0) lo * Math.sqrt(ratio) else Math.sqrt(lo * hi)
                if (tmaxOf(mid) > tmaxSec) lo = mid else hi = mid
            }
            val finalRatio = hi / lo
            val result = if (finalRatio.isFinite() && finalRatio > 0.0) {
                lo * Math.sqrt(finalRatio)
            } else {
                Math.sqrt(lo * hi)
            }
            return result.takeIf { it.isFinite() && it > ke }
        }
    }
}
