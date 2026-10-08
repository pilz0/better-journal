package foo.pilz.freaklog.data.substanceshare

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstanceColorsImportTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun envelopeRoundTrips() {
        val envelope = SharedSubstanceColorsEnvelope(
            format = SHARED_SUBSTANCE_COLORS_FORMAT,
            version = SHARED_SUBSTANCE_COLORS_VERSION,
            colors = listOf(
                SharedSubstanceColor("MDMA", 0xFFCA9EE6.toInt()),
                SharedSubstanceColor("LSD", 0xFF8839EF.toInt()),
            ),
        )
        val text = json.encodeToString(SharedSubstanceColorsEnvelope.serializer(), envelope)
        val decoded = json.decodeFromString(SharedSubstanceColorsEnvelope.serializer(), text)
        assertEquals(envelope, decoded)
    }

    @Test
    fun computeColorImportAppliesOnlyKnownSubstances() {
        val colors = listOf(
            SharedSubstanceColor("MDMA", 1),
            SharedSubstanceColor("LSD", 2),
            SharedSubstanceColor("Ketamine", 3),
        )
        val plan = computeColorImport(colors, existingNames = setOf("MDMA", "Ketamine"))
        assertEquals(listOf("MDMA", "Ketamine"), plan.toApply.map { it.substanceName })
        assertEquals(listOf("LSD"), plan.skipped.map { it.substanceName })
    }

    @Test
    fun computeColorImportSkipsAllWhenNoneKnown() {
        val colors = listOf(SharedSubstanceColor("MDMA", 1))
        val plan = computeColorImport(colors, existingNames = emptySet())
        assertTrue(plan.toApply.isEmpty())
        assertEquals(1, plan.skipped.size)
    }
}
