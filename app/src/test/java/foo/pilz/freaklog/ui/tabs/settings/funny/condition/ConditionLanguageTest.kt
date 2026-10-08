package foo.pilz.freaklog.ui.tabs.settings.funny.condition

import foo.pilz.freaklog.data.room.experiences.entities.Location
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionLanguageTest {

    private val engine = AchievementEngine()
    private fun eval(condition: String, ctx: EvalContext) = engine.evaluate(condition, ctx)

    @Test
    fun countComparison() {
        val ctx = evalContext(experiences = listOf(expWith(experience(1), listOf(ingestion()))))
        assertTrue(eval("experiences.count >= 1", ctx))
        assertFalse(eval("experiences.count >= 2", ctx))
    }

    @Test
    fun ingestionCount() {
        val ctx = evalContext(ingestions = List(3) { ingestion() })
        assertTrue(eval("ingestions.count >= 3", ctx))
        assertFalse(eval("ingestions.count >= 100", ctx))
    }

    @Test
    fun substanceEquality() {
        val ctx = evalContext(ingestions = listOf(ingestion(substanceName = "Methamphetamine")))
        assertTrue(eval("""any(ingestions) { it.substance == "Methamphetamine" }""", ctx))
        assertFalse(eval("""any(ingestions) { it.substance == "Caffeine" }""", ctx))
    }

    @Test
    fun routeEnumComparison() {
        val ctx = evalContext(ingestions = listOf(ingestion(route = AdministrationRoute.RECTAL)))
        assertTrue(eval("any(ingestions) { it.route == RECTAL }", ctx))
        assertFalse(eval("any(ingestions) { it.route == ORAL }", ctx))
    }

    @Test
    fun doseLevelOrdered() {
        val info = FakeSubstanceInfo(
            doses = mapOf(
                "DMT" to DoseClass.HEAVY,
                "Caffeine" to DoseClass.LIGHT
            )
        )
        val ctx = evalContext(
            ingestions = listOf(ingestion("DMT"), ingestion("Caffeine")),
            substanceInfo = info
        )
        assertTrue(eval("any(ingestions) { it.doseLevel >= heavy }", ctx))
        assertTrue(eval("any(ingestions) { it.doseLevel >= light }", ctx))
        assertFalse(
            eval(
                """any(ingestions) { it.substance == "Caffeine" && it.doseLevel >= strong }""",
                ctx
            )
        )
    }

    @Test
    fun comboWithinExperience() {
        val exp = expWith(experience(1), listOf(ingestion("LSD"), ingestion("MDMA")))
        val ctx = evalContext(experiences = listOf(exp))
        assertTrue(eval("""any(experiences) { it.has("LSD") && it.has("MDMA") }""", ctx))
        assertFalse(eval("""any(experiences) { it.has("LSD") && it.has("Ketamine") }""", ctx))
    }

    @Test
    fun comboRequiresSameExperience() {
        val e1 = expWith(experience(1), listOf(ingestion("LSD")))
        val e2 = expWith(experience(2), listOf(ingestion("MDMA")))
        val ctx = evalContext(experiences = listOf(e1, e2))
        assertFalse(eval("""any(experiences) { it.has("LSD") && it.has("MDMA") }""", ctx))
    }

    @Test
    fun sameIngestionRequiresOneIngestionToMatchAll() {
        val info = FakeSubstanceInfo(
            categoriesByName = mapOf(
                "Ketamine" to setOf("dissociative"),
                "Psilocybin mushrooms" to setOf("psychedelic"),
            ),
            doses = mapOf(
                "Ketamine" to DoseClass.COMMON,
                "Psilocybin mushrooms" to DoseClass.HEAVY
            ),
        )
        val ctx = evalContext(
            ingestions = listOf(ingestion("Ketamine"), ingestion("Psilocybin mushrooms")),
            substanceInfo = info,
        )
        assertFalse(
            eval(
                """any(ingestions) { "dissociative" in it.categories && it.doseLevel >= heavy }""",
                ctx
            )
        )
    }

    @Test
    fun sameIngestionTrueWhenOneMatchesAll() {
        val info = FakeSubstanceInfo(
            categoriesByName = mapOf("Ketamine" to setOf("dissociative")),
            doses = mapOf("Ketamine" to DoseClass.HEAVY),
        )
        val ctx = evalContext(ingestions = listOf(ingestion("Ketamine")), substanceInfo = info)
        assertTrue(
            eval(
                """any(ingestions) { "dissociative" in it.categories && it.doseLevel >= heavy }""",
                ctx
            )
        )
    }

    @Test
    fun hasNoteIgnoresBlank() {
        val withNote =
            evalContext(ingestions = listOf(ingestion(notes = "great"), ingestion(notes = null)))
        assertTrue(eval("any(ingestions) { it.hasNote }", withNote))
        val blank = evalContext(ingestions = listOf(ingestion(notes = "  ")))
        assertFalse(eval("any(ingestions) { it.hasNote }", blank))
    }

    @Test
    fun hasLocation() {
        val located = evalContext(
            experiences = listOf(
                expWith(
                    experience(1, location = Location("Home", null, null)),
                    listOf(ingestion())
                )
            ),
        )
        assertTrue(eval("any(experiences) { it.hasLocation }", located))
        val noLoc = evalContext(experiences = listOf(expWith(experience(2), listOf(ingestion()))))
        assertFalse(eval("any(experiences) { it.hasLocation }", noLoc))
    }

    @Test
    fun distinctSubstanceCount() {
        val ctx = evalContext(
            ingestions = listOf(
                ingestion("LSD"),
                ingestion("LSD"),
                ingestion("MDMA"),
                ingestion("DMT")
            )
        )
        assertTrue(eval("ingestions.substances.count >= 3", ctx))
        assertFalse(eval("ingestions.substances.count >= 4", ctx))
    }

    @Test
    fun distinctRouteCount() {
        val ctx = evalContext(
            ingestions = listOf(
                ingestion(route = AdministrationRoute.ORAL),
                ingestion(route = AdministrationRoute.SMOKED),
                ingestion(route = AdministrationRoute.ORAL),
            ),
        )
        assertTrue(eval("ingestions.routes.count >= 2", ctx))
        assertFalse(eval("ingestions.routes.count >= 3", ctx))
    }

    @Test
    fun substanceCountPredicate() {
        val ctx =
            evalContext(ingestions = listOf(ingestion("LSD"), ingestion("LSD"), ingestion("MDMA")))
        assertTrue(eval("""ingestions.count { it.substance == "LSD" } >= 2""", ctx))
        assertFalse(eval("""ingestions.count { it.substance == "MDMA" } >= 2""", ctx))
    }

    @Test
    fun membershipInSubstances() {
        val ctx = evalContext(
            ingestions = listOf(
                ingestion("Psilocybin mushrooms"),
                ingestion("Mescaline")
            )
        )
        assertTrue(
            eval(
                """"Psilocybin mushrooms" in ingestions.substances && "Mescaline" in ingestions.substances""",
                ctx
            )
        )
        assertFalse(eval(""""LSD" in ingestions.substances""", ctx))
    }

    @Test
    fun interactionCount() {
        val exp = expWith(experience(7), listOf(ingestion("A"), ingestion("B")))
        val ctx = evalContext(
            experiences = listOf(exp),
            interactionCounts = mapOf(
                (7 to InteractionFilter.DANGEROUS) to 1,
                (7 to InteractionFilter.ANY) to 4,
            ),
        )
        assertTrue(eval("any(experiences) { it.interactionCount(dangerous) >= 1 }", ctx))
        assertFalse(eval("any(experiences) { it.interactionCount(dangerous) >= 2 }", ctx))
        assertTrue(eval("any(experiences) { it.interactionCount(any) >= 4 }", ctx))
    }

    @Test
    fun logicalPrecedenceAndNegation() {
        val ctx = evalContext(ingestions = listOf(ingestion("LSD")))
        assertTrue(
            eval(
                """any(ingestions){ it.substance=="X" } || any(ingestions){ it.substance=="LSD" } && experiences.count >= 0""",
                ctx
            )
        )
        assertTrue(eval("""!any(ingestions) { it.substance == "MDMA" }""", ctx))
        assertFalse(eval("""!any(ingestions) { it.substance == "LSD" }""", ctx))
    }

    @Test
    fun hourAccessor() {
        val ctx = evalContext(ingestions = listOf(ingestion(time = instantAtHour(5))))
        assertTrue(eval("any(ingestions) { it.hour >= 4 && it.hour < 7 }", ctx))
        assertFalse(eval("any(ingestions) { it.hour >= 7 }", ctx))
    }

    @Test
    fun nestedQuantifierWithNamedParams() {
        val exp = expWith(experience(1), listOf(ingestion("MDMA"), ingestion("LSD")))
        val info = FakeSubstanceInfo(doses = mapOf("LSD" to DoseClass.HEAVY))
        val ctx = evalContext(experiences = listOf(exp), substanceInfo = info)
        assertTrue(
            eval(
                """any(experiences) { e -> e.has("MDMA") && any(e.ingestions) { i -> i.doseLevel >= heavy } }""",
                ctx
            )
        )
    }

    @Test
    fun allAndNone() {
        val ctx = evalContext(ingestions = listOf(ingestion("LSD"), ingestion("LSD")))
        assertTrue(eval("""all(ingestions) { it.substance == "LSD" }""", ctx))
        assertTrue(eval("""none(ingestions) { it.substance == "MDMA" }""", ctx))
        assertFalse(eval("""all(ingestions) { it.substance == "MDMA" }""", ctx))
    }

    @Test
    fun streakConsecutiveDays() {
        val ctx = evalContext(
            ingestions = listOf(
                ingestion(time = instantAtDay(0)),
                ingestion(time = instantAtDay(1)),
                ingestion(time = instantAtDay(2)),
                ingestion(time = instantAtDay(5)),
            ),
        )
        assertTrue(eval("ingestions.streak >= 3", ctx))
        assertFalse(eval("ingestions.streak >= 4", ctx))
    }

    @Test
    fun distinctDayCount() {
        val ctx = evalContext(
            ingestions = listOf(
                ingestion(time = instantAtDay(0)),
                ingestion(time = instantAtDay(0)),
                ingestion(time = instantAtDay(3)),
            ),
        )
        assertTrue(eval("ingestions.days.count >= 2", ctx))
        assertFalse(eval("ingestions.days.count >= 3", ctx))
    }

    @Test
    fun consumerAndEstimateFlags() {
        val ctx =
            evalContext(ingestions = listOf(ingestion(consumerName = "Alex", isEstimate = true)))
        assertTrue(eval("any(ingestions) { it.hasConsumer }", ctx))
        assertTrue(eval("any(ingestions) { it.isEstimate }", ctx))
        assertFalse(eval("any(ingestions) { it.customUnit }", ctx))
    }

    @Test
    fun doseValueAndUnitsComparison() {
        val ctx = evalContext(
            ingestions = listOf(
                ingestion("MDMA", dose = 200.0, units = "mg"),
                ingestion("LSD", dose = 100.0, units = "ug"),
            ),
        )
        assertTrue(eval("""any(ingestions) { it.dose >= 200 && it.units == "mg" }""", ctx))
        assertFalse(eval("""any(ingestions) { it.dose >= 200 && it.units == "ug" }""", ctx))
        assertTrue(eval("any(ingestions) { it.dose > 150 }", ctx))
        assertFalse(eval("any(ingestions) { it.dose > 500 }", ctx))
    }

    @Test
    fun filterThenStreakAndCount() {
        val ctx = evalContext(
            ingestions = listOf(
                ingestion("LSD", time = instantAtDay(0)),
                ingestion("LSD", time = instantAtDay(1)),
                ingestion("LSD", time = instantAtDay(2)),
                ingestion("MDMA", time = instantAtDay(0)),
            ),
        )
        assertTrue(eval("""ingestions.filter { it.substance == "LSD" }.streak >= 3""", ctx))
        assertFalse(eval("""ingestions.filter { it.substance == "LSD" }.streak >= 4""", ctx))
        assertTrue(eval("""ingestions.filter { it.substance == "LSD" }.count >= 3""", ctx))
        assertFalse(eval("""ingestions.filter { it.substance == "MDMA" }.streak >= 2""", ctx))
    }
}
