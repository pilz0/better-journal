package foo.pilz.freaklog.ui.tabs.settings.funny

import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AchievementPredicateTest {

    private val substanceRepo = mockk<SubstanceRepositoryInterface>(relaxed = true)
    private val interactionChecker = mockk<InteractionChecker>(relaxed = true)

    private fun testIngestion(
        name: String,
        route: AdministrationRoute = AdministrationRoute.ORAL,
        experienceId: Int = 1,
    ) = Ingestion(
        id = 0,
        substanceName = name,
        time = Instant.now(),
        endTime = null,
        creationDate = null,
        administrationRoute = route,
        dose = 10.0,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = "mg",
        experienceId = experienceId,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
    )

    private fun testExperience(id: Int = 1) = Experience(
        id = id,
        title = "Exp $id",
        text = "",
        sortDate = Instant.now(),
        creationDate = Instant.now(),
        location = null,
    )

    @Test
    fun minSubstances_countsDistinctSubstances() {
        val ingestions = listOf(
            testIngestion("MDMA"),
            testIngestion("MDMA"),
            testIngestion("Ketamine"),
        )
        val exp = ExperienceWithIngestionsTimedNotesAndRatings(
            experience = testExperience(1),
            ingestions = ingestions,
            timedNotes = emptyList(),
            ratings = emptyList(),
        )
        val ctx = AchievementContext(
            experiences = listOf(exp),
            allIngestions = ingestions,
            allTimedNotes = emptyList(),
            allRatings = emptyList(),
            customUnits = emptyList(),
            customRecipes = emptyList(),
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker,
        )

        assertTrue(evaluateAchievement("min_substances(2)", ctx))
        assertFalse(evaluateAchievement("min_substances(3)", ctx))
    }

    @Test
    fun minRoutes_countsDistinctRoutes() {
        val ingestions = listOf(
            testIngestion("Caffeine", AdministrationRoute.ORAL),
            testIngestion("Caffeine", AdministrationRoute.ORAL),
            testIngestion("Ketamine", AdministrationRoute.INSUFFLATED),
        )
        val exp = ExperienceWithIngestionsTimedNotesAndRatings(
            experience = testExperience(1),
            ingestions = ingestions,
            timedNotes = emptyList(),
            ratings = emptyList(),
        )
        val ctx = AchievementContext(
            experiences = listOf(exp),
            allIngestions = ingestions,
            allTimedNotes = emptyList(),
            allRatings = emptyList(),
            customUnits = emptyList(),
            customRecipes = emptyList(),
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker,
        )

        assertTrue(evaluateAchievement("min_routes(2)", ctx))
        assertFalse(evaluateAchievement("min_routes(3)", ctx))
    }

    @Test
    fun anySubstanceCount_matchesThreshold() {
        val ingestions = listOf(
            testIngestion("Caffeine"),
            testIngestion("Caffeine"),
            testIngestion("Caffeine"),
            testIngestion("LSD"),
        )
        val exp = ExperienceWithIngestionsTimedNotesAndRatings(
            experience = testExperience(1),
            ingestions = ingestions,
            timedNotes = emptyList(),
            ratings = emptyList(),
        )
        val ctx = AchievementContext(
            experiences = listOf(exp),
            allIngestions = ingestions,
            allTimedNotes = emptyList(),
            allRatings = emptyList(),
            customUnits = emptyList(),
            customRecipes = emptyList(),
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker,
        )

        assertTrue(evaluateAchievement("any_substance_count(3)", ctx))
        assertFalse(evaluateAchievement("any_substance_count(4)", ctx))
    }

    @Test
    fun evalContext_isMemoizedLazily() {
        val exp = ExperienceWithIngestionsTimedNotesAndRatings(
            experience = testExperience(1),
            ingestions = listOf(testIngestion("LSD")),
            timedNotes = emptyList(),
            ratings = emptyList(),
        )
        val ctx = AchievementContext(
            experiences = listOf(exp),
            allIngestions = exp.ingestions,
            allTimedNotes = emptyList(),
            allRatings = emptyList(),
            customUnits = emptyList(),
            customRecipes = emptyList(),
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker,
        )

        val first = ctx.evalContext
        val second = ctx.evalContext
        assertEquals(first, second)
        assertEquals(1, first.experiences.size)
        assertEquals(1, first.ingestions.size)
    }

    @Test
    fun streakFunctions_handleEdgeCasesAndEmptyLists() {
        assertEquals(0, longestConsecutiveDayStreak(emptyList()))
        assertEquals(0, longestSoberStreak(emptyList()))
        assertEquals(0L, journalSpanDays(emptyList()))

        val single = listOf(testIngestion("MDMA"))
        assertEquals(1, longestConsecutiveDayStreak(single))
        assertEquals(0, longestSoberStreak(single))
        assertEquals(0L, journalSpanDays(single))
    }

    @Test
    fun streakFunctions_calculateCorrectStreaks() {
        val now = Instant.now()
        val day1 = testIngestion("MDMA").copy(time = now.minusSeconds(86400 * 3))
        val day2 = testIngestion("MDMA").copy(time = now.minusSeconds(86400 * 2))
        val day4 = testIngestion("MDMA").copy(time = now)

        val list = listOf(day1, day2, day4)
        assertEquals(2, longestConsecutiveDayStreak(list))
        assertEquals(1, longestSoberStreak(list))
        assertTrue(journalSpanDays(list) >= 3L)
    }

    @Test
    fun evaluateAchievement_handlesMalformedConditionsSafely() {
        val exp = ExperienceWithIngestionsTimedNotesAndRatings(
            experience = testExperience(1),
            ingestions = listOf(testIngestion("LSD")),
            timedNotes = emptyList(),
            ratings = emptyList(),
        )
        val ctx = AchievementContext(
            experiences = listOf(exp),
            allIngestions = exp.ingestions,
            allTimedNotes = emptyList(),
            allRatings = emptyList(),
            customUnits = emptyList(),
            customRecipes = emptyList(),
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker,
        )

        assertFalse(evaluateAchievement("invalid_func()", ctx))
        assertFalse(evaluateAchievement("min_ingestions(not_a_number)", ctx))
        assertFalse(evaluateAchievement("((((unterminated", ctx))
    }
}
