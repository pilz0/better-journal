package foo.pilz.freaklog.ui.tabs.journal.experience.recommendations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AssistantImagesTest {
    @Test
    fun assistantImagesNeverReachNetworkOrLocalFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = assistantImageLoader(context)
        val server = MockWebServer().apply { start() }
        try {
            listOf(server.url("/private?journal=secret").toString(), "file:///data/private", "content://private/notes").forEach {
                val result = loader.execute(ImageRequest.Builder(context).data(it).build())
                assertTrue(result is ErrorResult)
            }
            assertEquals(0, server.requestCount)
        } finally {
            loader.shutdown()
            server.shutdown()
        }
    }
}
