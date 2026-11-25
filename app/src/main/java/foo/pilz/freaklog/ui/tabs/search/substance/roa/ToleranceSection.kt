/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.search.substance.roa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.substances.classes.Tolerance

@Preview(showBackground = true)
@Composable
fun ToleranceSectionPreview() {
  ToleranceSection(
      tolerance =
          Tolerance(
              full = "with prolonged use",
              half = "two weeks",
              zero = "1 month",
          ),
      crossTolerances =
          listOf(
              "dopamine",
              "stimulant",
          ),
  )
}

@Composable
fun ToleranceSection(
    tolerance: Tolerance?,
    crossTolerances: List<String>,
    modifier: Modifier = Modifier,
) {
  if (tolerance != null || crossTolerances.isNotEmpty()) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (tolerance != null) {
        val labelWidth = 52.dp
        ToleranceRow(label = "full:", value = tolerance.full, labelWidth = labelWidth)
        ToleranceRow(label = "half:", value = tolerance.half, labelWidth = labelWidth)
        ToleranceRow(label = "zero:", value = tolerance.zero, labelWidth = labelWidth)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "* zero is the time until tolerance is like the first time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (crossTolerances.isNotEmpty()) {
        if (tolerance != null) Spacer(modifier = Modifier.height(2.dp))
        val names = crossTolerances.distinct().joinToString(separator = ", ")
        Text(
            text = "Cross tolerance with $names.",
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

@Composable
private fun ToleranceRow(
    label: String,
    value: String?,
    labelWidth: Dp,
) {
  if (value == null) return
  Row(verticalAlignment = Alignment.Top) {
    Text(
        text = label,
        modifier = Modifier.width(labelWidth),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}
