@file:OptIn(androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi::class)

package foo.pilz.freaklog.data.health

import android.content.Context
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.FhirVersion
import androidx.health.connect.client.records.MedicalResource
import androidx.health.connect.client.request.CreateMedicalDataSourceRequest
import androidx.health.connect.client.request.GetMedicalDataSourcesRequest
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest
import androidx.health.connect.client.request.ReadMedicalResourcesPageRequest
import androidx.health.connect.client.request.ReadMedicalResourcesRequest
import androidx.health.connect.client.request.UpsertMedicalResourceRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StoredMedication(val id: String, val json: String)

/** Android medical-record API boundary. Keep platform FHIR objects out of the sync model. */
@Singleton
class MedicationRecordStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val client get() = HealthConnectClient.getOrCreate(context)

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun hasPermission() =
        client.permissionController.getGrantedPermissions().containsAll(MedicationHealthConnect.permissions)

    suspend fun ownSources(): List<String> =
        client.getMedicalDataSources(GetMedicalDataSourcesRequest(listOf(context.packageName)))
            .filter {
                it.packageName == context.packageName &&
                    it.displayName == MedicationHealthConnect.SOURCE_NAME &&
                    it.fhirBaseUri.toString() == MedicationExport.FHIR_BASE
            }.map { it.id }

    suspend fun createSource(): String = client.createMedicalDataSource(
        CreateMedicalDataSourceRequest(
            fhirBaseUri = Uri.parse(MedicationExport.FHIR_BASE),
            displayName = MedicationHealthConnect.SOURCE_NAME,
            fhirVersion = FhirVersion(4, 0, 1),
        )
    ).id

    suspend fun readPage(source: String, pageToken: String?): MedicationPage {
        val request: ReadMedicalResourcesRequest = if (pageToken == null) {
            ReadMedicalResourcesInitialRequest(
                medicalResourceType = MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS,
                medicalDataSourceIds = setOf(source),
                pageSize = 500,
            )
        } else {
            ReadMedicalResourcesPageRequest(pageToken, 500)
        }
        val response = client.readMedicalResources(request)
        return MedicationPage(
            response.medicalResources.map { StoredMedication(it.fhirResource.id, it.fhirResource.data) },
            response.nextPageToken,
        )
    }

    suspend fun upsert(source: String, resources: List<String>) {
        client.upsertMedicalResources(
            resources.map { UpsertMedicalResourceRequest(source, FhirVersion(4, 0, 1), it) }
        )
    }

    suspend fun delete(source: String, ids: List<String>) {
        client.deleteMedicalResources(
            ids.map {
                androidx.health.connect.client.records.MedicalResourceId(
                    source,
                    androidx.health.connect.client.records.FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT,
                    it,
                )
            }
        )
    }

    suspend fun removeSource(source: String) = client.deleteMedicalDataSourceWithData(source)
}

data class MedicationPage(val records: List<StoredMedication>, val nextPageToken: String?)
