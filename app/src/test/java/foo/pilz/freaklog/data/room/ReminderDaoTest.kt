/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.room.reminders.ReminderDao
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reminderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and getReminderById retrieves the exact reminder`() = runTest {
        val reminder = Reminder(
            title = "Daily Supplement",
            hour = 9,
            minute = 0,
            isEnabled = true,
            scheduleType = "DAILY_AT_TIMES",
            timesOfDay = "09:00,21:00",
            substanceName = "Magnesium",
            dose = 400.0,
            units = "mg"
        )

        val id = dao.insert(reminder).toInt()
        val loaded = dao.getReminderById(id)

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.title).isEqualTo("Daily Supplement")
        assertThat(loaded.timesOfDay).isEqualTo("09:00,21:00")
        assertThat(loaded.substanceName).isEqualTo("Magnesium")
        assertThat(loaded.dose).isEqualTo(400.0)
    }

    @Test
    fun `getAllReminders returns all inserted reminders`() = runTest {
        val rem1 = Reminder(title = "Morning Dose", hour = 8, minute = 0, isEnabled = true)
        val rem2 = Reminder(title = "Evening Dose", hour = 20, minute = 0, isEnabled = false)

        dao.insert(rem1)
        dao.insert(rem2)

        val all = dao.getAllReminders()
        assertThat(all).hasSize(2)
    }

    @Test
    fun `update modifies reminder properties`() = runTest {
        val reminder = Reminder(title = "Old Title", hour = 10, minute = 30, isEnabled = false)
        val id = dao.insert(reminder).toInt()

        val updated = reminder.copy(id = id, title = "New Title", isEnabled = true)
        dao.update(updated)

        val fetched = dao.getReminderById(id)
        assertThat(fetched).isNotNull()
        assertThat(fetched!!.title).isEqualTo("New Title")
        assertThat(fetched.isEnabled).isEqualTo(true)
    }

    @Test
    fun `delete removes the reminder`() = runTest {
        val reminder = Reminder(title = "To Delete", hour = 12, minute = 0, isEnabled = true)
        val id = dao.insert(reminder).toInt()

        val toDelete = dao.getReminderById(id)!!
        dao.delete(toDelete)

        val fetched = dao.getReminderById(id)
        assertThat(fetched).isNull()
    }

    @Test
    fun `deleteAll removes all reminders`() = runTest {
        dao.insert(Reminder(title = "R1", hour = 1, minute = 0, isEnabled = true))
        dao.insert(Reminder(title = "R2", hour = 2, minute = 0, isEnabled = true))

        dao.deleteAll()
        val all = dao.getAllReminders()
        assertThat(all).isEmpty()
    }
}
