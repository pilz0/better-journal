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

package foo.pilz.freaklog.data.substances.parse

import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubstanceParserTest {

    private lateinit var parser: SubstanceParser

    @Before
    fun setUp() {
        parser = SubstanceParser()
    }

    // ===== parseSubstanceFile Tests =====

    @Test
    fun testParseSubstanceFile_invalidJson() {
        val result = parser.parseSubstanceFile("not valid json")
        assertTrue(result.substances.isEmpty())
        assertTrue(result.categories.isEmpty())
    }

    @Test
    fun testParseSubstanceFile_emptyString() {
        val result = parser.parseSubstanceFile("")
        assertTrue(result.substances.isEmpty())
        assertTrue(result.categories.isEmpty())
    }

    @Test
    fun testParseSubstanceFile_emptyObject() {
        val result = parser.parseSubstanceFile("{}")
        assertTrue(result.substances.isEmpty())
        assertTrue(result.categories.isEmpty())
    }

    @Test
    fun testParseSubstanceFile_withSubstances() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "LSD",
                        "url": "https://psychonautwiki.org/wiki/LSD"
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        assertEquals(1, result.substances.size)
        assertEquals("LSD", result.substances[0].name)
    }

    @Test
    fun testParseSubstanceFile_withCategories() {
        val json = """
            {
                "categories": [
                    {
                        "name": "Psychedelic",
                        "description": "Psychedelic substances"
                    }
                ],
                "substances": []
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        assertEquals(1, result.categories.size)
        assertEquals("Psychedelic", result.categories[0].name)
    }

    @Test
    fun testParseSubstanceFile_substanceWithCommonNames() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "MDMA",
                        "url": "https://psychonautwiki.org/wiki/MDMA",
                        "commonNames": ["Ecstasy", "Molly", "MDMA"]
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        assertEquals(1, result.substances.size)
        val substance = result.substances[0]
        // MDMA should be removed from commonNames as it's the same as name
        assertEquals(2, substance.commonNames.size)
        assertTrue(substance.commonNames.contains("Ecstasy"))
        assertTrue(substance.commonNames.contains("Molly"))
        assertFalse(substance.commonNames.contains("MDMA"))
    }

    @Test
    fun testParseSubstanceFile_substanceWithTolerance() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "LSD",
                        "url": "https://psychonautwiki.org/wiki/LSD",
                        "tolerance": {
                            "full": "almost immediately",
                            "half": "5-7 days",
                            "zero": "14 days"
                        }
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        val tolerance = result.substances[0].tolerance
        assertNotNull(tolerance)
        assertEquals("almost immediately", tolerance!!.full)
        assertEquals("5-7 days", tolerance.half)
        assertEquals("14 days", tolerance.zero)
    }

    @Test
    fun testParseSubstanceFile_substanceWithInteractions() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "MDMA",
                        "url": "https://psychonautwiki.org/wiki/MDMA",
                        "interactions": {
                            "dangerous": ["MAOIs", "Lithium"],
                            "unsafe": ["Tramadol"],
                            "uncertain": ["Cannabis"]
                        }
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        val interactions = result.substances[0].interactions
        assertNotNull(interactions)
        assertEquals(2, interactions!!.dangerous.size)
        assertEquals(1, interactions.unsafe.size)
        assertEquals(1, interactions.uncertain.size)
        assertTrue(interactions.dangerous.contains("MAOIs"))
        assertTrue(interactions.unsafe.contains("Tramadol"))
        assertTrue(interactions.uncertain.contains("Cannabis"))
    }

    @Test
    fun testParseSubstanceFile_substanceWithRoas() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "MDMA",
                        "url": "https://psychonautwiki.org/wiki/MDMA",
                        "roas": [
                            {
                                "name": "oral",
                                "dose": {
                                    "units": "mg",
                                    "lightMin": 40,
                                    "commonMin": 75,
                                    "strongMin": 140,
                                    "heavyMin": 200
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        val roas = result.substances[0].roas
        assertEquals(1, roas.size)
        assertEquals(AdministrationRoute.ORAL, roas[0].route)
        assertEquals("mg", roas[0].roaDose?.units)
        assertEquals(40.0, roas[0].roaDose?.lightMin!!, 0.01)
    }

    @Test
    fun testParseSubstanceFile_substanceWithDuration() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "LSD",
                        "url": "https://psychonautwiki.org/wiki/LSD",
                        "roas": [
                            {
                                "name": "sublingual",
                                "duration": {
                                    "onset": {"min": 15, "max": 30, "units": "minutes"},
                                    "comeup": {"min": 15, "max": 30, "units": "minutes"},
                                    "peak": {"min": 3, "max": 5, "units": "hours"},
                                    "offset": {"min": 3, "max": 5, "units": "hours"},
                                    "total": {"min": 8, "max": 12, "units": "hours"},
                                    "afterglow": {"min": 12, "max": 48, "units": "hours"}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        val duration = result.substances[0].roas[0].roaDuration
        assertNotNull(duration)
        assertNotNull(duration!!.onset)
        assertNotNull(duration.peak)
        assertNotNull(duration.total)
        assertEquals(15f, duration.onset!!.min!!, 0.01f)
        assertEquals(30f, duration.onset.max!!, 0.01f)
    }

    @Test
    fun testParseSubstanceFile_substanceWithBioavailability() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "Morphine",
                        "url": "https://psychonautwiki.org/wiki/Morphine",
                        "roas": [
                            {
                                "name": "oral",
                                "bioavailability": {
                                    "min": 20,
                                    "max": 40
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        val bioavailability = result.substances[0].roas[0].bioavailability
        assertNotNull(bioavailability)
        assertEquals(20.0, bioavailability!!.min!!, 0.01)
        assertEquals(40.0, bioavailability.max!!, 0.01)
    }

    @Test
    fun testParseSubstanceFile_allRoutes() {
        val routes = listOf(
            "oral", "sublingual", "buccal", "insufflated", "rectal",
            "transdermal", "subcutaneous", "intramuscular", "intravenous",
            "smoked", "inhaled", "medikinet", "kinecteen"
        )
        
        val roasJson = routes.joinToString(",") { """{"name": "$it"}""" }
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "TestSubstance",
                        "url": "https://example.com",
                        "roas": [$roasJson]
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        assertEquals(13, result.substances[0].roas.size)
    }

    @Test
    fun testParseSubstanceFile_unknownRouteIsSkipped() {
        val json = """
            {
                "categories": [],
                "substances": [
                    {
                        "name": "TestSubstance",
                        "url": "https://example.com",
                        "roas": [
                            {"name": "oral"},
                            {"name": "unknown_route"},
                            {"name": "insufflated"}
                        ]
                    }
                ]
            }
        """.trimIndent()
        
        val result = parser.parseSubstanceFile(json)
        // unknown_route should be skipped
        assertEquals(2, result.substances[0].roas.size)
    }

    // ===== extractSubstanceString Tests =====

    @Test
    fun testExtractSubstanceString_invalidJson() {
        val result = parser.extractSubstanceString("not valid json")
        assertNull(result)
    }

    @Test
    fun testExtractSubstanceString_emptyString() {
        val result = parser.extractSubstanceString("")
        assertNull(result)
    }

    @Test
    fun testExtractSubstanceString_missingData() {
        val result = parser.extractSubstanceString("{}")
        assertNull(result)
    }

    @Test
    fun testExtractSubstanceString_missingSubstances() {
        val result = parser.extractSubstanceString("""{"data": {}}""")
        assertNull(result)
    }

    @Test
    fun testExtractSubstanceString_valid() {
        val json = """
            {
                "data": {
                    "substances": [
                        {"name": "Caffeine", "roas": [{"name": "oral"}]}
                    ]
                }
            }
        """.trimIndent()
        
        val result = parser.extractSubstanceString(json)
        assertNotNull(result)
        assertTrue(result!!.contains("Caffeine"))
        assertTrue(result.contains("oral"))
    }

    @Test
    fun testExtractSubstanceString_multipleSubstances() {
        val json = """
            {
                "data": {
                    "substances": [
                        {"name": "Caffeine"},
                        {"name": "Nicotine"},
                        {"name": "Alcohol"}
                    ]
                }
            }
        """.trimIndent()
        
        val result = parser.extractSubstanceString(json)
        assertNotNull(result)
        assertTrue(result!!.contains("Caffeine"))
        assertTrue(result.contains("Nicotine"))
        assertTrue(result.contains("Alcohol"))
    }
}
