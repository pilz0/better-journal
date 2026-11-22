package foo.pilz.freaklog.data.anodyne

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnodyneParserTest {

  @Test
  fun testParseSubstance() {
    val jsonString =
        """
        {
          "Title": "Ketamine",
          "Abbreviation": "K",
          "Aliases": ["Special K"],
          "Classes": ["Dissociative"],
          "Dosing Info": [
            {
              "Method": "Insufflated",
              "Tiers": {
                "Light": {"Lower": 10, "Upper": 30, "Unit": "mg", "Entries": 50},
                "Common": {"Lower": 30, "Upper": 75, "Unit": "mg", "Entries": 120}
              }
            }
          ],
          "DurationOfAction": "45 - 90 minutes",
          "Biological Half-Life": "2.5 hours",
          "MolecularFormula": "C13H16ClNO",
          "MolecularWeight": "237.73 g/mol"
        }
        """
            .trimIndent()
    val json = JSONObject(jsonString)
    val substance = AnodyneParser.parseSubstance(json)
    assertNotNull(substance)
    assertEquals("Ketamine", substance?.title)
    assertEquals(listOf("K"), substance?.abbreviations)
    assertEquals("C₁₃H₁₆ClNO", substance?.formula)
    assertEquals(1, substance?.dosing?.size)
    assertEquals("Insufflated", substance?.dosing?.first()?.method)
    assertEquals(170, substance?.dosing?.first()?.totalEntries)
  }

  @Test
  fun testParseIndex() {
    val jsonString =
        """
        {
          "Entries": [
            {"Title": "MDMA", "Abr": ["Ecstasy"], "Classes": ["Entactogen"]},
            {"Title": "LSD", "Abr": "Acid", "Classes": ["Psychedelic"], "NoBuild": false},
            {"Title": "Hidden", "NoBuild": true}
          ]
        }
        """
            .trimIndent()
    val json = JSONObject(jsonString)
    val entries = AnodyneParser.parseIndex(json)
    assertEquals(2, entries.size)
    assertEquals("MDMA", entries[0].title)
    assertEquals(listOf("Ecstasy"), entries[0].abbreviations)
    assertEquals("LSD", entries[1].title)
    assertEquals(listOf("Acid"), entries[1].abbreviations)
  }

  @Test
  fun testParseUserNotes() {
    val jsonString =
        """
        {
          "Title": "alice",
          "TriedSubstances": [
            {
              "Name": "2C-B",
              "Routes": [
                {
                  "Name": "Oral",
                  "Dose": "20 mg",
                  "Onset": "45m",
                  "Duration": "5h"
                }
              ],
              "Effects": ["Visuals", "Euphoria"]
            }
          ]
        }
        """
            .trimIndent()
    val json = JSONObject(jsonString)
    val notes = AnodyneParser.parseUserNotes(json)
    assertEquals(1, notes.size)
    assertEquals("alice", notes[0].user)
    assertEquals("2C-B", notes[0].substanceName)
    assertEquals("Oral 20 mg", notes[0].routesSummary)
    assertEquals(listOf("Visuals", "Euphoria"), notes[0].effects)
  }
}
