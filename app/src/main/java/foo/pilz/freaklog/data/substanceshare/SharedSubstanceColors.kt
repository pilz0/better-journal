package foo.pilz.freaklog.data.substanceshare

import kotlinx.serialization.Serializable

const val SHARED_SUBSTANCE_COLORS_FORMAT = "gay.cybercrime.journal.substance-colors"
const val SHARED_SUBSTANCE_COLORS_VERSION = 1

@Serializable
data class SharedSubstanceColorsEnvelope(
    val format: String,
    val version: Int,
    val colors: List<SharedSubstanceColor> = emptyList(),
)

@Serializable
data class SharedSubstanceColor(
    val substanceName: String,
    val colorArgb: Int,
)
