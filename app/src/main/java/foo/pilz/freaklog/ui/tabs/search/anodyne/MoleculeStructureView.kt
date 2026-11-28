package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

private val MAX_STRUCTURE_HEIGHT = 180.dp
private val VERTICAL_PADDING = 8.dp
private val STROKE_INSET = 2.dp

@Composable
fun MoleculeStructureView(
    svg: String,
    substanceName: String,
    modifier: Modifier = Modifier,
) {
  val drawing = remember(svg) { MoleculeDrawing.parse(svg) } ?: return
  val vbWidth = drawing.viewBox.width
  val vbHeight = drawing.viewBox.height
  if (vbWidth <= 0f || vbHeight <= 0f) return
  val foregroundColor = MaterialTheme.colorScheme.onSurface

  Canvas(
      modifier =
          modifier
              .fillMaxWidth()
              .padding(vertical = VERTICAL_PADDING)
              .layout { measurable, constraints ->
                // Modifier.aspectRatio falls back to unbounded height when fillMaxWidth() and
                // heightIn(max) conflict on square/tall molecules, overflowing over nearby text.
                val defaultMaxPx = (MAX_STRUCTURE_HEIGHT - VERTICAL_PADDING * 2).roundToPx()
                val maxHeightPx =
                    min(constraints.maxHeight, defaultMaxPx).coerceAtLeast(constraints.minHeight)
                val width =
                    if (constraints.hasBoundedWidth) {
                      constraints.maxWidth
                    } else {
                      (maxHeightPx * (vbWidth / vbHeight))
                          .roundToInt()
                          .coerceIn(constraints.minWidth, constraints.maxWidth)
                    }
                val naturalHeight = (width * (vbHeight / vbWidth)).roundToInt()
                val height = naturalHeight.coerceIn(constraints.minHeight, maxHeightPx)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.placeRelative(0, 0) }
              }
              .clipToBounds()
              .semantics { contentDescription = "Structure of $substanceName" },
  ) {
    val inset = STROKE_INSET.toPx()
    val availWidth = size.width - inset * 2f
    val availHeight = size.height - inset * 2f
    if (availWidth <= 0f || availHeight <= 0f) return@Canvas

    val scale = min(availWidth / vbWidth, availHeight / vbHeight)
    val offsetX = (size.width - vbWidth * scale) / 2f - drawing.viewBox.left * scale
    val offsetY = (size.height - vbHeight * scale) / 2f - drawing.viewBox.top * scale

    drawContext.canvas.save()
    drawContext.canvas.translate(offsetX, offsetY)
    drawContext.canvas.scale(scale, scale)

    for (shape in drawing.shapes) {
      shape.fill?.let { fillPaint ->
        drawPath(
            path = shape.path,
            color = fillPaint.resolve(foregroundColor),
        )
      }
      shape.stroke?.let { strokePaint ->
        drawPath(
            path = shape.path,
            color = strokePaint.resolve(foregroundColor),
            // CDK always draws round caps and joins; butt caps leave notches where bonds meet.
            style = Stroke(width = shape.lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }
    }

    drawContext.canvas.restore()
  }
}
