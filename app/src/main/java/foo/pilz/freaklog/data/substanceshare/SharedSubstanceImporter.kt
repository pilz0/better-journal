package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import android.net.Uri
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed class ImportResult {
    data class Imported(val name: String) : ImportResult()
    data class CollisionPending(val payload: SharedSubstance, val existingName: String) :
        ImportResult()

    data class Failed(val reason: ImportFailure) : ImportResult()
}

enum class ImportFailure { OpenStream, Parse, UnsupportedFormat }

private val json = Json { ignoreUnknownKeys = true }

suspend fun parseAndImport(
    context: Context,
    uri: Uri,
    repository: CustomSubstanceRepository,
): ImportResult {
    val text = try {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    } catch (e: Exception) {
        null
    } ?: return ImportResult.Failed(ImportFailure.OpenStream)

    val envelope = try {
        json.decodeFromString(SharedSubstanceEnvelope.serializer(), text)
    } catch (e: SerializationException) {
        return ImportResult.Failed(ImportFailure.Parse)
    }

    if (envelope.format !in SHARED_SUBSTANCE_FORMATS || envelope.version > SHARED_SUBSTANCE_VERSION) {
        return ImportResult.Failed(ImportFailure.UnsupportedFormat)
    }

    val payload = envelope.substance
    val existing = repository.getWithEverythingByName(payload.name)
    return if (existing == null) {
        repository.insertFromShared(payload.expand())
        ImportResult.Imported(payload.name)
    } else {
        ImportResult.CollisionPending(payload, existing.substance.name)
    }
}

suspend fun resolveCollisionReplace(
    payload: SharedSubstance,
    repository: CustomSubstanceRepository,
) {
    val existing = repository.getWithEverythingByName(payload.name) ?: return
    repository.delete(existing.substance)
    repository.insertFromShared(payload.expand())
}

suspend fun resolveCollisionKeepBoth(
    payload: SharedSubstance,
    existingNames: Set<String>,
    repository: CustomSubstanceRepository,
): String {
    val newName = nextAvailableImportName(payload.name, existingNames)
    repository.insertFromShared(payload.expand(targetName = newName))
    return newName
}

internal fun nextAvailableImportName(base: String, existing: Set<String>): String {
    if (!existing.contains(base)) return base
    val first = "$base (imported)"
    if (!existing.contains(first)) return first
    var i = 2
    while (existing.contains("$base (imported $i)")) i++
    return "$base (imported $i)"
}
