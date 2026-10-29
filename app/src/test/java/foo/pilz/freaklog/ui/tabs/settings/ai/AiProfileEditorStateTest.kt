package foo.pilz.freaklog.ui.tabs.settings.ai

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class AiProfileEditorStateTest {
    @Test
    fun remoteHttpProfileMustBeSecuredBeforeSaving() {
        val draft = AiProfileDraft(name = "Local model", model = "model", baseUrl = "http://192.168.1.5/v1")
        assertThat(AiProfileEditorState(draft = draft).canSave).isFalse()
        assertThat(AiProfileEditorState(draft = draft.copy(baseUrl = "https://192.168.1.5/v1")).canSave).isTrue()
        assertThat(AiProfileEditorState(draft = draft.copy(baseUrl = "http://localhost:11434/v1")).canSave).isTrue()
    }
}
