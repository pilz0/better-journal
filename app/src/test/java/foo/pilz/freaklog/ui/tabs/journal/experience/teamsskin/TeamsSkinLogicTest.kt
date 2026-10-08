package foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin

import foo.pilz.freaklog.ui.tabs.journal.experience.notification.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamsSkinLogicTest {
    @Test
    fun activePhasesAreActive() {
        assertTrue(isPhaseActive(Phase.ONSET))
        assertTrue(isPhaseActive(Phase.COMEUP))
        assertTrue(isPhaseActive(Phase.PEAK))
        assertTrue(isPhaseActive(Phase.OFFSET))
    }

    @Test
    fun finishedAndUnknownAreNotActive() {
        assertFalse(isPhaseActive(Phase.FINISHED))
        assertFalse(isPhaseActive(Phase.UNKNOWN))
    }

    @Test
    fun activeWhenHallucinogenAndActive() {
        assertTrue(
            hasActivePsychedelic(
                listOf(
                    SkinEligibilityEntry(false, Phase.PEAK),
                    SkinEligibilityEntry(true, Phase.ONSET),
                )
            )
        )
    }

    @Test
    fun notActiveWhenHallucinogenFinished() {
        assertFalse(
            hasActivePsychedelic(
                listOf(
                    SkinEligibilityEntry(true, Phase.FINISHED),
                    SkinEligibilityEntry(false, Phase.PEAK),
                )
            )
        )
    }

    @Test
    fun notActiveWhenEmpty() {
        assertFalse(hasActivePsychedelic(emptyList()))
    }

    @Test
    fun triggerRespectsProbabilityAndEligibility() {
        assertTrue(shouldTriggerOnOpen(true, 0.1, 0.15))
        assertFalse(shouldTriggerOnOpen(true, 0.2, 0.15))
        assertFalse(shouldTriggerOnOpen(false, 0.0, 0.15))
    }

    @Test
    fun germanNameDeterministicAndInRange() {
        assertEquals(germanNameFor(0), germanNameFor(GERMAN_NAMES.size))
        assertTrue(GERMAN_NAMES.contains(germanNameFor(-7)))
    }

    @Test
    fun initialsFromFirstAndLastWord() {
        assertEquals("BB", teamsInitials("Bernd das Brot"))
        assertEquals("AM", teamsInitials("Angela Merkel"))
        assertEquals("H", teamsInitials("Hartmut"))
        assertEquals("S6", teamsInitials("Skibidi 67"))
        assertEquals("", teamsInitials("  "))
    }

    @Test
    fun avatarColorsDistinctPerName() {
        val indices = GERMAN_NAMES.map { teamsAvatarColorIndex(it, 10) }
        assertEquals(GERMAN_NAMES.size, indices.toSet().size)
    }

    @Test
    fun avatarColorIndexStableAndInRangeForUnknownNames() {
        assertEquals(
            teamsAvatarColorIndex("Steve Smegma", 10),
            teamsAvatarColorIndex("Steve Smegma", 10)
        )
        assertTrue(teamsAvatarColorIndex("Steve Smegma", 10) in 0..9)
    }
}
