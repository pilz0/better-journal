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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.tabs.search.substance.BulletPoints
import foo.pilz.freaklog.ui.tabs.search.substance.SectionText
import foo.pilz.freaklog.ui.tabs.search.substance.VerticalSpace
import foo.pilz.freaklog.ui.theme.horizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun ExplainTimelineScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline info") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = horizontalPadding)
        ) {
            VerticalSpace()
            CardWithTitle(title = "Simplifying/False assumptions") {
                val text = buildAnnotatedString {
                    append("To be able to draw the timeline with the given data multiple ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                        append("often false")
                    }
                    append(" simplifying assumptions are made:")
                }
                SectionText(text = text)
                BulletPoints(
                    points = listOf(
                        "Taking e.g. x amount now will give the same effect as taking x amount later. There is no immediate tolerance.",
                        "Taking twice the dose will give twice the effect.",
                        "Duration ranges from PsychonautWiki can be applied for all kinds of dosages.",
                        "Oral ingestions are always on an empty stomach and therefore not delayed (by up to 4 hours).",
                        "Every substance follows simple two-compartment (one-absorption-rate, one-elimination-rate) pharmacokinetics, regardless of the actual metabolic pathway.",
                    )
                )
            }
            CardWithTitle(title = "Understanding the timeline") {
                BulletPoints(
                    points = listOf(
                        "Each ingestion is plotted as a smooth pharmacokinetic absorption/elimination curve (a Bateman function) instead of a polygon. The curve has no vertical spike at ingestion, a single rounded peak, and an exponential tail.",
                        "The peak time of the curve (tmax) is taken from PsychonautWiki's onset and comeup durations. The tail length is taken from the total duration when defined, otherwise estimated from onset+comeup+peak+offset.",
                        "Ingestions of the same substance via the same route are stacked on a shared sample grid, so two overlapping doses produce a single taller combined curve.",
                        "Different substances or administration routes are always drawn as separate curves, even if they overlap in time.",
                        "When PsychonautWiki data is incomplete (e.g. only a total duration is known) the curve is drawn with a dotted stroke to signal that its shape is uncertain. The location of the peak in such cases is a rough estimate.",
                        "When a time range is given for the ingestion (an infusion), the curve is the analytical convolution of the Bateman function with that ingestion window — the rise is slower and the peak occurs later than for the equivalent point ingestion.",
                    )
                )
            }
            CardWithTitle(title = "PsychonautWiki durations") {
                SectionText(
                    text = "Duration refers to the length of time over which the subjective effects of a psychoactive substance manifest themselves.\n" +
                            "Duration can be broken down into 6 parts: (1) total duration (2) onset (3) come up (4) peak (5) offset and (6) after effects. Depending upon the substance consumed, each of these occurs in a separate and continuous fashion."
                )
                val titleStyle = MaterialTheme.typography.titleSmall
                Text(text = "Total", style = titleStyle)
                SectionText(text = "The total duration of a substance can be defined as the amount of time it takes for the effects of a substance to completely wear off into sobriety, starting from the moment the substance is first administered.")
                Text(text = "Onset", style = titleStyle)
                SectionText(text = "The onset phase can be defined as the period until the very first changes in perception (i.e. \"first alerts\") are able to be detected.")
                Text(text = "Come up", style = titleStyle)
                SectionText(text = "The \"come up\" phase can be defined as the period between the first noticeable changes in perception and the point of highest subjective intensity. This is colloquially known as \"coming up.\"")
                Text(text = "Peak", style = titleStyle)
                SectionText(text = "The peak phase can be defined as period of time in which the intensity of the substance's effects are at its height.")
                Text(text = "Offset", style = titleStyle)
                SectionText(text = "The offset phase can be defined as the amount of time in between the conclusion of the peak and shifting into a sober state. This is colloquially referred to as \"coming down.\"")
                Text(text = "After effects", style = titleStyle)
                SectionText(
                    text = "The after effects can be defined as any residual effects which may remain after the experience has reached its conclusion. This is colloquially known as a \"hangover\" or an \"afterglow\" depending on the substance and usage.\n" +
                            "The after effects are not included as part of the total duration."
                )
            }
        }
    }
}

