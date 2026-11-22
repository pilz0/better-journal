package foo.pilz.freaklog.data.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnodyneTimingTest {

  @Test
  fun testParseRange() {
    val range = AnodyneTiming.rangeIn("3 – 6 hours")
    assertNotNull(range)
    assertEquals(3.0f, range?.min ?: 0f, 0.01f)
    assertEquals(6.0f, range?.max ?: 0f, 0.01f)
    assertEquals(DurationUnits.HOURS, range?.units)

    val single = AnodyneTiming.rangeIn("45 mins")
    assertNotNull(single)
    assertEquals(45f, single?.min ?: 0f, 0.01f)
    assertEquals(45f, single?.max ?: 0f, 0.01f)
    assertEquals(DurationUnits.MINUTES, single?.units)
  }

  @Test
  fun testCombinedDuration() {
    val routes =
        listOf(
            AnodyneUserNote.Route("Oral", "100 mg", "30m", "45m", "4h"),
            AnodyneUserNote.Route("Oral", "120 mg", "45m", "1h", "6h"),
        )
    val duration = AnodyneTiming.combinedDuration(routes)
    assertNotNull(duration)
    assertEquals(30f, duration?.onset?.min ?: 0f, 0.01f)
    assertEquals(45f, duration?.onset?.max ?: 0f, 0.01f)
    assertEquals(4f, duration?.total?.min ?: 0f, 0.01f)
    assertEquals(6f, duration?.total?.max ?: 0f, 0.01f)
  }
}
