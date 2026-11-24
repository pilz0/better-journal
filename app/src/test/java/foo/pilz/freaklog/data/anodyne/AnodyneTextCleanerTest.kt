package foo.pilz.freaklog.data.anodyne

import org.junit.Assert.assertEquals
import org.junit.Test

class AnodyneTextCleanerTest {

  @Test
  fun testSubscriptFormula() {
    assertEquals(
        "C₁₁H₁₅NO₂",
        AnodyneTextCleaner.subscript("C<sub>11</sub>H<sub>15</sub>NO<sub>2</sub>"),
    )
    assertEquals("H₂O", AnodyneTextCleaner.subscript("H2O"))
  }

  @Test
  fun testCleanHtmlAndMacros() {
    val input = "Sample {-{Efn|note}-} with <b>bold</b> &amp; &nbsp; text."
    assertEquals("Sample with bold & text.", AnodyneTextCleaner.clean(input))
  }

  @Test
  fun macrosShowTheirText() {
    assertEquals(
        "light stimulation, heroin, cotard's syndrome",
        AnodyneTextCleaner.clean(
            "{sev 'light'} {xeff 'stimulation' 'stimulation'}, {sub 'diacetylmorphine' 'heroin'}, " +
                "{wkp 'Cotard%27s syndrome' 'cotard{apos}s syndrome'}{rev}"
        ),
    )
  }

  @Test
  fun spansLinkWikipediaIcdEffectsAndWikiPages() {
    val text =
        "{b}reaccuring{Eb} {xeff 'suicidal-ideation'} ({icd 'R45.8'}), " +
            "{wkp 'Social_rejection#Rejection_sensitivity' 'rejection sensitivity'} with {sub 'ketamine'}"
    val spans = AnodyneTextCleaner.spans(text, "https://wiki.example/")
    assertEquals(
        listOf(
            AnodyneTextCleaner.Span("reaccuring "),
            AnodyneTextCleaner.Span(
                "suicidal ideation",
                "https://www.effectindex.com/effects/suicidal-ideation",
            ),
            AnodyneTextCleaner.Span(" ("),
            AnodyneTextCleaner.Span("R45.8", "https://icd.who.int/browse10/2019/en#/R45.8"),
            AnodyneTextCleaner.Span("), "),
            AnodyneTextCleaner.Span(
                "rejection sensitivity",
                "https://en.wikipedia.org/wiki/Social_rejection#Rejection_sensitivity",
            ),
            AnodyneTextCleaner.Span(" with "),
            AnodyneTextCleaner.Span(
                "ketamine",
                "https://wiki.example/substance/ketamine",
                substanceTitle = "ketamine",
            ),
        ),
        spans,
    )
    assertEquals(AnodyneTextCleaner.clean(text), spans.joinToString("") { it.text })
  }

  @Test
  fun spansHighlightSeverityAndKeepUnknownMacrosAsText() {
    assertEquals(
        listOf(
            AnodyneTextCleaner.Span("strong", isSeverity = true),
            AnodyneTextCleaner.Span(" "),
            AnodyneTextCleaner.Span("Mixed anxiety", "https://en.wikipedia.org/wiki/Mixed_anxiety"),
            AnodyneTextCleaner.Span(" typo"),
        ),
        AnodyneTextCleaner.spans(
            " {sev 'strong'} {wkp 'Mixed anxiety'} {wpk 'typo'}{rev} ",
            "https://a.b",
        ),
    )
  }
}
