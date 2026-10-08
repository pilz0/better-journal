package foo.pilz.freaklog.ui.tabs.journal.outlookskin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class OutlookSkinLogicTest {

    @Test
    fun subjectDeterministicWithPrefix() {
        assertEquals(outlookSubject("Quarterly review", 0), outlookSubject("Quarterly review", 5))
        assertEquals("Quarterly review", outlookSubject("Quarterly review", 0))
        assertTrue(outlookSubject("Quarterly review", 1).endsWith("Quarterly review"))
    }

    @Test
    fun timeTextByRecency() {
        val now = Instant.parse("2026-07-14T15:00:00Z")
        val zone = ZoneOffset.UTC
        assertEquals("09:30", outlookTimeText(Instant.parse("2026-07-14T09:30:00Z"), now, zone))
        assertEquals("2 Mar", outlookTimeText(Instant.parse("2026-03-02T09:30:00Z"), now, zone))
        assertEquals("24/12/2023", outlookTimeText(Instant.parse("2023-12-24T09:30:00Z"), now, zone))
    }
}
