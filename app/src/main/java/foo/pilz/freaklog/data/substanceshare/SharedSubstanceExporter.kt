package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
    prettyPrint = true
    encodeDefaults = false
}

fun shareCustomSubstance(context: Context, substance: CustomSubstanceWithEverything) {
    val envelope = SharedSubstanceEnvelope(
        format = SHARED_SUBSTANCE_FORMAT,
        version = SHARED_SUBSTANCE_VERSION,
        substance = substance.toShared(),
    )
    val payload = json.encodeToString(envelope)
    val dir = File(context.cacheDir, "shared_substances").apply { mkdirs() }
    val sanitized = substance.substance.name.sanitizeForFile()
    val file = File(dir, "$sanitized.substance")
    file.writeText(payload)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, substance.substance.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun String.sanitizeForFile(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifEmpty { "substance" }
