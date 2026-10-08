package foo.pilz.freaklog.ui.graph.paint

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.unit.Density
import foo.pilz.freaklog.ui.graph.scene.CornerRole
import foo.pilz.freaklog.ui.graph.scene.DotRole
import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import foo.pilz.freaklog.ui.graph.scene.GraphScene
import foo.pilz.freaklog.ui.graph.scene.PathOp
import foo.pilz.freaklog.ui.graph.scene.StrokeRole
import foo.pilz.freaklog.ui.graph.scene.TextAnchor
import foo.pilz.freaklog.ui.graph.scene.TextRole
import foo.pilz.freaklog.ui.graph.style.GraphStyle

fun Canvas.paintScene(scene: GraphScene, graphStyle: GraphStyle, density: Density, progress: Float = 1f) {
    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0f || h <= 0f) return

    val strokeWidthPx = with(density) { graphStyle.strokeWidth.toPx() }
    val cornerRadiusPx = with(density) { graphStyle.cornerRadius.toPx() }
    val bandHeightPx = with(density) { graphStyle.timeRangeBandHeight.toPx() }
    val ingestionDotPx = with(density) { graphStyle.dotRadius.toPx() }
    val ratingDotPx = with(density) { graphStyle.ratingDotRadius.toPx() }
    val markerStrokePx = with(density) { graphStyle.markerStrokeWidth.toPx() }

    val solidStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = CornerPathEffect(cornerRadiusPx)
    }
    val dottedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(
            floatArrayOf(strokeWidthPx * graphStyle.dashOnMultiplier, strokeWidthPx * graphStyle.dashOffMultiplier),
            0f,
        )
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val opaqueFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = markerStrokePx
        strokeCap = Paint.Cap.ROUND
    }

    scene.primitives.forEach { primitive ->
        when (primitive) {
            is GraphPrimitive.StrokePath -> {
                val paint = if (primitive.role == StrokeRole.Dotted) dottedStroke else solidStroke
                paint.color = primitive.color
                drawPath(primitive.ops.toAndroidPath(w, h, progress), paint)
            }
            is GraphPrimitive.FillPath -> {
                fillPaint.color = withAlpha(primitive.color, primitive.alpha)
                drawPath(primitive.ops.toAndroidPath(w, h, progress), fillPaint)
            }
            is GraphPrimitive.Dot -> {
                opaqueFill.color = primitive.color
                val radius = if (primitive.role == DotRole.Rating) ratingDotPx else ingestionDotPx
                drawCircle(primitive.center.x * w, h - primitive.center.y * h * progress, radius, opaqueFill)
            }
            is GraphPrimitive.Band -> {
                opaqueFill.color = primitive.color
                drawRect(primitive.startXFraction * w, h - bandHeightPx, primitive.endXFraction * w, h, opaqueFill)
            }
            is GraphPrimitive.VerticalMarker -> {
                markerPaint.color = primitive.color
                val x = primitive.xFraction * w
                drawLine(x, h, x, 0f, markerPaint)
            }
            is GraphPrimitive.RoundRect -> {
                val left = primitive.left * w
                val right = primitive.right * w
                val top = h - primitive.top * h
                val bottom = h - primitive.bottom * h
                val radius = if (primitive.corner == CornerRole.Bar) (right - left) * graphStyle.barCornerRatio else 0f
                val paint = if (primitive.filled) {
                    opaqueFill.also { it.color = primitive.color }
                } else {
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = strokeWidthPx
                        color = primitive.color
                    }
                }
                drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
            }
            is GraphPrimitive.Text -> {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primitive.color
                    textSize = with(density) {
                        when (primitive.role) {
                            TextRole.AxisHour, TextRole.AxisLabel -> graphStyle.axisLabelSize.toPx()
                            TextRole.RatingSign, TextRole.Tooltip -> graphStyle.ratingSignSize.toPx()
                        }
                    }
                    textAlign = when (primitive.anchor) {
                        TextAnchor.Start -> Paint.Align.LEFT
                        TextAnchor.Center -> Paint.Align.CENTER
                        TextAnchor.End -> Paint.Align.RIGHT
                    }
                }
                drawText(primitive.text, primitive.pos.x * w, h - primitive.pos.y * h, textPaint)
            }
        }
    }
}

private fun withAlpha(color: Int, alpha: Float): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (a shl 24)
}

private fun List<PathOp>.toAndroidPath(w: Float, h: Float, progress: Float): Path {
    val path = Path()
    forEach { op ->
        when (op) {
            is PathOp.MoveTo -> path.moveTo(op.to.x * w, h - op.to.y * h * progress)
            is PathOp.LineTo -> path.lineTo(op.to.x * w, h - op.to.y * h * progress)
            is PathOp.QuadTo -> path.quadTo(
                op.control.x * w, h - op.control.y * h * progress,
                op.to.x * w, h - op.to.y * h * progress,
            )
            PathOp.Close -> path.close()
        }
    }
    return path
}
