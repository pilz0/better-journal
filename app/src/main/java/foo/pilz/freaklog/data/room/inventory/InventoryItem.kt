/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.data.room.inventory

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A user-entered inventory item. Holds just enough to identify a substance
 * (either a PsychonautWiki substance by name, or a user's own "custom"
 * substance by name) plus a freeform note. Quantity tracking is explicitly
 * out of scope.
 */
@Entity
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val substanceName: String,
    val isCustom: Boolean = false,
    val notes: String = "",
    val addedAt: Instant = Instant.now()
)
