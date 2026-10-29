package foo.pilz.freaklog.ui.tabs.settings.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.profiles.AiProfileStore
import foo.pilz.freaklog.data.ai.profiles.AiProviderPreset
import foo.pilz.freaklog.data.ai.profiles.aiClientFor
import foo.pilz.freaklog.data.ai.provider.AiApiStandard
import foo.pilz.freaklog.data.ai.provider.AiChatRequest
import foo.pilz.freaklog.data.ai.provider.AiEndpoint
import foo.pilz.freaklog.data.ai.provider.AiMessage
import foo.pilz.freaklog.data.ai.provider.AiStreamEvent
import foo.pilz.freaklog.ui.main.navigation.graphs.AiProfileEditorRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** The editable fields of a profile. The key is only known when the user types a new one. */
data class AiProfileDraft(
    val name: String = "",
    val preset: AiProviderPreset = AiProviderPreset.GEMINI,
    val standard: AiApiStandard = preset.standard,
    val baseUrl: String = preset.baseUrl,
    val model: String = preset.suggestedModel,
    val maxOutputTokens: String = AiProfile.DEFAULT_MAX_OUTPUT_TOKENS.toString(),
    /** Newly typed key. Blank means "keep the saved key" unless [removeSavedKey] is set. */
    val apiKey: String = "",
    val hasSavedKey: Boolean = false,
    val removeSavedKey: Boolean = false,
)

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data class Passed(val message: String) : ConnectionTest
    data class Failed(val message: String) : ConnectionTest
}

data class AiProfileEditorState(
    val isNew: Boolean = true,
    val isLoaded: Boolean = false,
    val draft: AiProfileDraft = AiProfileDraft(name = AiProviderPreset.GEMINI.label),
    val models: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val modelsError: String? = null,
    val test: ConnectionTest = ConnectionTest.Idle,
) {
    val missingKey: Boolean
        get() = draft.preset.requiresApiKey && draft.apiKey.isBlank() && (!draft.hasSavedKey || draft.removeSavedKey)
    val canSave: Boolean
        get() = draft.name.isNotBlank() && draft.baseUrl.isNotBlank() && draft.model.isNotBlank()
}

@HiltViewModel
class AiProfileEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileStore: AiProfileStore,
) : ViewModel() {

    private val profileId: String? = savedStateHandle.toRoute<AiProfileEditorRoute>().profileId.ifBlank { null }

    private val _state = MutableStateFlow(AiProfileEditorState(isNew = profileId == null))
    val state: StateFlow<AiProfileEditorState> = _state.asStateFlow()

    private var networkJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = profileId?.let { profileStore.profile(it) }
            if (existing != null) {
                val hasKey = profileStore.apiKey(existing.id).isNotBlank()
                _state.update {
                    it.copy(
                        isLoaded = true,
                        draft = AiProfileDraft(
                            name = existing.name,
                            preset = existing.preset,
                            standard = existing.standard,
                            baseUrl = existing.baseUrl,
                            model = existing.model,
                            maxOutputTokens = existing.maxOutputTokens.toString(),
                            hasSavedKey = hasKey,
                        ),
                    )
                }
            } else {
                _state.update { it.copy(isLoaded = true) }
            }
        }
    }

    /** Switching preset fills in its defaults but keeps a key the user already typed. */
    fun selectPreset(preset: AiProviderPreset) {
        edit { draft ->
            val renamed = draft.name.isBlank() || AiProviderPreset.entries.any { it.label == draft.name }
            draft.copy(
                preset = preset,
                name = if (renamed) preset.label else draft.name,
                standard = preset.standard,
                baseUrl = preset.baseUrl,
                model = preset.suggestedModel,
            )
        }
        _state.update { it.copy(models = emptyList(), modelsError = null) }
    }

    fun edit(transform: (AiProfileDraft) -> AiProfileDraft) {
        _state.update { it.copy(draft = transform(it.draft), test = ConnectionTest.Idle) }
    }

    fun removeSavedKey() = edit { it.copy(apiKey = "", removeSavedKey = true) }

    fun fetchModels() {
        networkJob?.cancel()
        _state.update { it.copy(isFetchingModels = true, modelsError = null) }
        networkJob = viewModelScope.launch {
            try {
                val models = client().listModels()
                _state.update {
                    it.copy(
                        models = models,
                        modelsError = if (models.isEmpty()) "The server listed no models." else null,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _state.update { it.copy(modelsError = e.message ?: "Couldn't load models.") }
            } finally {
                _state.update { it.copy(isFetchingModels = false) }
            }
        }
    }

    /** Sends a tiny request with the current settings and reports what came back. */
    fun testConnection() {
        networkJob?.cancel()
        _state.update { it.copy(test = ConnectionTest.Running) }
        networkJob = viewModelScope.launch {
            try {
                val result = try {
                    val draft = _state.value.draft
                    val request = AiChatRequest(
                        model = draft.model.trim(),
                        system = "You are a connectivity check. Reply with the single word OK.",
                        messages = listOf(AiMessage.User("Reply with OK.")),
                        tools = emptyList(),
                        maxOutputTokens = TEST_MAX_TOKENS,
                    )
                    val reply = StringBuilder()
                    withTimeout(TEST_TIMEOUT_MS) {
                        client().stream(request).collect { event ->
                            if (event is AiStreamEvent.Completed) reply.append(event.message.text)
                        }
                    }
                    val text = reply.toString().trim().take(80)
                    ConnectionTest.Passed(if (text.isEmpty()) "Connected." else "Connected. The model replied: $text")
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    ConnectionTest.Failed("No answer within ${TEST_TIMEOUT_MS / 1000} seconds.")
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    ConnectionTest.Failed(e.message ?: e::class.java.simpleName)
                }
                _state.update { it.copy(test = result) }
            } finally {
                _state.update { if (it.test is ConnectionTest.Running) it.copy(test = ConnectionTest.Idle) else it }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        val draft = current.draft
        viewModelScope.launch {
            val profile = AiProfile(
                id = profileId ?: AiProfileStore.newId(),
                name = draft.name.trim(),
                presetId = draft.preset.name,
                standard = draft.standard,
                baseUrl = draft.baseUrl.trim(),
                model = draft.model.trim(),
                maxOutputTokens = draft.maxOutputTokens.toIntOrNull()?.coerceIn(MIN_TOKENS, MAX_TOKENS)
                    ?: AiProfile.DEFAULT_MAX_OUTPUT_TOKENS,
            )
            val key = when {
                draft.apiKey.isNotBlank() -> draft.apiKey
                draft.removeSavedKey -> ""
                else -> null
            }
            profileStore.save(profile, key)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = profileId ?: return onDone()
        viewModelScope.launch {
            profileStore.delete(id)
            onDone()
        }
    }

    private suspend fun client() = _state.value.draft.let { draft ->
        aiClientFor(draft.standard, AiEndpoint(draft.baseUrl.trim(), effectiveKey(draft)))
    }

    private suspend fun effectiveKey(draft: AiProfileDraft): String = when {
        draft.apiKey.isNotBlank() -> draft.apiKey.trim()
        draft.removeSavedKey || profileId == null -> ""
        else -> profileStore.apiKey(profileId)
    }

    private companion object {
        const val TEST_MAX_TOKENS = 256
        const val TEST_TIMEOUT_MS = 60_000L
        const val MIN_TOKENS = 256
        const val MAX_TOKENS = 128_000
    }
}
