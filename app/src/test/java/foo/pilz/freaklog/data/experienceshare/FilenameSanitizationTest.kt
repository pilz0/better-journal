package foo.pilz.freaklog.data.experienceshare

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameSanitizationTest {

    @Test
    fun `safe characters are kept`() {
        assertEquals("Trip_2026-01.final", sanitizeForShareFile("Trip_2026-01.final"))
    }

    @Test
    fun `path separators, spaces and unicode become underscores`() {
        assertEquals("a_b_.._c_d__", sanitizeForShareFile("a b/../c\\d ü"))
    }

    @Test
    fun `long titles are cut to 64 characters`() {
        assertEquals(64, sanitizeForShareFile("x".repeat(200)).length)
    }

    @Test
    fun `titles with nothing usable fall back to a default name`() {
        assertEquals("experience", sanitizeForShareFile(""))
        assertEquals("experience", sanitizeForShareFile("🍄🍄"))
        assertEquals("experience", sanitizeForShareFile("../.."))
    }
}
