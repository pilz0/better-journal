package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun MoleculeStructureView(
    svg: String,
    substanceName: String,
    modifier: Modifier = Modifier,
) {
  val drawing = remember(svg) { MoleculeDrawing.parse(svg) } ?: return
  val foregroundColor = MaterialTheme.colorScheme.onSurface

  Canvas(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(max = 180.dp)
              .aspectRatio(drawing.viewBox.width / drawing.viewBox.height.coerceAtLeast(1f))
              .padding(vertical = 8.dp)
              .semantics { contentDescription = "Structure of $substanceName" },
  ) {
    val scale = min(size.width / drawing.viewBox.width, size.height / drawing.viewBox.height)
    val offsetX = (size.width - drawing.viewBox.width * scale) / 2f - drawing.viewBox.left * scale
    val offsetY = (size.height - drawing.viewBox.height * scale) / 2f - drawing.viewBox.top * scale

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
            style = Stroke(width = shape.lineWidth),
        )
      }
    }

    drawContext.canvas.restore()
  }
}
