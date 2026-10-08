package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
    prettyPrint = true
    encodeDefaults = false
}

suspend fun shareSubstanceGroup(
    context: Context,
    group: SubstanceGroupWithItems,
    customSubstanceRepo: CustomSubstanceRepository,
    experienceRepo: ExperienceRepository,
) {
    val customNames = group.items
        .filter { it.isCustomSubstance }
        .map { it.substanceName }
        .distinct()
    val customSubstances = customSubstanceRepo.getWithEverythingByNames(customNames)
    val unitIds = group.items.mapNotNull { it.customUnitId }.distinct()
    val customUnits = experienceRepo.getCustomUnitsByIds(unitIds)
    val sharedGroup = group.toShared(customSubstances, customUnits)
    val envelope = SharedGroupEnvelope(
        format = SHARED_SUBSTANCE_GROUP_FORMAT,
        version = SHARED_SUBSTANCE_GROUP_VERSION,
        group = sharedGroup,
    )
    val payload = json.encodeToString(envelope)
    val dir = File(context.cacheDir, "shared_substance_groups").apply { mkdirs() }
    val sanitized = group.group.name.sanitizeForGroupFile()
    val file = File(dir, "$sanitized.group")
    file.writeText(payload)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, group.group.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun String.sanitizeForGroupFile(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifEmpty { "group" }
