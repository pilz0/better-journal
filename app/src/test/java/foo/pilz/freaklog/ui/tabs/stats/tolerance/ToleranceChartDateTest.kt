package foo.pilz.freaklog.ui.tabs.stats.tolerance

import androidx.compose.ui.graphics.Color
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ToleranceChartDateTest {

  @Test
  fun `utc date picker round trip preserves local calendar date across timezones`() {
    val zones =
        listOf(
            ZoneId.of("America/New_York"),
            ZoneId.of("UTC"),
            ZoneId.of("Asia/Tokyo"),
        )
    val instants =
        listOf(
            Instant.parse("2026-01-10T01:00:00Z"),
            Instant.parse("2026-03-08T07:30:00Z"),
            Instant.parse("2026-06-15T23:45:00Z"),
            Instant.parse("2026-11-01T05:15:00Z"),
        )

    for (zone in zones) {
      for (instant in instants) {
        val expectedLocalDate = instant.atZone(zone).toLocalDate()
        val utcMillis = instantToDatePickerUtcMillis(instant, zoneId = zone)
        val roundTrippedInstant = datePickerUtcMillisToInstant(utcMillis, zoneId = zone)
        val roundTrippedLocalDate = roundTrippedInstant.atZone(zone).toLocalDate()
        assertEquals(
            "Zone $zone changed calendar date for $instant",
            expectedLocalDate,
            roundTrippedLocalDate,
        )
        assertEquals(expectedLocalDate.atStartOfDay(zone).toInstant(), roundTrippedInstant)
      }
    }
  }

  @Test
  fun `tolerance window barColor resolves light and dark theme colors from adaptiveColor`() {
    val start = Instant.parse("2026-01-01T00:00:00Z")
    val end = Instant.parse("2026-01-04T00:00:00Z")
    val window =
        ToleranceWindow(
            substanceName = "LSD",
            start = start,
            end = end,
            toleranceType = ToleranceType.FULL,
            substanceColor = Color.Red,
            adaptiveColor = AdaptiveColor.BLUE,
        )

    assertEquals(AdaptiveColor.BLUE.getComposeColor(false), window.barColor(isDarkTheme = false))
    assertEquals(AdaptiveColor.BLUE.getComposeColor(true), window.barColor(isDarkTheme = true))
    assertNotEquals(
        window.barColor(isDarkTheme = false),
        window.barColor(isDarkTheme = true),
    )

    val halfWindow = window.copy(toleranceType = ToleranceType.HALF)
    assertEquals(
        AdaptiveColor.BLUE.getComposeColor(true).copy(alpha = 0.5f),
        halfWindow.barColor(isDarkTheme = true),
    )
  }
}
