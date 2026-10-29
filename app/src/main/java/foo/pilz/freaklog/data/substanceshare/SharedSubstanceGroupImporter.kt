package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import android.net.Uri
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed class GroupImportResult {
    data class Imported(val name: String) : GroupImportResult()
    data class CollisionPending(val payload: SharedGroup, val existingName: String) :
        GroupImportResult()

    data class Failed(val reason: ImportFailure) : GroupImportResult()
}

private val groupJson = Json { ignoreUnknownKeys = true }

suspend fun parseAndImportGroup(
    context: Context,
    uri: Uri,
    groupRepo: SubstanceGroupRepository,
    customSubstanceRepo: CustomSubstanceRepository,
    experienceRepo: ExperienceRepository,
): GroupImportResult {
    val text = try {
        context.contentResolver.openInputStream(uri)?.use { readSharedPayload(it) }
    } catch (e: Exception) {
        null
    } ?: return GroupImportResult.Failed(ImportFailure.OpenStream)

    val envelope = try {
        groupJson.decodeFromString(SharedGroupEnvelope.serializer(), text)
    } catch (e: SerializationException) {
        return GroupImportResult.Failed(ImportFailure.Parse)
    }

    if (envelope.format != SHARED_SUBSTANCE_GROUP_FORMAT || envelope.version > SHARED_SUBSTANCE_GROUP_VERSION) {
        return GroupImportResult.Failed(ImportFailure.UnsupportedFormat)
    }

    val payload = envelope.group
    if (!payload.isSafeToImport()) return GroupImportResult.Failed(ImportFailure.Parse)
    val existing = groupRepo.getWithItemsByName(payload.name)
    return if (existing == null) {
        commitGroupImport(payload, payload.name, groupRepo, customSubstanceRepo, experienceRepo)
        GroupImportResult.Imported(payload.name)
    } else {
        GroupImportResult.CollisionPending(payload, existing.group.name)
    }
}

suspend fun resolveGroupCollisionReplace(
    payload: SharedGroup,
    groupRepo: SubstanceGroupRepository,
    customSubstanceRepo: CustomSubstanceRepository,
    experienceRepo: ExperienceRepository,
) {
    require(payload.isSafeToImport()) { "Invalid shared group references or size" }
    val existing = groupRepo.getWithItemsByName(payload.name) ?: return
    groupRepo.delete(existing.group)
    commitGroupImport(payload, payload.name, groupRepo, customSubstanceRepo, experienceRepo)
}

suspend fun resolveGroupCollisionKeepBoth(
    payload: SharedGroup,
    existingNames: Set<String>,
    groupRepo: SubstanceGroupRepository,
    customSubstanceRepo: CustomSubstanceRepository,
    experienceRepo: ExperienceRepository,
): String {
    require(payload.isSafeToImport()) { "Invalid shared group references or size" }
    val newName = nextAvailableImportName(payload.name, existingNames)
    commitGroupImport(payload, newName, groupRepo, customSubstanceRepo, experienceRepo)
    return newName
}

private suspend fun commitGroupImport(
    payload: SharedGroup,
    targetName: String,
    groupRepo: SubstanceGroupRepository,
    customSubstanceRepo: CustomSubstanceRepository,
    experienceRepo: ExperienceRepository,
) {
    require(payload.isSafeToImport()) { "Invalid shared group references or size" }
    val existingNames =
        customSubstanceRepo.getExistingNames(payload.customSubstances.map { it.name })
    for (sharedSub in payload.customSubstances) {
        if (sharedSub.name !in existingNames) {
            customSubstanceRepo.insertFromShared(sharedSub.expand())
        }
    }
    val unitIdByKey = mutableMapOf<Triple<String, String, AdministrationRoute>, Int>()
    val existingUnits = experienceRepo.getAllCustomUnitsSorted().associateBy {
        Triple(it.substanceName, it.name, it.administrationRoute)
    }
    for (sharedUnit in payload.customUnits) {
        val existing = existingUnits[Triple(sharedUnit.substanceName, sharedUnit.name, sharedUnit.administrationRoute)]
        val id = if (existing != null && existing.toSharedCustomUnit() == sharedUnit) {
            existing.id
        } else {
            experienceRepo.insert(sharedUnit.toEntity())
        }
        unitIdByKey[Triple(
            sharedUnit.substanceName,
            sharedUnit.name,
            sharedUnit.administrationRoute
        )] = id
    }
    val items = payload.items.map { sharedItem ->
        val resolvedCustomUnitId = sharedItem.customUnitRef?.let { ref ->
            unitIdByKey[Triple(ref.substanceName, ref.name, ref.administrationRoute)]
        }
        SubstanceGroupItem(
            sortOrder = sharedItem.sortOrder,
            substanceName = sharedItem.substanceName,
            isCustomSubstance = sharedItem.isCustomSubstance,
            administrationRoute = sharedItem.administrationRoute,
            dose = sharedItem.dose,
            units = sharedItem.units,
            isEstimate = sharedItem.isEstimate,
            estimatedDoseStandardDeviation = sharedItem.estimatedDoseStandardDeviation,
            customUnitId = resolvedCustomUnitId,
        )
    }
    groupRepo.create(SubstanceGroup(name = targetName), items)
}

internal fun SharedGroup.isSafeToImport(): Boolean {
    if (items.size > 1000 || customUnits.size > 1000 || customSubstances.size > 1000) return false
    val included = customUnits.map { Triple(it.substanceName, it.name, it.administrationRoute) }.toSet()
    if (included.size != customUnits.size) return false
    return items.all { item ->
        item.customUnitRef?.let { ref ->
            ref.substanceName == item.substanceName && ref.administrationRoute == item.administrationRoute &&
                Triple(ref.substanceName, ref.name, ref.administrationRoute) in included
        } ?: true
    }
}
