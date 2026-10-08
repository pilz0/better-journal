package foo.pilz.freaklog.data.substanceshare

import org.junit.Assert.assertEquals
import org.junit.Test

class NextAvailableImportNameTest {

    @Test
    fun returnsBaseWhenUnused() {
        assertEquals("LSD", nextAvailableImportName("LSD", existing = emptySet()))
    }

    @Test
    fun appendsImportedOnFirstCollision() {
        assertEquals("LSD (imported)", nextAvailableImportName("LSD", existing = setOf("LSD")))
    }

    @Test
    fun appendsNumberOnFurtherCollisions() {
        val existing = setOf("LSD", "LSD (imported)")
        assertEquals("LSD (imported 2)", nextAvailableImportName("LSD", existing = existing))
    }

    @Test
    fun findsNextAvailableNumber() {
        val existing = setOf("LSD", "LSD (imported)", "LSD (imported 2)", "LSD (imported 3)")
        assertEquals("LSD (imported 4)", nextAvailableImportName("LSD", existing = existing))
    }
}
