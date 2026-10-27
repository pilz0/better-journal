package foo.pilz.freaklog.data.room.experiences

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substanceshare.SharedDose
import foo.pilz.freaklog.data.substanceshare.SharedDuration
import foo.pilz.freaklog.data.substanceshare.SharedInteraction
import foo.pilz.freaklog.data.substanceshare.SharedRoa
import foo.pilz.freaklog.data.substanceshare.SharedSubstance
import foo.pilz.freaklog.data.substanceshare.expand
import foo.pilz.freaklog.data.substanceshare.resolveCollisionReplace
import foo.pilz.freaklog.data.substanceshare.toShared
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomSubstanceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: CustomSubstanceRepository

    private val profileTables = listOf(
        "customroaduration", "customroadose", "customroa",
        "custominteraction", "customcategoryassignment", "customcrosstolerance",
    )
    private val failingTables = profileTables.takeLast(3)
    private val original = payload("Original", AdministrationRoute.ORAL)
    private val replacement = payload("Replacement", AdministrationRoute.INSUFFLATED)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CustomSubstanceRepository(db.experienceDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insertFromShared stores the complete profile`() = runTest {
        val id = repository.insertFromShared(original.expand())

        assertThat(id).isGreaterThan(0)
        assertThat(repository.getWithEverything(id)?.toShared()).isEqualTo(original)
    }

    @Test
    fun `replaceFromShared replaces the complete profile and removes old children`() = runTest {
        val oldId = repository.insertFromShared(original.expand())
        val existing = requireNotNull(repository.getWithEverything(oldId))

        val newId = repository.replaceFromShared(existing.substance, replacement.expand())

        assertThat(newId).isGreaterThan(oldId)
        assertThat(repository.getWithEverything(newId)?.toShared()).isEqualTo(replacement)
        assertThat(repository.getWithEverything(oldId)).isNull()
        assertNoProfileRows(oldId)
    }

    @Test
    fun `collision replacement stores the complete replacement profile`() = runTest {
        repository.insertFromShared(original.expand())

        resolveCollisionReplace(replacement, repository)

        assertThat(repository.getWithEverythingByName(original.name)?.toShared()).isEqualTo(replacement)
    }

    @Test
    fun `failed shared inserts roll back the substance and every profile table`() = runTest {
        for (table in failingTables) {
            assertInsertRejected(table) { repository.insertFromShared(original.expand()) }

            assertThat(repository.getWithEverythingByName(original.name)).isNull()
            assertRowCount("customsubstance", 0)
            profileTables.forEach { assertRowCount(it, 0) }
        }
    }

    @Test
    fun `failed collision replacements restore the original substance and every child`() = runTest {
        val id = repository.insertFromShared(original.expand())
        val before = requireNotNull(repository.getWithEverything(id))

        for (table in failingTables) {
            assertInsertRejected(table) { resolveCollisionReplace(replacement, repository) }

            assertThat(repository.getWithEverythingByName(original.name)).isEqualTo(before)
            assertRowCount("customsubstance", 1)
            profileTables.forEach { assertRowCount(it, 1) }
        }
    }

    private suspend fun assertInsertRejected(table: String, operation: suspend () -> Unit) {
        // Fail inside SQLite after earlier profile writes, rather than mocking a DAO call.
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(
            "CREATE TRIGGER reject_profile_insert BEFORE INSERT ON $table " +
                "BEGIN SELECT RAISE(ABORT, 'Rejected profile insert'); END"
        )
        val failure = try {
            runCatching { operation() }.exceptionOrNull()
        } finally {
            sqlite.execSQL("DROP TRIGGER reject_profile_insert")
        }
        assertThat(failure).isNotNull().isInstanceOf(SQLiteConstraintException::class)
    }

    private fun assertNoProfileRows(substanceId: Int) {
        for (table in profileTables) {
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM $table WHERE customSubstanceId = ?",
                arrayOf(substanceId),
            ).use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0), table).isEqualTo(0)
            }
        }
    }

    private fun assertRowCount(table: String, expected: Int) {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0), table).isEqualTo(expected)
        }
    }

    private fun payload(label: String, route: AdministrationRoute) = SharedSubstance(
        name = "Shared substance",
        units = "mg",
        description = label,
        roas = listOf(
            SharedRoa(
                route = route,
                dose = SharedDose(units = "mg", commonMin = if (route == AdministrationRoute.ORAL) 10.0 else 5.0),
                duration = SharedDuration(total = DurationRange(1f, 2f, DurationUnits.HOURS)),
                bioavailability = Bioavailability(50.0, 80.0),
            )
        ),
        interactions = listOf(
            SharedInteraction(CustomInteractionSeverity.UNSAFE, CustomInteractionTargetType.SUBSTANCE, "$label target")
        ),
        categories = listOf("$label category"),
        crossTolerances = listOf("$label cross tolerance"),
    )
}
