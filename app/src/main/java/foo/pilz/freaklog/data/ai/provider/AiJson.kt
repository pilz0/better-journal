package foo.pilz.freaklog.data.ai.provider

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object AiJson {
    /**
     * Converts a [JSONObject] into a shallow map. Primitives stay String/Number/Boolean, JSON
     * `null` becomes Kotlin null, and nested objects and arrays stay JSONObject/JSONArray.
     */
    fun toMap(json: JSONObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = json.get(key).takeUnless { it == JSONObject.NULL }
        }
        return map
    }

    fun fromMap(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v ?: JSONObject.NULL) }
        return json
    }

    /** Parses streamed tool arguments. Blank or broken JSON becomes an empty object. */
    fun parseArguments(raw: String): JSONObject {
        if (raw.isBlank()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: JSONException) {
            JSONObject()
        }
    }

    fun objects(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }

    /** Copies a schema, upper-casing every `type` value (Gemini's OpenAPI dialect). */
    fun upperCaseSchemaTypes(schema: JSONObject): JSONObject {
        val copy = JSONObject()
        schema.keys().forEach { key ->
            val value = schema.get(key)
            copy.put(
                key,
                when {
                    key == "type" && value is String -> value.uppercase()
                    value is JSONObject -> upperCaseSchemaTypes(value)
                    else -> value
                },
            )
        }
        return copy
    }
}
