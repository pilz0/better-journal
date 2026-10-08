/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.vitals

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToLong

data class HeartRateSample(val time: Instant, val bpm: Long)

data class HeartRateSummary(val min: Long, val average: Long, val max: Long)

fun summarizeHeartRate(samples: List<HeartRateSample>): HeartRateSummary? =
    if (samples.isEmpty()) {
        null
    } else {
        HeartRateSummary(
            min = samples.minOf { it.bpm },
            average = samples.map { it.bpm }.average().roundToLong(),
            max = samples.maxOf { it.bpm },
        )
    }

/** Reads heart rate recorded by other apps and wearables through Android's Health Connect. */
object HeartRateHealthConnect {
    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(HeartRateRecord::class))

    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun isPermissionGranted(context: Context): Boolean =
        HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()
            .containsAll(permissions)

    suspend fun read(context: Context, start: Instant, end: Instant): List<HeartRateSample> {
        val client = HealthConnectClient.getOrCreate(context)
        val samples = mutableListOf<HeartRateSample>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                )
            )
            response.records.forEach { record ->
                record.samples.forEach { sample ->
                    if (!sample.time.isBefore(start) && !sample.time.isAfter(end)) {
                        samples.add(HeartRateSample(sample.time, sample.beatsPerMinute))
                    }
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return downsampleHeartRate(samples.sortedBy { it.time }, start, end)
    }

    private const val PAGE_SIZE = 5000
}

/** Averages samples into at most [MAX_POINTS] time buckets so long experiences stay cheap to draw. */
internal fun downsampleHeartRate(
    samples: List<HeartRateSample>,
    start: Instant,
    end: Instant,
): List<HeartRateSample> {
    if (samples.size <= MAX_POINTS) return samples
    val bucketSeconds = max(1L, Duration.between(start, end).seconds / MAX_POINTS)
    return samples
        .groupBy { Duration.between(start, it.time).seconds / bucketSeconds }
        .map { (_, group) ->
            HeartRateSample(
                time = Instant.ofEpochSecond(group.map { it.time.epochSecond }.average().roundToLong()),
                bpm = group.map { it.bpm }.average().roundToLong(),
            )
        }
        .sortedBy { it.time }
}

internal const val MAX_POINTS = 500
