package foo.pilz.freaklog.ui.tabs.stats.tolerance

import org.junit.Assert.assertEquals
import org.junit.Test

class JoinNamesTest {
  @Test
  fun joinsLikeASentence() {
    assertEquals("", joinNames(emptyList()))
    assertEquals("DMT", joinNames(listOf("DMT")))
    assertEquals("DMT and 2C-B", joinNames(listOf("DMT", "2C-B")))
    assertEquals("DMT, 2C-B, and LSD", joinNames(listOf("DMT", "2C-B", "LSD")))
  }
}
