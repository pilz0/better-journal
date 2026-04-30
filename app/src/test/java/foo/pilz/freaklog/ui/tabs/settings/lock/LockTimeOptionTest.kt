/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.settings.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTimeOptionTest {

    @Test
    fun `disabled lock never locks`() {
        assertFalse(shouldLockNow(false, LockTimeOption.AFTER_5_MINUTES, 0L, 1_000_000L))
    }

    @Test
    fun `enabled with no last-active forces a lock`() {
        assertTrue(shouldLockNow(true, LockTimeOption.AFTER_5_MINUTES, 0L, 1_000L))
    }

    @Test
    fun `IMMEDIATELY locks for any non-zero gap`() {
        assertTrue(shouldLockNow(true, LockTimeOption.IMMEDIATELY, 1_000L, 1_001L))
    }

    @Test
    fun `does not lock before timeout elapses`() {
        // 4 minutes after pause, with a 5-minute timeout: should not lock yet.
        assertFalse(shouldLockNow(true, LockTimeOption.AFTER_5_MINUTES, 0L + 1_000L, 1_000L + 4 * 60))
    }

    @Test
    fun `locks once timeout has elapsed`() {
        assertTrue(shouldLockNow(true, LockTimeOption.AFTER_5_MINUTES, 1_000L, 1_000L + 5 * 60))
        assertTrue(shouldLockNow(true, LockTimeOption.AFTER_1_HOUR, 0L + 1L, 1L + 60 * 60))
    }

    @Test
    fun `negative elapsed time is treated as zero`() {
        // Clock skew shouldn't immediately lock when timeout > 0.
        assertFalse(shouldLockNow(true, LockTimeOption.AFTER_5_MINUTES, 1_000L, 500L))
    }

    @Test
    fun `fromName parses persisted enum names`() {
        assertEquals(LockTimeOption.AFTER_30_MINUTES, LockTimeOption.fromName("AFTER_30_MINUTES"))
        assertEquals(LockTimeOption.DEFAULT, LockTimeOption.fromName(null))
        assertEquals(LockTimeOption.DEFAULT, LockTimeOption.fromName("BOGUS"))
    }
}
