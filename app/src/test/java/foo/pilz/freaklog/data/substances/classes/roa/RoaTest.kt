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

package foo.pilz.freaklog.data.substances.classes.roa

import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoaTest {

    @Test
    fun testRoa_allFieldsPresent() {
        val roa = Roa(
            route = AdministrationRoute.ORAL,
            roaDose = RoaDose("mg", 10.0, 25.0, 50.0, 100.0),
            roaDuration = RoaDuration(
                onset = DurationRange(15f, 30f, DurationUnits.MINUTES),
                comeup = DurationRange(30f, 60f, DurationUnits.MINUTES),
                peak = DurationRange(2f, 4f, DurationUnits.HOURS),
                offset = DurationRange(2f, 4f, DurationUnits.HOURS),
                total = DurationRange(6f, 10f, DurationUnits.HOURS),
                afterglow = DurationRange(6f, 12f, DurationUnits.HOURS)
            ),
            bioavailability = Bioavailability(20.0, 40.0)
        )
        assertNotNull(roa)
        assertEquals(AdministrationRoute.ORAL, roa.route)
        assertNotNull(roa.roaDose)
        assertNotNull(roa.roaDuration)
        assertNotNull(roa.bioavailability)
    }

    @Test
    fun testRoa_onlyRoute() {
        val roa = Roa(
            route = AdministrationRoute.SMOKED,
            roaDose = null,
            roaDuration = null,
            bioavailability = null
        )
        assertNotNull(roa)
        assertEquals(AdministrationRoute.SMOKED, roa.route)
        assertNull(roa.roaDose)
        assertNull(roa.roaDuration)
        assertNull(roa.bioavailability)
    }

    @Test
    fun testRoa_withDoseOnly() {
        val roa = Roa(
            route = AdministrationRoute.INSUFFLATED,
            roaDose = RoaDose("mg", 5.0, 15.0, 30.0, 60.0),
            roaDuration = null,
            bioavailability = null
        )
        assertNotNull(roa)
        assertNotNull(roa.roaDose)
        assertEquals("mg", roa.roaDose!!.units)
    }

    @Test
    fun testRoa_withDurationOnly() {
        val roa = Roa(
            route = AdministrationRoute.SUBLINGUAL,
            roaDose = null,
            roaDuration = RoaDuration(
                onset = DurationRange(15f, 30f, DurationUnits.MINUTES),
                comeup = null,
                peak = DurationRange(3f, 5f, DurationUnits.HOURS),
                offset = null,
                total = DurationRange(8f, 12f, DurationUnits.HOURS),
                afterglow = null
            ),
            bioavailability = null
        )
        assertNotNull(roa)
        assertNotNull(roa.roaDuration)
        assertNotNull(roa.roaDuration!!.onset)
        assertNotNull(roa.roaDuration!!.peak)
    }

    @Test
    fun testRoa_allRouteTypes() {
        val routes = AdministrationRoute.entries
        routes.forEach { route ->
            val roa = Roa(
                route = route,
                roaDose = null,
                roaDuration = null,
                bioavailability = null
            )
            assertEquals(route, roa.route)
        }
    }

    @Test
    fun testRoa_oralTypical() {
        // Typical oral ROA for MDMA
        val roa = Roa(
            route = AdministrationRoute.ORAL,
            roaDose = RoaDose("mg", 40.0, 75.0, 140.0, 200.0),
            roaDuration = RoaDuration(
                onset = DurationRange(30f, 60f, DurationUnits.MINUTES),
                comeup = DurationRange(15f, 30f, DurationUnits.MINUTES),
                peak = DurationRange(1.5f, 2.5f, DurationUnits.HOURS),
                offset = DurationRange(1f, 1.5f, DurationUnits.HOURS),
                total = DurationRange(3f, 5f, DurationUnits.HOURS),
                afterglow = DurationRange(12f, 48f, DurationUnits.HOURS)
            ),
            bioavailability = null
        )
        assertNotNull(roa)
    }

    @Test
    fun testRoa_intravenousTypical() {
        // Typical intravenous ROA
        val roa = Roa(
            route = AdministrationRoute.INTRAVENOUS,
            roaDose = RoaDose("mg", 5.0, 15.0, 30.0, 50.0),
            roaDuration = RoaDuration(
                onset = DurationRange(0f, 1f, DurationUnits.MINUTES),
                comeup = null,
                peak = DurationRange(15f, 30f, DurationUnits.MINUTES),
                offset = DurationRange(30f, 60f, DurationUnits.MINUTES),
                total = DurationRange(45f, 90f, DurationUnits.MINUTES),
                afterglow = null
            ),
            bioavailability = Bioavailability(100.0, 100.0)
        )
        assertEquals(AdministrationRoute.INTRAVENOUS, roa.route)
        assertEquals(100.0, roa.bioavailability!!.min!!, 0.01)
    }

    @Test
    fun testRoa_insufflatedTypical() {
        // Typical insufflated ROA for ketamine
        val roa = Roa(
            route = AdministrationRoute.INSUFFLATED,
            roaDose = RoaDose("mg", 15.0, 30.0, 75.0, 150.0),
            roaDuration = RoaDuration(
                onset = DurationRange(2f, 5f, DurationUnits.MINUTES),
                comeup = DurationRange(5f, 10f, DurationUnits.MINUTES),
                peak = DurationRange(30f, 45f, DurationUnits.MINUTES),
                offset = DurationRange(30f, 60f, DurationUnits.MINUTES),
                total = DurationRange(1f, 2f, DurationUnits.HOURS),
                afterglow = DurationRange(4f, 8f, DurationUnits.HOURS)
            ),
            bioavailability = Bioavailability(25.0, 50.0)
        )
        assertEquals(AdministrationRoute.INSUFFLATED, roa.route)
    }
}
