package foo.pilz.freaklog.ui.tabs.safer.spray

import foo.pilz.freaklog.data.room.SprayRepository
import foo.pilz.freaklog.data.room.experiences.entities.Spray
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SprayCalculatorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<SprayRepository>()

    private val defaultSpray = Spray(
        id = 1,
        name = "Standard 10ml",
        contentInMl = 10.0,
        numSprays = 100.0,
        creationDate = Instant.now(),
        isPreferred = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllSpraysFlow() } returns flowOf(listOf(defaultSpray))
        coEvery { repository.getPreferredSpray() } returns defaultSpray
        coEvery { repository.getAllSprays() } returns listOf(defaultSpray)
        coEvery { repository.setPreferred(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `concentration and purity calculations parse dot and comma decimals`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        advanceUntilIdle()

        viewModel.setTotalWeight("100,0")
        viewModel.setLiquidAmountInMl("10,0")
        viewModel.setPurityInPercent("80,0")

        assertEquals(10.0, viewModel.getConcentrationPerMl()!!, 0.001)
        assertEquals(125.0, viewModel.getDoseAdjustedToPurity()!!, 0.001)
    }

    @Test
    fun `zero purity or zero liquid returns null to prevent division by zero`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        advanceUntilIdle()

        viewModel.setTotalWeight("100")
        viewModel.setLiquidAmountInMl("0")
        viewModel.setPurityInPercent("0")

        assertNull(viewModel.getConcentrationPerMl())
        assertNull(viewModel.getDoseAdjustedToPurity())
    }

    @Test
    fun `number of sprays calculation with valid spray`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.spraysFlow.collect()
        }
        advanceUntilIdle()

        viewModel.selectSpray(1)
        viewModel.setLiquidAmountInMl("5,0")

        // 5 ml * (100 sprays / 10 ml) = 50 sprays
        assertEquals(50.0, viewModel.getNumberOfSprays()!!, 0.001)
    }

    @Test
    fun `non-finite decimal inputs return null`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        advanceUntilIdle()

        viewModel.setTotalWeight("NaN")
        viewModel.setLiquidAmountInMl("Infinity")
        viewModel.setPurityInPercent("-Infinity")

        assertNull(viewModel.getConcentrationPerMl())
        assertNull(viewModel.getDoseAdjustedToPurity())
        assertNull(viewModel.getNumberOfSprays())
    }

    @Test
    fun `selectSpray updates totalWeight when liquid amount and weight per spray are set`() = runTest {
        val secondSpray = Spray(
            id = 2,
            name = "Small 5ml",
            contentInMl = 5.0,
            numSprays = 100.0,
            creationDate = Instant.now(),
            isPreferred = false,
        )
        every { repository.getAllSpraysFlow() } returns flowOf(listOf(defaultSpray, secondSpray))

        val viewModel = SprayCalculatorViewModel(repository)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.spraysFlow.collect()
        }
        advanceUntilIdle()

        viewModel.selectSpray(1)
        viewModel.setLiquidAmountInMl("10")
        viewModel.setWeightPerSpray("5")
        assertEquals("500", viewModel.totalWeight.value)

        viewModel.selectSpray(2)
        assertEquals("1000", viewModel.totalWeight.value)
    }

    @Test
    fun `purity over 100 percent or negative returns null`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        advanceUntilIdle()

        viewModel.setTotalWeight("100")
        viewModel.setPurityInPercent("120")
        assertNull(viewModel.getDoseAdjustedToPurity())

        viewModel.setPurityInPercent("-10")
        assertNull(viewModel.getDoseAdjustedToPurity())
    }

    @Test
    fun `invalid spray with non-finite or non-positive values does not crash liquid volume update`() = runTest {
        val invalidSpray = Spray(
            id = 3,
            name = "Zero volume spray",
            contentInMl = 0.0,
            numSprays = 100.0,
            creationDate = Instant.now(),
            isPreferred = false,
        )
        every { repository.getAllSpraysFlow() } returns flowOf(listOf(defaultSpray, invalidSpray))

        val viewModel = SprayCalculatorViewModel(repository)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.spraysFlow.collect()
        }
        advanceUntilIdle()

        viewModel.selectSpray(3)
        viewModel.setWeightPerSpray("5")
        viewModel.setTotalWeight("100")

        // Should not crash or update to NaN/Infinity
        assertEquals("", viewModel.liquidAmountInMl.value)
    }

    @Test
    fun `clearing weight per spray or liquid volume resets calculated total weight`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.spraysFlow.collect()
        }
        advanceUntilIdle()

        viewModel.selectSpray(1)
        viewModel.setLiquidAmountInMl("10")
        viewModel.setWeightPerSpray("5")
        assertEquals("500", viewModel.totalWeight.value)

        viewModel.setWeightPerSpray("")
        assertEquals("", viewModel.totalWeight.value)
        assertEquals("10", viewModel.liquidAmountInMl.value)

        viewModel.setWeightPerSpray("5")
        assertEquals("500", viewModel.totalWeight.value)
        viewModel.setLiquidAmountInMl("0")
        assertEquals("", viewModel.totalWeight.value)
    }

    @Test
    fun `clearing total weight or weight per spray resets calculated liquid volume`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.spraysFlow.collect()
        }
        advanceUntilIdle()

        viewModel.selectSpray(1)
        viewModel.setWeightPerSpray("5")
        viewModel.setTotalWeight("500")
        assertEquals("10", viewModel.liquidAmountInMl.value)

        viewModel.setWeightPerSpray("0")
        assertEquals("", viewModel.liquidAmountInMl.value)
        assertEquals("500", viewModel.totalWeight.value)

        viewModel.setWeightPerSpray("5")
        assertEquals("10", viewModel.liquidAmountInMl.value)
        viewModel.setTotalWeight("")
        assertEquals("", viewModel.liquidAmountInMl.value)
    }

    @Test
    fun `selecting a spray persists it as preferred immediately`() = runTest {
        val viewModel = SprayCalculatorViewModel(repository)
        advanceUntilIdle()

        viewModel.selectSpray(1)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setPreferred(1) }
    }

    @Test
    fun `deleting the selected last spray clears selection and calculated output`() = runTest {
        coEvery { repository.delete(any()) } returns Unit
        coEvery { repository.getAllSprays() } returns emptyList()
        val viewModel = SprayCalculatorViewModel(repository)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.spraysFlow.collect()
        }
        advanceUntilIdle()

        viewModel.selectSpray(1)
        viewModel.setLiquidAmountInMl("10")
        viewModel.setWeightPerSpray("5")
        assertEquals("500", viewModel.totalWeight.value)

        viewModel.deleteSpray(defaultSpray)
        advanceUntilIdle()

        assertNull(viewModel.selectedSprayId.value)
        assertEquals("", viewModel.totalWeight.value)
        assertNull(viewModel.getNumberOfSprays())
    }

    @Test
    fun `addSpray ignores non-finite or non-positive parameters`() = runTest {
        coEvery { repository.insert(any()) } returns 5L
        val viewModel = SprayCalculatorViewModel(repository)
        advanceUntilIdle()

        viewModel.addSpray("Broken", Double.NaN, 100.0)
        viewModel.addSpray("Broken", 10.0, 0.0)
        viewModel.addSpray("Broken", -10.0, 100.0)
        viewModel.addSpray("Broken", 10.0, Double.POSITIVE_INFINITY)
        viewModel.addSpray("  ", 10.0, 100.0)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.insert(any()) }

        viewModel.addSpray(" Valid ", 10.0, 100.0)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.insert(match { it.name == "Valid" }) }
        assertEquals(5, viewModel.selectedSprayId.value)
    }
}
