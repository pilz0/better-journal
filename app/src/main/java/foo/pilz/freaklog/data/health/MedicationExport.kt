package foo.pilz.freaklog.data.health

import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/** A report of self-logged doses, never a prescription or a claim of ongoing treatment. */
data class MedicationExport(val generatedAt: Instant, val statements: List<JsonObject>) {
    val patient: JsonObject = buildJsonObject {
        put("resourceType", "Patient")
        put("id", "freaklog-self")
    }

    fun bundle(): String {
        val entries = (listOf(patient) + statements).map { resource ->
            buildJsonObject {
                put("fullUrl", "$FHIR_BASE/${resource["resourceType"]!!.jsonPrimitive.content}/${resource["id"]!!.jsonPrimitive.content}")
                put("resource", resource)
            }
        }
        return buildJsonObject {
            put("resourceType", "Bundle")
            put("type", "collection")
            put("timestamp", generatedAt.toString())
            put("entry", JsonArray(entries))
        }.toString()
    }

    fun summary(): String = buildString {
        appendLine("Freaklog self-reported medication intake")
        appendLine("Generated $generatedAt. Logged doses in the preceding 30 days; not a prescription list.")
        appendLine("No inference about current treatment or adherence. Missing doses may be unlogged.")
        statements.forEach { statement ->
            val name = (statement["medicationCodeableConcept"] as JsonObject)["text"]!!.jsonPrimitive.content
            val period = statement["effectivePeriod"] as? JsonObject
            val time = statement["effectiveDateTime"]?.jsonPrimitive?.content
                ?: "${period?.get("start")?.jsonPrimitive?.content} to ${period?.get("end")?.jsonPrimitive?.content}"
            appendLine("$name — $time")
            val dosage = (statement["dosage"] as JsonArray).first() as JsonObject
            appendLine("  " + dosage["text"]!!.jsonPrimitive.content)
        }
    }

    companion object {
        const val FHIR_BASE = "freaklog://health/medications"
        val WINDOW: Duration = Duration.ofDays(30)

        fun create(
            rows: List<IngestionWithCompanionAndCustomUnit>,
            selected: Set<String>,
            now: Instant,
        ): MedicationExport {
            val statements = rows.asSequence()
                .filter { it.ingestion.substanceName in selected }
                .filter { it.ingestion.consumerName == null }
                .filter { it.ingestion.time >= now.minus(WINDOW) && it.ingestion.time <= now }
                .sortedBy { it.ingestion.time }
                .map { statement(it, now) }
                .toList()
            return MedicationExport(now, statements)
        }

        private fun statement(row: IngestionWithCompanionAndCustomUnit, now: Instant): JsonObject {
            val ingestion = row.ingestion
            val end = ingestion.endTime?.takeIf { it >= ingestion.time }
            // Include both unit counts and their calculated base dose, including uncertainty.
            val doseText = row.customUnitDose?.let { custom ->
                custom.doseDescription + (custom.calculatedDoseDescription?.let { " ($it)" } ?: "; base dose unknown")
            } ?: row.doseDescription
            // Keep units in text instead of inventing UCUM codes.
            val dosage = buildJsonObject {
                put(
                    "text",
                    "$doseText; route: ${ingestion.administrationRoute.name}" +
                        (ingestion.formulationName?.let { "; formulation: $it" } ?: "") +
                        (ingestion.saltForm?.let { "; salt: $it" } ?: "")
                )
                put("route", buildJsonObject { put("text", ingestion.administrationRoute.name) })
            }
            val note = buildJsonObject {
                put("text", "Self-reported in Freaklog. One logged intake, not a prescription or ongoing medication schedule.")
            }
            val medication = buildJsonObject { put("text", ingestion.substanceName) }
            return buildJsonObject {
                put("resourceType", "MedicationStatement")
                put("id", "freaklog-dose-${ingestion.id}")
                put("meta", buildJsonObject { put("source", FHIR_BASE) })
                put("status", if (end != null && end > now) "active" else "completed")
                put("medicationCodeableConcept", medication)
                put("subject", buildJsonObject { put("reference", "Patient/freaklog-self") })
                put("informationSource", buildJsonObject { put("reference", "Patient/freaklog-self") })
                if (end == null) {
                    put("effectiveDateTime", ingestion.time.toString())
                } else {
                    val period = buildJsonObject {
                        put("start", ingestion.time.toString())
                        put("end", end.toString())
                    }
                    put("effectivePeriod", period)
                }
                put("dosage", JsonArray(listOf(dosage)))
                put("note", JsonArray(listOf(note)))
            }
        }
    }
}
