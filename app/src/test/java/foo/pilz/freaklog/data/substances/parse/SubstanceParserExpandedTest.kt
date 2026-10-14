package foo.pilz.freaklog.data.substances.parse

import foo.pilz.freaklog.data.substances.classes.SubstanceFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SubstanceParserExpandedTest {

    private lateinit var parser: SubstanceParser
    private lateinit var substanceFile: SubstanceFile

    @Before
    fun setUp() {
        parser = SubstanceParser()
        val file = listOf(
            File("src/main/assets/Substances.json"),
            File("app/src/main/assets/Substances.json"),
        ).first { it.exists() }
        substanceFile = parser.parseSubstanceFile(file.readText())
    }

    @Test
    fun parsedSubstanceFileContainsSubstantialDataset() {
        assertTrue("Substance list must not be empty", substanceFile.substances.isNotEmpty())
        assertTrue("Substances list should have at least 100 entries", substanceFile.substances.size >= 100)
        assertTrue("Categories list must not be empty", substanceFile.categories.isNotEmpty())
    }

    @Test
    fun allCategoriesHaveNonBlankNames() {
        substanceFile.categories.forEach { category ->
            assertFalse("Category name should not be blank", category.name.isBlank())
        }
    }

    @Test
    fun allSubstancesHaveUniqueAndNonBlankNames() {
        val seenNames = mutableSetOf<String>()
        substanceFile.substances.forEach { substance ->
            assertFalse("Substance name cannot be blank", substance.name.isBlank())
            assertFalse("Duplicate substance name: ${substance.name}", seenNames.contains(substance.name))
            seenNames.add(substance.name)
            assertNotNull("Substance URL should not be null", substance.url)
        }
    }

    @Test
    fun allSubstanceRoasHaveConsistentDurationValues() {
        substanceFile.substances.forEach { substance ->
            substance.roas.forEach { roa ->
                val duration = roa.roaDuration ?: return@forEach
                duration.onset?.let { range ->
                    val min = range.min
                    val max = range.max
                    if (min != null) {
                        assertTrue("Onset min must be non-negative in ${substance.name}", min >= 0f)
                    }
                    if (min != null && max != null) {
                        assertTrue("Onset max must be >= min in ${substance.name}", max >= min)
                    }
                }
                duration.total?.let { range ->
                    val min = range.min
                    val max = range.max
                    if (min != null) {
                        assertTrue("Total min must be non-negative in ${substance.name}", min >= 0f)
                    }
                    if (min != null && max != null) {
                        assertTrue("Total max must be >= min in ${substance.name}", max >= min)
                    }
                }
            }
        }
    }

    @Test
    fun allSubstanceRoasHavePositiveDoseValues() {
        substanceFile.substances.forEach { substance ->
            substance.roas.forEach { roa ->
                val dose = roa.roaDose ?: return@forEach
                listOfNotNull(dose.lightMin, dose.commonMin, dose.strongMin, dose.heavyMin).forEach { threshold ->
                    assertTrue("Dose threshold must be positive in ${substance.name}", threshold > 0.0)
                }
                if (dose.lightMin != null || dose.commonMin != null || dose.strongMin != null || dose.heavyMin != null) {
                    assertNotNull("Units should be present when dose thresholds exist in ${substance.name}", dose.units)
                    assertFalse("Units should not be blank in ${substance.name}", dose.units.isNullOrBlank())
                }
            }
        }
    }

    @Test
    fun bioavailabilitiesAreWithinZeroToOneHundredPercent() {
        substanceFile.substances.forEach { substance ->
            substance.roas.forEach { roa ->
                val bio = roa.bioavailability ?: return@forEach
                val min = bio.min ?: return@forEach
                val max = bio.max ?: return@forEach
                assertTrue("Bioavailability min should be >= 0 in ${substance.name}", min >= 0.0)
                assertTrue("Bioavailability max should be <= 100 in ${substance.name}", max <= 100.0)
                assertTrue("Bioavailability min should be <= max in ${substance.name}", min <= max)
            }
        }
    }

    @Test
    fun interactionsDoNotContainSelfReferences() {
        substanceFile.substances.forEach { substance ->
            val interactions = substance.interactions ?: return@forEach
            val dangerous = interactions.dangerous
            val unsafe = interactions.unsafe
            val uncertain = interactions.uncertain

            assertFalse("Self-interaction in dangerous: ${substance.name}", dangerous.contains(substance.name))
            assertFalse("Self-interaction in unsafe: ${substance.name}", unsafe.contains(substance.name))
            assertFalse("Self-interaction in uncertain: ${substance.name}", uncertain.contains(substance.name))
        }
    }

    @Test
    fun crossTolerancesAreNonBlank() {
        substanceFile.substances.forEach { substance ->
            substance.crossTolerances.forEach { cross ->
                assertFalse("Cross-tolerance cannot be blank in ${substance.name}", cross.isBlank())
            }
        }
    }
}
