/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.settings.lock

/**
 * How long the journal can stay in the background before it locks again.
 *
 * The values match the iOS app: Immediately / 5 min / 10 min / 30 min / 1 hour.
 */
enum class LockTimeOption(val displayText: String, val timeoutSeconds: Long) {
    IMMEDIATELY("Immediately", 0L),
    AFTER_5_MINUTES("After 5 minutes", 5 * 60L),
    AFTER_10_MINUTES("After 10 minutes", 10 * 60L),
    AFTER_30_MINUTES("After 30 minutes", 30 * 60L),
    AFTER_1_HOUR("After 1 hour", 60 * 60L);

    companion object {
        val DEFAULT = IMMEDIATELY

        fun fromName(name: String?): LockTimeOption =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Pure helper used by [BiometricAuthManager] (and easily unit-testable on the JVM).
 *
 * The journal should re-lock when:
 *  - lock is enabled, and
 *  - more time than [option].timeoutSeconds has passed since [lastActiveEpochSeconds].
 *
 * For [LockTimeOption.IMMEDIATELY] any non-zero gap forces a re-lock so that switching
 * away from the app and back triggers re-authentication.
 */
fun shouldLockNow(
    isLockEnabled: Boolean,
    option: LockTimeOption,
    lastActiveEpochSeconds: Long,
    nowEpochSeconds: Long,
): Boolean {
    if (!isLockEnabled) return false
    if (lastActiveEpochSeconds <= 0L) return true
    val elapsed = (nowEpochSeconds - lastActiveEpochSeconds).coerceAtLeast(0L)
    return elapsed >= option.timeoutSeconds
}
