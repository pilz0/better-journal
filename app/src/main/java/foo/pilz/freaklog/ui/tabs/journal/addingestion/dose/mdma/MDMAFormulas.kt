/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose.mdma

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Pure helpers for the MDMA dose-picker screen.
 *
 * Kept in a separate file with no Android dependencies so they can be unit-tested on the JVM
 * (and reused by other UI surfaces in the future).
 */
object MDMAFormulas {
    /** Maximum recommended dose (mg/kg) — male / "biological male" reference. */
    const val MAX_MG_PER_KG_MALE: Double = 1.5

    /** Maximum recommended dose (mg/kg) — female / "biological female" reference. */
    const val MAX_MG_PER_KG_FEMALE: Double = 1.3

    /** The "optimal" single oral dose suggested by Dutch drug-testing data. */
    const val OPTIMAL_DOSE_MG: Double = 90.0

    /** Lower bound of the dose-effect chart, in mg. */
    const val CHART_MIN_DOSE_MG: Double = 10.0

    /** Upper bound of the dose-effect chart, in mg. */
    const val CHART_MAX_DOSE_MG: Double = 180.0

    enum class Sex { MALE, FEMALE }

    /**
     * Maximum recommended dose in mg, rounded to the nearest 5 mg for practicality.
     *
     * @param weightKg the user's body weight in kilograms
     * @param sex which mg/kg coefficient to use
     */
    fun maxDoseMg(weightKg: Double, sex: Sex): Double {
        val coefficient = when (sex) {
            Sex.MALE -> MAX_MG_PER_KG_MALE
            Sex.FEMALE -> MAX_MG_PER_KG_FEMALE
        }
        val raw = weightKg * coefficient
        // Round to nearest 5 mg for practicality, never below 0.
        return (raw / 5.0).roundToInt().coerceAtLeast(0) * 5.0
    }

    /**
     * Estimated "desirable effects" curve, peaking near [OPTIMAL_DOSE_MG].
     *
     * Returns a value in `[0, 1]` representing relative desirable-effect intensity at the
     * given dose. Modelled as a Gaussian centred at the optimal dose.
     */
    fun desirableEffectAt(doseMg: Double): Double {
        val sigma = 35.0
        val z = (doseMg - OPTIMAL_DOSE_MG) / sigma
        return exp(-0.5 * z * z).coerceIn(0.0, 1.0)
    }

    /**
     * Estimated "adverse effects" curve, rising steeply past [OPTIMAL_DOSE_MG].
     *
     * Returns a value in `[0, 1]` representing relative adverse-effect intensity.
     * Modelled as a logistic curve centred slightly above the optimal dose.
     */
    fun adverseEffectAt(doseMg: Double): Double {
        val midpoint = OPTIMAL_DOSE_MG + 20.0
        val steepness = 0.07
        return (1.0 / (1.0 + exp(-steepness * (doseMg - midpoint)))).coerceIn(0.0, 1.0)
    }

    /** Returns true if [substanceName] (case-insensitive) refers to MDMA. */
    fun isMdma(substanceName: String?): Boolean {
        if (substanceName == null) return false
        val normalized = substanceName.trim().lowercase()
        return normalized in MDMA_ALIASES
    }

    private val MDMA_ALIASES: Set<String> = setOf(
        "mdma",
        "ecstasy",
        "molly",
        "mandy",
        "xtc",
    )

    /**
     * Sample [count] dose values evenly between [CHART_MIN_DOSE_MG] and [CHART_MAX_DOSE_MG].
     *
     * Useful for plotting the desirable/adverse curves.
     */
    fun sampleDoses(count: Int): List<Double> {
        require(count >= 2) { "count must be >= 2" }
        val step = (CHART_MAX_DOSE_MG - CHART_MIN_DOSE_MG) / (count - 1)
        return (0 until count).map { CHART_MIN_DOSE_MG + it * step }
    }
}
