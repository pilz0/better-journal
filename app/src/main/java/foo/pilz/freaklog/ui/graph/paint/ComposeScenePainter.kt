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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
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

internal sealed interface CompiledPrimitive {
  class StrokePath(val path: Path, val color: Color, val stroke: Stroke) : CompiledPrimitive

  class FillPath(val path: Path, val color: Color) : CompiledPrimitive

  class Dot(val color: Color, val radius: Float, val center: Offset) : CompiledPrimitive

  class Band(val color: Color, val topLeft: Offset, val size: Size) : CompiledPrimitive

  class VerticalMarker(
      val color: Color,
      val start: Offset,
      val end: Offset,
      val strokeWidth: Float,
  ) : CompiledPrimitive

  class RoundRect(
      val color: Color,
      val topLeft: Offset,
      val size: Size,
      val cornerRadius: CornerRadius,
      val stroke: Stroke?,
  ) : CompiledPrimitive

  class Text(val layoutResult: TextLayoutResult, val topLeft: Offset) : CompiledPrimitive
}

internal class CompiledComposeScene(val primitives: List<CompiledPrimitive>)

private class SceneCompileContext(
    val style: GraphStyle,
    density: Density,
    val textMeasurer: TextMeasurer,
    val width: Float,
    val height: Float,
    val progress: Float,
) {
  val strokeWidthPx = with(density) { style.strokeWidth.toPx() }
  val cornerRadiusPx = with(density) { style.cornerRadius.toPx() }
  val normalStroke =
      Stroke(
          width = strokeWidthPx,
          cap = StrokeCap.Round,
          pathEffect = PathEffect.cornerPathEffect(cornerRadiusPx),
      )
  val dottedStroke =
      Stroke(
          width = strokeWidthPx,
          cap = StrokeCap.Round,
          pathEffect =
              PathEffect.dashPathEffect(
                  floatArrayOf(
                      strokeWidthPx * style.dashOnMultiplier,
                      strokeWidthPx * style.dashOffMultiplier,
                  )
              ),
      )
  val roundRectStroke = Stroke(width = strokeWidthPx)
  val bandHeight = with(density) { style.timeRangeBandHeight.toPx() }
  val markerStrokeWidth = with(density) { style.markerStrokeWidth.toPx() }
  val ingestionDotRadius = with(density) { style.dotRadius.toPx() }
  val ratingDotRadius = with(density) { style.ratingDotRadius.toPx() }
}

internal fun compileComposeScene(
    scene: GraphScene,
    style: GraphStyle,
    density: Density,
    width: Float,
    height: Float,
    textMeasurer: TextMeasurer,
    progress: Float = 1f,
): CompiledComposeScene {
  if (width <= 0f || height <= 0f) return CompiledComposeScene(emptyList())
  val ctx = SceneCompileContext(style, density, textMeasurer, width, height, progress)
  return CompiledComposeScene(scene.primitives.map { compilePrimitive(it, ctx) })
}

private fun compilePrimitive(
    primitive: GraphPrimitive,
    ctx: SceneCompileContext,
): CompiledPrimitive =
    when (primitive) {
      is GraphPrimitive.StrokePath ->
          CompiledPrimitive.StrokePath(
              path =
                  Path().apply {
                    fillComposePath(primitive.ops, ctx.width, ctx.height, ctx.progress)
                  },
              color = Color(primitive.color),
              stroke =
                  if (primitive.role == StrokeRole.Dotted) ctx.dottedStroke else ctx.normalStroke,
          )
      is GraphPrimitive.FillPath ->
          CompiledPrimitive.FillPath(
              path =
                  Path().apply {
                    fillComposePath(primitive.ops, ctx.width, ctx.height, ctx.progress)
                  },
              color = Color(primitive.color).copy(alpha = primitive.alpha),
          )
      is GraphPrimitive.Dot ->
          CompiledPrimitive.Dot(
              color = Color(primitive.color),
              radius =
                  if (primitive.role == DotRole.Rating) {
                    ctx.ratingDotRadius
                  } else {
                    ctx.ingestionDotRadius
                  },
              center = pointToOffset(primitive.center, ctx.width, ctx.height, ctx.progress),
          )
      is GraphPrimitive.Band ->
          CompiledPrimitive.Band(
              color = Color(primitive.color),
              topLeft = Offset(primitive.startXFraction * ctx.width, ctx.height - ctx.bandHeight),
              size =
                  Size(
                      (primitive.endXFraction - primitive.startXFraction) * ctx.width,
                      ctx.bandHeight,
                  ),
          )
      is GraphPrimitive.VerticalMarker -> {
        val x = primitive.xFraction * ctx.width
        CompiledPrimitive.VerticalMarker(
            color = Color(primitive.color),
            start = Offset(x, ctx.height),
            end = Offset(x, 0f),
            strokeWidth = ctx.markerStrokeWidth,
        )
      }
      is GraphPrimitive.RoundRect -> compileRoundRectPrimitive(primitive, ctx)
      is GraphPrimitive.Text -> compileTextPrimitive(primitive, ctx)
    }

