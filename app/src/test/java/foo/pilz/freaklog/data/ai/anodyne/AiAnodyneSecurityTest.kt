package foo.pilz.freaklog.data.ai.anodyne

import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiAnodyneSecurityTest {
    @Test
    fun freeformPrivateTextCannotBecomeAWikiRequest() = runTest {
        val client = mockk<AnodyneWikiClient>()
        val prefs = mockk<UserPreferences> { every { aiAnodyneEnabledFlow } returns flowOf(true) }
        val substances = mockk<SubstanceRepository> { every { getAllSubstances() } returns emptyList() }
        val tools = AiAnodyneTools(client, prefs, substances)
        listOf("search_anodyne_wiki" to "query", "lookup_anodyne_wiki" to "substance").forEach { (tool, param) ->
            val result = tools.execute(tool, mapOf(param to "My private journal at home"))!!
            assertEquals("error", result.getString("status"))
        }
        coVerify(exactly = 0) { client.search(any(), any(), any()) }
        coVerify(exactly = 0) { client.substance(any(), any()) }
        assertNotNull(tools.approvedPublicName("MDMA"))
        assertNull(tools.approvedPublicName("MDMA/private-secret"))
    }
}
