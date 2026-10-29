package foo.pilz.freaklog.data.experienceshare

import org.junit.Assert.assertThrows
import org.junit.Test

class ShareImageBudgetTest {
    @Test
    fun oversizedAndOverflowingDimensionsAreRejectedBeforeAllocation() {
        requireShareImageSize(1440, 2000)
        listOf(1440 to 100_000, Int.MAX_VALUE to Int.MAX_VALUE, 0 to 1, 1 to 12_001).forEach { (w, h) ->
            assertThrows(IllegalArgumentException::class.java) { requireShareImageSize(w, h) }
        }
    }
}
