package foo.pilz.freaklog.ui.tabs.journal.experience.components

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthRelativeToCommonDoseTest {
  // averageCommonDose = (75 + 125) / 2 = 100 mg
  private val roaDose = RoaDose("mg", 40.0, 75.0, 125.0, 175.0)

  private fun ingestion(dose: Double?, units: String?) =
      IngestionWithCompanionAndCustomUnit(
          ingestion =
              Ingestion(
                  substanceName = "MDMA",
                  time = Instant.EPOCH,
                  administrationRoute = AdministrationRoute.ORAL,
                  dose = dose,
                  isDoseAnEstimate = false,
                  estimatedDoseStandardDeviation = null,
                  units = units,
                  experienceId = 1,
                  notes = null,
                  stomachFullness = null,
                  consumerName = null,
                  customUnitId = null,
              ),
          substanceCompanion = null,
          customUnit = null,
      )

  @Test
  fun `dose in a convertible unit is compared to the common dose`() {
    assertEquals(1.0, getStrengthRelativeToCommonDose(ingestion(100.0, "mg"), 7.0, roaDose), 1e-9)
    assertEquals(1.0, getStrengthRelativeToCommonDose(ingestion(0.1, "g"), 7.0, roaDose), 1e-9)
    assertEquals(
        0.5,
        getStrengthRelativeToCommonDose(ingestion(50_000.0, "µg"), 7.0, roaDose),
        1e-9,
    )
  }

  @Test
  fun `other units fall back to the average dose`() {
    assertEquals(2.0, getStrengthRelativeToCommonDose(ingestion(4.0, "pills"), 2.0, roaDose), 1e-9)
    assertEquals(1.0, getStrengthRelativeToCommonDose(ingestion(null, "mg"), 2.0, roaDose), 1e-9)
    assertEquals(1.0, getStrengthRelativeToCommonDose(ingestion(-5.0, "mg"), 2.0, roaDose), 1e-9)
    assertEquals(3.0, getStrengthRelativeToCommonDose(ingestion(3.0, "g"), 0.0, null), 1e-9)
  }
}
