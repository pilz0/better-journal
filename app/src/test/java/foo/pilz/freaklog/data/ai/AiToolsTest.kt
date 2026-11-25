package foo.pilz.freaklog.data.ai

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.ai.anodyne.AiAnodyneTools
import foo.pilz.freaklog.data.ai.provider.AiToolSpec
import foo.pilz.freaklog.data.ai.vitals.AiVitalsTools
import foo.pilz.freaklog.data.ai.web.AiWebTools
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiToolsTest {

  private val experienceDao = mockk<ExperienceDao>()
  private val substanceTools =
      mockk<AiSubstanceTools> {
        every { specs } returns emptyList()
        coEvery { execute(any(), any()) } returns null
      }
  private val anodyneTools =
      mockk<AiAnodyneTools> {
        coEvery { isEnabled() } returns false
        every { specs } returns emptyList()
        coEvery { execute(any(), any()) } returns null
      }
  private val webTools =
      mockk<AiWebTools> {
        coEvery { isEnabled() } returns false
        every { specs } returns
            listOf(
                AiToolSpec("web_search", "Search web", JSONObject()),
                AiToolSpec("fetch_webpage", "Fetch webpage", JSONObject()),
            )
        coEvery { execute(any(), any()) } returns null
      }
  private val vitalsTools =
      mockk<AiVitalsTools> {
        coEvery { isEnabled() } returns false
        every { specs } returns listOf(AiToolSpec("get_vitals", "Read vitals", JSONObject()))
        coEvery { execute(any(), any()) } returns null
      }
  private val locked = MutableStateFlow(false)
  private val auth = mockk<BiometricAuthManager> { every { shouldLock } returns locked }
  private val aiTools =
      AiTools(experienceDao, substanceTools, anodyneTools, webTools, vitalsTools, auth)

  @Test
  fun executeUnknownToolReturnsErrorStatus() = runTest {
    val result = aiTools.execute("non_existent_tool", emptyMap())
    assertThat(result.getString("status")).isEqualTo("error")
    assertThat(result.getString("message")).isEqualTo("Unknown tool 'non_existent_tool'.")
  }

  @Test
  fun executeListRecentExperiencesCoercesLimitAndReturnsArray() = runTest {
    val mockExp =
        EntityBuilders.experience(
            id = 1,
            title = "Test Trip",
            sortDate = Instant.ofEpochMilli(1700000000000L),
            text = "Notes",
        )
    val mockRow =
        ExperienceWithIngestionsTimedNotesAndRatings(
            experience = mockExp,
            ingestions = emptyList(),
            timedNotes = emptyList(),
            ratings = emptyList(),
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
    val mockIngestions =
        listOf(
            EntityBuilders.ingestion(
                id = 1,
                experienceId = 1,
                substanceName = "Caffeine",
                time = now,
                administrationRoute = AdministrationRoute.ORAL,
                dose = 100.0,
                units = "mg",
            ),
            EntityBuilders.ingestion(
                id = 2,
                experienceId = 2,
                substanceName = "Caffeine",
                time = now.minusSeconds(3600),
                administrationRoute = AdministrationRoute.ORAL,
                dose = 50.0,
                units = "mg",
            ),
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

  @Test
  fun executeGetRecentIngestionsHandlesStringArgumentsAndNonFiniteDoses() = runTest {
    val now = Instant.now()
    val mockIngestions =
        listOf(
            EntityBuilders.ingestion(
                id = 1,
                experienceId = 1,
                substanceName = "Ketamine",
                time = now,
                dose = Double.NaN,
            )
        )
    coEvery {
      experienceDao.getIngestionsSince(any<Instant>(), any<Int>())
    } returns mockIngestions

    val result = aiTools.execute("get_recent_ingestions", mapOf("days_back" to "14"))
    assertThat(result.getString("status")).isEqualTo("success")
    assertThat(result.getInt("days_back")).isEqualTo(14)
    val ingestions = result.getJSONArray("ingestions")
    assertThat(ingestions.length()).isEqualTo(1)
    assertThat(ingestions.getJSONObject(0).isNull("dose")).isTrue()
  }

  @Test
  fun executeGetExperienceDetailsHandlesStringArgumentsAndNonFiniteDoses() = runTest {
    val exp = EntityBuilders.experience(id = 42, title = "Trip")
    val ing =
        EntityBuilders.ingestion(
            id = 10,
            experienceId = 42,
            substanceName = "LSD",
            dose = Double.POSITIVE_INFINITY,
        )
    val ic =
        foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit(
            ing,
            null,
            null,
        )
    val details =
        foo.pilz.freaklog.data.room.experiences.relations
            .ExperienceWithIngestionsCompanionsAndRatings(
                experience = exp,
                ingestionsWithCompanions = listOf(ic),
                ratings = emptyList(),
            )
    coEvery {
      experienceDao.getExperienceWithIngestionsCompanionsAndRatings(42)
    } returns details

    val result =
        aiTools.execute(
            "get_experience_details",
            mapOf("experience_id" to "42", "max_notes_chars" to "500"),
        )
    assertThat(result.getString("status")).isEqualTo("success")
    assertThat(result.getInt("experience_id")).isEqualTo(42)
    val ingestions = result.getJSONArray("ingestions")
    assertThat(ingestions.length()).isEqualTo(1)
    assertThat(ingestions.getJSONObject(0).isNull("dose")).isTrue()
  }

  @Test
  fun lockedJournalRejectsToolsBeforeReadingData() = runTest {
    locked.value = true
    val result = aiTools.execute("list_recent_experiences", emptyMap())
    assertThat(result.getString("status")).isEqualTo("error")
    coVerify(exactly = 0) {
      experienceDao.getRecentExperiencesWithIngestionsTimedNotesAndRatingsSorted(any())
    }
  }

  @Test
  fun specsIncludeWebAndVitalsOnlyWhenEnabled() = runTest {
    val offNames = aiTools.specs().map { it.name }
    assertThat("web_search" in offNames).isEqualTo(false)
    assertThat("fetch_webpage" in offNames).isEqualTo(false)
    assertThat("get_vitals" in offNames).isEqualTo(false)

    coEvery { webTools.isEnabled() } returns true
    coEvery { vitalsTools.isEnabled() } returns true
    val onNames = aiTools.specs().map { it.name }
    assertThat("web_search" in onNames).isEqualTo(true)
    assertThat("fetch_webpage" in onNames).isEqualTo(true)
    assertThat("get_vitals" in onNames).isEqualTo(true)
  }

  @Test
  fun executeDispatchesToWebAndVitalsAndHonorsBiometricLock() = runTest {
    coEvery { webTools.execute("web_search", any()) } returns
        JSONObject().put("status", "success").put("tag", "web")
    coEvery { vitalsTools.execute("get_vitals", any()) } returns
        JSONObject().put("status", "success").put("tag", "vitals")

    assertThat(aiTools.execute("web_search", mapOf("query" to "lsd")).getString("tag"))
        .isEqualTo("web")
    assertThat(aiTools.execute("get_vitals", emptyMap()).getString("tag")).isEqualTo("vitals")

    locked.value = true
    assertThat(aiTools.execute("web_search", mapOf("query" to "lsd")).getString("status"))
        .isEqualTo("error")
    assertThat(aiTools.execute("get_vitals", emptyMap()).getString("status")).isEqualTo("error")
    coVerify(exactly = 1) { webTools.execute("web_search", any()) }
    coVerify(exactly = 1) { vitalsTools.execute("get_vitals", any()) }
  }
}
