package foo.pilz.freaklog.data.anodyne

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.ai.provider.AiHttp
import foo.pilz.freaklog.data.anodyne.model.AnodyneIndexEntry
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import java.io.File
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class AnodyneRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val fallbackStore: AnodyneFallbackStore,
) {
  companion object {
    const val DEFAULT_INSTANCE_URL = "https://anodyne.wiki"
    private const val SUBSTANCE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val INDEX_TTL_MS = 24L * 60 * 60 * 1000
    private const val USER_NOTES_TTL_MS = 24L * 60 * 60 * 1000

    fun normalizeUrl(raw: String): String? {
      var text = raw.trim()
      if (text.isBlank()) return null
      if (!text.contains("://")) {
        text = "https://$text"
      }
      while (text.endsWith("/")) {
        text = text.dropLast(1)
      }
      return try {
        val parsed = URL(text)
        val proto = parsed.protocol?.lowercase()
        if (proto != "http" && proto != "https") return null
        if (parsed.host.isNullOrBlank() || parsed.query != null) return null
        text
      } catch (_: Exception) {
        null
      }
    }
  }

  private val cacheDir: File
    get() = File(context.cacheDir, "AnodyneWiki").apply { if (!exists()) mkdirs() }

  suspend fun getBaseUrl(): String {
    return userPreferences.anodyneBaseUrlFlow.first().ifBlank { DEFAULT_INSTANCE_URL }
  }

  suspend fun isAutoLoadEnabled(): Boolean {
    return userPreferences.anodyneAutoLoadEnabledFlow.first()
  }

  fun cachedLookup(name: String): AnodyneSubstance? {
    val file = getSubstanceCacheFile(name)
    if (!file.exists()) return null
    if (System.currentTimeMillis() - file.lastModified() > SUBSTANCE_TTL_MS) return null
    return try {
      val json = JSONObject(file.readText())
      AnodyneParser.parseSubstance(json)?.also {
        fallbackStore.record(it, name)
      }
    } catch (_: Exception) {
      null
    }
  }

  suspend fun lookup(name: String, otherNames: List<String> = emptyList()): Result<AnodyneSubstance?> =
      withContext(Dispatchers.IO) {
        cachedLookup(name)?.let { return@withContext Result.success(it) }

        val baseUrl = getBaseUrl()
        val directResult = fetchSubstance(baseUrl, name)
        if (directResult.isSuccess && directResult.getOrNull() != null) {
          val sub = directResult.getOrNull()!!
          saveSubstanceToCache(name, sub)
          fallbackStore.record(sub, name)
          return@withContext Result.success(sub)
        }

        // Try index for alias match
        val indexEntries = getIndexEntries().getOrNull().orEmpty()
        val allSearchNames = listOf(name) + otherNames
        val match = indexEntries.firstOrNull { entry ->
          allSearchNames.any { entry.matches(it) }
        }

        if (match != null && !match.title.equals(name, ignoreCase = true)) {
          val canonicalResult = fetchSubstance(baseUrl, match.title)
          if (canonicalResult.isSuccess && canonicalResult.getOrNull() != null) {
            val sub = canonicalResult.getOrNull()!!
            saveSubstanceToCache(name, sub)
            saveSubstanceToCache(match.title, sub)
            fallbackStore.record(sub, name)
            return@withContext Result.success(sub)
          }
        }

        Result.success(null)
      }

  private suspend fun fetchSubstance(baseUrl: String, title: String): Result<AnodyneSubstance?> =
      try {
        val encoded = URLEncoder.encode(title.trim(), "UTF-8").replace("+", "%20")
        val url = "$baseUrl/api/substance/$encoded"
        val json = AiHttp.getJson(url, emptyMap())
        Result.success(AnodyneParser.parseSubstance(json))
      } catch (e: Exception) {
        Result.failure(e)
      }

  suspend fun getIndexEntries(): Result<List<AnodyneIndexEntry>> =
      withContext(Dispatchers.IO) {
        val indexFile = File(cacheDir, "index.json")
        if (indexFile.exists() && System.currentTimeMillis() - indexFile.lastModified() < INDEX_TTL_MS) {
          try {
            val json = JSONObject(indexFile.readText())
            return@withContext Result.success(AnodyneParser.parseIndex(json))
          } catch (_: Exception) {}
        }

        val baseUrl = getBaseUrl()
        try {
          val url = "$baseUrl/api/index/substance"
          val json = AiHttp.getJson(url, emptyMap())
          indexFile.writeText(json.toString())
          val entries = AnodyneParser.parseIndex(json)
          Result.success(entries)
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

  suspend fun getUserNotes(names: List<String>): Result<List<AnodyneUserNote>> =
      withContext(Dispatchers.IO) {
        val notesFile = File(cacheDir, "user_notes.json")
        val allNotes = mutableListOf<AnodyneUserNote>()

        if (notesFile.exists() && System.currentTimeMillis() - notesFile.lastModified() < USER_NOTES_TTL_MS) {
          try {
            val arr = org.json.JSONArray(notesFile.readText())
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              allNotes.addAll(AnodyneParser.parseUserNotes(obj))
            }
            fallbackStore.record(allNotes)
            return@withContext Result.success(filterNotes(allNotes, names))
          } catch (_: Exception) {}
        }

        val baseUrl = getBaseUrl()
        try {
          val indexUrl = "$baseUrl/api/index/user"
          val indexJson = AiHttp.getJson(indexUrl, emptyMap())
          val entries = indexJson.optJSONArray("Entries") ?: org.json.JSONArray()
          val collectedUserJsons = org.json.JSONArray()

          for (i in 0 until entries.length()) {
            val userTitle = entries.optJSONObject(i)?.optString("Title").takeIf { !it.isNullOrBlank() } ?: continue
            val encodedUser = URLEncoder.encode(userTitle, "UTF-8").replace("+", "%20")
            val userUrl = "$baseUrl/api/user/$encodedUser"
            try {
              val userJson = AiHttp.getJson(userUrl, emptyMap())
              collectedUserJsons.put(userJson)
              allNotes.addAll(AnodyneParser.parseUserNotes(userJson))
            } catch (_: Exception) {}
          }

          notesFile.writeText(collectedUserJsons.toString())
          fallbackStore.record(allNotes)
          Result.success(filterNotes(allNotes, names))
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

  private fun filterNotes(notes: List<AnodyneUserNote>, names: List<String>): List<AnodyneUserNote> {
    val nameSet = names.map { it.lowercase() }.toSet()
    return notes.filter { note ->
      note.hasDetails && nameSet.contains(note.substanceName.lowercase())
    }
  }

  fun getCacheSizeBytes(): Long {
    val files = cacheDir.listFiles() ?: return 0L
    return files.sumOf { it.length() }
  }

  fun clearCache() {
    cacheDir.listFiles()?.forEach { it.delete() }
    fallbackStore.clear()
  }

  private fun getSubstanceCacheFile(name: String): File {
    val safe = name.lowercase().replace(Regex("[^a-z0-9]"), "_")
    return File(cacheDir, "substance_$safe.json")
  }

  private fun saveSubstanceToCache(lookupKey: String, substance: AnodyneSubstance) {
    try {
      val file = getSubstanceCacheFile(lookupKey)
      val json = JSONObject().apply {
        put("Title", substance.title)
        put("Abbreviation", org.json.JSONArray(substance.abbreviations))
        put("Aliases", org.json.JSONArray(substance.aliases))
        put("Classes", org.json.JSONArray(substance.classes))
        put("DurationOfAction", substance.durationOfAction)
        put("Biological Half-Life", substance.halfLife)
        put("Interactions", substance.interactions)
        put("Mechanism of Action", substance.mechanismOfAction)
        put("Metabolism/Metabolites", substance.metabolism)
        put("Health Effects", substance.healthEffects)
        put("Drug Warnings", substance.drugWarnings)
        put("Reported Fatal Dose", substance.reportedFatalDose)
        put("Treatment", substance.treatment)
        put("MolecularFormula", substance.formula)
        put("MolecularWeight", substance.molarMass)
        if (substance.structureSvg != null) {
          put("Structure", substance.structureSvg)
        }
      }
      file.writeText(json.toString())
    } catch (_: Exception) {}
  }
}
