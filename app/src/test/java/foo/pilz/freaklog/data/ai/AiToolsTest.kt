package foo.pilz.freaklog.data.ai

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiToolsTest {

    private val experienceDao = mockk<ExperienceDao>()
    private val substanceTools = mockk<AiSubstanceTools> {
        coEvery { execute(any(), any()) } returns null
    }
    private val aiTools = AiTools(experienceDao, substanceTools)

    @Test
    fun executeUnknownToolReturnsErrorStatus() = runTest {
        val result = aiTools.execute("non_existent_tool", emptyMap())
        assertThat(result.getString("status")).isEqualTo("error")
        assertThat(result.getString("message")).isEqualTo("Unknown tool 'non_existent_tool'.")
    }

    @Test
    fun executeListRecentExperiencesCoercesLimitAndReturnsArray() = runTest {
        val mockExp = EntityBuilders.experience(
            id = 1,
            title = "Test Trip",
            sortDate = Instant.ofEpochMilli(1700000000000L),
            text = "Notes"
        )
        val mockRow = ExperienceWithIngestionsTimedNotesAndRatings(
            experience = mockExp,
            ingestions = emptyList(),
            timedNotes = emptyList(),
            ratings = emptyList()
        )
        coEvery {
            experienceDao.getRecentExperiencesWithIngestionsTimedNotesAndRatingsSorted(5)
        } returns listOf(mockRow)

        val result = aiTools.execute("list_recent_experiences", mapOf("limit" to 5))
        assertThat(result.getString("status")).isEqualTo("success")
        val experiences = result.getJSONArray("experiences")
        assertThat(experiences.length()).isEqualTo(1)
        assertThat(experiences.getJSONObject(0).getString("title")).isEqualTo("Test Trip")
    }

    @Test
    fun executeGetExperienceDetailsReturnsErrorWhenMissing() = runTest {
        coEvery {
            experienceDao.getExperienceWithIngestionsCompanionsAndRatings(999)
        } returns null

        val result = aiTools.execute("get_experience_details", mapOf("experience_id" to 999))
        assertThat(result.getString("status")).isEqualTo("error")
        assertThat(result.getString("message")).isEqualTo("No experience found with id 999.")
    }

    @Test
    fun executeSearchExperiencesRequiresQuery() = runTest {
        val result = aiTools.execute("search_experiences", mapOf("query" to "  "))
        assertThat(result.getString("status")).isEqualTo("error")
        assertThat(result.getString("message")).isEqualTo("Parameter 'query' is required.")
    }

    @Test
    fun executeSearchExperiencesBySubstanceRequiresSubstance() = runTest {
        val result = aiTools.execute("search_experiences_by_substance", mapOf("substance" to ""))
        assertThat(result.getString("status")).isEqualTo("error")
        assertThat(result.getString("message")).isEqualTo("Parameter 'substance' is required.")
    }

    @Test
    fun executeGetRecentIngestionsReturnsEmptyListWhenNoData() = runTest {
        coEvery {
            experienceDao.getIngestionsSince(any<Instant>(), any<Int>())
        } returns emptyList()

        val result = aiTools.execute("get_recent_ingestions", mapOf("days_back" to 7))
        assertThat(result.getString("status")).isEqualTo("success")
        assertThat(result.getInt("days_back")).isEqualTo(7)
        assertThat(result.getInt("count")).isEqualTo(0)
    }

    @Test
    fun executeGetSubstanceUsageStatsGroupsCorrectly() = runTest {
        val now = Instant.now()
        val mockIngestions = listOf(
            EntityBuilders.ingestion(
                id = 1,
                experienceId = 1,
                substanceName = "Caffeine",
                time = now,
                administrationRoute = AdministrationRoute.ORAL,
                dose = 100.0,
                units = "mg"
            ),
            EntityBuilders.ingestion(
                id = 2,
                experienceId = 2,
                substanceName = "Caffeine",
                time = now.minusSeconds(3600),
                administrationRoute = AdministrationRoute.ORAL,
                dose = 50.0,
                units = "mg"
            )
        )
        coEvery {
            experienceDao.getIngestionsSince(any<Instant>(), 10000)
        } returns mockIngestions

        val result = aiTools.execute("get_substance_usage_stats", mapOf("days_back" to 30))
        assertThat(result.getString("status")).isEqualTo("success")
        assertThat(result.getInt("total_ingestions")).isEqualTo(2)
        val substances = result.getJSONArray("substances")
        assertThat(substances.length()).isEqualTo(1)
        assertThat(substances.getJSONObject(0).getString("substance")).isEqualTo("Caffeine")
        assertThat(substances.getJSONObject(0).getInt("times_logged")).isEqualTo(2)
    }

    @Test
    fun executeHandlesDaoExceptionsGracefully() = runTest {
        coEvery {
            experienceDao.getRecentExperiencesWithIngestionsTimedNotesAndRatingsSorted(any<Int>())
        } throws RuntimeException("Database error")

        val result = aiTools.execute("list_recent_experiences", emptyMap())
        assertThat(result.getString("status")).isEqualTo("error")
        assertThat(result.getString("message").contains("Database error")).isTrue()
    }
}
