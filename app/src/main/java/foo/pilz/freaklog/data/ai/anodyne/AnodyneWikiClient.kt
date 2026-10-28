package foo.pilz.freaklog.data.ai.anodyne

import android.util.JsonReader
import android.util.JsonToken
import foo.pilz.freaklog.data.ai.provider.AiException
import foo.pilz.freaklog.data.ai.provider.AiHttp
import org.json.JSONArray
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** One entry of the anodyne.wiki substance index. */
data class AnodyneIndexEntry(val title: String, val aliases: List<String>, val classes: List<String>)

/**
 * Client for the AnodyneWiki API (https://anodyne.wiki/api), or any server that offers the same
 * endpoints. Substance pages can be several megabytes (mostly embedded experience reports), so
 * they are read with a streaming parser that keeps only the fields useful for harm reduction.
 */
@Singleton
class AnodyneWikiClient @Inject constructor() {
    companion object {
        const val DEFAULT_BASE_URL = "https://anodyne.wiki/api"
        private const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024
        private const val MAX_TEXT_CHARS = 1500
        private const val MAX_LIST_ITEMS = 10
        private const val INDEX_TTL_MS = 24L * 60 * 60 * 1000

        /** Free-text fields that are kept, with the key they get in the tool result. */
        private val TEXT_FIELDS = mapOf(
            "DurationOfAction" to "duration_of_action",
            "Biological Half-Life" to "biological_half_life",
            "Interactions" to "interactions",
            "Adverse Effects" to "adverse_effects",
            "Drug Warnings" to "drug_warnings",
            "Health Effects" to "health_effects",
            "Reported Fatal Dose" to "reported_fatal_dose",
            "Chemical Classes" to "chemical_class",
        )
        private val LIST_FIELDS = mapOf(
            "Abbreviation" to "abbreviations",
            "Aliases" to "aliases",
            "Classes" to "classes",
        )
        private val MACRO_WITH_TEXT = Regex("""\{\w+\s+'([^']*)'\}""")
        private val OTHER_MACRO = Regex("""\{[^{}]*\}""")
        private val HTML_TAG = Regex("""<[^>]+>""")
        private val WHITESPACE = Regex("""\s+""")

        /** Removes HTML tags, wiki macros and entities from a field. */
        fun clean(text: String): String = text
            .replace(MACRO_WITH_TEXT, "$1")
            .replace(OTHER_MACRO, "")
            .replace(HTML_TAG, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(WHITESPACE, " ")
            .trim()
    }

    private var cachedIndex: Pair<String, List<AnodyneIndexEntry>>? = null
    private var cachedAt = 0L

    /** Returns the cleaned substance page, or null when the wiki has no page with that name. */
    suspend fun substance(baseUrl: String, name: String): JSONObject? {
        val url = AiHttp.join(baseUrl, "substance/" + encodePathSegment(name.trim()))
        return try {
            AiHttp.get(url, emptyMap()) { stream, _ -> parseSubstance(LimitedInputStream(stream)) }
        } catch (e: AiException) {
            if (e.httpCode == 404) null else throw e
        }
    }

    /** Searches titles and alternative names in the substance index. */
    suspend fun search(baseUrl: String, query: String, limit: Int): List<AnodyneIndexEntry> {
        val q = query.trim().lowercase()
        val index = index(baseUrl)
        val exact = index.filter { e -> e.title.lowercase() == q || e.aliases.any { it.lowercase() == q } }
        val partial = index.filter { e ->
            e !in exact && (e.title.lowercase().contains(q) || e.aliases.any { it.lowercase().contains(q) })
        }
        return (exact + partial).take(limit)
    }

    private suspend fun index(baseUrl: String): List<AnodyneIndexEntry> {
        val now = System.currentTimeMillis()
        cachedIndex?.let { (url, entries) ->
            if (url == baseUrl && now - cachedAt < INDEX_TTL_MS) return entries
        }
        val json = AiHttp.getJson(AiHttp.join(baseUrl, "index/substance"), emptyMap())
        val entries = parseIndex(json)
        cachedIndex = baseUrl to entries
        cachedAt = now
        return entries
    }

    internal fun parseIndex(json: JSONObject): List<AnodyneIndexEntry> {
        val array = json.optJSONArray("Entries") ?: throw AiException("The wiki index has an unexpected format.")
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val title = entry.optString("Title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val aliases = listOf("Abr", "DTitle", "LTitle").flatMap { stringsOf(entry.opt(it)) }
            AnodyneIndexEntry(title, aliases, stringsOf(entry.opt("Classes")))
        }
    }

    private fun stringsOf(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is JSONArray -> (0 until value.length()).map { value.optString(it) }
        else -> emptyList()
    }.filter { it.isNotBlank() }

    /** Streams the substance object, keeping only whitelisted fields. */
    internal fun parseSubstance(stream: InputStream): JSONObject? {
        val out = JSONObject()
        var notFound = false
        JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                when {
                    key == "NotFound" -> notFound = readText(reader) == "true"
                    key == "Title" -> out.put("name", readText(reader))
                    key in TEXT_FIELDS -> readText(reader).let(::clean).takeIf { it.isNotBlank() }
                        ?.let { out.put(TEXT_FIELDS.getValue(key), it.take(MAX_TEXT_CHARS)) }
                    key in LIST_FIELDS -> readStrings(reader).take(MAX_LIST_ITEMS).takeIf { it.isNotEmpty() }
                        ?.let { out.put(LIST_FIELDS.getValue(key), JSONArray(it)) }
                    key == "Dosing Info" -> out.put("dosing", readValue(reader))
                    key == "Subjective Effects" -> out.put("subjective_effects", readValue(reader))
                    else -> reader.skipValue()
                }
            }
        }
        return if (notFound) null else out
    }

    /** Reads a string, number, boolean or array of strings as one string. */
    private fun readText(reader: JsonReader): String = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        JsonToken.BEGIN_ARRAY -> readStrings(reader).joinToString("; ")
        else -> {
            reader.skipValue()
            ""
        }
    }

    private fun readStrings(reader: JsonReader): List<String> {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) return listOf(readText(reader)).filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.STRING) result += reader.nextString() else reader.skipValue()
        }
        reader.endArray()
        return result.filter { it.isNotBlank() }
    }

    /** Reads any small JSON value into org.json types. */
    private fun readValue(reader: JsonReader): Any = when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> JSONObject().also { obj ->
            reader.beginObject()
            while (reader.hasNext()) obj.put(reader.nextName(), readValue(reader))
            reader.endObject()
        }
        JsonToken.BEGIN_ARRAY -> JSONArray().also { arr ->
            reader.beginArray()
            while (reader.hasNext()) arr.put(readValue(reader))
            reader.endArray()
        }
        JsonToken.NUMBER -> reader.nextDouble()
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NULL -> {
            reader.nextNull()
            JSONObject.NULL
        }
        else -> clean(reader.nextString())
    }

    private fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    /** Fails instead of reading an unexpectedly huge response to the end. */
    private class LimitedInputStream(input: InputStream) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it) }

        private fun add(n: Int) {
            count += n
            if (count > MAX_RESPONSE_BYTES) throw AiException("The wiki page is too large.")
        }
    }
}
