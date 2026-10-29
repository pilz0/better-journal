package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.content.Context
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class TimelinePrivacyTest {
    @Test
    fun lockedJournalNeverLoadsNotificationData() = runTest {
        val context = mockk<Context>()
        val experiences = mockk<ExperienceRepository>()
        val prefs = mockk<UserPreferences> { every { isLockEnabledFlow } returns flowOf(true) }
        val renderer = TimelineNotificationRenderer(context, experiences, mockk(), prefs, mockk())
        assertNull(renderer.render(1))
        assertNull(renderer.render(TimelineNotificationService.GLOBAL_TIMELINE_ID))
        coVerify(exactly = 0) { experiences.getExperience(any()) }
        coVerify(exactly = 0) { experiences.getIngestionsWithCompanions(any(), any()) }
    }
}
