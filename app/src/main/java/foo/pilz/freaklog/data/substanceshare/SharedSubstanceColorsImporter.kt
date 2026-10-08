package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import android.net.Uri
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed class ColorImportResult {
    data class Imported(val applied: Int, val skipped: Int) : ColorImportResult()
    data class Failed(val reason: ImportFailure) : ColorImportResult()
}

data class ColorImportPlan(
    val toApply: List<SharedSubstanceColor>,
    val skipped: List<SharedSubstanceColor>,
)

private val json = Json { ignoreUnknownKeys = true }

suspend fun parseAndImportColors(
    context: Context,
    uri: Uri,
    experienceRepository: ExperienceRepository,
): ColorImportResult {
    val text = try {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    } catch (e: Exception) {
        null
    } ?: return ColorImportResult.Failed(ImportFailure.OpenStream)

    val envelope = try {
        json.decodeFromString(SharedSubstanceColorsEnvelope.serializer(), text)
    } catch (e: SerializationException) {
        return ColorImportResult.Failed(ImportFailure.Parse)
    }

    if (envelope.format != SHARED_SUBSTANCE_COLORS_FORMAT || envelope.version > SHARED_SUBSTANCE_COLORS_VERSION) {
        return ColorImportResult.Failed(ImportFailure.UnsupportedFormat)
    }

    val existing = experienceRepository.getAllSubstanceCompanions().associateBy { it.substanceName }
    val plan = computeColorImport(envelope.colors, existing.keys)
    plan.toApply.forEach { entry ->
        val companion = existing[entry.substanceName] ?: return@forEach
        experienceRepository.update(companion.copy(color = AdaptiveColor.Custom(entry.colorArgb)))
    }
    return ColorImportResult.Imported(applied = plan.toApply.size, skipped = plan.skipped.size)
}

fun computeColorImport(
    colors: List<SharedSubstanceColor>,
    existingNames: Set<String>,
): ColorImportPlan {
    val (toApply, skipped) = colors.partition { it.substanceName in existingNames }
    return ColorImportPlan(toApply = toApply, skipped = skipped)
}
