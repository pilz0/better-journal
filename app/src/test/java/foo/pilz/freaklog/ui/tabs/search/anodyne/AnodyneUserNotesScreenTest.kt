package foo.pilz.freaklog.ui.tabs.search.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import org.junit.Assert.assertTrue
import org.junit.Test

class AnodyneUserNotesScreenTest {
  @Test
  fun noteWithRoutesHasDetails() {
    val note =
        AnodyneUserNote(
            user = "alice",
            substanceName = "MDMA",
            routes = listOf(AnodyneUserNote.Route("Oral", "120 mg", "30m", "45m", "4h")),
        )
    assertTrue(note.hasDetails)
  }
}
