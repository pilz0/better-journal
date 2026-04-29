/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAvailability
import foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption

/**
 * Settings UI for the biometric app lock.
 *
 * The toggle is disabled when the device cannot perform biometric authentication.
 * The "Lock after" dropdown is only visible while the lock is enabled.
 */
@Composable
fun LockSettingsSection(
    isLockEnabled: Boolean,
    lockTimeOption: LockTimeOption,
    biometricAvailability: BiometricAvailability,
    onLockEnabledChange: (Boolean) -> Unit,
    onLockTimeOptionChange: (LockTimeOption) -> Unit,
) {
    val canUseBiometrics = biometricAvailability == BiometricAvailability.AVAILABLE
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lock journal")
            Switch(
                checked = isLockEnabled && canUseBiometrics,
                enabled = canUseBiometrics,
                onCheckedChange = onLockEnabledChange,
            )
        }
        if (!canUseBiometrics) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = biometricAvailability.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AnimatedVisibility(visible = isLockEnabled && canUseBiometrics) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Lock after",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LockTimeOptionDropdown(
                    selected = lockTimeOption,
                    onSelected = onLockTimeOptionChange,
                )
            }
        }
    }
}

@Composable
private fun LockTimeOptionDropdown(
    selected: LockTimeOption,
    onSelected: (LockTimeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected.displayText)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LockTimeOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayText) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}
