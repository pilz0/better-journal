package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

const val SLEEP_GRAPH_LINE_HEIGHT = 4.0f
const val SLEEP_GRAPH_LINE_MARGIN_BOTTOM = 5.0f
const val SLEEP_GRAPH_LINE_ROUNDING = 2.0f

enum class SleepStageType {
    Awake {
        override val displayText = "Awake"
        override val displayInTimeline = true
        override val color = Color(0xFFFF9966)
    },
    Sleeping {
        override val displayText = "Asleep"
        override val displayInTimeline = true
        override val color = Color(0xFF3366FF)
    },
    SleepingLight {
        override val displayText = "Light sleep"
        override val displayInTimeline = true
        override val color = Color(0xFF6699FF)
    },
    SleepingDeep {
        override val displayText = "Deep sleep"
        override val displayInTimeline = true
        override val color = Color(0xFF6666FF)
    },
    SleepingREM {
        override val displayText = "REM sleep"
        override val displayInTimeline = true
        override val color = Color(0xFF9933FF)
    },
    Unknown {
        override val displayText = "Unknown sleep state"
        override val displayInTimeline = false
        override val color = Color(0xFF000000)
    };

    abstract val displayText: String
    abstract val displayInTimeline: Boolean
    abstract val color: Color
}

data class SleepStageSample(val start: Instant, val end: Instant, val stage: SleepStageType)
data class SleepSessionSample(val stages: List<SleepStageSample>) {
    val start get() = stages.minByOrNull { it.start }!!.start
    val end get() = stages.maxByOrNull { it.end }!!.end
}

object SleepHealthConnect {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )

    suspend fun isPermissionGranted(context: Context): Boolean =
        HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()
            .containsAll(permissions)

    suspend fun read(context: Context, start: Instant, end: Instant): List<SleepSessionSample> {
        val client = HealthConnectClient.getOrCreate(context)
        val result = mutableListOf<SleepSessionSample>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        start.minus(Duration.ofHours(24)),
                        end.plus(Duration.ofHours(2))
                    ),
                    pageSize = 5000,
                    pageToken = pageToken
                )
            )

            pageToken = response.pageToken

            for (record in response.records) {
                result.add(SleepSessionSample(
                    stages = record.stages.map {
                        SleepStageSample(
                            start = it.startTime,
                            end = it.endTime,
                            stage = when (it.stage) {
                                SleepSessionRecord.STAGE_TYPE_AWAKE,
                                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.Awake
                                SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.Sleeping
                                SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.SleepingLight
                                SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.SleepingDeep
                                SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.SleepingREM
                                else -> SleepStageType.Unknown
                            },
                        )
                    }
                ))
            }
        } while (pageToken != null)

        return result.sortedBy { it.start }
    }

    fun flattenSleepSessions(sessions: List<SleepSessionSample>)
        = sessions.flatMap { it.stages }

    fun sleepStateAt(
        samples: List<SleepStageSample>,
        at: Instant,
        withSleepStages: Boolean = false
    ): SleepStageSample? = samples
        .filter { it.stage.displayInTimeline }
        .firstOrNull { it.start <= at && it.end >= at }
        .let {
            if (withSleepStages) it
            else it?.copy(
                stage = when (it.stage) {
                    SleepStageType.SleepingLight -> SleepStageType.Sleeping
                    SleepStageType.SleepingDeep -> SleepStageType.Sleeping
                    SleepStageType.SleepingREM -> SleepStageType.Sleeping
                    else -> it.stage
                }
            )
        }

    data class GraphSleepLineShape(
        val x1: Float,
        val x2: Float,
        val roundLeft: Boolean,
        val roundRight: Boolean,
        val color: Color
    )

    fun buildGraphShapes(
        sessions: List<SleepSessionSample>,
        startTime: Instant,
        widthInSeconds: Float,
        withSleepStages: Boolean = false,
    ): List<GraphSleepLineShape>
        = sessions.flatMap { sample ->
            sample.stages
                .mapIndexed { index, stage ->
                    if (stage.stage.displayInTimeline) {
                        GraphSleepLineShape(
                            x1 = Duration.between(startTime, stage.start).seconds / widthInSeconds,
                            x2 = Duration.between(startTime, stage.end).seconds / widthInSeconds,
                            roundLeft = index == 0
                                    || sample.stages.getOrNull(index - 1)?.stage?.displayInTimeline == false,
                            roundRight = index == sample.stages.lastIndex
                                    || sample.stages.getOrNull(index + 1)?.stage?.displayInTimeline == false,
                            color = if (withSleepStages) stage.stage.color else when (stage.stage) {
                                SleepStageType.SleepingLight -> SleepStageType.Sleeping.color
                                SleepStageType.SleepingDeep -> SleepStageType.Sleeping.color
                                SleepStageType.SleepingREM -> SleepStageType.Sleeping.color
                                else -> stage.stage.color
                            },
                        )
                    } else null
                }
                .filterNotNull()
                .fold(emptyList<GraphSleepLineShape>()) { acc, shape ->
                    // collapses consecutive segments of equal color into a single segment
                    acc.lastOrNull()?.let { last ->
                        if (last.color == shape.color) {
                            return@fold acc.subList(0, acc.size - 1) + last.copy(x2 = shape.x2)
                        }
                    }
                    acc + shape
                }
                .filterNot { (it.x2 < 0f) xor (it.x1 > 1f) } // remove segments that wouldn't be visible
                .let { it.mapIndexed { index, shape ->
                    shape.copy(
                        // clamp values to 0..1
                        x1 = max(0f, shape.x1),
                        x2 = min(1f, shape.x2),

                        // since we removed out of bounds segments, make sure the first and last segments are rounded
                        roundLeft = shape.roundLeft || index == 0,
                        roundRight = shape.roundRight || index == it.lastIndex,
                    )
                } }
        }
}