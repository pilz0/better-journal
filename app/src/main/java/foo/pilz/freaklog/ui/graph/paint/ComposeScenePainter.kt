package foo.pilz.freaklog.ui.graph.paint

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import foo.pilz.freaklog.ui.graph.scene.CornerRole
import foo.pilz.freaklog.ui.graph.scene.DotRole
import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import foo.pilz.freaklog.ui.graph.scene.GraphScene
import foo.pilz.freaklog.ui.graph.scene.PathOp
import foo.pilz.freaklog.ui.graph.scene.StrokeRole
import foo.pilz.freaklog.ui.graph.scene.TextAnchor
import foo.pilz.freaklog.ui.graph.scene.TextRole
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.graph.style.GraphStyle

fun DrawScope.paintScene(
    scene: GraphScene,
    style: GraphStyle,
    textMeasurer: TextMeasurer,
    progress: Float = 1f,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    scene.primitives.forEach { primitive ->
        when (primitive) {
            is GraphPrimitive.StrokePath ->
                drawPath(primitive.ops.toComposePath(w, h, progress), Color(primitive.color), style = strokeFor(primitive.role, style))
            is GraphPrimitive.FillPath ->
                drawPath(primitive.ops.toComposePath(w, h, progress), Color(primitive.color).copy(alpha = primitive.alpha))
            is GraphPrimitive.Dot ->
                drawCircle(Color(primitive.color), dotRadiusFor(primitive.role, style), pointToOffset(primitive.center, w, h, progress))
            is GraphPrimitive.Band -> {
                val bandHeight = style.timeRangeBandHeight.toPx()
                drawRect(
                    color = Color(primitive.color),
                    topLeft = Offset(primitive.startXFraction * w, h - bandHeight),
                    size = Size((primitive.endXFraction - primitive.startXFraction) * w, bandHeight),
                )
            }
            is GraphPrimitive.VerticalMarker -> {
                val x = primitive.xFraction * w
                drawLine(
                    color = Color(primitive.color),
                    start = Offset(x, h),
                    end = Offset(x, 0f),
                    strokeWidth = style.markerStrokeWidth.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            is GraphPrimitive.RoundRect -> {
                val left = primitive.left * w
                val right = primitive.right * w
                val top = h - primitive.top * h
                val bottom = h - primitive.bottom * h
                val radius = when (primitive.corner) {
                    CornerRole.Bar -> (right - left) * style.barCornerRatio
                    CornerRole.Band -> 0f
                }
                val cr = CornerRadius(radius, radius)
                if (primitive.filled) {
                    drawRoundRect(Color(primitive.color), Offset(left, top), Size(right - left, bottom - top), cr)
                } else {
                    drawRoundRect(Color(primitive.color), Offset(left, top), Size(right - left, bottom - top), cr, style = Stroke(width = style.strokeWidth.toPx()))
                }
            }
            is GraphPrimitive.Text -> {
                val textStyle = TextStyle(color = Color(primitive.color), fontSize = textSizeFor(primitive.role, style))
                val measured = textMeasurer.measure(primitive.text, style = textStyle)
                val anchorOffset = when (primitive.anchor) {
                    TextAnchor.Start -> 0f
                    TextAnchor.Center -> measured.size.width / 2f
                    TextAnchor.End -> measured.size.width.toFloat()
                }
                drawText(
                    textMeasurer,
                    text = primitive.text,
                    topLeft = Offset(primitive.pos.x * w - anchorOffset, h - primitive.pos.y * h),
                    style = textStyle,
                )
            }
        }
    }
}

private fun pointToOffset(v: Vec2, w: Float, h: Float, progress: Float): Offset =
    Offset(v.x * w, h - v.y * h * progress)

private fun List<PathOp>.toComposePath(w: Float, h: Float, progress: Float): Path {
    val path = Path()
    forEach { op ->
        when (op) {
            is PathOp.MoveTo -> path.moveTo(op.to.x * w, h - op.to.y * h * progress)
            is PathOp.LineTo -> path.lineTo(op.to.x * w, h - op.to.y * h * progress)
            is PathOp.QuadTo -> path.quadraticTo(
                op.control.x * w, h - op.control.y * h * progress,
                op.to.x * w, h - op.to.y * h * progress,
            )
            PathOp.Close -> path.close()
        }
    }
    return path
}

private fun DrawScope.strokeFor(role: StrokeRole, style: GraphStyle): Stroke {
    val width = style.strokeWidth.toPx()
    return when (role) {
        StrokeRole.Normal -> Stroke(width = width, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(style.cornerRadius.toPx()))
        StrokeRole.Dotted -> Stroke(
            width = width,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * style.dashOnMultiplier, width * style.dashOffMultiplier)),
        )
    }
}

private fun DrawScope.dotRadiusFor(role: DotRole, style: GraphStyle): Float = when (role) {
    DotRole.Ingestion -> style.dotRadius.toPx()
    DotRole.Rating -> style.ratingDotRadius.toPx()
}


private fun textSizeFor(role: TextRole, style: GraphStyle): TextUnit = when (role) {
    TextRole.AxisHour, TextRole.AxisLabel -> style.axisLabelSize
    TextRole.RatingSign, TextRole.Tooltip -> style.ratingSignSize
}
