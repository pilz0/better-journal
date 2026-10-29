package foo.pilz.freaklog.data.ai.anodyne

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnodyneWikiClientTest {
    private lateinit var server: MockWebServer
    private val client = AnodyneWikiClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun base() = server.url("/api").toString()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun keepsUsefulFieldsAndSkipsHugeOnes() = runBlocking {
        val reports = JSONArray().apply { repeat(2000) { put(JSONObject().put("Text", "x".repeat(1000))) } }
        val page = JSONObject()
            .put("Abbreviation", JSONArray(listOf("MDMA")))
            .put("Biological Half-Life", "6–10 hours")
            .put("Dosing Info", JSONArray().put(JSONObject().put("Method", "Oral").put("Tiers", JSONObject())))
            .put("DurationOfAction", "3 – 6&nbsp;hours")
            .put("Interactions", "Avoid {wkp 'MAOIs'} and <b>lithium</b>.{Ep}")
            .put("Reddit Experience Reports", reports)
            .put("SMILES", "CC(CC1=CC2=C(C=C1)OCO2)NC")
            .put("Title", "3,4-Methylenedioxymethamphetamine")
        server.enqueue(json(page.toString()))

        val result = client.substance(base(), "3,4-Methylenedioxymethamphetamine")!!

        assertThat(server.takeRequest().path).isEqualTo("/api/substance/3%2C4-Methylenedioxymethamphetamine")
        assertThat(result.getString("name")).isEqualTo("3,4-Methylenedioxymethamphetamine")
        assertThat(result.getJSONArray("abbreviations").getString(0)).isEqualTo("MDMA")
        assertThat(result.getString("duration_of_action")).isEqualTo("3 – 6 hours")
        assertThat(result.getString("interactions")).isEqualTo("Avoid MAOIs and lithium.")
        assertThat(result.getJSONArray("dosing").getJSONObject(0).getString("Method")).isEqualTo("Oral")
        assertThat(result.has("SMILES")).isFalse()
        assertThat(result.toString().length < 2000).isEqualTo(true)
    }

    @Test
    fun abbreviationMayBeAPlainString() = runBlocking {
        server.enqueue(json("""{"Abbreviation":"SP","Title":"Substance P"}"""))
        val result = client.substance(base(), "Substance P")!!
        assertThat(result.getJSONArray("abbreviations").getString(0)).isEqualTo("SP")
        assertThat(server.takeRequest().path).isEqualTo("/api/substance/Substance%20P")
    }

    @Test
    fun missingPageIsNull() = runBlocking {
        server.enqueue(json("""{"NotFound":true,"Title":"nope"}"""))
        assertThat(client.substance(base(), "nope")).isNull()
    }

    @Test
    fun searchPrefersExactMatchesAndCachesTheIndex() = runBlocking {
        server.enqueue(
            json(
                """{"Name":"Substance","Entries":[
                  {"Title":"Methylone","Abr":"bk-MDMA"},
                  {"Title":"3,4-Methylenedioxymethamphetamine","Abr":["MDMA"],"Classes":["Entactogen"]},
                  {"Title":"Glucose","DTitle":"Dextrose"}]}""",
            ),
        )
        val first = client.search(base(), "mdma", 5)
        assertThat(first.map { it.title }).containsExactly("3,4-Methylenedioxymethamphetamine", "Methylone")
        val second = client.search(base(), "dextrose", 5)
        assertThat(second.map { it.title }).containsExactly("Glucose")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun cleanStripsMarkup() {
        assertThat(AnodyneWikiClient.clean("C<sub>9</sub>H {wkp 'first-pass  metabolism'}{Ep}"))
            .isEqualTo("C9H first-pass metabolism")
    }

    @Test(timeout = 5000)
    fun malformedHtmlHasBoundedProcessingAndOutput() {
        val result = AnodyneWikiClient.clean("<".repeat(1_000_000))
        org.junit.Assert.assertTrue(result.length <= 12_000)
    }

    @Test
    fun manyExactAliasesKeepResultLimitAndPriority() = runBlocking {
        val entries = JSONArray().apply {
            repeat(2000) { put(JSONObject().put("Title", "Page$it").put("Abr", "same")) }
        }
        server.enqueue(json(JSONObject().put("Entries", entries).toString()))
        val matches = client.search(base(), "same", 10)
        assertThat(matches.size).isEqualTo(10)
        assertThat(matches.first().title).isEqualTo("Page0")
    }
}
