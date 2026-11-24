package foo.pilz.freaklog.ui.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Built into Android 14+. On Android 12 and 13 it is the Health Connect app, which the SDK finds
 * through the `<queries>` entry in the manifest.
 */
fun isHealthConnectAvailable(context: Context): Boolean =
    HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

/** True on Android 12/13 when the Health Connect app is missing or too old. */
fun isHealthConnectInstallable(context: Context): Boolean =
    HealthConnectClient.getSdkStatus(context) ==
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

fun healthConnectInstallIntent(): Intent =
    Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
                .toUri(),
        )
        .setPackage("com.android.vending")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

suspend fun hasHealthPermissions(context: Context, permissions: Set<String>): Boolean =
    HealthConnectClient.getOrCreate(context)
        .permissionController
        .getGrantedPermissions()
        .containsAll(permissions)

/**
 * Every record of [type] that starts in `[start - 24 h, end]`, across all pages. The extra day
 * catches series and sessions that began before [start]; callers trim to the exact range.
 */
suspend fun <T : Record> readHealthRecords(
    context: Context,
    type: KClass<T>,
    start: Instant,
    end: Instant,
): List<T> {
  val client = HealthConnectClient.getOrCreate(context)
  val records = mutableListOf<T>()
  var pageToken: String? = null
  do {
    val response =
        client.readRecords(
            ReadRecordsRequest(
                recordType = type,
                timeRangeFilter = TimeRangeFilter.between(start.minus(Duration.ofHours(24)), end),
                pageSize = 5000,
                pageToken = pageToken,
            )
        )
    records += response.records
    pageToken = response.pageToken
  } while (pageToken != null)
  return records
}
