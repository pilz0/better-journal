package com.ndm4.freakquery

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream

object FreakQueryLoader {
    fun loadLogs(json: String, config: FreakQueryConfig = FreakQueryConfig()): List<Map<String, Any?>> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            when (trimmed.first()) {
                '[' -> jsonArrayToRows(JSONArray(trimmed))
                '{' -> {
                    val obj = JSONObject(trimmed)
                    if (obj.has("experiences")) loadJournal(obj, config) else emptyList()
                }
                else -> emptyList()
            }
        } catch (e: JSONException) {
            android.util.Log.w("FreakQueryLoader", "Failed to parse JSON input", e)
            emptyList()
        }
    }

    fun loadLogs(file: File, config: FreakQueryConfig = FreakQueryConfig()): List<Map<String, Any?>> =
        loadLogs(file.readText(Charsets.UTF_8), config)

    fun loadLogs(inputStream: InputStream, config: FreakQueryConfig = FreakQueryConfig()): List<Map<String, Any?>> =
        loadLogs(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }, config)

    private fun loadJournal(data: JSONObject, config: FreakQueryConfig): List<Map<String, Any?>> {
        val rows = mutableListOf<MutableMap<String, Any?>>()
        val experiences = data.optJSONArray("experiences") ?: return emptyList()

        for (i in 0 until experiences.length()) {
            val exp = experiences.optJSONObject(i) ?: continue
            val ingestions = exp.optJSONArray("ingestions") ?: continue

            for (j in 0 until ingestions.length()) {
                val ing = ingestions.optJSONObject(j) ?: continue
                val time = jsonValue(ing, "time")
                val id = time?.toString()?.toLongOrNull() ?: (rows.size + 1).toLong()

                val row = linkedMapOf<String, Any?>(
                    "id" to id,
                    "time" to time,
                    "substance" to Aliases.canonicalValue(config, "substance", jsonValue(ing, "substanceName") ?: ""),
                    "route" to Aliases.canonicalValue(config, "route", jsonValue(ing, "administrationRoute") ?: ""),
                    "dose" to jsonValue(ing, "dose"),
                    "unit" to Aliases.canonicalValue(config, "unit", jsonValue(ing, "units") ?: "")
                )

                jsonValue(ing, "administrationSite")?.let {
                    val site = it.toString()
                    if (site.isNotBlank()) row["site"] = Aliases.canonicalValue(config, "site", site)
                }
                jsonValue(ing, "notes")?.let {
                    if (it.toString().isNotBlank()) row["notes"] = it
                }
                if (ing.optBoolean("isDoseAnEstimate", false)) row["estimated"] = true
                rows += row
            }
        }

        return rows
    }

    private fun jsonArrayToRows(array: JSONArray): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            rows += jsonObjectToMap(obj)
        }
        return rows
    }

    internal fun jsonObjectToMap(obj: JSONObject): MutableMap<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = convertJson(obj.opt(key))
        }
        return out
    }

    private fun convertJson(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> {
            val list = mutableListOf<Any?>()
            for (i in 0 until value.length()) list += convertJson(value.opt(i))
            list
        }
        else -> value
    }

    private fun jsonValue(obj: JSONObject, key: String): Any? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return convertJson(obj.opt(key))
    }
}
