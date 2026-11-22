package foo.pilz.freaklog.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalProviderTest {

  @Test
  fun `own process is allowed without evaluating lock state`() {
    var checkedLock = false
    enforceJournalUnlocked(callingUid = 1000, ownUid = 1000) {
      checkedLock = true
      true
    }
    assertTrue(!checkedLock)
  }

  @Test
  fun `external caller is refused when app lock is enabled`() {
    val exception =
        assertThrows(SecurityException::class.java) {
          enforceJournalUnlocked(callingUid = 2000, ownUid = 1000) { true }
        }
    assertEquals(
        "The journal is locked. Turn off app lock in Freaklog to share it.",
        exception.message,
    )
  }

  @Test
  fun `external caller is allowed when app lock is disabled`() {
    var checkedLock = false
    enforceJournalUnlocked(callingUid = 2000, ownUid = 1000) {
      checkedLock = true
      false
    }
    assertTrue(checkedLock)
  }
}
