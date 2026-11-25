package foo.pilz.freaklog.ui.tabs.stats

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.testing.EntityBuilders
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

  private val now = Instant.parse("2026-05-10T12:00:00Z")

  private fun ingestionItem(
      id: Int,
      substanceName: String,
      dose: Double?,
      units: String? = "mg",
      time: Instant,
      experienceId: Int,
  ) =
      IngestionWithCompanionAndCustomUnit(
          ingestion =
              EntityBuilders.ingestion(
                  id = id,
                  substanceName = substanceName,
                  dose = dose,
                  units = units,
                  time = time,
                  experienceId = experienceId,
              ),
          substanceCompanion = null,
          customUnit = null,
      )

  private fun experienceWithItems(
      id: Int,
      sortDate: Instant,
      vararg items: IngestionWithCompanionAndCustomUnit,
  ) =
      ExperienceWithIngestionsAndCompanions(
          experience = EntityBuilders.experience(id = id, sortDate = sortDate),
          ingestionsWithCompanionAndCustomUnit = items.toList(),
      )

  @Test
  fun `includes substances without companion records and computes partial knownTotalDose`() {
    val exp =
        experienceWithItems(
            id = 1,
            sortDate = now,
            ingestionItem(1, "Orphaned", 50.0, "mg", now.minusSeconds(3600), 1),
            ingestionItem(2, "Orphaned", null, "mg", now.minusSeconds(1800), 1),
        )

    val model =
        StatsCalculator.build(
            experiences = listOf(exp),
            companions = emptyList(),
            option = TimePickerOption.DAYS_7,
            consumerName = null,
            now = now,
            zone = ZoneOffset.UTC,
            locale = Locale.US,
        )

    assertTrue(model.areThereAnyIngestions)
    assertEquals(2, model.totalIngestions)
    assertEquals(1, model.totalExperiences)
    val item = model.statItems.single()
    assertEquals(AdaptiveColor.BLUE, item.color)
    assertNull(item.totalDose)
    assertNotNull(item.knownTotalDose)
    assertEquals(50.0, item.knownTotalDose!!.dose, 1e-9)
    assertFalse(item.hasMixedUnits)
    assertEquals(1, item.unknownDoseCount)
    assertEquals(2, model.chartBuckets.last().totalCount)
    assertEquals("Orphaned", model.chartBuckets.last().items.single().substanceName)
  }

  @Test
  fun `sorts statItems by experiences ingestions or recency and detects mixed units`() {
    val experiences =
        listOf(
            experienceWithItems(
                id = 1,
                sortDate = now,
                ingestionItem(1, "Frequent", 10.0, "mg", now.minusSeconds(7200), 1),
                ingestionItem(2, "Frequent", 2.0, "ml", now.minusSeconds(3600), 1),
                ingestionItem(3, "Frequent", 15.0, "mg", now.minusSeconds(1800), 1),
            ),
            experienceWithItems(
                id = 2,
                sortDate = now.minusSeconds(86400),
                ingestionItem(4, "Steady", 5.0, "mg", now.minusSeconds(86400), 2),
            ),
            experienceWithItems(
                id = 3,
                sortDate = now.minusSeconds(172800),
                ingestionItem(5, "Steady", 5.0, "mg", now.minusSeconds(172800), 3),
            ),
        )
    val companions =
        listOf(
            SubstanceCompanion("Frequent", AdaptiveColor.PINK),
            SubstanceCompanion("Steady", AdaptiveColor.GREEN),
        )

    fun namesFor(sort: StatsSortOption) =
        StatsCalculator.build(
                experiences = experiences,
                companions = companions,
                option = TimePickerOption.DAYS_7,
                consumerName = null,
                sortOption = sort,
                now = now,
                zone = ZoneOffset.UTC,
            )
            .statItems
            .map { it.substanceName }

    val byExp =
        StatsCalculator.build(
            experiences = experiences,
            companions = companions,
            option = TimePickerOption.DAYS_7,
            consumerName = null,
            sortOption = StatsSortOption.EXPERIENCES,
            now = now,
            zone = ZoneOffset.UTC,
        )
    assertEquals(listOf("Steady", "Frequent"), byExp.statItems.map { it.substanceName })
    assertTrue(byExp.statItems.first { it.substanceName == "Frequent" }.hasMixedUnits)
    assertEquals(listOf("Frequent", "Steady"), namesFor(StatsSortOption.INGESTIONS))
    assertEquals(listOf("Frequent", "Steady"), namesFor(StatsSortOption.RECENT))
  }

  @Test
  fun `TimePickerOption emptyRangeText uses natural grammar`() {
    assertEquals("the last 7 days", TimePickerOption.DAYS_7.emptyRangeText)
    assertEquals("the last 30 days", TimePickerOption.DAYS_30.emptyRangeText)
    assertEquals("the last 26 weeks", TimePickerOption.WEEKS_26.emptyRangeText)
    assertEquals("the last 12 months", TimePickerOption.MONTHS_12.emptyRangeText)
    assertEquals("the last 10 years", TimePickerOption.YEARS_10.emptyRangeText)
  }
}
