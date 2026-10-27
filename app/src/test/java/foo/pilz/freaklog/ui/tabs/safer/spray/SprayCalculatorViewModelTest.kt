package foo.pilz.freaklog.ui.tabs.safer.spray

import foo.pilz.freaklog.data.room.SprayRepository
import foo.pilz.freaklog.data.room.experiences.entities.Spray
import io.mockk.coEvery
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
}
