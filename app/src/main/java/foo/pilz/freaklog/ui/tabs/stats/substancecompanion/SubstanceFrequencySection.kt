/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact horizontal-bar comparison of how often this substance was logged in three time ranges.
 */
@Composable
fun SubstanceFrequencySection(
    frequency: SubstanceFrequency,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Usage frequency", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(4.dp))
      Text(
          text = "Distinct experiences containing this substance.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(12.dp))
      val maxValue = maxOf(frequency.last30Days, frequency.last12Months, frequency.allTime, 1)
      FrequencyRow(
          label = "Last 30 days",
          value = frequency.last30Days,
          maxValue = maxValue,
          barColor = barColor,
      )
      FrequencyRow(
          label = "Last 12 months",
          value = frequency.last12Months,
          maxValue = maxValue,
          barColor = barColor,
      )
      FrequencyRow(
          label = "All time",
          value = frequency.allTime,
          maxValue = maxValue,
          barColor = barColor,
      )
    }
  }
}

@Composable
private fun FrequencyRow(
    label: String,
    value: Int,
    maxValue: Int,
    barColor: Color,
) {
  val pillShape = RoundedCornerShape(50)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
        text = label,
        modifier = Modifier.widthIn(min = 112.dp),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Box(
        modifier =
            Modifier.weight(1f)
                .height(10.dp)
                .clip(pillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
      val safeMax = maxValue.coerceAtLeast(1)
      val ratio = (value.toFloat() / safeMax).coerceIn(0f, 1f)
      if (ratio > 0f) {
        Box(
            modifier =
                Modifier.fillMaxWidth(ratio).height(10.dp).clip(pillShape).background(barColor),
        )
      }
    }
    Text(
        text = value.toString(),
        modifier = Modifier.widthIn(min = 36.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
  }
}
