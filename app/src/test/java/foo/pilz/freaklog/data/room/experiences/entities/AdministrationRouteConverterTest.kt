/*
 * Copyright (c) 2022-2024. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.room.experiences.entities

import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdministrationRouteConverterTest {

    private val converter = AdministrationRouteConverter()

    // ===== fromAdministrationRoute (enum → String) =====

    @Test
    fun testFromAdministrationRoute_orAL() {
        assertEquals("ORAL", converter.fromAdministrationRoute(AdministrationRoute.ORAL))
    }

    @Test
    fun testFromAdministrationRoute_null() {
        assertNull(converter.fromAdministrationRoute(null))
    }

    // ===== toAdministrationRoute (String → enum) =====

    @Test
    fun testToAdministrationRoute_nullReturnsNull() {
        assertNull(converter.toAdministrationRoute(null))
    }

    @Test
    fun testToAdministrationRoute_allValidNames() {
        AdministrationRoute.entries.forEach { route ->
            assertEquals(route, converter.toAdministrationRoute(route.name))
        }
    }

    @Test
    fun testToAdministrationRoute_unknownValues_fallBackToOral() {
        listOf("KINECTEEN", "MEDIKINET", "COMPLETELY_UNKNOWN", "").forEach { input ->
            assertEquals(
                "Expected ORAL fallback for '$input'",
                AdministrationRoute.ORAL,
                converter.toAdministrationRoute(input)
            )
        }
    }

    // ===== round-trip =====

    @Test
    fun testRoundTrip_allValues() {
        AdministrationRoute.entries.forEach { route ->
            val string = converter.fromAdministrationRoute(route)
            val back = converter.toAdministrationRoute(string)
            assertEquals("Round-trip failed for $route", route, back)
        }
    }
}
