/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose.mdma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MDMAFormulasTest {

    @Test
    fun `max dose male is roughly 1_5 mg per kg rounded to 5`() {
        // 70 kg * 1.5 = 105 -> rounded to nearest 5 = 105
        assertEquals(105.0, MDMAFormulas.maxDoseMg(70.0, MDMAFormulas.Sex.MALE), 0.001)
        // 80 kg * 1.5 = 120
        assertEquals(120.0, MDMAFormulas.maxDoseMg(80.0, MDMAFormulas.Sex.MALE), 0.001)
    }

    @Test
    fun `max dose female is roughly 1_3 mg per kg rounded to 5`() {
        // 60 kg * 1.3 = 78 -> rounded to nearest 5 = 80
        assertEquals(80.0, MDMAFormulas.maxDoseMg(60.0, MDMAFormulas.Sex.FEMALE), 0.001)
    }

    @Test
    fun `max dose is non-negative for zero weight`() {
        assertEquals(0.0, MDMAFormulas.maxDoseMg(0.0, MDMAFormulas.Sex.MALE), 0.001)
    }

    @Test
    fun `desirable effect peaks near optimal dose`() {
        val atOptimal = MDMAFormulas.desirableEffectAt(MDMAFormulas.OPTIMAL_DOSE_MG)
        val atLow = MDMAFormulas.desirableEffectAt(20.0)
        val atHigh = MDMAFormulas.desirableEffectAt(180.0)
        assertTrue(atOptimal > atLow)
        assertTrue(atOptimal > atHigh)
        assertEquals(1.0, atOptimal, 0.001)
    }

    @Test
    fun `adverse effect monotonically increases past optimal`() {
        val atLow = MDMAFormulas.adverseEffectAt(40.0)
        val atOptimal = MDMAFormulas.adverseEffectAt(MDMAFormulas.OPTIMAL_DOSE_MG)
        val atHigh = MDMAFormulas.adverseEffectAt(180.0)
        assertTrue(atLow < atOptimal)
        assertTrue(atOptimal < atHigh)
    }

    @Test
    fun `isMdma matches case-insensitive aliases`() {
        assertTrue(MDMAFormulas.isMdma("MDMA"))
        assertTrue(MDMAFormulas.isMdma("mdma"))
        assertTrue(MDMAFormulas.isMdma("Ecstasy"))
        assertTrue(MDMAFormulas.isMdma("Molly"))
        assertTrue(MDMAFormulas.isMdma("xtc"))
        assertFalse(MDMAFormulas.isMdma("Methamphetamine"))
        assertFalse(MDMAFormulas.isMdma(null))
        assertFalse(MDMAFormulas.isMdma(""))
    }

    @Test
    fun `sampleDoses spans the chart range`() {
        val samples = MDMAFormulas.sampleDoses(10)
        assertEquals(10, samples.size)
        assertEquals(MDMAFormulas.CHART_MIN_DOSE_MG, samples.first(), 0.001)
        assertEquals(MDMAFormulas.CHART_MAX_DOSE_MG, samples.last(), 0.001)
    }
}
