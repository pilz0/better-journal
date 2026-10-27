/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.room.experiences

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import foo.pilz.freaklog.data.export.ExperienceSerializable
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReplaceEverythingTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExperienceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.experienceDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `replaceEverything atomically replaces existing experiences and ingestions with new export`() = runTest {
        val oldId = dao.insert(EntityBuilders.experience(title = "Old experience")).toInt()
        dao.insert(EntityBuilders.ingestion(experienceId = oldId))

        val export = JournalExport(
            experiences = listOf(
                ExperienceSerializable(
                    title = "Imported experience",
                    text = "Freshly imported",
                    creationDate = Instant.parse("2026-02-01T12:00:00Z"),
                    sortDate = Instant.parse("2026-02-01T12:00:00Z"),
                    isFavorite = true,
                    location = null,
                    ingestions = emptyList(),
                    ratings = emptyList(),
                    timedNotes = emptyList(),
                    bloodPressure = emptyList(),
                )
            ),
            customUnits = emptyList(),
            reminders = emptyList(),
            webhooks = emptyList(),
            substanceGroups = emptyList(),
            customSubstances = emptyList(),
            customSubstanceDetails = emptyList(),
            intakeLimits = emptyList(),
        )

        dao.replaceEverything(export)

        val imported = dao.getExperience(1)
        assertNotNull(imported)
        assertEquals("Imported experience", imported!!.title)
    }
}
