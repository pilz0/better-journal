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

package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.graphics.applyCanvas
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.theme.JournalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders an AllTimelines composable to a Bitmap.
 * This allows the widget to use the exact same graph renderer as the ExperienceScreen.
 */
suspend fun renderTimelineToBitmap(
    context: Context,
    model: AllTimelinesModel,
    timeDisplayOption: TimeDisplayOption,
    isShowingCurrentTime: Boolean,
    width: Int,
    height: Int
): Bitmap = withContext(Dispatchers.Main) {
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            JournalTheme {
                AllTimelines(
                    model = model,
                    timeDisplayOption = timeDisplayOption,
                    isShowingCurrentTime = isShowingCurrentTime,
                    modifier = Modifier
                        .width(width.dp)
                        .height(height.dp)
                )
            }
        }
    }

    // Measure and layout the view
    val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
    val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    composeView.measure(widthMeasureSpec, heightMeasureSpec)
    composeView.layout(0, 0, width, height)

    // Create bitmap and draw the view
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        composeView.draw(this)
    }

    bitmap
}
