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
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.room.experiences.entities.Spray
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
class SprayDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SprayDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sprayDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and getSprayById retrieves the exact spray`() = runTest {
        val spray = Spray(
            name = "Ketamine Nasal Spray",
            contentInMl = 10.0,
            numSprays = 100.0,
            creationDate = Instant.parse("2026-01-01T10:00:00Z"),
            isPreferred = false
        )

        val id = dao.insert(spray)
        val loaded = dao.getSprayById(id.toInt())

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Ketamine Nasal Spray")
        assertThat(loaded.contentInMl).isEqualTo(10.0)
        assertThat(loaded.numSprays).isEqualTo(100.0)
    }

    @Test
    fun `getAllSprays returns sprays ordered by creationDate descending`() = runTest {
        val sprayOld = Spray(
            name = "Old Spray",
            contentInMl = 10.0,
            numSprays = 100.0,
            creationDate = Instant.parse("2026-01-01T10:00:00Z")
        )
        val sprayNew = Spray(
            name = "New Spray",
            contentInMl = 15.0,
            numSprays = 150.0,
            creationDate = Instant.parse("2026-02-01T10:00:00Z")
        )

        dao.insert(sprayOld)
        dao.insert(sprayNew)

        val all = dao.getAllSprays()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.name }).containsExactly("New Spray", "Old Spray")
    }

    @Test
    fun `update modifies spray correctly`() = runTest {
        val spray = Spray(
            name = "Saline Spray",
            contentInMl = 20.0,
            numSprays = 200.0
        )
        val id = dao.insert(spray).toInt()

        val updated = spray.copy(id = id, name = "Modified Saline Spray", numSprays = 220.0)
        dao.update(updated)

        val fetched = dao.getSprayById(id)
        assertThat(fetched).isNotNull()
        assertThat(fetched!!.name).isEqualTo("Modified Saline Spray")
        assertThat(fetched.numSprays).isEqualTo(220.0)
    }

    @Test
    fun `delete removes the spray`() = runTest {
        val spray = Spray(name = "Temp Spray", contentInMl = 10.0, numSprays = 100.0)
        val id = dao.insert(spray).toInt()

        val toDelete = dao.getSprayById(id)!!
        dao.delete(toDelete)

        val fetched = dao.getSprayById(id)
        assertThat(fetched).isNull()
    }

    @Test
    fun `setPreferred and clearAllPreferred toggle preferred flag`() = runTest {
        val spray1 = Spray(name = "Spray 1", contentInMl = 10.0, numSprays = 100.0, isPreferred = false)
        val spray2 = Spray(name = "Spray 2", contentInMl = 10.0, numSprays = 100.0, isPreferred = false)

        dao.insert(spray1)
        val id2 = dao.insert(spray2).toInt()

        dao.setPreferred(id2)
        val preferred = dao.getPreferredSpray()
        assertThat(preferred).isNotNull()
        assertThat(preferred!!.id).isEqualTo(id2)

        dao.clearAllPreferred()
        val nonePreferred = dao.getPreferredSpray()
        assertThat(nonePreferred).isNull()
    }

    @Test
    fun `repository insert replaces the preferred spray`() = runTest {
        val repository = SprayRepository(dao)
        val first = Spray(name = "First", contentInMl = 10.0, numSprays = 100.0, isPreferred = true)
        repository.insert(first)
        val secondId = repository.insert(first.copy(name = "Second")).toInt()

        assertThat(dao.getAllSprays().filter { it.isPreferred }.map { it.id }).containsExactly(secondId)
        assertThat(repository.getPreferredSpray()?.id).isEqualTo(secondId)
    }

    @Test
    fun `repository insert of a nonpreferred spray preserves the preference`() = runTest {
        val repository = SprayRepository(dao)
        val spray = Spray(name = "Preferred", contentInMl = 10.0, numSprays = 100.0, isPreferred = true)
        val preferredId = repository.insert(spray).toInt()
        repository.insert(spray.copy(name = "Other", isPreferred = false))

        assertThat(dao.getAllSprays().filter { it.isPreferred }.map { it.id }).containsExactly(preferredId)
    }

    @Test
    fun `deleteAll removes all spray records`() = runTest {
        dao.insert(Spray(name = "Spray A", contentInMl = 10.0, numSprays = 100.0))
        dao.insert(Spray(name = "Spray B", contentInMl = 10.0, numSprays = 100.0))

        dao.deleteAll()
        val all = dao.getAllSprays()
        assertThat(all).isEmpty()
    }
}
