package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SubstanceDosageStatsHelperTest {
    private val now = Instant.parse("2026-04-29T12:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun ingestion(
        id: Int,
        time: String,
        dose: Double?,
        units: String? = "mg",
        experienceId: Int = id,
        route: AdministrationRoute = AdministrationRoute.ORAL,
    ) = Ingestion(
        id = id,
        substanceName = "MDMA",
        time = Instant.parse(time),
        administrationRoute = route,
        dose = dose,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = units,
        experienceId = experienceId,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
    )

    @Test
    fun `build returns buckets summary unknown count and mixed units for selected range`() {
        val model = SubstanceDosageStatsHelper.build(
            ingestions = listOf(
                ingestion(1, "2026-04-29T08:00:00Z", 100.0, "mg", experienceId = 1),
                ingestion(2, "2026-04-29T09:00:00Z", null, "mg", experienceId = 1),
                ingestion(3, "2026-04-20T08:00:00Z", 50.0, "ug", experienceId = 2),
                ingestion(4, "2026-01-01T08:00:00Z", 200.0, "mg", experienceId = 3),
            ),
            range = DosageTimeRange.DAYS_30,
            now = now,
            zone = zone,
        )

        assertThat(model.buckets.size).isEqualTo(30)
        assertThat(model.summary.totalSessions).isEqualTo(2)
        assertThat(model.summary.totalKnownDose).isEqualTo(null)
        assertThat(model.summary.averageKnownDosePerSession).isEqualTo(null)
        assertThat(model.summary.unknownDoseCount).isEqualTo(1)
        assertThat(model.summary.unitsUsed).containsExactly("mg", "ug")
        assertThat(model.hasMixedUnits).isTrue()
    }

    @Test
    fun `average dose per session ignores unknown doses and counts distinct experiences`() {
        val model = SubstanceDosageStatsHelper.build(
            ingestions = listOf(
                ingestion(1, "2026-04-29T08:00:00Z", 100.0, experienceId = 1),
                ingestion(2, "2026-04-29T09:00:00Z", 50.0, experienceId = 1),
                ingestion(3, "2026-04-28T08:00:00Z", null, experienceId = 2),
            ),
            range = DosageTimeRange.DAYS_30,
            now = now,
            zone = zone,
        )

        assertThat(model.summary.totalSessions).isEqualTo(2)
        assertThat(model.summary.averageKnownDosePerSession).isEqualTo(75.0)
        assertThat(model.summary.unknownDoseCount).isEqualTo(1)
    }

    @Test
    fun `all range creates at least one bucket and includes old data`() {
        val model = SubstanceDosageStatsHelper.build(
            ingestions = listOf(ingestion(1, "2024-04-29T08:00:00Z", 100.0)),
            range = DosageTimeRange.ALL,
            now = now,
            zone = zone,
        )

        assertThat(model.buckets.isNotEmpty()).isTrue()
        assertThat(model.summary.totalKnownDose).isEqualTo(100.0)
    }
}
