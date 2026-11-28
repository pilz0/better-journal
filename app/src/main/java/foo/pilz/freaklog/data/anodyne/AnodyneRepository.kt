package foo.pilz.freaklog.data.anodyne

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.ai.profiles.isInsecureRemoteUrl
import foo.pilz.freaklog.data.ai.provider.AiException
import foo.pilz.freaklog.data.ai.provider.AiHttp
import foo.pilz.freaklog.data.anodyne.model.AnodyneIndexEntry
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AnodyneRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
  private class CachedEntry<T>(
      val value: T,
      val cachedAtMs: Long,
      val fileLastModifiedMs: Long,
      val baseUrl: String = "",
  )

  private val substanceMemoryCache = ConcurrentHashMap<String, CachedEntry<AnodyneSubstance>>()
  @Volatile private var indexMemoryCache: CachedEntry<List<AnodyneIndexEntry>>? = null
  @Volatile private var userNotesMemoryCache: CachedEntry<List<AnodyneUserNote>>? = null

  companion object {
    const val DEFAULT_INSTANCE_URL = "https://anodyne.wiki"
    private const val SUBSTANCE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val INDEX_TTL_MS = 24L * 60 * 60 * 1000
    private const val USER_NOTES_TTL_MS = 24L * 60 * 60 * 1000
    private const val USER_NOTES_FETCH_CONCURRENCY = 4
    private val UNSAFE_CACHE_KEY_REGEX = Regex("[^a-z0-9]")

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
        val proto = parsed.protocol?.lowercase(Locale.ROOT)
        if (proto != "http" && proto != "https") return null
        if (parsed.host.isNullOrBlank() || parsed.query != null) return null
        if (isInsecureRemoteUrl(text)) return null
        text.removeSuffix("/api")
      } catch (_: Exception) {
        null
      }
    }

    /** What to tell the user after a [lookup] that found no page, or null if it found one. */
    fun lookupProblem(result: Result<AnodyneSubstance?>): String? =
        when {
          result.isFailure ->
              "Couldn't reach AnodyneWiki. Check your connection or the instance URL."
          result.getOrNull() == null -> "AnodyneWiki has no page for this substance."
          else -> null
        }

    /** Link to the wiki page of [title] on the instance at [baseUrl]. */
    fun substancePageUrl(baseUrl: String, title: String): String =
        "$baseUrl/substance/" + URLEncoder.encode(title.trim(), "UTF-8").replace("+", "%20")
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
    if (!file.exists()) {
      substanceMemoryCache.remove(safeCacheKey(name))
      return null
    }
    val now = System.currentTimeMillis()
    val lastMod = file.lastModified()
    if (now - lastMod > SUBSTANCE_TTL_MS) return null
    val key = safeCacheKey(name)
    substanceMemoryCache[key]?.let { entry ->
      if (entry.fileLastModifiedMs == lastMod && now - entry.cachedAtMs <= SUBSTANCE_TTL_MS) {
        return entry.value
      }
    }
    return try {
      val json = JSONObject(file.readText())
      val parsed = AnodyneParser.parseSubstance(json)
      if (parsed != null) {
        substanceMemoryCache[key] = CachedEntry(parsed, lastMod, lastMod)
      }
      parsed
    } catch (_: Exception) {
      null
    }
  }

  suspend fun lookup(
      name: String,
      otherNames: List<String> = emptyList(),
  ): Result<AnodyneSubstance?> =
      withContext(Dispatchers.IO) {
        cachedLookup(name)?.let {
          return@withContext Result.success(it)
        }
        try {
          Result.success(fetchAndCache(name, otherNames))
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Offline or a broken page: report it, don't claim the wiki has no page.
          Result.failure(e)
        }
      }

  private suspend fun fetchAndCache(name: String, otherNames: List<String>): AnodyneSubstance? {
    val baseUrl = getBaseUrl()
    fetchSubstance(baseUrl, name)?.let { (json, sub) ->
      saveSubstanceToCache(name, json, sub)
      return sub
    }

    // Try the index for an alias match.
    val indexEntries = getIndexEntries().getOrThrow()
    val allSearchNames = listOf(name) + otherNames
    val match = indexEntries.firstOrNull { entry ->
      allSearchNames.any { entry.matches(it) }
    }

    if (match != null && !match.title.equals(name, ignoreCase = true)) {
      fetchSubstance(baseUrl, match.title)?.let { (json, sub) ->
        saveSubstanceToCache(name, json, sub)
        saveSubstanceToCache(match.title, json, sub)
        return sub
      }
    }

    return null
  }

  /** The raw page and its parsed form, or null when the wiki has no such page. */
  private suspend fun fetchSubstance(
      baseUrl: String,
      title: String,
  ): Pair<JSONObject, AnodyneSubstance>? {
    val encoded = URLEncoder.encode(title.trim(), "UTF-8").replace("+", "%20")
    val json =
        try {
          AiHttp.getJson("$baseUrl/api/substance/$encoded", emptyMap())
        } catch (e: AiException) {
          if (e.httpCode == 404) return null else throw e
        }
    return AnodyneParser.parseSubstance(json)?.let { json to it }
  }

  suspend fun getIndexEntries(): Result<List<AnodyneIndexEntry>> =
      withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val indexFile = File(cacheDir, "index.json")
        val now = System.currentTimeMillis()
        if (indexFile.exists() && now - indexFile.lastModified() < INDEX_TTL_MS) {
          val lastMod = indexFile.lastModified()
          indexMemoryCache?.let { entry ->
            if (
                entry.baseUrl == baseUrl &&
                    entry.fileLastModifiedMs == lastMod &&
                    now - entry.cachedAtMs < INDEX_TTL_MS
            ) {
              return@withContext Result.success(entry.value)
            }
          }
          try {
            val json = JSONObject(indexFile.readText())
            val entries = AnodyneParser.parseIndex(json)
            indexMemoryCache = CachedEntry(entries, lastMod, lastMod, baseUrl)
            return@withContext Result.success(entries)
          } catch (ce: CancellationException) {
            throw ce
          } catch (_: Exception) {
            // Cache corrupted, fetch fresh
          }
        }

        try {
          val url = "$baseUrl/api/index/substance"
          val json = AiHttp.getJson(url, emptyMap())
          indexFile.writeText(json.toString())
          val entries = AnodyneParser.parseIndex(json)
          val savedAt = System.currentTimeMillis()
          indexMemoryCache = CachedEntry(entries, savedAt, indexFile.lastModified(), baseUrl)
          Result.success(entries)
        } catch (ce: CancellationException) {
          throw ce
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

  suspend fun getUserNotes(names: List<String>): Result<List<AnodyneUserNote>> =
      withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val notesFile = File(cacheDir, "user_notes.json")
        val now = System.currentTimeMillis()

        if (notesFile.exists() && now - notesFile.lastModified() < USER_NOTES_TTL_MS) {
          val lastMod = notesFile.lastModified()
          userNotesMemoryCache?.let { entry ->
            if (
                entry.baseUrl == baseUrl &&
                    entry.fileLastModifiedMs == lastMod &&
                    now - entry.cachedAtMs < USER_NOTES_TTL_MS
            ) {
              return@withContext Result.success(filterNotes(entry.value, names))
            }
          }
          try {
            val arr = JSONArray(notesFile.readText())
            val cachedNotes = mutableListOf<AnodyneUserNote>()
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              cachedNotes.addAll(AnodyneParser.parseUserNotes(obj))
            }
            userNotesMemoryCache = CachedEntry(cachedNotes, lastMod, lastMod, baseUrl)
            return@withContext Result.success(filterNotes(cachedNotes, names))
          } catch (ce: CancellationException) {
            throw ce
          } catch (_: Exception) {
            // Cache corrupted, fetch fresh
          }
        }

        try {
          val indexUrl = "$baseUrl/api/index/user"
          val indexJson = AiHttp.getJson(indexUrl, emptyMap())
          val entries = indexJson.optJSONArray("Entries") ?: JSONArray()
          val titles =
              (0 until entries.length()).mapNotNull { i ->
                entries.optJSONObject(i)?.optString("Title")?.takeIf { it.isNotBlank() }
              }
          val semaphore = Semaphore(USER_NOTES_FETCH_CONCURRENCY)
          val fetchedUsers = coroutineScope {
            titles
                .map { userTitle ->
                  async {
                    semaphore.withPermit {
                      val encodedUser = URLEncoder.encode(userTitle, "UTF-8").replace("+", "%20")
                      val userUrl = "$baseUrl/api/user/$encodedUser"
                      try {
                        val userJson = AiHttp.getJson(userUrl, emptyMap())
                        userJson to AnodyneParser.parseUserNotes(userJson)
                      } catch (ce: CancellationException) {
                        throw ce
                      } catch (_: Exception) {
                        // Ignore individual user note failures
                        null
                      }
                    }
                  }
                }
                .awaitAll()
                .filterNotNull()
          }
          val collectedUserJsons = JSONArray()
          val allNotes = mutableListOf<AnodyneUserNote>()
          for ((userJson, notes) in fetchedUsers) {
            collectedUserJsons.put(userJson)
            allNotes.addAll(notes)
          }

          notesFile.writeText(collectedUserJsons.toString())
          val savedAt = System.currentTimeMillis()
          userNotesMemoryCache = CachedEntry(allNotes, savedAt, notesFile.lastModified(), baseUrl)
          Result.success(filterNotes(allNotes, names))
        } catch (ce: CancellationException) {
          throw ce
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

  private fun filterNotes(
      notes: List<AnodyneUserNote>,
      names: List<String>,
  ): List<AnodyneUserNote> {
    val nameSet = names.map { it.lowercase(Locale.ROOT) }.toSet()
    return notes.filter { note ->
      note.hasDetails && nameSet.contains(note.substanceName.lowercase(Locale.ROOT))
    }
  }

  fun getCacheSizeBytes(): Long {
    val files = cacheDir.listFiles() ?: return 0L
    return files.sumOf { it.length() }
  }

  fun clearCache() {
    substanceMemoryCache.clear()
    indexMemoryCache = null
    userNotesMemoryCache = null
    cacheDir.listFiles()?.forEach { it.delete() }
  }

  private fun safeCacheKey(name: String): String =
      name.lowercase(Locale.ROOT).replace(UNSAFE_CACHE_KEY_REGEX, "_")

  private fun getSubstanceCacheFile(name: String): File {
    // "page_": older "substance_" files held a lossy re-encoding of the page; ignore them.
    return File(cacheDir, "page_${safeCacheKey(name)}.json")
  }

  /** Caches the raw page so a cached lookup parses exactly like a fresh one. */
  private fun saveSubstanceToCache(
      lookupKey: String,
      json: JSONObject,
      parsed: AnodyneSubstance,
  ) {
    try {
      val file = getSubstanceCacheFile(lookupKey)
      file.writeText(json.toString())
      val modTime = file.lastModified()
      substanceMemoryCache[safeCacheKey(lookupKey)] = CachedEntry(parsed, modTime, modTime)
    } catch (_: Exception) {
      // Best effort cache write
    }
  }
}
