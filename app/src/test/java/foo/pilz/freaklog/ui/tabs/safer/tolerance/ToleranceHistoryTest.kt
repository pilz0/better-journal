package foo.pilz.freaklog.ui.tabs.safer.tolerance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.testing.EntityBuilders
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ToleranceHistoryTest {
    private lateinit var db: AppDatabase
    private lateinit var calculator: ToleranceCalculator
    private val substanceRepo = mockk<SubstanceRepository>()
    private var experienceId = 0

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val experienceRepo = ExperienceRepository(db, db.experienceDao(), mockk(), mockk(), mockk(), context)
        val customProfiles = mockk<CustomSubstanceProfiles> { every { byNameFlow } returns MutableStateFlow(emptyMap()) }
        calculator = ToleranceCalculator(substanceRepo, experienceRepo, customProfiles)
        experienceId = db.experienceDao().insert(EntityBuilders.experience()).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun substance(name: String, half: String, zero: String, cross: List<String> = emptyList()): Substance {
        return mockk {
            every { this@mockk.name } returns name
            every { tolerance } returns Tolerance(full = null, half = half, zero = zero)
            every { crossTolerances } returns cross
        }
    }

    @Test
    fun `fractional zero days retains ingestions older than the truncated window`() = runTest {
        val substance = substance("LSD", half = "1 day", zero = "2.9 days")
        every { substanceRepo.getSubstance("LSD") } returns substance
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val ingestion = EntityBuilders.ingestion(
            substanceName = "LSD", time = now.minus(Duration.ofHours(60)), experienceId = experienceId
        )
        db.experienceDao().insert(ingestion)
        // An ingestion beyond the rounded three-day window must still be excluded.
        db.experienceDao().insert(ingestion.copy(time = now.minus(Duration.ofDays(4))))

        val estimate = calculator.calculate("LSD")!!

        assertThat(estimate.zeroDays).isCloseTo(2.9f, 0.0001f)
        assertThat(estimate.toleranceLevel).isGreaterThan(0f)
        assertThat(estimate.toleranceLevel).isCloseTo(
            ToleranceCalculator.computeToleranceLevel(
                listOf(ingestion.time),
                ParsedTolerance(0f, 1f, 2.9f),
                now
            ),
            0.001f
        )
    }

    @Test
    fun `lookup history and cross tolerance ignore case and surrounding spaces`() = runTest {
        val selected = substance("LSD", half = "1 day", zero = "3 days", cross = listOf(" Psychedelics "))
        val related = substance("Psilocybin", half = "1 day", zero = "3 days", cross = listOf("pSYCHedelics"))
        every { substanceRepo.getSubstance(any()) } returns null
        every { substanceRepo.getAllSubstances() } returns listOf(selected, related)
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val lastSelectedTime = now.minus(Duration.ofHours(48))
        db.experienceDao().insert(
            EntityBuilders.ingestion(
                substanceName = " LsD ",
                time = lastSelectedTime,
                experienceId = experienceId
            )
        )
        for (name in listOf("pSILOCYBIN ", "PSILOCYBIN")) {
            db.experienceDao().insert(
                EntityBuilders.ingestion(
                    substanceName = name,
                    time = now.minus(Duration.ofHours(60)),
                    experienceId = experienceId
                )
            )
        }
        db.experienceDao().insert(
            EntityBuilders.ingestion(
                substanceName = "Unrelated",
                time = now,
                experienceId = experienceId
            )
        )

        val estimate = calculator.calculate("  lSd  ")!!

        assertThat(estimate.substanceName).isEqualTo("LSD")
        assertThat(estimate.lastIngestionTime).isEqualTo(lastSelectedTime)
        assertThat(estimate.crossToleranceContributors.map { it.lowercase() }).containsExactly("psilocybin")
        assertThat(estimate.toleranceLevel).isCloseTo(
            ToleranceCalculator.computeToleranceLevel(
                listOf(lastSelectedTime, now.minus(Duration.ofHours(60)), now.minus(Duration.ofHours(60))),
                ParsedTolerance(0f, 1f, 3f),
                now
            ),
            0.001f
        )
    }

    @Test
    fun `selected substance with different stored casing is not a cross tolerance contributor`() = runTest {
        every { substanceRepo.getSubstance("LSD") } returns substance("LSD", "1 day", "3 days")
        db.experienceDao().insert(
            EntityBuilders.ingestion(
                substanceName = "lsd",
                time = Instant.now().minusSeconds(3600),
                experienceId = experienceId
            )
        )

        assertThat(calculator.calculate("LSD")!!.crossToleranceContributors).isEmpty()
    }
}
