package foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin

import foo.pilz.freaklog.ui.tabs.journal.experience.notification.Phase

const val TEAMS_SKIN_OPEN_PROBABILITY = 0.05
const val TEAMS_SKIN_MIN_DELAY_MS = 1_800_000L
const val TEAMS_SKIN_MAX_DELAY_MS = 5_400_000L

val GERMAN_NAMES = listOf(
    "Bernd das Brot",
    "Angela Merkel",
    "Benjamin Netanyahu",
    "Martin Wareh",
    "Jeff Benzos",
)

data class SkinEligibilityEntry(val isHallucinogen: Boolean, val phase: Phase)

fun isPhaseActive(phase: Phase): Boolean =
    phase == Phase.ONSET || phase == Phase.COMEUP || phase == Phase.PEAK || phase == Phase.OFFSET

fun hasActivePsychedelic(entries: List<SkinEligibilityEntry>): Boolean =
    entries.any { it.isHallucinogen && isPhaseActive(it.phase) }

fun shouldTriggerOnOpen(isEligible: Boolean, roll: Double, probability: Double): Boolean =
    isEligible && roll < probability

fun germanNameFor(seed: Int): String = GERMAN_NAMES[Math.floorMod(seed, GERMAN_NAMES.size)]

fun randomGermanName(): String = GERMAN_NAMES.random()

fun teamsInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (words.size) {
        0 -> ""
        1 -> words.first().take(1).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

fun teamsAvatarColorIndex(name: String, paletteSize: Int): Int {
    val listIndex = GERMAN_NAMES.indexOf(name)
    return if (listIndex >= 0) listIndex % paletteSize
    else Math.floorMod(name.hashCode(), paletteSize)
}
