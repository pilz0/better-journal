package foo.pilz.freaklog.data.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnodyneRepositoryTest {

  @Test
  fun testNormalizeUrl() {
    assertEquals("https://anodyne.wiki", AnodyneRepository.normalizeUrl("anodyne.wiki"))
    assertEquals("https://anodyne.wiki", AnodyneRepository.normalizeUrl("https://anodyne.wiki/"))
    // HTTPS is required outside loopback, like every other configurable endpoint.
    assertNull(AnodyneRepository.normalizeUrl("http://wiki.local:8080/"))
    assertEquals("http://localhost:8080", AnodyneRepository.normalizeUrl("http://localhost:8080/"))
    // The old AI setting stored the API URL; the instance URL is what we keep.
    assertEquals(
        "https://mirror.example.org",
        AnodyneRepository.normalizeUrl("mirror.example.org/api/"),
    )
    assertEquals(
        "https://mirror.example.org",
        AnodyneRepository.normalizeUrl("https://mirror.example.org"),
    )
    assertNull(AnodyneRepository.normalizeUrl(""))
    assertNull(AnodyneRepository.normalizeUrl("ftp://invalid"))
  }

  @Test
  fun substancePageUrlEncodesTheTitle() {
    assertEquals(
        "https://mirror.example.org/substance/3%2C4-MDMA%20base",
        AnodyneRepository.substancePageUrl("https://mirror.example.org", "3,4-MDMA base"),
    )
  }

  @Test
  fun lookupProblemSeparatesMissingPagesFromUnreachableWikis() {
    assertNull(AnodyneRepository.lookupProblem(Result.success(AnodyneSubstance("Caffeine"))))
    assertEquals(
        "AnodyneWiki has no page for this substance.",
        AnodyneRepository.lookupProblem(Result.success(null)),
    )
    assertEquals(
        true,
        AnodyneRepository.lookupProblem(Result.failure(java.io.IOException()))
            ?.startsWith("Couldn't reach AnodyneWiki"),
    )
  }
}
