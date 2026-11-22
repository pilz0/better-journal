package foo.pilz.freaklog.data.ai

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.profiles.AiProfileStore
import foo.pilz.freaklog.data.ai.profiles.AiProviderPreset
import foo.pilz.freaklog.data.ai.provider.AiApiStandard
import foo.pilz.freaklog.data.ai.provider.AiConversation
import foo.pilz.freaklog.data.ai.provider.AiException
import foo.pilz.freaklog.data.ai.provider.AiToolCall
import foo.pilz.freaklog.data.ai.provider.AiTurnEvent
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val repository = mockk<AiChatbotRepository>()
  private val profileStore = mockk<AiProfileStore>()
  private val auth = mockk<BiometricAuthManager>()

  private val shouldLockFlow = MutableStateFlow(false)
  private val profilesFlow = MutableStateFlow<List<AiProfile>>(emptyList())
  private val activeProfileIdFlow = MutableStateFlow<String?>("p1")

  private val testProfile =
      AiProfile(
          id = "p1",
          name = "Default",
          presetId = AiProviderPreset.OPENAI.name,
          standard = AiApiStandard.OPENAI_CHAT,
          baseUrl = "https://api.openai.com",
          model = "gpt-4o",
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { auth.shouldLock } returns shouldLockFlow
    every { profileStore.profilesFlow } returns profilesFlow
    every { profileStore.activeProfileIdFlow } returns activeProfileIdFlow
    profilesFlow.value = listOf(testProfile)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(conversation: AiConversation): AiChatViewModel {
    coEvery { repository.createChatSession(any()) } returns
        AiChatSessionResult.Ready(conversation, testProfile, "Context")

    val vm = AiChatViewModel(repository, profileStore, auth)
    testDispatcher.scheduler.advanceUntilIdle()
    vm.initialize(null)
    testDispatcher.scheduler.advanceUntilIdle()
    return vm
  }

  @Test
  fun `locking clears chat items and active session but preserves loaded profiles and activeProfileId`() =
      runTest(testDispatcher) {
        val conversation = mockk<AiConversation>(relaxed = true)
        val vm = createViewModel(conversation)

        assertThat(vm.uiState.value.profiles).isEqualTo(listOf(testProfile))
        assertThat(vm.uiState.value.activeProfileId).isEqualTo("p1")
        assertThat(vm.uiState.value.items.isNotEmpty()).isTrue()

        shouldLockFlow.value = true
        advanceUntilIdle()

        // Locking resets items and initialized session, but keeps profiles and activeProfileId
        assertThat(vm.uiState.value.items).isEqualTo(emptyList())
        assertThat(vm.uiState.value.profiles).isEqualTo(listOf(testProfile))
        assertThat(vm.uiState.value.activeProfileId).isEqualTo("p1")
      }

  @Test
  fun `retryLastTurn resends failed user message when turn failed before any text`() =
      runTest(testDispatcher) {
        val conversation = mockk<AiConversation>()
        // First turn fails before any text
        every { conversation.send("Hello") } returns flow { throw AiException("Network error") }
        // Rollback returns null because the turn had already rolled back on failure
        every { conversation.rollbackLastTurn() } returns null

        val vm = createViewModel(conversation)
        vm.sendMessage("Hello")
        advanceUntilIdle()

        assertThat(vm.uiState.value.canRetry).isTrue()
        assertThat(vm.uiState.value.items.last().let { (it as ChatItem.Message).isError }).isTrue()

        // On retry, it should resend "Hello"
        every { conversation.send("Hello") } returns
            flowOf(AiTurnEvent.TextDelta("Hi back!"), AiTurnEvent.Completed("Hi back!"))
        vm.retryLastTurn()
        advanceUntilIdle()

        val messages = vm.uiState.value.items.filterIsInstance<ChatItem.Message>()
        assertThat(messages.any { it.isUser && it.text == "Hello" }).isTrue()
        assertThat(messages.last().text).isEqualTo("Hi back!")
      }

  @Test
  fun `empty assistant response shows empty response notice for current turn`() =
      runTest(testDispatcher) {
        val conversation = mockk<AiConversation>()
        // First turn succeeds with text
        every { conversation.send("Turn 1") } returns
            flowOf(AiTurnEvent.TextDelta("Answer 1"), AiTurnEvent.Completed("Answer 1"))
        val vm = createViewModel(conversation)
        vm.sendMessage("Turn 1")
        advanceUntilIdle()

        // Second turn returns empty response
        every { conversation.send("Turn 2") } returns flowOf(AiTurnEvent.Completed(""))
        vm.sendMessage("Turn 2")
        advanceUntilIdle()

        val messages = vm.uiState.value.items.filterIsInstance<ChatItem.Message>()
        assertThat(messages.last().text).isEqualTo("The assistant returned an empty response.")
      }

  @Test
  fun `turn with earlier text before tool call does not show empty response notice`() =
      runTest(testDispatcher) {
        val conversation = mockk<AiConversation>()
        val call = AiToolCall("call1", "tool", emptyMap())
        every { conversation.send("Use tool") } returns
            flowOf(
                AiTurnEvent.TextDelta("Checking..."),
                AiTurnEvent.ToolStarted("t1", call),
                AiTurnEvent.ToolFinished("t1", call, JSONObject()),
                AiTurnEvent.Completed(""),
            )
        val vm = createViewModel(conversation)
        vm.sendMessage("Use tool")
        advanceUntilIdle()

        val messages = vm.uiState.value.items.filterIsInstance<ChatItem.Message>()
        assertThat(messages.any { it.text == "Checking..." }).isTrue()
        assertThat(messages.none { it.text == "The assistant returned an empty response." })
            .isTrue()
      }
}
