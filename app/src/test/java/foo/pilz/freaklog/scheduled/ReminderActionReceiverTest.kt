/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.scheduled

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderActionReceiverTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExperienceDao
    private lateinit var receiver: ReminderActionReceiver

    private val reminder = Reminder(
        title = "Morning meds",
        hour = 8,
        minute = 0,
        isEnabled = true,
        substanceName = "Caffeine",
        administrationRoute = "ORAL",
        dose = 100.0,
        units = "mg",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.experienceDao()
        receiver = ReminderActionReceiver().apply {
            experienceRepository = ExperienceRepository(
                experienceDao = dao,
                reminderDao = db.reminderDao(),
                webhookDao = db.webhookDao(),
                ingestionWebhookMessageDao = db.ingestionWebhookMessageDao(),
                context = context,
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertExperience(id: Int, title: String, at: Instant) {
        dao.insert(EntityBuilders.experience(id = id, title = title, creationDate = at, sortDate = at))
        dao.insert(EntityBuilders.ingestion(experienceId = id, time = at, creationDate = at))
    }

    @Test
    fun `take without recent experience does not overwrite the first experience`() = runTest {
        val longAgo = Instant.now().minus(10, ChronoUnit.DAYS)
        insertExperience(id = 1, title = "First trip", at = longAgo)

        receiver.handleTake(reminder)

        assertThat(dao.getExperience(1)?.title).isEqualTo("First trip")
        assertThat(dao.getIngestionsWithCompanionsFlow(1).first()).hasSize(1)
        val created = dao.getExperience(2)
        assertThat(created).isNotNull()
        assertThat(created?.title).isEqualTo("Morning meds")
        assertThat(dao.getIngestionsWithCompanionsFlow(2).first()).hasSize(1)
    }

    @Test
    fun `new experience id is above the global maximum, not just recent ones`() = runTest {
        val now = Instant.now()
        // Higher id but older sort date, so it is outside the two-day lookup window.
        insertExperience(id = 4, title = "Backdated", at = now.minus(30, ChronoUnit.DAYS))
        // Inside the window but too long ago to attach the new ingestion to.
        insertExperience(id = 3, title = "Yesterday", at = now.minus(20, ChronoUnit.HOURS))

        receiver.handleTake(reminder)

        assertThat(dao.getExperience(4)?.title).isEqualTo("Backdated")
        assertThat(dao.getExperience(3)?.title).isEqualTo("Yesterday")
        assertThat(dao.getExperience(5)?.title).isEqualTo("Morning meds")
    }

    @Test
    fun `take attaches to an experience that started recently`() = runTest {
        val now = Instant.now()
        insertExperience(id = 7, title = "Ongoing", at = now.minus(1, ChronoUnit.HOURS))

        receiver.handleTake(reminder)

        val ingestions = dao.getIngestionsWithCompanionsFlow(7).first()
        assertThat(ingestions.map { it.ingestion.notes })
            .containsExactlyInAnyOrder(null, "Logged from reminder")
        assertThat(dao.getExperience(8)).isEqualTo(null)
    }
}
