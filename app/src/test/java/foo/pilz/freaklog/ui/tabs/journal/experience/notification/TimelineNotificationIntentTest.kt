/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimelineNotificationIntentTest {

    @Test
    fun `buildOpenIntent for specific experience creates OPEN_EXPERIENCE action and sets extra`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val experienceId = 42

        val intent = TimelineNotificationRenderer.buildOpenIntent(context, experienceId)

        assertThat(intent.action).isEqualTo("${context.packageName}${MainActivity.ACTION_OPEN_EXPERIENCE}")
        assertThat(intent.getIntExtra("experienceId", -1)).isEqualTo(42)
        assertThat((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0).isTrue()
        assertThat((intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0).isTrue()
    }

    @Test
    fun `buildOpenIntent for global timeline creates JOURNAL_SCREEN action without experienceId`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = TimelineNotificationRenderer.buildOpenIntent(
            context,
            TimelineNotificationService.GLOBAL_TIMELINE_ID
        )

        assertThat(intent.action).isEqualTo("${context.packageName}${MainActivity.ACTION_JOURNAL_SCREEN}")
        assertThat(intent.getIntExtra("experienceId", -1)).isEqualTo(-1)
        assertThat((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0).isTrue()
        assertThat((intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0).isTrue()
    }
}
