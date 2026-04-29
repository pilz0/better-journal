/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose.mdma

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.ui.theme.horizontalPadding
import kotlin.math.roundToInt

/**
 * MDMA-specific dose-picker section, shown inside [ChooseDoseScreen] when the user
 * is logging MDMA. Mirrors the iOS app's `ChooseMDMADoseScreen` view.
 *
 * Includes:
 *  - a weight + sex based maximum-dose calculator with "Use this dose"
 *  - an effect chart showing desirable vs adverse effects across 10–180 mg
 *  - an educational note about pill purity variability
 */
@Composable
fun MDMACalculator(
    onApplyDose: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "MDMA dose calculator",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Estimates a safer maximum oral dose based on body weight and biological sex. " +
                    "Always research current best practices and account for tolerance and drug interactions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            MDMAMaxDoseSection(onApplyDose = onApplyDose)
            HorizontalDivider()
            MDMAOptimalDoseSection()
            HorizontalDivider()
            MDMAPillsSection()
        }
    }
}

@Composable
private fun MDMAMaxDoseSection(onApplyDose: (String) -> Unit) {
    var weightKg by remember { mutableFloatStateOf(70f) }
    var sex by remember { mutableStateOf(MDMAFormulas.Sex.MALE) }
    val maxDose = remember(weightKg, sex) {
        MDMAFormulas.maxDoseMg(weightKg.toDouble(), sex)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Body weight: ${weightKg.roundToInt()} kg",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = weightKg,
            onValueChange = { weightKg = (it / 5f).roundToInt() * 5f },
            valueRange = 40f..150f,
            steps = ((150 - 40) / 5) - 1,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(MDMAFormulas.Sex.MALE, MDMAFormulas.Sex.FEMALE)
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = sex == option,
                    onClick = { sex = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = {
                        Text(
                            when (option) {
                                MDMAFormulas.Sex.MALE -> "Male (1.5 mg/kg)"
                                MDMAFormulas.Sex.FEMALE -> "Female (1.3 mg/kg)"
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Suggested max dose: ${maxDose.roundToInt()} mg",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = { onApplyDose(maxDose.roundToInt().toString()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Use this dose")
        }
    }
}

@Composable
private fun MDMAOptimalDoseSection() {
    val desirableColor = MaterialTheme.colorScheme.primary
    val adverseColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Optimal dose (Dutch testing data)",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Desirable effects peak around ${MDMAFormulas.OPTIMAL_DOSE_MG.roundToInt()} mg, " +
                "while adverse effects rise sharply above that.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width
                val h = size.height
                val sampleCount = 60
                val doses = MDMAFormulas.sampleDoses(sampleCount)
                val xs = doses.indices.map { i -> i / (sampleCount - 1f) * w }

                fun pathFor(values: List<Double>): Path {
                    val p = Path()
                    values.forEachIndexed { i, v ->
                        val x = xs[i]
                        val y = h - (v.toFloat() * h)
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    return p
                }

                // Optimal-dose vertical marker
                val markerX = ((MDMAFormulas.OPTIMAL_DOSE_MG - MDMAFormulas.CHART_MIN_DOSE_MG) /
                    (MDMAFormulas.CHART_MAX_DOSE_MG - MDMAFormulas.CHART_MIN_DOSE_MG)).toFloat() * w
                drawLine(
                    color = gridColor,
                    start = Offset(markerX, 0f),
                    end = Offset(markerX, h),
                    strokeWidth = 2f,
                )

                drawPath(
                    path = pathFor(doses.map { MDMAFormulas.desirableEffectAt(it) }),
                    color = desirableColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                )
                drawPath(
                    path = pathFor(doses.map { MDMAFormulas.adverseEffectAt(it) }),
                    color = adverseColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${MDMAFormulas.CHART_MIN_DOSE_MG.roundToInt()} mg", style = MaterialTheme.typography.labelSmall)
            Text(
                text = "${MDMAFormulas.OPTIMAL_DOSE_MG.roundToInt()} mg (optimal)",
                style = MaterialTheme.typography.labelSmall,
            )
            Text("${MDMAFormulas.CHART_MAX_DOSE_MG.roundToInt()} mg", style = MaterialTheme.typography.labelSmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Legend(color = desirableColor, label = "Desirable")
            Legend(color = adverseColor, label = "Adverse")
        }
        Text(
            text = "Curves derived from public Dutch drug-testing service data; for orientation only.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier
            .height(10.dp)
            .padding(end = 4.dp)) {
            Canvas(modifier = Modifier.height(10.dp)) {
                drawRect(color = color, size = androidx.compose.ui.geometry.Size(20f, 4f))
            }
        }
        Spacer(Modifier.padding(start = 16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MDMAPillsSection() {
    var pillCount by remember { mutableStateOf("1") }
    var mgPerPill by remember { mutableStateOf("100") }
    val total = remember(pillCount, mgPerPill) {
        val pills = pillCount.toDoubleOrNull() ?: return@remember null
        val mg = mgPerPill.toDoubleOrNull() ?: return@remember null
        pills * mg
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Estimating from pills", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Pill purity varies wildly between batches. Always test pills with a reagent kit, " +
                "and assume the per-pill mg below is an estimate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = pillCount,
                onValueChange = { pillCount = it.replace(',', '.') },
                label = { Text("Pills") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = mgPerPill,
                onValueChange = { mgPerPill = it.replace(',', '.') },
                label = { Text("mg / pill") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Estimated total: ${total?.let { "${it.roundToInt()} mg" } ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun MDMACalculatorPreview() {
    MDMACalculator(onApplyDose = {})
}
