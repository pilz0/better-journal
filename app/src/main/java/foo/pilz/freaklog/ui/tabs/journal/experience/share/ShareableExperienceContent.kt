/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.share

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.models.IngestionElement
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelines
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureReading
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateSample
import foo.pilz.freaklog.ui.utils.getDateWithWeekdayText
import foo.pilz.freaklog.ui.utils.getShortTimeText
import java.time.Instant

/** Everything the shared image shows. Notes are deliberately not part of it. */
data class ShareableExperienceModel(
    val title: String,
    val firstIngestionTime: Instant,
    val locationName: String,
    val ingestionElements: List<IngestionElement>,
    val timelineModel: AllTimelinesModel?,
    val heartRateSamples: List<HeartRateSample> = emptyList(),
    val bloodPressureReadings: List<BloodPressureReading> = emptyList(),
)

/** One line of the ingestion list: "14:30  100 µg LSD, sublingual". */
fun ingestionShareLine(element: IngestionElement): String {
    val withUnit = element.ingestionWithCompanionAndCustomUnit
    val ingestion = withUnit.ingestion
    return "${ingestion.time.getShortTimeText()}  ${withUnit.doseDescription} ${ingestion.substanceName}, " +
        ingestion.administrationRoute.displayText.lowercase()
}

/** Plain-text summary sent alongside the image, for apps that drop the picture. */
fun buildShareText(model: ShareableExperienceModel): String = buildString {
    appendLine(model.title)
    append(model.firstIngestionTime.getDateWithWeekdayText())
    if (model.locationName.isNotBlank()) append(" · ${model.locationName}")
    model.ingestionElements.forEach {
        appendLine()
        append(ingestionShareLine(it))
    }
}

@Composable
fun ShareableExperienceContent(model: ShareableExperienceModel) {
    val isDarkTheme = isSystemInDarkTheme()
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(model.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOf(model.firstIngestionTime.getDateWithWeekdayText(), model.locationName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (model.timelineModel != null) {
                AllTimelines(
                    model = model.timelineModel,
                    isShowingCurrentTime = false,
                    timeDisplayOption = TimeDisplayOption.REGULAR,
                    heartRateSamples = model.heartRateSamples,
                    bloodPressureReadings = model.bloodPressureReadings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
            model.ingestionElements.forEach { element ->
                val color = element.ingestionWithCompanionAndCustomUnit.substanceCompanion?.color
                    ?: AdaptiveColor.RED
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color.getComposeColor(isDarkTheme))
                    )
                    Text(
                        ingestionShareLine(element),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Text(
                "Freaklog",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
