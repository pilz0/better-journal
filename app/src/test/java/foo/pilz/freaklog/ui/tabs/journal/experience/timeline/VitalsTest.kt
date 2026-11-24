package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.percent
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.Instant
import org.junit.Test

class VitalsTest {
  private val start: Instant = Instant.parse("2026-01-01T00:00:00Z")

  private fun sample(minutes: Long, value: Double) =
      VitalSample(start.plusSeconds(minutes * 60), value)

  @Test
  fun `small changes stay flat thanks to the minimum deviation`() {
    // SpO2 96 -> 97 with a 3 % minimum spread moves a third of the max height, not all of it.
    val polylines =
        buildVitalPolylines(
            listOf(sample(0, 96.0), sample(1, 97.0)),
            start,
            widthInSeconds = 3600f,
            minDeviation = VitalKind.OXYGEN_SATURATION.minDeviation,
        )
    assertThat(polylines.single()[0].y).isEqualTo(0.5f + 1f / 3f * 0.35f)
    assertThat(polylines.single()[1].y).isEqualTo(0.5f)
  }

  @Test
  fun `nearest value is null inside a gap`() {
    val samples =
        listOf(sample(0, 14.0), sample(1, 15.0), sample(2, 15.0), sample(3, 15.0), sample(60, 16.0))
    assertThat(nearestVitalValue(samples, start.plusSeconds(30))).isEqualTo(14.0)
    assertThat(nearestVitalValue(samples, start.plusSeconds(30 * 60))).isNull()
  }

  @Test
  fun `records map to samples`() {
    val spo2 =
        OxygenSaturationRecord(
            time = start,
            zoneOffset = null,
            percentage = 95.0.percent,
            metadata = Metadata.manualEntry(),
        )
    val skin =
        SkinTemperatureRecord(
            startTime = start,
            startZoneOffset = null,
            endTime = start.plusSeconds(120),
            endZoneOffset = null,
            deltas =
                listOf(
                    SkinTemperatureRecord.Delta(start, TemperatureDelta.celsius(0.2)),
                    SkinTemperatureRecord.Delta(
                        start.plusSeconds(60),
                        TemperatureDelta.celsius(-0.1),
                    ),
                ),
            metadata = Metadata.manualEntry(),
        )
    assertThat(spo2.toVitalSamples()).containsExactly(VitalSample(start, 95.0))
    assertThat(skin.toVitalSamples().map { it.value }).containsExactly(0.2, -0.1)
  }

  @Test
  fun `labels carry their unit`() {
    assertThat(VitalKind.OXYGEN_SATURATION.format(95.6)).isEqualTo("96% SpO₂")
    assertThat(VitalKind.SKIN_TEMPERATURE.format(0.34)).isEqualTo("+0.3 °C skin")
    assertThat(VitalKind.HEART_RATE_VARIABILITY.format(42.4)).isEqualTo("42 ms HRV")
  }
}
