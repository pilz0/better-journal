package foo.pilz.freaklog.data.ai.web

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextExtractorTest {

  @Test
  fun extractsTitleBlockParagraphsAndStripsActiveTags() {
    val html =
        """
        <!doctype html>
        <html>
          <head>
            <title>MDMA &mdash; Pharmacology</title>
            <style>body { color: red; }</style>
            <script>var secret = "</div>should not appear"; if (1 < 2) {}</script>
          </head>
          <body>
            <!-- hidden comment -->
            <noscript>Enable JS</noscript>
            <svg><svg><text>Icon</text></svg></svg>
            <template><p>Template content</p></template>
            <h1>Overview</h1>
            <p>First paragraph with <b>bold</b> text.</p>
            <div>Second line<br>after break</div>
            <ul><li>Item one</li><li>Item two</li></ul>
            <table><tr><td>100</td><td>mg</td></tr></table>
          </body>
        </html>
        """
            .trimIndent()

    val extracted = HtmlTextExtractor.extract(html)
    assertThat(extracted.title).isEqualTo("MDMA — Pharmacology")
    assertThat(extracted.text).contains("Overview")
    assertThat(extracted.text).contains("First paragraph with bold text.")
    assertThat(extracted.text).contains("Second line\nafter break")
    assertThat(extracted.text).contains("Item one\nItem two")
    assertThat(extracted.text).contains("100 mg")
    assertThat(extracted.text).doesNotContain("should not appear")
    assertThat(extracted.text).doesNotContain("color: red")
    assertThat(extracted.text).doesNotContain("hidden comment")
    assertThat(extracted.text).doesNotContain("Enable JS")
    assertThat(extracted.text).doesNotContain("Icon")
    assertThat(extracted.text).doesNotContain("Template content")
  }

  @Test
  fun decodesNamedDecimalAndHexEntities() {
    val decoded =
        HtmlTextExtractor.decodeEntities(
            "A &amp; B &lt;tag&gt; &quot;quoted&quot; &#39;single&#39; 50&nbsp;&mu;g &#8212; &#x2014; &#xB5;g"
        )
    assertThat(decoded).isEqualTo("A & B <tag> \"quoted\" 'single' 50 µg — — µg")
  }

  @Test(timeout = 5000)
  fun handlesMalformedAndRepeatedAnglesInLinearTime() {
    val repeatedLt = HtmlTextExtractor.extract("<".repeat(1_000_000))
    val unclosedTags = HtmlTextExtractor.extract("<a ".repeat(500_000) + ">done")
    assertTrue(repeatedLt.text.length == 1_000_000)
    assertThat(unclosedTags.text).contains("done")
  }
}
