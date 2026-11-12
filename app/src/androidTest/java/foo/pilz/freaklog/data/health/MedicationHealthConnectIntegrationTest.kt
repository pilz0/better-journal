@file:OptIn(androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi::class)

package foo.pilz.freaklog.data.health

import android.content.Context
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.FhirVersion
import androidx.health.connect.client.request.CreateMedicalDataSourceRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses synthetic data in an isolated source, never the user's Freaklog export. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class MedicationHealthConnectIntegrationTest {
  @Test
  fun platformAcceptsSelfReportedMedicationAndStableUpsert() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assumeTrue(HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
    val client = HealthConnectClient.getOrCreate(context)
    assumeTrue(
        "This system image does not expose medical records.",
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
    )
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val permission = HealthPermission.PERMISSION_WRITE_MEDICAL_DATA
    automation.grantRuntimePermission(context.packageName, permission)
    val source =
        client.createMedicalDataSource(
            CreateMedicalDataSourceRequest(
                Uri.parse("freaklog://integration-test/${UUID.randomUUID()}"),
                "Freaklog integration test ${UUID.randomUUID()}",
                FhirVersion(4, 0, 1),
            )
        )
    try {
      val now = Instant.now().minusSeconds(60)
      val dose =
          Ingestion(
              id = 1,
              substanceName = "Synthetic test medication",
              time = now,
              administrationRoute = AdministrationRoute.ORAL,
              dose = 5.0,
              isDoseAnEstimate = true,
              estimatedDoseStandardDeviation = null,
              units = "mg",
              experienceId = 0,
              notes = "Must stay private",
              stomachFullness = null,
              consumerName = null,
              customUnitId = null,
          )
      val report =
          MedicationExport.create(
              listOf(IngestionWithCompanionAndCustomUnit(dose, null, null)),
              setOf(dose.substanceName),
              now,
          )
      val store = MedicationRecordStore(context)
      assertTrue(store.isAvailable())
      assertTrue(store.hasPermission())
      assertFalse(source.id in store.ownSources())
      val resources = (listOf(report.patient) + report.statements).map { it.toString() }
      store.upsert(source.id, resources)
      val written = store.readPage(source.id, null).records
      store.upsert(source.id, resources)
      val read = store.readPage(source.id, null).records
      assertEquals(written.map { it.id }, read.map { it.id })
      assertEquals(1, read.size)
      assertTrue(read.single().json.contains("Synthetic test medication"))
      assertTrue(!read.single().json.contains("Must stay private"))
      store.delete(source.id, read.map { it.id })
      assertTrue(store.readPage(source.id, null).records.isEmpty())
    } finally {
      client.deleteMedicalDataSourceWithData(source.id)
    }
  }
}
