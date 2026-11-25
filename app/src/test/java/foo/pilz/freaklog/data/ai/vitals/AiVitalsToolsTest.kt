package foo.pilz.freaklog.data.ai.vitals

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading as LegacyBloodPressure
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureReading
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepSessionSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepStageSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepStageType
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalKind
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalSeries
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiVitalsToolsTest {

  private val experienceDao = mockk<ExperienceDao>()
  private val enabledFlow = MutableStateFlow(true)
  private val userPreferences =
      mockk<UserPreferences> { every { aiVitalsEnabledFlow } returns enabledFlow }
  private val substances =
      mockk<SubstanceRepositoryInterface> { every { getSubstance(any()) } returns null }
  private val customProfiles =
      mockk<CustomSubstanceProfiles> { every { getRoa(any(), any()) } returns null }
  private val dataSource = mockk<VitalsDataSource>()
  private val tools =
      AiVitalsTools(experienceDao, userPreferences, substances, customProfiles, dataSource)

  @Test
  fun rejectsWhenDisabled() = runTest {
    enabledFlow.value = false
    val result = tools.execute("get_vitals", emptyMap())!!
    assertThat(result.getString("status")).isEqualTo("error")
  }

  @Test
  fun unknownExperienceIdAndUnknownTypesReturnError() = runTest {
    coEvery { experienceDao.getExperienceWithIngestionsCompanionsAndRatings(99) } returns null
    val missingExp = tools.execute("get_vitals", mapOf("experience_id" to 99))!!
    assertThat(missingExp.getString("status")).isEqualTo("error")

    val badType = tools.execute("get_vitals", mapOf("types" to listOf("astrology")))!!
    assertThat(badType.getString("status")).isEqualTo("error")
  }

  @Test
  fun formatsHeartRateBucketsSleepOverlapExtraVitalsAndMergedBpForExperience() = runTest {
    val startBase = Instant.parse("2026-01-01T12:00:00Z")
    stubExperienceWithLegacyBp(startBase)
    coEvery { dataSource.read(any(), any(), any()) } returns buildSampleSnapshot(startBase)

    val result = tools.execute("get_vitals", mapOf("experience_id" to 7))!!
    assertThat(result.getString("status")).isEqualTo("success")
    assertThat(result.getInt("experience_id")).isEqualTo(7)
    assertThat(result.getString("experience_title")).isEqualTo("Day Trip")

    val hr = result.getJSONObject("heart_rate")
    assertThat(hr.getInt("sample_count")).isEqualTo(30)
    assertThat(hr.getLong("min_bpm")).isEqualTo(65L)
    assertThat(hr.getLong("max_bpm")).isEqualTo(94L)
    assertThat(hr.getJSONArray("timeline").length() <= 24).isEqualTo(true)

    val bp = result.getJSONObject("blood_pressure")
    assertThat(bp.getInt("count")).isEqualTo(2)
    assertThat(bp.getJSONObject("latest").getString("source")).isEqualTo("journal_legacy")
    assertThat(bp.getJSONObject("latest").getInt("pulse_bpm")).isEqualTo(88)

    val sleep = result.getJSONArray("sleep")
    assertThat(sleep.length()).isEqualTo(1)
    assertThat(sleep.getJSONObject(0).getLong("duration_minutes")).isEqualTo(60L)
    assertThat(sleep.getJSONObject(0).getJSONObject("stages_minutes").getLong("deep"))
        .isEqualTo(60L)

    val extra = result.getJSONObject("extra_vitals").getJSONObject("oxygen_saturation")
    assertThat(extra.getDouble("latest")).isEqualTo(98.0)
    assertThat(result.getJSONArray("missing_permission_types").getString(0))
        .isEqualTo("respiratory_rate")
    assertThat(result.getString("note")).contains("respiratory_rate")
  }

  private fun stubExperienceWithLegacyBp(startBase: Instant) {
    val exp = EntityBuilders.experience(id = 7, title = "Day Trip", sortDate = startBase)
    val ing =
        EntityBuilders.ingestion(id = 1, experienceId = 7, substanceName = "LSD", time = startBase)
    val details =
        ExperienceWithIngestionsCompanionsAndRatings(
            experience = exp,
            ingestionsWithCompanions = listOf(IngestionWithCompanionAndCustomUnit(ing, null, null)),
            ratings = emptyList(),
        )
    coEvery { experienceDao.getExperienceWithIngestionsCompanionsAndRatings(7) } returns details
    coEvery { experienceDao.getTimedNotes(7) } returns emptyList()
    coEvery { experienceDao.getBloodPressureReadings(7) } returns
        listOf(
            LegacyBloodPressure(
                id = 1,
                experienceId = 7,
                time = startBase.plusSeconds(1800),
                systolic = 128,
                diastolic = 82,
                pulse = 88,
            )
        )
  }

  private fun buildSampleSnapshot(startBase: Instant): VitalsSnapshot {
    val hrSamples =
        (0 until 30).map { i -> HeartRateSample(startBase.plusSeconds(i * 300L), 65L + i) }
    val sleepInWindow =
        SleepSessionSample(
            stages =
                listOf(
                    SleepStageSample(
                        startBase.plus(Duration.ofHours(3)),
                        startBase.plus(Duration.ofHours(4)),
                        SleepStageType.SleepingDeep,
                    )
                )
        )
    val sleepOutsideWindow =
        SleepSessionSample(
            stages =
                listOf(
                    SleepStageSample(
                        startBase.minus(Duration.ofHours(10)),
                        startBase.minus(Duration.ofHours(5)),
                        SleepStageType.SleepingLight,
                    )
                )
        )
    return VitalsSnapshot(
        healthConnectAvailable = true,
        grantedTypes =
            setOf(
                VitalsTypes.HEART_RATE,
                VitalsTypes.BLOOD_PRESSURE,
                VitalsTypes.SLEEP,
                VitalsTypes.OXYGEN_SATURATION,
            ),
        missingPermissionTypes = setOf(VitalsTypes.RESPIRATORY_RATE),
        heartRate = hrSamples,
        bloodPressure =
            listOf(BloodPressureReading("hc-1", startBase.plusSeconds(600), 120.0, 78.0)),
        sleep = listOf(sleepOutsideWindow, sleepInWindow),
        extraVitals =
            listOf(
                VitalSeries(
                    VitalKind.OXYGEN_SATURATION,
                    listOf(VitalSample(startBase.plusSeconds(1200), 98.0)),
                )
            ),
    )
  }

  @Test
  fun hoursBackClampsAndReportsUnavailableHealthConnectAndAliases() = runTest {
    val startSlot = slot<Instant>()
    val endSlot = slot<Instant>()
    val typesSlot = slot<Set<String>>()
    coEvery { experienceDao.getBloodPressureReadingsBetween(any(), any()) } returns emptyList()
    coEvery { dataSource.read(capture(startSlot), capture(endSlot), capture(typesSlot)) } returns
        VitalsSnapshot(
            healthConnectAvailable = false,
            grantedTypes = emptySet(),
            missingPermissionTypes = setOf(VitalsTypes.HEART_RATE, VitalsTypes.OXYGEN_SATURATION),
            heartRate = emptyList(),
            bloodPressure = emptyList(),
            sleep = emptyList(),
            extraVitals = emptyList(),
        )

    val result =
        tools.execute(
            "get_vitals",
            mapOf("hours_back" to 300, "types" to listOf("hr", "spo2")),
        )!!
    assertThat(result.getString("status")).isEqualTo("success")
    assertThat(result.getBoolean("health_connect_available")).isFalse()
    assertThat(Duration.between(startSlot.captured, endSlot.captured).toHours()).isEqualTo(168L)
    assertThat(typesSlot.captured)
        .isEqualTo(setOf(VitalsTypes.HEART_RATE, VitalsTypes.OXYGEN_SATURATION))
    assertThat(result.getJSONArray("available_data_types").length()).isEqualTo(0)
    assertThat(result.getString("note")).contains("unavailable")
  }
}
