package foo.pilz.freaklog.ui.tabs.settings.funny.condition

import foo.pilz.freaklog.ui.tabs.settings.funny.usesConditionLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class AchievementJsonTest {

    private val conditionsById: Map<String, String> by lazy {
        val file = listOf(
            File("src/main/assets/achievements.json"),
            File("app/src/main/assets/achievements.json"),
        ).first { it.exists() }
        Json.parseToJsonElement(file.readText()).jsonArray.associate { entry ->
            val obj = entry.jsonObject
            obj.getValue("id").jsonPrimitive.content to obj.getValue("condition").jsonPrimitive.content
        }
    }

    @Test
    fun everyConditionLanguageConditionParsesAndEvaluates() {
        val engine = AchievementEngine()
        val context = evalContext()
        val conditions = conditionsById.filterValues { usesConditionLanguage(it) }
        assertTrue("expected upstream achievements in achievements.json", conditions.isNotEmpty())
        conditions.forEach { (id, condition) ->
            runCatching { engine.evaluate(condition, context) }
                .onFailure { fail("Condition for '$id' failed to evaluate: $condition\n${it.message}") }
        }
    }

    @Test
    fun olderPredicateSyntaxIsNotMistakenForTheConditionLanguage() {
        listOf("min_experiences(1)", "min_ingestions(10)", "in_experience(category(opioid) & route(SMOKED))")
            .forEach { assertFalse(it, usesConditionLanguage(it)) }
        assertTrue(usesConditionLanguage(conditionsById.getValue("night_owl")))
    }
}
