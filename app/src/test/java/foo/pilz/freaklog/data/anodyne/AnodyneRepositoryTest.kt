package foo.pilz.freaklog.data.anodyne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnodyneRepositoryTest {

  @Test
  fun testNormalizeUrl() {
    assertEquals("https://anodyne.wiki", AnodyneRepository.normalizeUrl("anodyne.wiki"))
    assertEquals("https://anodyne.wiki", AnodyneRepository.normalizeUrl("https://anodyne.wiki/"))
    assertEquals(
        "http://wiki.local:8080",
        AnodyneRepository.normalizeUrl("http://wiki.local:8080/"),
    )
    assertEquals(
        "https://mirror.example.org",
        AnodyneRepository.normalizeUrl("https://mirror.example.org"),
    )
    assertNull(AnodyneRepository.normalizeUrl(""))
    assertNull(AnodyneRepository.normalizeUrl("ftp://invalid"))
  }
}
