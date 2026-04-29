/*
 * Copyright (c) 2024. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.journal.addingestion.time

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor

/**
 * Modal dialog for picking an arbitrary RGB color.
 *
 * Includes:
 *  - A square SV (saturation × value) area that picks a point at a given hue.
 *  - A horizontal hue slider.
 *  - A live hex (RRGGBB) text field for typing or pasting an exact value.
 *  - A preview swatch.
 *
 * Returns an [AdaptiveColor.Custom] (always opaque, alpha = 0xFF) on OK.
 */
@Composable
fun CustomColorDialog(
    initialColor: AdaptiveColor.Custom?,
    onDismiss: () -> Unit,
    onPick: (AdaptiveColor.Custom) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        val argb = initialColor?.argb ?: Color.Red.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        rgbToHsv(r, g, b)
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }

    val previewColor = Color.hsv(hue, saturation, value)
    val previewArgb = previewColor.toArgb() or 0xFF000000.toInt()
    val hexText = "%06X".format(previewArgb and 0xFFFFFF)
    var hexFieldText by remember { mutableStateOf(hexText) }
    // Re-sync the hex field only when HSV changes (e.g., user dragged the SV/hue picker),
    // not on every recomposition. This lets the user type partial input like "1" without
    // it being immediately overwritten by the current 6-digit value.
    LaunchedEffect(hue, saturation, value) {
        if (hexFieldText.uppercase() != hexText) {
            hexFieldText = hexText
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Pick a custom color", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Saturation × Value picker
                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChanged = { s, v ->
                        saturation = s
                        value = v
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Hue slider
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = previewColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(48.dp),
                    ) {}
                    TextField(
                        value = hexFieldText,
                        onValueChange = { input ->
                            val cleaned = input.uppercase().filter {
                                it in '0'..'9' || it in 'A'..'F'
                            }.take(6)
                            hexFieldText = cleaned
                            if (cleaned.length == 6) {
                                val parsed = cleaned.toInt(16)
                                val r = ((parsed shr 16) and 0xFF) / 255f
                                val g = ((parsed shr 8) and 0xFF) / 255f
                                val b = (parsed and 0xFF) / 255f
                                val hsv = rgbToHsv(r, g, b)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("Hex (RRGGBB)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(AdaptiveColor.Custom(previewArgb)) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onChanged: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.x.coerceAtLeast(1f)
                    val h = size.y.coerceAtLeast(1f)
                    val s = (offset.x / w).coerceIn(0f, 1f)
                    val v = (1f - offset.y / h).coerceIn(0f, 1f)
                    onChanged(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.x.coerceAtLeast(1f)
                    val h = size.y.coerceAtLeast(1f)
                    val s = (change.position.x / w).coerceIn(0f, 1f)
                    val v = (1f - change.position.y / h).coerceIn(0f, 1f)
                    onChanged(s, v)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            size = Offset(this.size.width, this.size.height)
            // Horizontal: white -> pure hue
            val pureHue = Color.hsv(hue, 1f, 1f)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, pureHue)),
            )
            // Vertical: transparent on top -> black on bottom (multiplies value)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            )
            // Selection indicator
            val cx = saturation * this.size.width
            val cy = (1f - value) * this.size.height
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = Offset(cx, cy),
                style = Stroke(width = 3f),
            )
            drawCircle(
                color = Color.Black,
                radius = 8f,
                center = Offset(cx, cy),
                style = Stroke(width = 1f),
            )
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    Column {
        Text(text = "Hue: ${hue.toInt()}°", fontSize = 12.sp)
        Slider(
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
        )
    }
}

private fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    val v = max
    return floatArrayOf(h, s, v)
}

@Preview
@Composable
private fun CustomColorDialogPreview() {
    CustomColorDialog(
        initialColor = AdaptiveColor.Custom(0xFF22AAFF.toInt()),
        onDismiss = {},
        onPick = {},
    )
}
