package foo.pilz.freaklog.ui.tabs.safer.tolerance

import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.testing.EntityBuilders
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ToleranceCalculatorTest {

    private val parsed = ParsedTolerance(fullDays = 0f, halfLifeDays = 7f, zeroDays = 14f)

    private val substanceRepo = mockk<SubstanceRepository>()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val profiles = MutableStateFlow<Map<String, CustomSubstanceWithEverything>>(emptyMap())
    private val customProfiles = mockk<CustomSubstanceProfiles> { every { byNameFlow } returns profiles }
    private val calculator = ToleranceCalculator(substanceRepo, experienceRepo, customProfiles)
    private val queriedNames = slot<List<String>>()
    private var storedIngestions = emptyList<Ingestion>()

    @Before
    fun setUpRepositories() {
        every { substanceRepo.getSubstance(any()) } returns null
        every { substanceRepo.getAllSubstances() } returns emptyList()
        coEvery { experienceRepo.getIngestionsSince(capture(queriedNames), any()) } answers {
            val names = queriedNames.captured.map { it.trim().lowercase() }
            storedIngestions.filter { it.substanceName.trim().lowercase() in names }
        }
        coEvery { experienceRepo.getLastIngestion(any()) } answers {
            val name = firstArg<String>().trim().lowercase()
            storedIngestions.filter { it.substanceName.trim().lowercase() == name }.maxByOrNull { it.time }
        }
    }

    private fun customProfile(
        name: String,
        full: String? = null,
        half: String? = null,
        zero: String? = null,
        cross: List<String> = emptyList(),
    ) = CustomSubstanceWithEverything(
        substance = CustomSubstance(
            name = name, units = "mg", description = "",
            toleranceFull = full, toleranceHalf = half, toleranceZero = zero,
        ),
        crossTolerances = cross.map { CustomCrossTolerance(categoryName = it) },
    )

    private fun setProfiles(vararg profiles: CustomSubstanceWithEverything) {
        this.profiles.value = profiles.associateBy { it.substance.name }
    }

    private fun builtIn(name: String, half: String?, zero: String?, cross: List<String> = emptyList()): Substance =
        mockk {
            every { this@mockk.name } returns name
            every { tolerance } returns Tolerance(full = null, half = half, zero = zero)
            every { crossTolerances } returns cross
        }

    private fun ingestion(name: String, daysAgo: Long, now: Instant = Instant.now()) =
        EntityBuilders.ingestion(substanceName = name, time = now.minus(daysAgo, ChronoUnit.DAYS))

    @Test
    fun `custom substance tolerance decays with its own half life`() = runTest {
        setProfiles(customProfile("Mystery", full = "immediately", half = "1 week", zero = "2 weeks"))
        storedIngestions = listOf(ingestion("Mystery", daysAgo = 7))

        val estimate = calculator.calculate("Mystery")

        assertNotNull(estimate)
        assertEquals("Mystery", estimate!!.substanceName)
        assertEquals(7f, estimate.halfLifeDays)
        assertEquals(14f, estimate.zeroDays)
        assertEquals(0.5f, estimate.toleranceLevel, 0.05f)
        assertEquals(storedIngestions.single().time, estimate.lastIngestionTime)
        assertTrue(estimate.crossToleranceContributors.isEmpty())
        assertEquals(listOf("Mystery"), queriedNames.captured)
    }

    @Test
    fun `custom substance lookup ignores case and surrounding spaces`() = runTest {
        setProfiles(customProfile("Mystery Powder", half = "3 days", zero = "1 week"))
        storedIngestions = listOf(ingestion("mystery powder", daysAgo = 1))

        val estimate = calculator.calculate("  MYSTERY powder ")

        assertEquals("Mystery Powder", estimate?.substanceName)
        assertTrue(estimate!!.toleranceLevel > 0.7f)
    }

    @Test
    fun `custom substance with only a zero tolerance text derives its half life`() = runTest {
        setProfiles(customProfile("Mystery", zero = "10 days"))

        val estimate = calculator.calculate("Mystery")!!

        assertEquals(5f, estimate.halfLifeDays)
        assertEquals(10f, estimate.zeroDays)
        assertEquals(0f, estimate.toleranceLevel)
        assertNull(estimate.lastIngestionTime)
    }

    @Test
    fun `custom substance without usable tolerance has no estimate`() = runTest {
        setProfiles(
            customProfile("NoTolerance", cross = listOf("psychedelic")),
            customProfile("OnlyFull", full = "immediately"),
            customProfile("Unparseable", half = "a while", zero = "unknown"),
        )

        for (name in listOf("NoTolerance", "OnlyFull", "Unparseable", "Unknown")) {
            assertNull(name, calculator.calculate(name))
        }
    }

    @Test
    fun `custom cross tolerances include built-in and custom substances sharing a category`() = runTest {
        setProfiles(
            customProfile("Mystery", half = "1 week", zero = "2 weeks", cross = listOf(" Psychedelic ")),
            customProfile("Other Custom", cross = listOf("PSYCHEDELIC")),
            customProfile("Custom Stimulant", cross = listOf("stimulant")),
        )
        every { substanceRepo.getAllSubstances() } returns listOf(
            builtIn("LSD", "1 week", "2 weeks", cross = listOf("psychedelic")),
            builtIn("Caffeine", "1 day", "3 days", cross = listOf("stimulant")),
        )
        val now = Instant.now()
        storedIngestions = listOf(
            ingestion("LSD", daysAgo = 3, now),
            ingestion("other custom", daysAgo = 5, now),
            ingestion("Caffeine", daysAgo = 0, now),
            ingestion("Custom Stimulant", daysAgo = 0, now),
        )

        val estimate = calculator.calculate("Mystery")!!

        assertEquals(setOf("Mystery", "LSD", "Other Custom"), queriedNames.captured.toSet())
        assertEquals(setOf("LSD", "other custom"), estimate.crossToleranceContributors.toSet())
        assertNull(estimate.lastIngestionTime)
        assertEquals(
            ToleranceCalculator.computeToleranceLevel(
                listOf(now.minus(3, ChronoUnit.DAYS), now.minus(5, ChronoUnit.DAYS)),
                ParsedTolerance(0f, 7f, 14f),
                now
            ),
            estimate.toleranceLevel,
            0.001f
        )
    }

    @Test
    fun `custom substance is not its own cross tolerance contributor`() = runTest {
        setProfiles(customProfile("Mystery", half = "1 week", zero = "2 weeks", cross = listOf("psychedelic")))
        storedIngestions = listOf(ingestion("MYSTERY", daysAgo = 1))

        val estimate = calculator.calculate("Mystery")!!

        assertEquals(listOf("Mystery"), queriedNames.captured)
        assertTrue(estimate.crossToleranceContributors.isEmpty())
    }

    @Test
    fun `built-in cross tolerances include custom substances sharing a category`() = runTest {
        val lsd = builtIn("LSD", "1 week", "2 weeks", cross = listOf("psychedelic"))
        every { substanceRepo.getSubstance("LSD") } returns lsd
        every { substanceRepo.getAllSubstances() } returns listOf(lsd)
        setProfiles(
            customProfile("Mystery", cross = listOf("Psychedelic")),
            customProfile("Unrelated", cross = listOf("opioid")),
        )
        storedIngestions = listOf(ingestion("Mystery", daysAgo = 2), ingestion("Unrelated", daysAgo = 0))

        val estimate = calculator.calculate("LSD")!!

        assertEquals(listOf("LSD", "Mystery"), queriedNames.captured)
        assertEquals(listOf("Mystery"), estimate.crossToleranceContributors)
        assertTrue(estimate.toleranceLevel > 0.7f)
    }

    @Test
    fun `built-in substance takes precedence over a custom substance of the same name`() = runTest {
        every { substanceRepo.getSubstance("LSD") } returns builtIn("LSD", "1 week", "2 weeks")
        setProfiles(customProfile("LSD", half = "1 day", zero = "2 days"))

        val estimate = calculator.calculate("LSD")!!

        assertEquals(7f, estimate.halfLifeDays)
        assertEquals(14f, estimate.zeroDays)
    }

    @Test
    fun `custom substances added after construction are found`() = runTest {
        assertNull(calculator.calculate("Mystery"))

        setProfiles(customProfile("Mystery", half = "1 week", zero = "2 weeks"))

        assertEquals("Mystery", calculator.calculate("Mystery")?.substanceName)
    }

    @Test
    fun testNoIngestions() {
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(emptyList(), parsed, Instant.now()))
    }

    @Test
    fun testRecentIngestion() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(1, ChronoUnit.HOURS)), parsed, now)
        assertTrue(level > 0.9f)
    }

    @Test
    fun testHalfLifeDecay() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(7, ChronoUnit.DAYS)), parsed, now)
        assertEquals(0.5f, level, 0.05f)
    }

    @Test
    fun testOldIngestion() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(21, ChronoUnit.DAYS)), parsed, now)
        assertTrue(level < 0.15f)
    }

    @Test
    fun testMultipleIngestionsCombine() {
        val now = Instant.now()
        val single = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(5, ChronoUnit.DAYS)), parsed, now)
        val double = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(5, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS)), parsed, now)
        assertTrue(double > single)
    }

    @Test
    fun testCapsAtOne() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel((1..20).map { now.minus(it.toLong(), ChronoUnit.HOURS) }, parsed, now)
        assertEquals(1f, level, 0.001f)
    }

    @Test
    fun testFutureIngestionDoesNotBlowUp() {
        val now = Instant.now()
        val future = now.plus(5, ChronoUnit.DAYS)
        val level = ToleranceCalculator.computeToleranceLevel(listOf(future), parsed, now)
        assertEquals(1f, level, 0.001f)
    }

    @Test
    fun testNonFiniteHalfLifeReturnsZero() {
        val now = Instant.now()
        val recent = listOf(now.minus(1, ChronoUnit.HOURS))
        val nanParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = Float.NaN, zeroDays = 14f)
        val infParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = Float.POSITIVE_INFINITY, zeroDays = 14f)
        val zeroParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = 0f, zeroDays = 14f)
        val negParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = -5f, zeroDays = 14f)

        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, nanParsed, now))
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, infParsed, now))
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, zeroParsed, now))
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, negParsed, now))
    }
}
