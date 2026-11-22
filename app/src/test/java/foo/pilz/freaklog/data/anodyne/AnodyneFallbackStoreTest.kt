package foo.pilz.freaklog.data.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneDosing
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneTier
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnodyneFallbackStoreTest {

  @Test
  fun testRecordAndRetrieveDoseAndDuration() {
    val store = AnodyneFallbackStore()
    val dosing =
        AnodyneDosing(
            method = "Oral",
            common = AnodyneTier(100.0, 150.0, "mg", 20),
            strong = AnodyneTier(150.0, 200.0, "mg", 15),
            heavy = AnodyneTier(200.0, 300.0, "mg", 5),
        )
    val substance =
        AnodyneSubstance(
            title = "Mescaline",
            abbreviations = listOf("Peyote"),
            dosing = listOf(dosing),
            durationOfAction = "8 - 12 hours",
        )
    store.record(substance)

    val dose = store.getDose("Mescaline", AdministrationRoute.ORAL)
    assertNotNull(dose)
    assertEquals("mg", dose?.units)
    assertEquals(100.0, dose?.commonMin ?: 0.0, 0.01)

    val aliasDose = store.getDose("Peyote", AdministrationRoute.ORAL)
    assertNotNull(aliasDose)
    assertEquals(100.0, aliasDose?.commonMin ?: 0.0, 0.01)

    val duration = store.getDuration("Mescaline", AdministrationRoute.ORAL)
    assertNotNull(duration)
    assertEquals(8f, duration?.total?.min ?: 0f, 0.01f)
    assertEquals(12f, duration?.total?.max ?: 0f, 0.01f)
  }

  @Test
  fun testUserNoteTimingsOverrideTotal() {
    val store = AnodyneFallbackStore()
    val route = AnodyneUserNote.Route("Insufflated", "20 mg", "5m", "10m", "2h")
    val notes =
        listOf(AnodyneUserNote(user = "bob", substanceName = "NovelChem", routes = listOf(route)))
    store.record(notes)

    val dur = store.getDuration("NovelChem", AdministrationRoute.INSUFFLATED)
    assertNotNull(dur)
    assertEquals(5f, dur?.onset?.min ?: 0f, 0.01f)
  }
}
