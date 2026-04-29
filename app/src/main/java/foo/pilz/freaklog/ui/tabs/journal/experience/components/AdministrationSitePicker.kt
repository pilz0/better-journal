/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A reusable composable for selecting administration site (nostril or injection location).
 * Shows a dropdown with site options and a "None" option to clear the selection.
 */
@Composable
fun AdministrationSitePicker(
    administrationSite: String,
    siteOptions: List<String>,
    onSiteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isShowingDropDownMenu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.wrapContentSize(Alignment.TopEnd)
    ) {
        OutlinedButton(
            onClick = { isShowingDropDownMenu = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (administrationSite.isNotBlank()) administrationSite else "Select site (optional)")
        }
        DropdownMenu(
            expanded = isShowingDropDownMenu,
            onDismissRequest = { isShowingDropDownMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSiteChange("")
                    isShowingDropDownMenu = false
                }
            )
            siteOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSiteChange(option)
                        isShowingDropDownMenu = false
                    }
                )
            }
        }
    }
}
