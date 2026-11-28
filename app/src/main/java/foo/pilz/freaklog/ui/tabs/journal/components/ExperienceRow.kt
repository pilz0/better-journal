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

package foo.pilz.freaklog.ui.tabs.journal.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.getDateWithWeekdayText

@Preview(showBackground = true)
@Composable
fun ExperienceRow(
    @PreviewParameter(ExperienceWithIngestionsCompanionsAndRatingsPreviewProvider::class)
    experienceWithIngestionsCompanionsAndRatings: ExperienceWithIngestionsCompanionsAndRatings,
    navigateToExperienceScreen: () -> Unit = {},
    isTimeRelativeToNow: Boolean = true,
) {
  val isDarkTheme = isSystemInDarkTheme()
  val ingestions =
      remember(experienceWithIngestionsCompanionsAndRatings) {
        experienceWithIngestionsCompanionsAndRatings.ingestionsWithCompanions.sortedBy {
          it.ingestion.time
        }
      }
  val barBrush =
      remember(ingestions, isDarkTheme) {
        if (ingestions.size >= 2) {
          val colors = ingestions.map {
            (it.substanceCompanion?.color ?: AdaptiveColor.RED).getComposeColor(isDarkTheme)
          }
          Brush.verticalGradient(colors = colors)
        } else {
          null
        }
      }
  val barColor =
      remember(ingestions, isDarkTheme) {
        when (ingestions.size) {
          0 -> Color.LightGray.copy(alpha = 0.1f)
          1 ->
              (ingestions.first().substanceCompanion?.color ?: AdaptiveColor.RED).getComposeColor(
                  isDarkTheme
              )
          else -> Color.Transparent
        }
      }
  val experience = experienceWithIngestionsCompanionsAndRatings.experience
  val formattedDate =
      remember(experienceWithIngestionsCompanionsAndRatings.sortInstant) {
        experienceWithIngestionsCompanionsAndRatings.sortInstant.getDateWithWeekdayText()
      }

  Column(
      modifier =
          Modifier.clickable {
                navigateToExperienceScreen()
              }
              .fillMaxWidth()
              .padding(horizontal = horizontalPadding, vertical = 5.dp)
              .drawBehind {
                val barWidth = 11.dp.toPx()
                val radius = 3.dp.toPx()
                val cornerRadius = CornerRadius(radius, radius)
                val barSize = Size(barWidth, size.height)
                if (barBrush != null) {
                  drawRoundRect(
                      brush = barBrush,
                      topLeft = Offset.Zero,
                      size = barSize,
                      cornerRadius = cornerRadius,
                  )
                } else {
                  drawRoundRect(
                      color = barColor,
                      topLeft = Offset.Zero,
                      size = barSize,
                      cornerRadius = cornerRadius,
                  )
                }
              }
              .padding(start = 21.dp)
  ) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text(
          text = experience.title,
          style = MaterialTheme.typography.titleMedium,
      )
      if (experience.isFavorite) {
        Icon(imageVector = Icons.Filled.Star, contentDescription = "Is favorite")
      }
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
      val substanceNames =
          remember(ingestions) {
            ingestions.map { it.ingestion.substanceName }.distinct().joinToString(separator = ", ")
          }
      if (substanceNames.isNotEmpty()) {
        Text(text = substanceNames)
      } else {
        Text(
            text = "No substance yet",
        )
      }
      val rating = experienceWithIngestionsCompanionsAndRatings.rating?.sign
      if (rating != null) {
        Text(text = rating)
      }
    }
    val consumerNames =
        remember(ingestions) {
          ingestions
              .mapNotNull { it.ingestion.consumerName }
              .distinct()
              .joinToString(separator = ", ")
        }
    if (consumerNames.isNotEmpty()) {
      Text(text = "With: $consumerNames", style = MaterialTheme.typography.labelSmall)
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
      val timeStyle = MaterialTheme.typography.labelMedium
      if (isTimeRelativeToNow) {
        RelativeDateTextNew(
            dateTime = experienceWithIngestionsCompanionsAndRatings.sortInstant,
            style = timeStyle,
        )
      } else {
        Text(
            text = formattedDate,
            style = timeStyle,
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      val location = experience.location
      if (location != null) {
        Text(
            text = location.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
        )
      }
    }
  }
}
