/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.substances

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.findEffectiveRoa
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import org.junit.Test

class EffectiveRoaTest {

    private val oralRoa = Roa(
        route = AdministrationRoute.ORAL,
        roaDose = null,
        roaDuration = null,
        bioavailability = null,
    )

    private val insufflatedRoa = Roa(
        route = AdministrationRoute.INSUFFLATED,
        roaDose = null,
        roaDuration = null,
        bioavailability = null,
    )

    private val rectalRoa = Roa(
        route = AdministrationRoute.RECTAL,
        roaDose = null,
        roaDuration = null,
        bioavailability = null,
    )

    private fun createSubstance(roas: List<Roa>): Substance {
        return Substance(
            name = "TestSubstance",
            commonNames = emptyList(),
            url = "",
            isApproved = true,
            tolerance = null,
            crossTolerances = emptyList(),
            addictionPotential = null,
            toxicities = emptyList(),
            categories = emptyList(),
            summary = null,
            effectsSummary = null,
            dosageRemark = null,
            generalRisks = null,
            longtermRisks = null,
            saferUse = emptyList(),
            interactions = null,
            roas = roas,
        )
    }

    @Test
    fun `getEffectiveRoa returns exact route when toggle is disabled`() {
        val substance = createSubstance(listOf(oralRoa, insufflatedRoa, rectalRoa))

        assertThat(substance.getEffectiveRoa(AdministrationRoute.RECTAL, useIntranasalForRectal = false))
            .isEqualTo(rectalRoa)
        assertThat(substance.getEffectiveRoa(AdministrationRoute.INSUFFLATED, useIntranasalForRectal = false))
            .isEqualTo(insufflatedRoa)
        assertThat(substance.getEffectiveRoa(AdministrationRoute.ORAL, useIntranasalForRectal = false))
            .isEqualTo(oralRoa)
    }

    @Test
    fun `getEffectiveRoa substitutes insufflated for rectal when toggle is enabled`() {
        val substance = createSubstance(listOf(oralRoa, insufflatedRoa, rectalRoa))

        assertThat(substance.getEffectiveRoa(AdministrationRoute.RECTAL, useIntranasalForRectal = true))
            .isEqualTo(insufflatedRoa)
    }

    @Test
    fun `getEffectiveRoa falls back to rectal when insufflated is missing and toggle is enabled`() {
        val substance = createSubstance(listOf(oralRoa, rectalRoa))

        assertThat(substance.getEffectiveRoa(AdministrationRoute.RECTAL, useIntranasalForRectal = true))
            .isEqualTo(rectalRoa)
    }

    @Test
    fun `getEffectiveRoa returns null when both rectal and insufflated are missing`() {
        val substance = createSubstance(listOf(oralRoa))

        assertThat(substance.getEffectiveRoa(AdministrationRoute.RECTAL, useIntranasalForRectal = true))
            .isNull()
        assertThat(substance.getEffectiveRoa(AdministrationRoute.RECTAL, useIntranasalForRectal = false))
            .isNull()
    }

    @Test
    fun `getEffectiveRoa does not affect non-rectal routes when toggle is enabled`() {
        val substance = createSubstance(listOf(oralRoa, insufflatedRoa, rectalRoa))

        assertThat(substance.getEffectiveRoa(AdministrationRoute.ORAL, useIntranasalForRectal = true))
            .isEqualTo(oralRoa)
        assertThat(substance.getEffectiveRoa(AdministrationRoute.INSUFFLATED, useIntranasalForRectal = true))
            .isEqualTo(insufflatedRoa)
    }

    @Test
    fun `findEffectiveRoa works for custom substances`() {
        val profiles = mapOf(
            "CustomKetamine" to CustomSubstanceWithEverything(
                substance = CustomSubstance(name = "CustomKetamine", units = "mg", description = "")
            )
        )

        // Without any routes declared, returns null
        assertThat(findEffectiveRoa(profiles, "CustomKetamine", AdministrationRoute.RECTAL, useIntranasalForRectal = true))
            .isNull()
    }
}
