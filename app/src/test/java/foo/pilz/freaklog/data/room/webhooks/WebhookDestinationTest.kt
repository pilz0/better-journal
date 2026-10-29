package foo.pilz.freaklog.data.room.webhooks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebhookDestinationTest {
    @Test
    fun changingDestinationUnlinksMessageIdsButEditingLabelKeepsThem() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val experience = db.experienceDao().insert(EntityBuilders.experience()).toInt()
            val ingestion = db.experienceDao().insert(EntityBuilders.ingestion(experienceId = experience)).toInt()
            val dao = db.webhookDao()
            val first = Webhook(name = "first", url = "https://example.com/webhooks/1/old")
            val id = dao.insert(first).toInt()
            val saved = first.copy(id = id)
            val links = db.ingestionWebhookMessageDao()
            links.insert(IngestionWebhookMessage(ingestionId = ingestion, webhookId = id, messageId = "private-message"))
            dao.update(saved.copy(name = "new label"))
            assertEquals(1, links.getByIngestion(ingestion).size)
            val captured = links.getByIngestion(ingestion).single()
            dao.update(saved.copy(url = "https://example.com/webhooks/2/new"))
            assertTrue(links.getByIngestion(ingestion).isEmpty())
            assertFalse(links.isCurrentDestination(captured.id, captured.messageId, saved.url))
            assertNull(links.insertForDestination(captured.copy(id = 0), saved.url))
            links.insert(IngestionWebhookMessage(ingestionId = ingestion, webhookId = id, messageId = "second-message"))
            dao.upsert(saved)
            assertTrue(links.getByIngestion(ingestion).isEmpty())
        } finally {
            db.close()
        }
    }
}
