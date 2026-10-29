/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.search

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.tabs.journal.addingestion.search.suggestion.models.Suggestion
import org.junit.Test
import java.time.Instant

class SuggestionTest {

    private val testColor = AdaptiveColor.RED
    private val testInstant = Instant.now()

    @Test
    fun `pure substance suggestion matches when substance name is in set`() {
        val suggestion = Suggestion.PureSubstanceSuggestion(
            administrationRoute = AdministrationRoute.ORAL,
            substanceName = "MDMA",
            adaptiveColor = testColor,
            dosesAndUnit = emptyList(),
            sortInstant = testInstant,
        )

        val matchingSet = setOf("MDMA", "LSD", "Ketamine")
        val nonMatchingSet = setOf("Psilocybin", "Caffeine")

        assertThat(suggestion.isInSearch("MD", matchingSet)).isTrue()
        assertThat(suggestion.isInSearch("MD", nonMatchingSet)).isFalse()
    }

    @Test
    fun `custom unit suggestion matches on empty search or matching substance name or matching fields`() {
        val customUnit = CustomUnit(
            id = 1,
            substanceName = "2C-B",
            name = "Purple pill",
            administrationRoute = AdministrationRoute.ORAL,
            dose = 15.0,
            estimatedDoseStandardDeviation = null,
            isEstimate = false,
            isArchived = false,
            unit = "pill",
            unitPlural = "pills",
            originalUnit = "mg",
            note = "Pressed tablet",
        )
        val suggestion = Suggestion.CustomUnitSuggestion(
            customUnit = customUnit,
            adaptiveColor = testColor,
            dosesAndUnit = emptyList(),
            sortInstant = testInstant,
        )

        // Empty search always matches
        assertThat(suggestion.isInSearch("", emptySet())).isTrue()

        // Matches if substance name is in filtered set
        assertThat(suggestion.isInSearch("2C", setOf("2C-B"))).isTrue()

        // Matches if unit name contains search text
        assertThat(suggestion.isInSearch("purple", emptySet())).isTrue()

        // Matches if unit field contains search text
        assertThat(suggestion.isInSearch("pill", emptySet())).isTrue()

        // Matches if note contains search text
        assertThat(suggestion.isInSearch("pressed", emptySet())).isTrue()

        // Rejects non-matching text when substance not in set
        assertThat(suggestion.isInSearch("alcohol", emptySet())).isFalse()
    }

    @Test
    fun `custom substance suggestion matches on empty search or matching name`() {
        val customSubstance = CustomSubstance(
            id = 1,
            name = "Homebrew Tincture",
            units = "ml",
            description = "Herbal extract",
        )
        val suggestion = Suggestion.CustomSubstanceSuggestion(
            administrationRoute = AdministrationRoute.SUBLINGUAL,
            customSubstance = customSubstance,
            adaptiveColor = testColor,
            dosesAndUnit = emptyList(),
            sortInstant = testInstant,
        )

        assertThat(suggestion.isInSearch("", emptySet())).isTrue()
        assertThat(suggestion.isInSearch("homebrew", emptySet())).isTrue()
        assertThat(suggestion.isInSearch("TINCTURE", emptySet())).isTrue()
        assertThat(suggestion.isInSearch("unknown", emptySet())).isFalse()
    }
}
