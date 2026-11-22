package foo.pilz.freaklog.data.anodyne

import org.junit.Assert.assertEquals
import org.junit.Test

class AnodyneTextCleanerTest {

    @Test
    fun testSubscriptFormula() {
        assertEquals("C₁₁H₁₅NO₂", AnodyneTextCleaner.subscript("C<sub>11</sub>H<sub>15</sub>NO<sub>2</sub>"))
        assertEquals("H₂O", AnodyneTextCleaner.subscript("H2O"))
    }

    @Test
    fun testCleanHtmlAndMacros() {
        val input = "Sample {-{Efn|note}-} with <b>bold</b> &amp; &nbsp; text."
        assertEquals("Sample with bold & text.", AnodyneTextCleaner.clean(input))
    }
}
