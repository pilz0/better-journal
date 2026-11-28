package foo.pilz.freaklog.data.anodyne

import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnodyneRepositoryLookupTest {
  private lateinit var server: MockWebServer
  private lateinit var repository: AnodyneRepository

  @Before
  fun setUp() {
    server = MockWebServer().apply { start() }
    val prefs =
        mockk<UserPreferences> {
          every { anodyneBaseUrlFlow } returns flowOf(server.url("/").toString().removeSuffix("/"))
        }
    repository = AnodyneRepository(ApplicationProvider.getApplicationContext(), prefs)
    repository.clearCache()
  }

  @After fun tearDown() = server.shutdown()

  @Test
  fun serverErrorIsAFailureNotAMissingPage() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(500))
    // A reachable index must not turn the failed page into "no page".
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"Entries":[{"Title":"Ketamine"}]}""")
    )
    val result = repository.lookup("Ketamine")
    assertThat(result.isFailure).isTrue()
    assertThat(AnodyneRepository.lookupProblem(result))
        .isEqualTo("Couldn't reach AnodyneWiki. Check your connection or the instance URL.")
  }

  @Test
  fun missingPageWithoutAliasIsNotFound() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(404))
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"Entries":[{"Title":"Caffeine"}]}""")
    )
    val result = repository.lookup("Nothingamine")
    assertThat(result.getOrNull()).isNull()
    assertThat(result.isSuccess).isTrue()
  }

  @Test
  fun cachedLookupUsesInMemoryCacheUntilCleared() = runBlocking {
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"Title":"LSD","Summary":"Classical psychedelic"}""")
    )
    val first = repository.lookup("LSD").getOrNull()
    val second = repository.lookup("LSD").getOrNull()
    assertThat(first?.title).isEqualTo("LSD")
    assertThat(second?.title).isEqualTo("LSD")
    assertThat(server.requestCount).isEqualTo(1)

    repository.clearCache()
    assertThat(repository.cachedLookup("LSD")).isNull()
  }

  @Test
  fun getUserNotesFetchesConcurrentlyAndToleratesSingleUserError() = runBlocking {
    server.dispatcher =
        object : Dispatcher() {
          override fun dispatch(request: RecordedRequest): MockResponse =
              when (request.path) {
                "/api/index/user" ->
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"Entries":[{"Title":"Alice"},{"Title":"Broken"},{"Title":"Bob"}]}"""
                        )
                "/api/user/Alice" ->
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"Title":"Alice","Substances":[{"Name":"LSD","Notes":"Clear"}]}"""
                        )
                "/api/user/Broken" -> MockResponse().setResponseCode(500)
                "/api/user/Bob" ->
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"Title":"Bob","Substances":[{"Name":"LSD","Notes":"Strong"}]}"""
                        )
                else -> MockResponse().setResponseCode(404)
              }
        }

    val result = repository.getUserNotes(listOf("LSD"))
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrNull()?.map { it.user }?.sorted()).isEqualTo(listOf("Alice", "Bob"))
  }
}