private fun compileRoundRectPrimitive(
    primitive: GraphPrimitive.RoundRect,
    ctx: SceneCompileContext,
): CompiledPrimitive.RoundRect {
  val left = primitive.left * ctx.width
  val right = primitive.right * ctx.width
  val top = ctx.height - primitive.top * ctx.height
  val bottom = ctx.height - primitive.bottom * ctx.height
  val radius =
      when (primitive.corner) {
        CornerRole.Bar -> (right - left) * ctx.style.barCornerRatio
        CornerRole.Band -> 0f
      }
  return CompiledPrimitive.RoundRect(
      color = Color(primitive.color),
      topLeft = Offset(left, top),
      size = Size(right - left, bottom - top),
      cornerRadius = CornerRadius(radius, radius),
      stroke = if (primitive.filled) null else ctx.roundRectStroke,
  )
}

private fun compileTextPrimitive(
    primitive: GraphPrimitive.Text,
    ctx: SceneCompileContext,
): CompiledPrimitive.Text {
  val textStyle =
      TextStyle(
          color = Color(primitive.color),
          fontSize = textSizeFor(primitive.role, ctx.style),
      )
  val measured = ctx.textMeasurer.measure(primitive.text, style = textStyle)
  val anchorOffset =
      when (primitive.anchor) {
        TextAnchor.Start -> 0f
        TextAnchor.Center -> measured.size.width / 2f
        TextAnchor.End -> measured.size.width.toFloat()
      }
  return CompiledPrimitive.Text(
      layoutResult = measured,
      topLeft =
          Offset(
              primitive.pos.x * ctx.width - anchorOffset,
              ctx.height - primitive.pos.y * ctx.height,
          ),
  )
}

internal fun DrawScope.drawCompiledScene(compiled: CompiledComposeScene) {
  for (primitive in compiled.primitives) {
    when (primitive) {
      is CompiledPrimitive.StrokePath ->
          drawPath(primitive.path, primitive.color, style = primitive.stroke)
      is CompiledPrimitive.FillPath -> drawPath(primitive.path, primitive.color)
      is CompiledPrimitive.Dot -> drawCircle(primitive.color, primitive.radius, primitive.center)
      is CompiledPrimitive.Band ->
          drawRect(color = primitive.color, topLeft = primitive.topLeft, size = primitive.size)
      is CompiledPrimitive.VerticalMarker ->
          drawLine(
              color = primitive.color,
              start = primitive.start,
              end = primitive.end,
              strokeWidth = primitive.strokeWidth,
              cap = StrokeCap.Round,
          )
      is CompiledPrimitive.RoundRect ->
          if (primitive.stroke == null) {
            drawRoundRect(
                color = primitive.color,
                topLeft = primitive.topLeft,
                size = primitive.size,
                cornerRadius = primitive.cornerRadius,
            )
          } else {
            drawRoundRect(
                color = primitive.color,
                topLeft = primitive.topLeft,
                size = primitive.size,
                cornerRadius = primitive.cornerRadius,
                style = primitive.stroke,
            )
          }
      is CompiledPrimitive.Text ->
          drawText(textLayoutResult = primitive.layoutResult, topLeft = primitive.topLeft)
    }
  }
}

