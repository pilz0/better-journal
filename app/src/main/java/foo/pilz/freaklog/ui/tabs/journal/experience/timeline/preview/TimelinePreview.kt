/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.journal.experience.timeline.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration

private val OnsetColor = Color(0xFF7986CB)
private val ComeupColor = Color(0xFF42A5F5)
private val PeakColor = Color(0xFFEF5350)
private val OffsetColor = Color(0xFFFFA726)

/**
 * Compact horizontal bar showing the relative proportions of onset / comeup
 * / peak / offset for a given [RoaDuration]. Used as a read-only preview on
 * the dose-entry and edit-ingestion screens. No time axis — the bar is
 * always full width and represents a single ingestion's typical arc.
 */
@Composable
fun TimelinePreview(
    duration: RoaDuration?,
    modifier: Modifier = Modifier,
) {
    if (duration == null) return
    val onset = avg(duration.onset)
    val comeup = avg(duration.comeup)
    val peak = avg(duration.peak)
    val offset = avg(duration.offset)
    val total = onset + comeup + peak + offset
    if (total <= 0f) return

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Projected timeline",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            PhaseBar(onset / total, OnsetColor)
            PhaseBar(comeup / total, ComeupColor)
            PhaseBar(peak / total, PeakColor)
            PhaseBar(offset / total, OffsetColor)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Legend("Onset", OnsetColor, duration.onset)
            Legend("Comeup", ComeupColor, duration.comeup)
            Legend("Peak", PeakColor, duration.peak)
            Legend("Offset", OffsetColor, duration.offset)
        }
    }
}

@Composable
private fun RowScope.PhaseBar(weight: Float, color: Color) {
    if (weight <= 0f) return
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
private fun Legend(name: String, color: Color, range: DurationRange?) {
    if (range == null) return
    val min = range.minInSec
    val max = range.maxInSec
    if (min == null && max == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = " $name ${formatRange(min, max)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun avg(range: DurationRange?): Float {
    if (range == null) return 0f
    val min = range.minInSec
    val max = range.maxInSec
    return when {
        min != null && max != null -> (min + max) / 2f
        min != null -> min
        max != null -> max
        else -> 0f
    }
}

private fun formatRange(min: Float?, max: Float?): String {
    fun secToHuman(s: Float): String {
        val minutes = (s / 60f).toInt()
        return if (minutes < 60) "${minutes}m" else "%.1fh".format(s / 3600f)
    }
    return when {
        min != null && max != null -> "${secToHuman(min)}–${secToHuman(max)}"
        min != null -> secToHuman(min)
        max != null -> secToHuman(max)
        else -> ""
    }
}
