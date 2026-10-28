package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import assertk.assertThat
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.testing.EntityBuilders
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SubstanceFrequencyTest {
    private val now = Instant.parse("2026-04-29T12:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun ingestion(id: Int, time: String, experienceId: Int = id) =
        EntityBuilders.ingestion(id = id, time = Instant.parse(time), experienceId = experienceId)

    @Test
    fun `counts are nested across ranges`() {
        val frequency = SubstanceFrequency.compute(
            ingestions = listOf(
                ingestion(1, "2026-04-28T08:00:00Z"),
                ingestion(2, "2026-04-10T08:00:00Z"),
                ingestion(3, "2026-01-01T08:00:00Z"),
                ingestion(4, "2025-06-01T08:00:00Z"),
                ingestion(5, "2024-01-01T08:00:00Z"),
            ),
            now = now,
            zone = zone,
        )

        assertThat(frequency).isEqualTo(SubstanceFrequency(last30Days = 2, last12Months = 4, allTime = 5))
    }

    @Test
    fun `multiple ingestions of one experience count once`() {
        val frequency = SubstanceFrequency.compute(
            ingestions = listOf(
                ingestion(1, "2026-04-28T08:00:00Z", experienceId = 7),
                ingestion(2, "2026-04-28T10:00:00Z", experienceId = 7),
                ingestion(3, "2026-04-28T12:00:00Z", experienceId = 7),
                ingestion(4, "2025-01-01T08:00:00Z", experienceId = 8),
            ),
            now = now,
            zone = zone,
        )

        assertThat(frequency).isEqualTo(SubstanceFrequency(last30Days = 1, last12Months = 1, allTime = 2))
    }

    @Test
    fun `range cutoffs are inclusive`() {
        val frequency = SubstanceFrequency.compute(
            ingestions = listOf(
                ingestion(1, "2026-03-30T12:00:00Z"),
                ingestion(2, "2025-04-29T12:00:00Z"),
                ingestion(3, "2025-04-29T11:59:59Z"),
            ),
            now = now,
            zone = zone,
        )

        assertThat(frequency).isEqualTo(SubstanceFrequency(last30Days = 1, last12Months = 2, allTime = 3))
    }

    @Test
    fun `future ingestions count toward all time only`() {
        val frequency = SubstanceFrequency.compute(
            ingestions = listOf(
                ingestion(1, "2026-04-29T12:00:00Z"),
                ingestion(2, "2026-04-29T12:00:01Z"),
                ingestion(3, "2026-06-01T08:00:00Z"),
            ),
            now = now,
            zone = zone,
        )

        assertThat(frequency).isEqualTo(SubstanceFrequency(last30Days = 1, last12Months = 1, allTime = 3))
    }

    @Test
    fun `empty ingestions give zero counts`() {
        val frequency = SubstanceFrequency.compute(ingestions = emptyList(), now = now, zone = zone)

        assertThat(frequency).isEqualTo(SubstanceFrequency(last30Days = 0, last12Months = 0, allTime = 0))
    }
}
