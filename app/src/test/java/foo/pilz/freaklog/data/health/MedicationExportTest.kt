package foo.pilz.freaklog.data.health

import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MedicationExportTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private fun row(ingestion: Ingestion) = IngestionWithCompanionAndCustomUnit(ingestion, null, null)
    private fun dose(id: Int = 1, name: String = "Medication", time: Instant = now) =
        EntityBuilders.ingestion(id = id, substanceName = name, time = time)

    @Test fun excludesUnselectedOtherPeopleOldAndFutureDoses() {
        val rows = listOf(
            row(dose()),
            row(dose(2, "Private substance")),
            row(dose(3).copy(consumerName = "Someone else")),
            row(dose(4).copy(consumerName = "")),
            row(dose(5, time = now.minus(Duration.ofDays(30)).minusNanos(1))),
            row(dose(6, time = now.plusNanos(1))),
            row(dose(7, time = now.minus(Duration.ofDays(30)))),
        )
        val report = MedicationExport.create(rows, setOf("Medication"), now)
        assertEquals(
            listOf("freaklog-dose-7", "freaklog-dose-1"),
            report.statements.map { it["id"]!!.jsonPrimitive.content }
        )
        assertTrue(MedicationExport.create(rows, emptySet(), now).statements.isEmpty())
    }

    @Test fun bundleHasResolvedPatientReferenceAndSelfReportedCompletedStatements() {
        val report = MedicationExport.create(listOf(row(dose().copy(notes = "Private journal note"))), setOf("Medication"), now)
        val entries = Json.parseToJsonElement(report.bundle()).jsonObject["entry"]!!.jsonArray
        val patient = entries[0].jsonObject["resource"]!!.jsonObject
        val statement = entries[1].jsonObject["resource"]!!.jsonObject
        assertEquals("Patient", patient["resourceType"]!!.jsonPrimitive.content)
        assertEquals("freaklog-self", patient["id"]!!.jsonPrimitive.content)
        assertEquals(2, patient.size)
        assertEquals("Patient/freaklog-self", statement["subject"]!!.jsonObject["reference"]!!.jsonPrimitive.content)
        assertEquals("completed", statement["status"]!!.jsonPrimitive.content)
        assertEquals(now.toString(), statement["effectiveDateTime"]!!.jsonPrimitive.content)
        assertFalse(report.bundle().contains("Private journal note"))
        assertFalse(report.bundle().contains("MedicationRequest"))
    }

    @Test fun customUnitsUncertaintyAndMissingDosesArePreservedWithoutInventedCodes() {
        val unit = CustomUnit(
            id = 10, substanceName = "Medication", name = "Tablet",
            administrationRoute = AdministrationRoute.ORAL,
            dose = 20.0, estimatedDoseStandardDeviation = 2.0, isEstimate = true,
            isArchived = false, unit = "tablet", originalUnit = "mg", note = "Private unit note",
        )
        val custom = row(dose().copy(dose = 2.0, units = "tablet", customUnitId = 10))
            .copy(customUnit = unit)
        val unknown = row(dose(2).copy(dose = null, units = null))
        val report = MedicationExport.create(listOf(custom, unknown), setOf("Medication"), now)
        assertTrue(report.summary().contains("2 tablets (40±4 mg)"))
        assertTrue(report.summary().contains("Unknown dose"))
        assertTrue(report.summary().contains("ORAL"))
        assertFalse(report.bundle().contains("Private unit note"))
        assertFalse(report.bundle().contains("unitsofmeasure"))
        assertFalse(report.bundle().contains("doseQuantity"))
    }

    @Test fun repeatedExportsKeepIdsWhileEditsChangeContentsAndDeletionsDisappear() {
        val original = MedicationExport.create(listOf(row(dose())), setOf("Medication"), now)
        val edited = MedicationExport.create(listOf(row(dose().copy(dose = 50.0))), setOf("Medication"), now.plusSeconds(1))
        assertEquals(original.statements[0]["id"], edited.statements[0]["id"])
        assertNotEquals(original.statements[0], edited.statements[0])
        assertTrue(MedicationExport.create(emptyList(), setOf("Medication"), now).statements.isEmpty())
    }

    @Test fun ongoingIntakeUsesItsRecordedPeriodAndBecomesCompletedAfterTheEnd() {
        val end = now.plusSeconds(60)
        val rows = listOf(row(dose(time = now.minusSeconds(60)).copy(endTime = end)))
        val ongoing = MedicationExport.create(rows, setOf("Medication"), now)
        val statement = ongoing.statements.single()
        assertEquals("active", statement["status"]!!.jsonPrimitive.content)
        assertEquals(end.toString(), statement["effectivePeriod"]!!.jsonObject["end"]!!.jsonPrimitive.content)
        val completed = MedicationExport.create(rows, setOf("Medication"), end.plusSeconds(1))
        assertEquals("completed", completed.statements.single()["status"]!!.jsonPrimitive.content)
        assertTrue(ongoing.summary().contains(end.toString()))
    }

    @Test fun quotesUnicodeAndNewlinesRoundTripInFhirAndSummary() {
        val name = "Medication \"A\"\nµg"
        val report = MedicationExport.create(listOf(row(dose(name = name))), setOf(name), now)
        val decoded = Json.parseToJsonElement(report.bundle()).jsonObject["entry"]!!.jsonArray[1]
            .jsonObject["resource"]!!.jsonObject["medicationCodeableConcept"]!!.jsonObject["text"]!!.jsonPrimitive.content
        assertEquals(name, decoded)
        assertTrue(report.summary().contains(name))
    }
}
