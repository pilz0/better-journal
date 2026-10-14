/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.classes.Interactions
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class InteractionCheckerTest {

    private val substanceRepo = mockk<SubstanceRepository>()
    private val customProfiles = mockk<CustomSubstanceProfiles>()
    private lateinit var checker: InteractionChecker

    private fun fakeSubstance(
        name: String,
        categories: List<String> = emptyList(),
        dangerous: List<String> = emptyList(),
        unsafe: List<String> = emptyList(),
        uncertain: List<String> = emptyList(),
    ): Substance {
        return Substance(
            name = name,
            commonNames = emptyList(),
            url = "https://example.com/$name",
            isApproved = true,
            tolerance = null,
            crossTolerances = emptyList(),
            addictionPotential = null,
            toxicities = emptyList(),
            categories = categories,
            summary = null,
            effectsSummary = null,
            dosageRemark = null,
            generalRisks = null,
            longtermRisks = null,
            saferUse = emptyList(),
            interactions = Interactions(
                dangerous = dangerous,
                unsafe = unsafe,
                uncertain = uncertain
            ),
            roas = emptyList()
        )
    }

    @Before
    fun setUp() {
        checker = InteractionChecker(
            substanceRepo = substanceRepo,
            customProfiles = customProfiles
        )
        // Default: no custom profile interactions
        every { customProfiles.getInteraction(any(), any(), any()) } returns null
    }

    @Test
    fun `same substance returns null`() {
        val result = checker.getInteractionBetween("LSD", "LSD")
        assertThat(result).isNull()
    }

    @Test
    fun `custom profile interaction overrides built-in data`() {
        every { customProfiles.getInteraction("MyChem", "LSD", any()) } returns InteractionType.DANGEROUS

        val result = checker.getInteractionBetween("MyChem", "LSD")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.DANGEROUS)
    }

    @Test
    fun `direct match dangerous interaction from A to B`() {
        val subA = fakeSubstance("MDMA", dangerous = listOf("MAOI"))
        val subB = fakeSubstance("MAOI")
        every { substanceRepo.getSubstance("MDMA") } returns subA
        every { substanceRepo.getSubstance("MAOI") } returns subB

        val result = checker.getInteractionBetween("MDMA", "MAOI")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.DANGEROUS)
        assertThat(result.aName).isEqualTo("MDMA")
        assertThat(result.bName).isEqualTo("MAOI")
    }

    @Test
    fun `direct match unsafe interaction from B to A`() {
        val subA = fakeSubstance("Alcohol")
        val subB = fakeSubstance("Ketamine", unsafe = listOf("Alcohol"))
        every { substanceRepo.getSubstance("Alcohol") } returns subA
        every { substanceRepo.getSubstance("Ketamine") } returns subB

        val result = checker.getInteractionBetween("Alcohol", "Ketamine")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.UNSAFE)
    }

    @Test
    fun `wildcard match replaces x with two chars to match substance`() {
        // "2C-T-x" with [\\S]{2} matches "2C-T-21"
        val subA = fakeSubstance("Tramadol", dangerous = listOf("2C-T-x"))
        val subB = fakeSubstance("2C-T-21")
        every { substanceRepo.getSubstance("Tramadol") } returns subA
        every { substanceRepo.getSubstance("2C-T-21") } returns subB

        val result = checker.getInteractionBetween("Tramadol", "2C-T-21")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.DANGEROUS)
    }

    @Test
    fun `special class Serotonin releasers expands to MDMA`() {
        val maoi = fakeSubstance("MAOI", dangerous = listOf("Serotonin releasers"))
        val mdma = fakeSubstance("MDMA")
        every { substanceRepo.getSubstance("MAOI") } returns maoi
        every { substanceRepo.getSubstance("MDMA") } returns mdma

        val result = checker.getInteractionBetween("MAOI", "MDMA")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.DANGEROUS)
    }

    @Test
    fun `special class Substituted amphetamines expands to Amphetamine`() {
        val maoi = fakeSubstance("MAOI", dangerous = listOf("Substituted amphetamines"))
        val amph = fakeSubstance("Amphetamine")
        every { substanceRepo.getSubstance("MAOI") } returns maoi
        every { substanceRepo.getSubstance("Amphetamine") } returns amph

        val result = checker.getInteractionBetween("MAOI", "Amphetamine")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.DANGEROUS)
    }

    @Test
    fun `Tricyclic antidepressants are stripped so they do not falsely match depressant`() {
        // Substance A lists "Tricyclic antidepressants" under unsafe
        // Substance B has category "depressant"
        // Because "Tricyclic antidepressants" is removed, it should not match "depressant"
        val subA = fakeSubstance("DrugA", unsafe = listOf("Tricyclic antidepressants"))
        val subB = fakeSubstance("DrugB", categories = listOf("depressant"))
        every { substanceRepo.getSubstance("DrugA") } returns subA
        every { substanceRepo.getSubstance("DrugB") } returns subB

        val result = checker.getInteractionBetween("DrugA", "DrugB")
        assertThat(result).isNull()
    }

    @Test
    fun `class match matches category of substance B against interaction in substance A`() {
        val subA = fakeSubstance("DrugA", uncertain = listOf("Opioids"))
        val subB = fakeSubstance("Morphine", categories = listOf("Opioid", "Depressant"))
        every { substanceRepo.getSubstance("DrugA") } returns subA
        every { substanceRepo.getSubstance("Morphine") } returns subB

        val result = checker.getInteractionBetween("DrugA", "Morphine")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.UNCERTAIN)
    }

    @Test
    fun `higher danger count wins when interactions are declared in both directions`() {
        // A -> B is UNCERTAIN (dangerCount = 1)
        // B -> A is DANGEROUS (dangerCount = 3)
        // DANGEROUS should win
        val subA = fakeSubstance("SubA", uncertain = listOf("SubB"))
        val subB = fakeSubstance("SubB", dangerous = listOf("SubA"))
        every { substanceRepo.getSubstance("SubA") } returns subA
        every { substanceRepo.getSubstance("SubB") } returns subB

        val result = checker.getInteractionBetween("SubA", "SubB")
        assertThat(result).isNotNull()
        assertThat(result!!.interactionType).isEqualTo(InteractionType.DANGEROUS)
    }

    @Test
    fun `returns null when neither substance lists an interaction`() {
        val subA = fakeSubstance("Caffeine")
        val subB = fakeSubstance("Water")
        every { substanceRepo.getSubstance("Caffeine") } returns subA
        every { substanceRepo.getSubstance("Water") } returns subB

        val result = checker.getInteractionBetween("Caffeine", "Water")
        assertThat(result).isNull()
    }
}
