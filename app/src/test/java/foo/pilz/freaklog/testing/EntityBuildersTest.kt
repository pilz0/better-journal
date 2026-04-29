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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Test
import java.time.Instant

/**
 * Smoke tests for the shared [EntityBuilders] fixture. Acts as a
 * canary: if these break, every other test that builds entities is
 * affected.
 */
class EntityBuildersTest {

    @Test
    fun `experience uses sensible defaults`() {
        val e = EntityBuilders.experience()
        assertThat(e.id).isEqualTo(0)
        assertThat(e.title).isEqualTo("Test Experience")
        assertThat(e.text).isEqualTo("")
        assertThat(e.creationDate).isEqualTo(EntityBuilders.FIXED_INSTANT)
        assertThat(e.sortDate).isEqualTo(EntityBuilders.FIXED_INSTANT)
        assertThat(e.isFavorite).isEqualTo(false)
        assertThat(e.location).isNull()
    }

    @Test
    fun `ingestion uses sensible defaults`() {
        val i = EntityBuilders.ingestion()
        assertThat(i.substanceName).isEqualTo("Caffeine")
        assertThat(i.administrationRoute).isEqualTo(AdministrationRoute.ORAL)
        assertThat(i.dose).isEqualTo(100.0)
        assertThat(i.units).isEqualTo("mg")
        assertThat(i.creationDate).isNotNull()
        assertThat(i.endTime).isNull()
    }

    @Test
    fun `ingestion overrides take effect`() {
        val custom = EntityBuilders.ingestion(
            substanceName = "LSD",
            dose = 100.0,
            units = "ug",
            administrationRoute = AdministrationRoute.SUBLINGUAL,
            time = Instant.parse("2025-12-31T23:59:00Z"),
        )
        assertThat(custom.substanceName).isEqualTo("LSD")
        assertThat(custom.units).isEqualTo("ug")
        assertThat(custom.administrationRoute).isEqualTo(AdministrationRoute.SUBLINGUAL)
        assertThat(custom.time).isEqualTo(Instant.parse("2025-12-31T23:59:00Z"))
    }

    @Test
    fun `fixed epoch milli is millisecond representation of fixed instant`() {
        assertThat(fixedEpochMilli).isEqualTo(EntityBuilders.FIXED_INSTANT.toEpochMilli())
    }
}
