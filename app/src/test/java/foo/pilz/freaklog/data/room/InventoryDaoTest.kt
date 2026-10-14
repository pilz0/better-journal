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
import foo.pilz.freaklog.data.room.inventory.InventoryDao
import foo.pilz.freaklog.data.room.inventory.InventoryItem
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
class InventoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: InventoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.inventoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and retrieve inventory item`() = runTest {
        val item = InventoryItem(
            substanceName = "MDMA",
            isCustom = false,
            notes = "Tested with Marquis and Simon's",
            addedAt = Instant.parse("2026-03-01T12:00:00Z")
        )

        val id = dao.insert(item).toInt()
        val all = dao.getAll()

        assertThat(all).hasSize(1)
        val loaded = all.first()
        assertThat(loaded.id).isEqualTo(id)
        assertThat(loaded.substanceName).isEqualTo("MDMA")
        assertThat(loaded.notes).isEqualTo("Tested with Marquis and Simon's")
    }

    @Test
    fun `getAll returns items ordered by addedAt descending`() = runTest {
        val itemOld = InventoryItem(
            substanceName = "LSD",
            addedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val itemNew = InventoryItem(
            substanceName = "DMT",
            addedAt = Instant.parse("2026-02-01T00:00:00Z")
        )

        dao.insert(itemOld)
        dao.insert(itemNew)

        val all = dao.getAll()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.substanceName }).containsExactly("DMT", "LSD")
    }

    @Test
    fun `update modifies inventory item`() = runTest {
        val item = InventoryItem(substanceName = "Psilocybin", notes = "Initial note")
        val id = dao.insert(item).toInt()

        val updated = item.copy(id = id, notes = "Updated note")
        dao.update(updated)

        val loaded = dao.getAll().first { it.id == id }
        assertThat(loaded.notes).isEqualTo("Updated note")
    }

    @Test
    fun `delete removes inventory item`() = runTest {
        val item = InventoryItem(substanceName = "2C-B")
        val id = dao.insert(item).toInt()

        val toDelete = dao.getAll().first { it.id == id }
        dao.delete(toDelete)

        val all = dao.getAll()
        assertThat(all).isEmpty()
    }

    @Test
    fun `deleteAll clears all items`() = runTest {
        dao.insert(InventoryItem(substanceName = "Item 1"))
        dao.insert(InventoryItem(substanceName = "Item 2"))

        dao.deleteAll()
        assertThat(dao.getAll()).isEmpty()
    }
}
