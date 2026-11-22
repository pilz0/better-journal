package foo.pilz.freaklog.ui.tabs.search.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnodyneUserNotesScreenTest {
  @Test
  fun testUserNoteSummaryFormat() {
    val note =
        AnodyneUserNote(
            user = "alice",
            substanceName = "MDMA",
            routes = listOf(AnodyneUserNote.Route("Oral", "120 mg", "30m", "45m", "4h")),
        )
    assertEquals("Oral 120 mg", note.routesSummary)
    assertTrue(note.hasDetails)
  }

  @Test
  fun testUserNoteEmptySummary() {
    val note =
        AnodyneUserNote(
            user = "bob",
            substanceName = "Ketamine",
            routes = emptyList(),
        )
    assertEquals("", note.routesSummary)
  }
}
