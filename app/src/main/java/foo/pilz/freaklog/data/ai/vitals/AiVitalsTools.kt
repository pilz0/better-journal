package foo.pilz.freaklog.data.ai.vitals

import foo.pilz.freaklog.data.ai.AI_DATE_FORMATTER
import foo.pilz.freaklog.data.ai.ToolParam
import foo.pilz.freaklog.data.ai.intArg
import foo.pilz.freaklog.data.ai.provider.AiToolSpec
import foo.pilz.freaklog.data.ai.stringListArg
import foo.pilz.freaklog.data.ai.toolError
import foo.pilz.freaklog.data.ai.toolSpec
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading as LegacyBloodPressure
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureReading
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepSessionSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepStageType
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalSeries
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that summarizes Health Connect and logged blood pressure vitals for the assistant. Disabled
 * by default; controlled by [UserPreferences.aiVitalsEnabledFlow].
 */
@Singleton
class AiVitalsTools
@Inject
constructor(
    private val experienceDao: ExperienceDao,
    private val userPreferences: UserPreferences,
    private val substances: SubstanceRepositoryInterface,
    private val customProfiles: CustomSubstanceProfiles,
    private val vitalsDataSource: VitalsDataSource,
) {
  companion object {
    const val DEFAULT_HOURS_BACK = 24
    const val MAX_HOURS_BACK = 168
    private const val MAX_SESSION_HOURS = 72L
    private const val MAX_HR_BUCKETS = 24
    private const val MAX_BP_READINGS = 40
    private const val MAX_EXTRA_SAMPLES = 20
    private const val SECONDS_PER_HOUR = 3600L
  }

  val specs: List<AiToolSpec> =
      listOf(
          toolSpec(
              name = "get_vitals",
              description =
                  "Read the user's physiological vitals from Health Connect and logged blood pressure " +
                      "measurements: heart rate, blood pressure, sleep stages, SpO2, respiratory rate, " +
                      "body/skin temperature, and HRV. Pass `experience_id` to inspect a specific " +
                      "session window, or `hours_back` for recent vitals.",
              ToolParam.integer(
                  "experience_id",
                  "Optional experience id whose session time window should be inspected.",
              ),
              ToolParam.integer(
                  "hours_back",
                  "How many hours back from now to look when experience_id is omitted (default $DEFAULT_HOURS_BACK, max $MAX_HOURS_BACK).",
              ),
              ToolParam.stringArray(
                  "types",
                  "Optional subset of vitals to read: heart_rate, blood_pressure, sleep, " +
                      "oxygen_saturation, respiratory_rate, body_temperature, skin_temperature, " +
                      "heart_rate_variability. Defaults to all granted metrics.",
              ),
          )
      )

  private val names = specs.map { it.name }.toSet()

  suspend fun isEnabled(): Boolean = userPreferences.aiVitalsEnabledFlow.first()

  /** Runs [name] if it is `get_vitals`; returns null for other tool names. */
  suspend fun execute(name: String, args: Map<String, Any?>): JSONObject? {
    if (name !in names) return null
    if (!isEnabled()) {
      return toolError("Vitals access is turned off in Settings → AI assistant.")
    }
    val requestedRaw = stringListArg(args["types"])
    val requestedTypes =
        if (requestedRaw.isEmpty()) {
          null
        } else {
          requestedRaw.mapNotNull(VitalsTypes::normalize).toSet().also { parsed ->
            if (parsed.isEmpty()) {
              return toolError(
                  "Unknown vital types: ${requestedRaw.joinToString()}. " +
                      "Valid types: ${VitalsTypes.ALL.joinToString()}."
              )
            }
          }
        }

    val now = Instant.now()
    val hasExperienceId = args["experience_id"] != null
    val window =
        if (hasExperienceId) {
          val expId = intArg(args["experience_id"], -1)
          if (expId < 0)
              return toolError("Parameter 'experience_id' must be a valid experience id.")
          resolveExperienceWindow(expId, now)
              ?: return toolError("No experience found with id $expId.")
        } else {
          val hours = intArg(args["hours_back"], DEFAULT_HOURS_BACK).coerceIn(1, MAX_HOURS_BACK)
          val start = now.minusSeconds(hours * SECONDS_PER_HOUR)
          val legacyBp =
              if (requestedTypes == null || VitalsTypes.BLOOD_PRESSURE in requestedTypes) {
                experienceDao.getBloodPressureReadingsBetween(start, now)
              } else {
                emptyList()
              }
          VitalsWindow(start = start, end = now, experience = null, legacyBp = legacyBp)
        }

    val snapshot = vitalsDataSource.read(window.start, window.end, requestedTypes)
    return buildResult(window, snapshot, requestedTypes)
  }

  private data class VitalsWindow(
      val start: Instant,
      val end: Instant,
      val experience: ExperienceWithIngestionsCompanionsAndRatings?,
      val legacyBp: List<LegacyBloodPressure>,
  )

  private suspend fun resolveExperienceWindow(experienceId: Int, now: Instant): VitalsWindow? {
    val exp =
        experienceDao.getExperienceWithIngestionsCompanionsAndRatings(experienceId) ?: return null
    val timedNotes = experienceDao.getTimedNotes(experienceId)
    val legacyBp = experienceDao.getBloodPressureReadings(experienceId)
    val allIngestions = exp.ingestionsWithCompanions.map { it.ingestion }
    val myIngestions = allIngestions.filter { it.consumerName == null }.ifEmpty { allIngestions }

    val expectedEnds = myIngestions.mapNotNull { ing ->
      val roa =
          customProfiles.getRoa(ing.substanceName, ing.administrationRoute)
              ?: substances.getSubstance(ing.substanceName)?.getRoa(ing.administrationRoute)
      val totalSec = roa?.roaDuration?.total?.maxInSec
      if (totalSec != null && totalSec.isFinite() && totalSec > 0f) {
        ing.time.plusSeconds(
            totalSec.coerceIn(1f, MAX_SESSION_HOURS * SECONDS_PER_HOUR.toFloat()).toLong()
        )
      } else {
        ing.endTime
      }
    }
    val eventTimes =
        myIngestions.map { it.time } +
            myIngestions.mapNotNull { it.endTime } +
            exp.ratings.mapNotNull { it.time } +
            timedNotes.map { it.time } +
            legacyBp.map { it.time }

    val (start, end) =
        if (eventTimes.isEmpty()) {
          val s = exp.experience.sortDate.minus(Duration.ofMinutes(30))
          val defaultEnd = exp.experience.sortDate.plus(Duration.ofHours(6))
          val e = if (defaultEnd.isAfter(now) && !s.isAfter(now)) now else defaultEnd
          s to e
        } else {
          val firstEvent = eventTimes.min()
          val lastEvent = (eventTimes + expectedEnds).max()
          val s = firstEvent.minus(Duration.ofMinutes(30))
          val afterLast = lastEvent.plus(Duration.ofHours(2))
          val minSpan = firstEvent.plus(Duration.ofHours(4))
          val rawEnd = if (afterLast.isAfter(minSpan)) afterLast else minSpan
          val maxEnd = s.plus(Duration.ofHours(MAX_SESSION_HOURS))
          var clamped = if (rawEnd.isAfter(maxEnd)) maxEnd else rawEnd
          if (clamped.isAfter(now) && !s.isAfter(now)) clamped = now
          s to if (clamped.isAfter(s)) clamped else s.plus(Duration.ofMinutes(1))
        }

    return VitalsWindow(start = start, end = end, experience = exp, legacyBp = legacyBp)
  }

  private fun buildResult(
      window: VitalsWindow,
      snapshot: VitalsSnapshot,
      requestedTypes: Set<String>?,
  ): JSONObject {
    val availableTypes = mutableListOf<String>()
    val result =
        JSONObject()
            .put("status", "success")
            .put("window_start", AI_DATE_FORMATTER.format(window.start))
            .put("window_end", AI_DATE_FORMATTER.format(window.end))
            .put("health_connect_available", snapshot.healthConnectAvailable)
            .put("granted_types", JSONArray(snapshot.grantedTypes.toList()))
            .put("missing_permission_types", JSONArray(snapshot.missingPermissionTypes.toList()))

    window.experience?.let { exp ->
      result.put("experience_id", exp.experience.id)
      result.put("experience_title", exp.experience.title)
    }

    if (snapshot.heartRate.isNotEmpty()) {
      availableTypes += VitalsTypes.HEART_RATE
      result.put("heart_rate", formatHeartRate(snapshot.heartRate))
    }

    val includeBp = requestedTypes == null || VitalsTypes.BLOOD_PRESSURE in requestedTypes
    val bpJson =
        if (includeBp) formatBloodPressure(snapshot.bloodPressure, window.legacyBp) else null
    if (bpJson != null) {
      availableTypes += VitalsTypes.BLOOD_PRESSURE
      result.put("blood_pressure", bpJson)
    }

    val sleepJson = formatSleep(snapshot.sleep, window.start, window.end)
    if (sleepJson.length() > 0) {
      availableTypes += VitalsTypes.SLEEP
      result.put("sleep", sleepJson)
    }

    val extraObj = formatExtraVitals(snapshot.extraVitals, availableTypes)
    if (extraObj.length() > 0) {
      result.put("extra_vitals", extraObj)
    }

    result.put("available_data_types", JSONArray(availableTypes))
    result.put("note", buildNote(snapshot))
    return result
  }

  private fun formatHeartRate(samples: List<HeartRateSample>): JSONObject {
    val sorted = samples.sortedBy { it.time }
    val minSample = sorted.minBy { it.bpm }
    val maxSample = sorted.maxBy { it.bpm }
    val sortedValues = sorted.map { it.bpm }.sorted()
    val median = sortedValues[sortedValues.size / 2]
    val avg = sorted.map { it.bpm }.average().roundToInt()
    val latest = sorted.last()
    return JSONObject()
        .put("sample_count", sorted.size)
        .put("min_bpm", minSample.bpm)
        .put("min_time", AI_DATE_FORMATTER.format(minSample.time))
        .put("max_bpm", maxSample.bpm)
        .put("max_time", AI_DATE_FORMATTER.format(maxSample.time))
        .put("avg_bpm", avg)
        .put("median_bpm", median)
        .put("latest_bpm", latest.bpm)
        .put("latest_time", AI_DATE_FORMATTER.format(latest.time))
        .put("timeline", bucketHeartRate(sorted))
  }

  private fun bucketHeartRate(sorted: List<HeartRateSample>): JSONArray {
    val arr = JSONArray()
    if (sorted.size <= MAX_HR_BUCKETS) {
      sorted.forEach { s ->
        arr.put(
            JSONObject()
                .put("time", AI_DATE_FORMATTER.format(s.time))
                .put("avg_bpm", s.bpm)
                .put("min_bpm", s.bpm)
                .put("max_bpm", s.bpm)
        )
      }
      return arr
    }
    val first = sorted.first().time
    val span = max(1L, Duration.between(first, sorted.last().time).seconds)
    val grouped = sorted.groupBy { sample ->
      val elapsed = Duration.between(first, sample.time).seconds.coerceAtLeast(0L)
      ((elapsed * MAX_HR_BUCKETS) / (span + 1)).toInt().coerceIn(0, MAX_HR_BUCKETS - 1)
    }
    grouped.toSortedMap().values.forEach { bucket ->
      val midMillis = bucket.map { it.time.toEpochMilli() }.average().roundToLong()
      arr.put(
          JSONObject()
              .put("time", AI_DATE_FORMATTER.format(Instant.ofEpochMilli(midMillis)))
              .put("avg_bpm", bucket.map { it.bpm }.average().roundToInt())
              .put("min_bpm", bucket.minOf { it.bpm })
              .put("max_bpm", bucket.maxOf { it.bpm })
      )
    }
    return arr
  }

  private data class BpEntry(
      val time: Instant,
      val systolic: Double,
      val diastolic: Double,
      val pulse: Int?,
      val source: String,
  )

  private fun formatBloodPressure(
      hcReadings: List<BloodPressureReading>,
      legacyReadings: List<LegacyBloodPressure>,
  ): JSONObject? {
    val merged =
        (hcReadings.map {
              BpEntry(it.time, it.systolic, it.diastolic, null, "health_connect")
            } +
                legacyReadings.map {
                  BpEntry(
                      it.time,
                      it.systolic.toDouble(),
                      it.diastolic.toDouble(),
                      it.pulse,
                      "journal_legacy",
                  )
                })
            .filter { it.systolic.isFinite() && it.diastolic.isFinite() }
            .sortedBy { it.time }
    if (merged.isEmpty()) return null
    val capped = merged.takeLast(MAX_BP_READINGS)
    val arr = JSONArray()
    capped.forEach { entry -> arr.put(entryToJson(entry)) }
    return JSONObject()
        .put("count", merged.size)
        .put("latest", entryToJson(merged.last()))
        .put("readings", arr)
  }

  private fun entryToJson(entry: BpEntry): JSONObject =
      JSONObject()
          .put("time", AI_DATE_FORMATTER.format(entry.time))
          .put("systolic_mmhg", round1(entry.systolic))
          .put("diastolic_mmhg", round1(entry.diastolic))
          .put("pulse_bpm", entry.pulse ?: JSONObject.NULL)
          .put("source", entry.source)

  private fun formatSleep(
      sessions: List<SleepSessionSample>,
      start: Instant,
      end: Instant,
  ): JSONArray {
    val arr = JSONArray()
    sessions.forEach { session ->
      val sStart = session.start ?: return@forEach
      val sEnd = session.end ?: sStart
      if (sEnd.isBefore(start) || sStart.isAfter(end)) return@forEach
      val durationMin = Duration.between(sStart, sEnd).toMinutes().coerceAtLeast(0L)
      val stageSec = mutableMapOf<SleepStageType, Long>()
      session.stages.forEach { st ->
        val sec = Duration.between(st.start, st.end).seconds.coerceAtLeast(0L)
        stageSec[st.stage] = (stageSec[st.stage] ?: 0L) + sec
      }
      val stagesJson =
          JSONObject()
              .put("awake", (stageSec[SleepStageType.Awake] ?: 0L) / 60L)
              .put("light", (stageSec[SleepStageType.SleepingLight] ?: 0L) / 60L)
              .put("deep", (stageSec[SleepStageType.SleepingDeep] ?: 0L) / 60L)
              .put("rem", (stageSec[SleepStageType.SleepingREM] ?: 0L) / 60L)
              .put("asleep", (stageSec[SleepStageType.Sleeping] ?: 0L) / 60L)
      arr.put(
          JSONObject()
              .put("start", AI_DATE_FORMATTER.format(sStart))
              .put("end", AI_DATE_FORMATTER.format(sEnd))
              .put("duration_minutes", durationMin)
              .put("stages_minutes", stagesJson)
      )
    }
    return arr
  }

  private fun formatExtraVitals(
      seriesList: List<VitalSeries>,
      availableTypes: MutableList<String>,
  ): JSONObject {
    val obj = JSONObject()
    seriesList.forEach { series ->
      val valid = series.samples.filter { it.value.isFinite() }.sortedBy { it.time }
      if (valid.isEmpty()) return@forEach
      val key = VitalsTypes.keyFor(series.kind)
      availableTypes += key
      val minSample = valid.minBy { it.value }
      val maxSample = valid.maxBy { it.value }
      val latest = valid.last()
      val avg = valid.map { it.value }.average()
      val sampled = downsampleEvenly(valid, MAX_EXTRA_SAMPLES)
      val samplesArr = JSONArray()
      sampled.forEach { s ->
        samplesArr.put(
            JSONObject().put("time", AI_DATE_FORMATTER.format(s.time)).put("value", round1(s.value))
        )
      }
      obj.put(
          key,
          JSONObject()
              .put("unit", VitalsTypes.unitFor(series.kind))
              .put("sample_count", valid.size)
              .put("min", round1(minSample.value))
              .put("min_time", AI_DATE_FORMATTER.format(minSample.time))
              .put("max", round1(maxSample.value))
              .put("max_time", AI_DATE_FORMATTER.format(maxSample.time))
              .put("avg", round1(avg))
              .put("latest", round1(latest.value))
              .put("latest_time", AI_DATE_FORMATTER.format(latest.time))
              .put("samples", samplesArr),
      )
    }
    return obj
  }

  private fun <T> downsampleEvenly(items: List<T>, maxCount: Int): List<T> {
    if (items.size <= maxCount) return items
    return (0 until maxCount).map { i -> items[(i * (items.size - 1)) / (maxCount - 1)] }
  }

  private fun buildNote(snapshot: VitalsSnapshot): String {
    val base = "Observational wearable and logged readings only; not medical diagnosis."
    return when {
      !snapshot.healthConnectAvailable ->
          "$base Health Connect is unavailable on this device (only legacy journal blood pressure readings can appear)."
      snapshot.missingPermissionTypes.isNotEmpty() ->
          "$base Missing Health Connect permissions for: " +
              snapshot.missingPermissionTypes.joinToString(", ") +
              ". Do not assume unpermitted metrics are normal."
      else -> base
    }
  }

  private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0
}
