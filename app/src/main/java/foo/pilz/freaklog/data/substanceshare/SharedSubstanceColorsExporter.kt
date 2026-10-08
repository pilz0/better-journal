package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
    prettyPrint = true
    encodeDefaults = false
}

fun shareSubstanceColors(context: Context, colors: List<SharedSubstanceColor>) {
    val envelope = SharedSubstanceColorsEnvelope(
        format = SHARED_SUBSTANCE_COLORS_FORMAT,
        version = SHARED_SUBSTANCE_COLORS_VERSION,
        colors = colors,
    )
    val payload = json.encodeToString(SharedSubstanceColorsEnvelope.serializer(), envelope)
    val dir = File(context.cacheDir, "shared_substance_colors").apply { mkdirs() }
    val file = File(dir, "substance_colors.colors")
    file.writeText(payload)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Substance colors")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
