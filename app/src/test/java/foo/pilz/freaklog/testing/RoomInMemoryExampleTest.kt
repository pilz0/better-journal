/*
 * Copyright (c) 2024-2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.testing

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.Room
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import foo.pilz.freaklog.data.room.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Demonstrates the Robolectric + Room test infrastructure.
 *
 * - Uses an in-memory Room database so tests are hermetic.
 * - Runs on the JVM via Robolectric — no emulator required.
 * - Uses kotlinx-coroutines-test (`runTest`) to drive suspending DAO calls.
 *
 * New tests that need a real `Context`, real Android resources, or real
 * `SharedPreferences` should follow this pattern instead of relying on the
 * legacy `isReturnDefaultValues = true` shim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomInMemoryExampleTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // The default executor isn't ideal for tests; allow main-thread
            // queries so we don't have to set up a dispatcher just for the
            // setup blocks. Each test still uses suspend functions.
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `experience round-trips through the DAO`() = runTest {
        val dao = db.experienceDao()
        val sample = EntityBuilders.experience(title = "Roundtrip")

        val newId = dao.insert(sample)

        assertThat(newId).isGreaterThan(0L)
        val fetched = dao.getExperience(newId.toInt())
        assertThat(fetched).isEqualTo(sample.copy(id = newId.toInt()))
    }
}
