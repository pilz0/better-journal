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
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.AppDatabase
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExperienceDaoTest {

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
    fun `getIngestionWithExperiencesFlow includes ingestion when time is exactly equal to fromInstant`() = runTest {
        val fromInstant = Instant.parse("2026-05-01T12:00:00Z")
        val toInstant = Instant.parse("2026-05-02T12:00:00Z")

        val expId = dao.insert(EntityBuilders.experience(title = "Boundary Trip")).toInt()
        val exactIngestionId = dao.insert(
            EntityBuilders.ingestion(
                experienceId = expId,
                substanceName = "MDMA",
                time = fromInstant,
            )
        ).toInt()

        val results = dao.getIngestionWithExperiencesFlow(fromInstant, toInstant).first()
        assertThat(results).hasSize(1)
        assertThat(results.first().ingestion.id).isEqualTo(exactIngestionId)
    }

    @Test
    fun `getIngestionWithExperiencesFlow correctly filters boundary conditions`() = runTest {
        val fromInstant = Instant.parse("2026-05-01T12:00:00Z")
        val toInstant = Instant.parse("2026-05-02T12:00:00Z")

        val expId = dao.insert(EntityBuilders.experience(title = "Multi-Ingestion Trip")).toInt()

        // 1 second before window (should be excluded)
        dao.insert(
            EntityBuilders.ingestion(
                experienceId = expId,
                substanceName = "Before",
                time = fromInstant.minusSeconds(1),
            )
        )
        // Exactly at fromInstant (should be included)
        val atFromId = dao.insert(
            EntityBuilders.ingestion(
                experienceId = expId,
                substanceName = "AtFrom",
                time = fromInstant,
            )
        ).toInt()
        // Mid-window (should be included)
        val midId = dao.insert(
            EntityBuilders.ingestion(
                experienceId = expId,
                substanceName = "Mid",
                time = fromInstant.plusSeconds(3600),
            )
        ).toInt()
        // Exactly at toInstant (should be excluded by time < toInstant)
        dao.insert(
            EntityBuilders.ingestion(
                experienceId = expId,
                substanceName = "AtTo",
                time = toInstant,
            )
        )
        // After toInstant (should be excluded)
        dao.insert(
            EntityBuilders.ingestion(
                experienceId = expId,
                substanceName = "AfterTo",
                time = toInstant.plusSeconds(60),
            )
        )

        val results = dao.getIngestionWithExperiencesFlow(fromInstant, toInstant).first()
        val resultIds = results.map { it.ingestion.id }
        assertThat(resultIds).containsExactly(atFromId, midId)
    }
}
