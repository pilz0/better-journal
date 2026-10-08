package foo.pilz.freaklog.ui.tabs.settings.funny.condition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionMonotonicityTest {

    private val engine = AchievementEngine()

    @Test
    fun countAndQuantifiersAreMonotonic() {
        assertTrue(engine.isMonotonic("experiences.count >= 1"))
        assertTrue(engine.isMonotonic("ingestions.count >= 100"))
        assertTrue(engine.isMonotonic("ingestions.substances.count >= 10"))
        assertTrue(engine.isMonotonic("ingestions.routes.count >= 4"))
        assertTrue(engine.isMonotonic("""any(ingestions) { it.substance == "X" }"""))
        assertTrue(engine.isMonotonic("any(ingestions) { it.doseLevel >= heavy }"))
    }

    @Test
    fun membershipAndFilterStreakAreMonotonic() {
        assertTrue(engine.isMonotonic(""""Mescaline" in ingestions.substances"""))
        assertTrue(engine.isMonotonic("""ingestions.filter { it.substance == "Ketamine" }.streak >= 7"""))
        assertTrue(engine.isMonotonic("""ingestions.count { it.substance == "Caffeine" } >= 50"""))
    }

    @Test
    fun perItemComparisonsInsideQuantifierAreMonotonic() {
        assertTrue(engine.isMonotonic("any(ingestions) { it.hour >= 0 && it.hour < 4 }"))
        assertTrue(engine.isMonotonic("any(ingestions) { it.doseLevel == threshold }"))
        assertTrue(engine.isMonotonic("any(experiences) { it.interactionCount(dangerous) >= 1 }"))
    }

    @Test
    fun nestedQuantifierIsMonotonic() {
        assertTrue(
            engine.isMonotonic(
                "any(experiences) { e -> any(e.ingestions) { i -> e.ingestions.count { it.substance == i.substance } >= 2 } }"
            )
        )
    }

    @Test
    fun negationIsNotMonotonic() {
        assertFalse(engine.isMonotonic("""!any(ingestions) { it.substance == "LSD" }"""))
    }

    @Test
    fun allAndNoneAreNotMonotonic() {
        assertFalse(engine.isMonotonic("""all(ingestions) { it.substance == "LSD" }"""))
        assertFalse(engine.isMonotonic("""none(ingestions) { it.substance == "MDMA" }"""))
    }

    @Test
    fun upperBoundedCountIsNotMonotonic() {
        assertFalse(engine.isMonotonic("ingestions.count <= 5"))
        assertFalse(engine.isMonotonic("ingestions.count == 5"))
    }

    @Test
    fun filterOrCountPredicateOnGrowingGlobalIsNotMonotonic() {
        // The predicate compares a fixed item against the growing global collection, so
        // items can leave the filtered set as data grows -> non-monotone.
        assertFalse(engine.isMonotonic("ingestions.filter { it.dose > ingestions.count }.count >= 1"))
        assertFalse(engine.isMonotonic("ingestions.count { it.dose > ingestions.count } >= 1"))
    }
}
