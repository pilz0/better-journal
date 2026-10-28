package foo.pilz.freaklog.data.ai

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.classes.Interactions
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.Interaction
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import foo.pilz.freaklog.ui.tabs.safer.tolerance.ToleranceCalculator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AiSubstanceToolsTest {

    private val mdma = Substance(
        name = "MDMA",
        commonNames = listOf("Molly", "Ecstasy"),
        url = "https://psychonautwiki.org/wiki/MDMA",
        isApproved = true,
        tolerance = null,
        crossTolerances = emptyList(),
        addictionPotential = "moderate",
        toxicities = emptyList(),
        categories = listOf("entactogen"),
        summary = "An entactogen.",
        effectsSummary = null,
        dosageRemark = null,
        generalRisks = null,
        longtermRisks = null,
        saferUse = listOf("Stay hydrated"),
        interactions = Interactions(dangerous = listOf("MAOIs"), unsafe = emptyList(), uncertain = emptyList()),
        roas = listOf(
            Roa(
                route = AdministrationRoute.ORAL,
                roaDose = RoaDose("mg", 40.0, 75.0, 140.0, 180.0),
                roaDuration = RoaDuration(
                    onset = null, comeup = null, peak = null, offset = null,
                    total = DurationRange(3f, 6f, DurationUnits.HOURS),
                    afterglow = null,
                ),
                bioavailability = null,
            ),
        ),
    )

    private val alcohol = mdma.copy(name = "Alcohol", commonNames = listOf("Booze"), roas = emptyList())

    private val substances = mockk<SubstanceRepositoryInterface> {
        every { getSubstance(any()) } returns null
        every { getSubstance("MDMA") } returns mdma
        every { getSubstance("Alcohol") } returns alcohol
        every { getAllSubstances() } returns listOf(mdma, alcohol)
    }
    private val customProfiles = mockk<CustomSubstanceProfiles> {
        every { byNameFlow } returns MutableStateFlow(emptyMap())
        every { getRoa(any(), any()) } returns null
    }
    private val interactionChecker = mockk<InteractionChecker> {
        every { getInteractionBetween(any(), any()) } returns null
        every { getInteractionBetween("MDMA", "Alcohol") } returns
            Interaction("MDMA", "Alcohol", InteractionType.UNSAFE)
    }
    private val experienceDao = mockk<ExperienceDao>()
    private val tools = AiSubstanceTools(
        substances, customProfiles, interactionChecker, mockk<ToleranceCalculator>(), experienceDao,
    )

    @Test
    fun otherToolNamesAreNotHandled() = runTest {
        assertThat(tools.execute("list_recent_experiences", emptyMap())).isEqualTo(null)
    }

    @Test
    fun substanceInfoResolvesCommonNames() = runTest {
        val result = tools.execute("get_substance_info", mapOf("name" to "molly"))!!
        assertThat(result.getString("status")).isEqualTo("success")
        assertThat(result.getString("name")).isEqualTo("MDMA")
        val oral = result.getJSONArray("routes").getJSONObject(0)
        assertThat(oral.getDouble("common_from")).isEqualTo(75.0)
        assertThat(oral.getString("total")).isEqualTo("3-6h")
        assertThat(result.getJSONObject("interactions").getJSONArray("dangerous").getString(0)).isEqualTo("MAOIs")
    }

    @Test
    fun unknownSubstanceIsAnError() = runTest {
        val result = tools.execute("get_substance_info", mapOf("name" to "unobtainium"))!!
        assertThat(result.getString("status")).isEqualTo("error")
    }

    @Test
    fun interactionsAreCheckedPairwiseWithCanonicalNames() = runTest {
        val result = tools.execute(
            "check_interactions",
            mapOf("substances" to JSONArray(listOf("ecstasy", "Alcohol", "Caffeine"))),
        )!!
        val found = result.getJSONArray("interactions")
        assertThat(found.length()).isEqualTo(1)
        assertThat(found.getJSONObject(0).getString("risk")).isEqualTo("unsafe")
        assertThat(result.getJSONArray("no_data").length()).isEqualTo(2)
        // Caffeine isn't in this test's substance data, so the model is told it couldn't be checked.
        assertThat(result.getJSONArray("unrecognized_substances").getString(0)).isEqualTo("Caffeine")
        assertThat(AiToolLabels.summarise("check_interactions", result)).isEqualTo("unknown substance: Caffeine")
    }

    @Test
    fun chemicalNamesResolveToTheAppsNames() = runTest {
        val result = tools.execute("check_interactions", mapOf("substances" to listOf("MDMA", "ethanol")))!!
        assertThat(result.getJSONArray("substances").getString(1)).isEqualTo("Alcohol")
        assertThat(result.has("unrecognized_substances")).isEqualTo(false)
        assertThat(AiToolLabels.summarise("check_interactions", result)).isEqualTo("1 interaction found")
    }

    @Test
    fun interactionsNeedTwoSubstances() = runTest {
        val result = tools.execute("check_interactions", mapOf("substances" to "MDMA"))!!
        assertThat(result.getString("status")).isEqualTo("error")
    }

    @Test
    fun activeIngestionsUseTheRouteDuration() = runTest {
        val now = Instant.now()
        coEvery { experienceDao.getIngestionsSince(any<Instant>(), any()) } returns listOf(
            EntityBuilders.ingestion(substanceName = "MDMA", time = now.minus(2, ChronoUnit.HOURS)),
            EntityBuilders.ingestion(substanceName = "MDMA", time = now.minus(10, ChronoUnit.HOURS)),
            EntityBuilders.ingestion(substanceName = "Mystery", time = now.minus(1, ChronoUnit.HOURS)),
        )
        val result = tools.execute("get_active_ingestions", mapOf("hours_back" to 12))!!
        val statuses = result.getJSONArray("ingestions").let { arr ->
            (0 until arr.length()).map { (arr.get(it) as JSONObject).getString("status") }
        }
        assertThat(statuses).isEqualTo(listOf("probably worn off", "probably still active", "unknown duration"))
        assertThat(AiToolLabels.summarise("get_active_ingestions", result)).contains("1 probably")
    }
}
