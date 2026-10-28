package foo.pilz.freaklog.data.room.experiences

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import foo.pilz.freaklog.data.export.ExperienceSerializable
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.export.WebhookSerializable
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepositoryIntegrityTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ExperienceRepository

    private val export = JournalExport(
        experiences = listOf(
            ExperienceSerializable(title = "Imported", text = "", sortDate = EntityBuilders.FIXED_INSTANT)
        ),
        reminders = listOf(Reminder(title = "Reminder", hour = 8, minute = 0, isEnabled = true)),
        webhooks = listOf(WebhookSerializable(name = "Destination", url = "https://example.com/webhook")),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExperienceRepository(
            db, db.experienceDao(), db.reminderDao(), db.webhookDao(), db.ingestionWebhookMessageDao(), context
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `failed import rolls back journal and reminders when webhook insertion fails`() = runTest {
        rejectWrite("INSERT", "webhook") { repository.insertEverything(export) }

        assertRowCount("experience", 0)
        assertRowCount("reminder", 0)
        assertRowCount("webhook", 0)
    }

    @Test
    fun `failed replacement restores existing journal reminders webhooks and message links`() = runTest {
        seedJournal()

        rejectWrite("INSERT", "webhook") { repository.replaceEverything(export) }

        assertSeedJournal()
    }

    @Test
    fun `failed deletion restores existing journal reminders webhooks and message links`() = runTest {
        seedJournal()

        rejectWrite("DELETE", "webhook") { repository.deleteEverything() }

        assertSeedJournal()
    }

    @Test
    fun `successful replacement and deletion cover journal reminders and webhooks`() = runTest {
        seedJournal()

        repository.replaceEverything(export)

        assertThat(db.experienceDao().getExperience(1)?.title).isEqualTo("Imported")
        assertRowCount("experience", 1)
        assertRowCount("ingestion", 0)
        assertRowCount("ingestion_webhook_message", 0)
        assertRowCount("reminder", 1)
        assertRowCount("webhook", 1)

        repository.deleteEverything()

        listOf("experience", "reminder", "webhook").forEach { assertRowCount(it, 0) }
    }

    @Test
    fun `recipe deletion rolls back parent removal when component cleanup fails`() = runTest {
        val recipes = CustomRecipeRepository(db, db.customRecipeDao())
        val component = CustomRecipeComponent(
            customRecipeId = 0, substanceName = "Caffeine", dose = 100.0, isDoseAnEstimate = false,
            estimatedDoseStandardDeviation = null, units = "mg", administrationRoute = AdministrationRoute.ORAL,
        )
        val recipeId = recipes.insertRecipeWithComponents(CustomRecipe(name = "Recipe"), listOf(component)).toInt()
        val before = recipes.getRecipeWithComponents(recipeId)
        // Components cascade with the parent, so inject a failure at the later explicit cleanup call.
        val failingDao = spyk(db.customRecipeDao())
        coEvery { failingDao.deleteAllComponents() } throws SQLiteConstraintException("Rejected component cleanup")
        val failingRepository = CustomRecipeRepository(db, failingDao)

        val failure = runCatching { failingRepository.deleteAllRecipes() }.exceptionOrNull()

        assertThat(failure).isNotNull().isInstanceOf(SQLiteConstraintException::class)
        assertThat(recipes.getRecipeWithComponents(recipeId)).isEqualTo(before)

        recipes.deleteAllRecipes()
        assertRowCount("CustomRecipe", 0)
        assertRowCount("CustomRecipeComponent", 0)
    }

    @Test
    fun `custom substance name projection returns sorted names including duplicates`() = runTest {
        val dao = db.experienceDao()
        for (name in listOf("Zeta", "Alpha", "Alpha")) {
            dao.insert(CustomSubstance(name = name, units = "mg", description = "Description"))
        }

        assertThat(repository.getAllCustomSubstanceNames()).isEqualTo(listOf("Alpha", "Alpha", "Zeta"))
    }

    @Test
    fun `unused companions are removed while companions with ingestions are preserved`() = runTest {
        val dao = db.experienceDao()
        dao.insert(SubstanceCompanion("Used"))
        dao.insert(SubstanceCompanion("Unused"))
        val experienceId = dao.insert(EntityBuilders.experience()).toInt()
        dao.insert(EntityBuilders.ingestion(substanceName = "Used", experienceId = experienceId))

        repository.deleteUnusedSubstanceCompanions()

        assertThat(repository.getAllSubstanceCompanions().map { it.substanceName }).isEqualTo(listOf("Used"))
        dao.deleteAllIngestions()
        repository.deleteUnusedSubstanceCompanions()
        assertRowCount("substancecompanion", 0)
    }

    private suspend fun seedJournal() {
        repository.insertEverything(export.copy(experiences = export.experiences.map { it.copy(title = "Original") }))
        val ingestionId = db.experienceDao().insert(EntityBuilders.ingestion(experienceId = 1)).toInt()
        val webhookId = db.webhookDao().getAll().single().id
        db.ingestionWebhookMessageDao().insert(
            IngestionWebhookMessage(ingestionId = ingestionId, webhookId = webhookId, messageId = "message")
        )
    }

    private suspend fun assertSeedJournal() {
        assertThat(db.experienceDao().getExperience(1)?.title).isEqualTo("Original")
        listOf("experience", "ingestion", "reminder", "webhook", "ingestion_webhook_message")
            .forEach { assertRowCount(it, 1) }
    }

    private suspend fun rejectWrite(operation: String, table: String, action: suspend () -> Unit) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(
            "CREATE TRIGGER reject_write BEFORE $operation ON $table " +
                "BEGIN SELECT RAISE(ABORT, 'Rejected write'); END"
        )
        val failure = try {
            runCatching { action() }.exceptionOrNull()
        } finally {
            sqlite.execSQL("DROP TRIGGER reject_write")
        }
        assertThat(failure).isNotNull().isInstanceOf(SQLiteConstraintException::class)
    }

    private fun assertRowCount(table: String, expected: Int) {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            assertThat(cursor.getInt(0), table).isEqualTo(expected)
        }
    }
}