fun DrawScope.paintScene(
    scene: GraphScene,
    style: GraphStyle,
    textMeasurer: TextMeasurer,
    progress: Float = 1f,
) {
  val w = size.width
  val h = size.height
  if (w <= 0f || h <= 0f) return
  val reusablePath = Path()
  val strokeWidthPx = style.strokeWidth.toPx()
  val normalStroke =
      Stroke(
          width = strokeWidthPx,
          cap = StrokeCap.Round,
          pathEffect = PathEffect.cornerPathEffect(style.cornerRadius.toPx()),
      )
  val dottedStroke =
      Stroke(
          width = strokeWidthPx,
          cap = StrokeCap.Round,
          pathEffect =
              PathEffect.dashPathEffect(
                  floatArrayOf(
                      strokeWidthPx * style.dashOnMultiplier,
                      strokeWidthPx * style.dashOffMultiplier,
                  )
              ),
      )
  val roundRectStroke = Stroke(width = strokeWidthPx)
  val bandHeight = style.timeRangeBandHeight.toPx()
  val markerStrokeWidth = style.markerStrokeWidth.toPx()

  scene.primitives.forEach { primitive ->
    when (primitive) {
      is GraphPrimitive.StrokePath -> {
        reusablePath.reset()
        reusablePath.fillComposePath(primitive.ops, w, h, progress)
        drawPath(
            reusablePath,
            Color(primitive.color),
            style = if (primitive.role == StrokeRole.Dotted) dottedStroke else normalStroke,
        )
      }
      is GraphPrimitive.FillPath -> {
        reusablePath.reset()
        reusablePath.fillComposePath(primitive.ops, w, h, progress)
        drawPath(
            reusablePath,
            Color(primitive.color).copy(alpha = primitive.alpha),
        )
      }
      is GraphPrimitive.Dot ->
          drawCircle(
              Color(primitive.color),
              dotRadiusFor(primitive.role, style),
              pointToOffset(primitive.center, w, h, progress),
          )
      is GraphPrimitive.Band -> {
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
            strokeWidth = markerStrokeWidth,
            cap = StrokeCap.Round,
        )
      }
      is GraphPrimitive.RoundRect -> {
        val left = primitive.left * w
        val right = primitive.right * w
        val top = h - primitive.top * h
        val bottom = h - primitive.bottom * h
        val radius =
            when (primitive.corner) {
              CornerRole.Bar -> (right - left) * style.barCornerRatio
              CornerRole.Band -> 0f
            }
        val cr = CornerRadius(radius, radius)
        if (primitive.filled) {
          drawRoundRect(
              Color(primitive.color),
              Offset(left, top),
              Size(right - left, bottom - top),
              cr,
          )
        } else {
          drawRoundRect(
              Color(primitive.color),
              Offset(left, top),
              Size(right - left, bottom - top),
              cr,
              style = roundRectStroke,
          )
        }
      }
      is GraphPrimitive.Text -> {
        val textStyle =
            TextStyle(color = Color(primitive.color), fontSize = textSizeFor(primitive.role, style))
        val measured = textMeasurer.measure(primitive.text, style = textStyle)
        val anchorOffset =
            when (primitive.anchor) {
              TextAnchor.Start -> 0f
              TextAnchor.Center -> measured.size.width / 2f
              TextAnchor.End -> measured.size.width.toFloat()
            }
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(primitive.pos.x * w - anchorOffset, h - primitive.pos.y * h),
        )
      }
    }
  }
}

private fun pointToOffset(v: Vec2, w: Float, h: Float, progress: Float): Offset =
    Offset(v.x * w, h - v.y * h * progress)

private fun Path.fillComposePath(ops: List<PathOp>, w: Float, h: Float, progress: Float) {
  for (op in ops) {
    when (op) {
      is PathOp.MoveTo -> moveTo(op.to.x * w, h - op.to.y * h * progress)
      is PathOp.LineTo -> lineTo(op.to.x * w, h - op.to.y * h * progress)
      is PathOp.QuadTo ->
          quadraticTo(
              op.control.x * w,
              h - op.control.y * h * progress,
              op.to.x * w,
              h - op.to.y * h * progress,
          )
      PathOp.Close -> close()
    }
  }
}

private fun DrawScope.dotRadiusFor(role: DotRole, style: GraphStyle): Float =
    when (role) {
      DotRole.Ingestion -> style.dotRadius.toPx()
      DotRole.Rating -> style.ratingDotRadius.toPx()
    }

private fun textSizeFor(role: TextRole, style: GraphStyle): TextUnit =
    when (role) {
      TextRole.AxisHour,
      TextRole.AxisLabel -> style.axisLabelSize
      TextRole.RatingSign,
      TextRole.Tooltip -> style.ratingSignSize
    }
