package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedBoundaryTest {
    private val route = AdministrationRoute.ORAL
    private val unit =
        SharedCustomUnit("Caffeine", "capsule", route, unit = "cap", originalUnit = "mg", note = "public")
    private val item = SharedGroupItem(
        0, "Caffeine", false, route,
        customUnitRef = SharedCustomUnitRef("Caffeine", "capsule", route)
    )

    @Test
    fun referencesMustMatchItemAndIncludeTheirDefinition() {
        assertTrue(SharedGroup("Group", listOf(item), customUnits = listOf(unit)).isSafeToImport())
        assertFalse(SharedGroup("Group", listOf(item)).isSafeToImport())
        assertFalse(SharedGroup("Group", listOf(item.copy(substanceName = "MDMA")), customUnits = listOf(unit)).isSafeToImport())
        val wrongRoute = item.copy(administrationRoute = AdministrationRoute.SMOKED)
        assertFalse(SharedGroup("Group", listOf(wrongRoute), customUnits = listOf(unit)).isSafeToImport())
        assertFalse(SharedGroup("Group", List(1001) { item }, customUnits = listOf(unit)).isSafeToImport())
    }

    @Test
    fun staleForeignUnitCannotLeakPrivateNotesIntoExport() {
        val privateUnit = unit.toEntity().copy(id = 42, note = "private journal detail")
        val group = SubstanceGroupWithItems(
            SubstanceGroup(id = 1, name = "Group"),
            listOf(SubstanceGroupItem(groupId = 1, sortOrder = 0, substanceName = "MDMA", administrationRoute = route, customUnitId = 42))
        )
        val shared = group.toShared(emptyList(), listOf(privateUnit))
        assertNull(shared.items.single().customUnitRef)
        assertTrue(shared.customUnits.isEmpty())
    }

    @Test
    fun snapshotsNeverReusePreviousRecipientsFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "shared_substance_colors").apply { mkdirs() }
        val first = sharedSnapshot(context, dir, "colors", ".colors").apply { writeText("first private snapshot") }
        val second = sharedSnapshot(context, dir, "colors", ".colors").apply { writeText("second private snapshot") }
        assertNotEquals(first, second)
        assertEquals("first private snapshot", first.readText())
        first.setLastModified(System.currentTimeMillis() - 48 * 60 * 60 * 1000L)
        sharedSnapshot(context, dir, "colors", ".colors")
        assertFalse(first.exists())
        assertTrue(second.exists())
    }

    @Test
    fun groupInputIsBoundedBeforeJsonParsing() {
        assertEquals("hello", readSharedPayload(ByteArrayInputStream("hello".toByteArray())))
        assertThrows(IllegalArgumentException::class.java) {
            readSharedPayload(ByteArrayInputStream(ByteArray(2 * 1024 * 1024 + 1)))
        }
    }
}
