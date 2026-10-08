package foo.pilz.freaklog.ui.graph.style

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GraphStyle(
    val strokeWidth: Dp,
    val dotRadius: Dp,
    val ratingDotRadius: Dp,
    val cornerRadius: Dp,
    val dashOnMultiplier: Float,
    val dashOffMultiplier: Float,
    val shapeAlpha: Float,
    val timeRangeBandHeight: Dp,
    val markerStrokeWidth: Dp,
    val axisLabelSize: TextUnit,
    val ratingSignSize: TextUnit,
    val gridColor: Color,
    val tickColor: Color,
    val axisLabelColor: Color,
    val currentTimeColor: Color,
    val dragLineColor: Color,
    val missingSubstanceColor: Color,
    val heartRateColor: Color,
    val bloodPressureDiastolicColor: Color,
    val bloodPressureSystolicColor: Color,
    val barWidthRatio: Float,
    val barCornerRatio: Float,
    val gridLineWidth: Dp,
    val tickLineWidth: Dp,
    val tickHeight: Dp,
)

val DefaultGraphStyle = GraphStyle(
    strokeWidth = 5.dp,
    dotRadius = 7.dp,
    ratingDotRadius = 5.dp,
    cornerRadius = 15.dp,
    dashOnMultiplier = 3f,
    dashOffMultiplier = 4f,
    shapeAlpha = 0.25f,
    timeRangeBandHeight = 3.dp,
    markerStrokeWidth = 3.dp,
    axisLabelSize = 8.sp,
    ratingSignSize = 12.sp,
    gridColor = Color(0x33888888),
    tickColor = Color(0x33888888),
    axisLabelColor = Color(0x99888888),
    currentTimeColor = Color(0xFF69F0AE),
    dragLineColor = Color(0xFF888888),
    missingSubstanceColor = Color.Magenta,
    heartRateColor = Color(0x66888888),
    bloodPressureDiastolicColor = Color.Red,
    bloodPressureSystolicColor = Color.Cyan,
    barWidthRatio = 0.7f,
    barCornerRatio = 1f / 6f,
    gridLineWidth = 1.dp,
    tickLineWidth = 2.dp,
    tickHeight = 3.dp,
)

val LocalGraphStyle = staticCompositionLocalOf { DefaultGraphStyle }

@Composable
@ReadOnlyComposable
fun themedGraphStyle(): GraphStyle {
    val scheme = MaterialTheme.colorScheme
    return DefaultGraphStyle.copy(
        gridColor = scheme.onSurface.copy(alpha = 0.2f),
        tickColor = scheme.onSurface.copy(alpha = 0.2f),
        axisLabelColor = scheme.onSurfaceVariant,
        currentTimeColor = scheme.primary,
        dragLineColor = scheme.onSurface,
        missingSubstanceColor = scheme.error,
        heartRateColor = scheme.outline,
    )
}
