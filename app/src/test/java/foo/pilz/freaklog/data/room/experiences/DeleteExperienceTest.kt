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
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRatingOption
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeleteExperienceTest {

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

    private suspend fun insertExperienceWithChildren(title: String): Int {
        val id = dao.insert(EntityBuilders.experience(title = title)).toInt()
        dao.insert(EntityBuilders.ingestion(experienceId = id))
        dao.insert(
            ShulginRating(
                time = EntityBuilders.FIXED_INSTANT,
                option = ShulginRatingOption.PLUS,
                experienceId = id,
            )
        )
        dao.insert(
            TimedNote(
                time = EntityBuilders.FIXED_INSTANT,
                note = "note of $title",
                color = AdaptiveColor.BLUE,
                experienceId = id,
                isPartOfTimeline = true,
            )
        )
        return id
    }

    @Test
    fun `deleteEverythingOfExperience removes timed notes with the experience`() = runTest {
        val deletedId = insertExperienceWithChildren("deleted")
        val keptId = insertExperienceWithChildren("kept")

        dao.deleteEverythingOfExperience(deletedId)

        assertThat(dao.getExperience(deletedId)).isNull()
        assertThat(dao.getIngestionsWithCompanionsFlow(deletedId).first()).isEmpty()
        assertThat(dao.getRatingsFlow(deletedId).first()).isEmpty()
        assertThat(dao.getTimedNotes(deletedId)).isEmpty()

        assertThat(dao.getExperience(keptId)).isNotNull()
        assertThat(dao.getIngestionsWithCompanionsFlow(keptId).first()).hasSize(1)
        assertThat(dao.getRatingsFlow(keptId).first()).hasSize(1)
        assertThat(dao.getTimedNotes(keptId)).hasSize(1)
    }
}
