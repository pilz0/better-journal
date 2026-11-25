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

package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun TimeArrowUpPreview() {
  TimeArrowUp(timeText = "4.5 hours")
}

@Composable
fun TimeArrowUp(timeText: String, modifier: Modifier = Modifier) {
  val lineColor = MaterialTheme.colorScheme.outlineVariant
  Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Canvas(modifier = Modifier.width(8.dp).height(14.dp)) {
      val stroke = 1.5.dp.toPx()
      val cx = size.width / 2f
      drawLine(
          start = Offset(x = cx, y = 0f),
          end = Offset(x = cx, y = size.height),
          strokeWidth = stroke,
          color = lineColor,
          cap = StrokeCap.Round,
      )
      drawLine(
          start = Offset(x = cx, y = 0f),
          end = Offset(x = 0f, y = size.width / 2f),
          strokeWidth = stroke,
          color = lineColor,
          cap = StrokeCap.Round,
      )
      drawLine(
          start = Offset(x = cx, y = 0f),
          end = Offset(x = size.width, y = size.width / 2f),
          strokeWidth = stroke,
          color = lineColor,
          cap = StrokeCap.Round,
      )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Text(
          text = timeText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
      )
    }
    Canvas(modifier = Modifier.width(8.dp).height(12.dp)) {
      val stroke = 1.5.dp.toPx()
      val cx = size.width / 2f
      drawLine(
          start = Offset(x = cx, y = 0f),
          end = Offset(x = cx, y = size.height),
          strokeWidth = stroke,
          color = lineColor,
          cap = StrokeCap.Round,
      )
    }
  }
}
