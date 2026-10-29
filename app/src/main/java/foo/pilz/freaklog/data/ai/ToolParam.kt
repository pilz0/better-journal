package foo.pilz.freaklog.data.ai

import foo.pilz.freaklog.data.ai.provider.AiToolSpec
import org.json.JSONArray
import org.json.JSONObject

/** One tool parameter. [schema] is its JSON Schema without the description. */
internal class ToolParam(val name: String, val schema: JSONObject, val description: String, val required: Boolean) {
    companion object {
        fun string(name: String, description: String, required: Boolean = false) =
            ToolParam(name, JSONObject().put("type", "string"), description, required)

        fun integer(name: String, description: String, required: Boolean = false) =
            ToolParam(name, JSONObject().put("type", "integer"), description, required)

        fun stringArray(name: String, description: String, required: Boolean = false) = ToolParam(
            name,
            JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")),
            description,
            required,
        )
    }
}

/** Builds a tool declaration with an object schema of [params]. */
internal fun toolSpec(name: String, description: String, vararg params: ToolParam): AiToolSpec {
    val properties = JSONObject()
    val required = JSONArray()
    params.forEach { p ->
        properties.put(p.name, JSONObject(p.schema.toString()).put("description", p.description))
        if (p.required) required.put(p.name)
    }
    val schema = JSONObject().put("type", "object").put("properties", properties)
    if (required.length() > 0) schema.put("required", required)
    return AiToolSpec(name, description, schema)
}

internal fun toolError(message: String): JSONObject = JSONObject().put("status", "error").put("message", message)

/** Reads a string-list argument that the model may send as an array or a comma-separated string. */
internal fun stringListArg(value: Any?): List<String> = when (value) {
    is JSONArray -> (0 until value.length()).map { value.optString(it) }
    is String -> value.split(',')
    is List<*> -> value.map { it.toString() }
    else -> emptyList()
}.map { it.trim() }.filter { it.isNotEmpty() }

/** Reads an integer argument that may arrive as a [Number] or a numeric [String]. */
internal fun intArg(value: Any?, default: Int): Int =
    (value as? Number)?.toInt() ?: (value as? String)?.trim()?.toIntOrNull() ?: default
