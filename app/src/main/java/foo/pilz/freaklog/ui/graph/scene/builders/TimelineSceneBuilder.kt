package foo.pilz.freaklog.ui.graph.scene.builders

import androidx.compose.ui.graphics.toArgb
import foo.pilz.freaklog.ui.graph.scene.DotRole
import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import foo.pilz.freaklog.ui.graph.scene.GraphScene
import foo.pilz.freaklog.ui.graph.scene.PathOp
import foo.pilz.freaklog.ui.graph.scene.StrokeRole
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.graph.style.GraphStyle

fun buildTimelineScene(groups: List<TimelineGroup>, widthInSeconds: Float, style: GraphStyle): GraphScene {
    val primitives = mutableListOf<GraphPrimitive>()
    for (group in groups) {
        val argb = (group.color ?: style.missingSubstanceColor).toArgb()
        for (segment in group.curve.segments) {
            primitives += GraphPrimitive.StrokePath(
                ops = segment.toStrokeOps(),
                color = argb,
                role = if (segment.dotted) StrokeRole.Dotted else StrokeRole.Normal,
            )
            primitives += GraphPrimitive.FillPath(
                ops = segment.toFillOps(),
                color = argb,
                alpha = style.shapeAlpha,
            )
        }
        for (range in group.timeRanges) {
            primitives += GraphPrimitive.Band(range.startFraction, range.endFraction, argb)
        }
        for (dot in group.curve.ingestionDots) {
            primitives += GraphPrimitive.Dot(Vec2(dot.seconds, dot.height), argb, DotRole.Ingestion)
        }
    }
    return GraphScene(primitives = primitives, widthInSeconds = widthInSeconds)
}

private fun CurveSegment.toStrokeOps(): List<PathOp> {
    if (points.isEmpty()) return emptyList()
    val ops = mutableListOf<PathOp>()
    ops += PathOp.MoveTo(Vec2(points.first().seconds, points.first().height))
    for (i in 1 until points.size) {
        ops += points[i].toPathOp()
    }
    return ops
}

private fun CurveSegment.toFillOps(): List<PathOp> {
    if (points.isEmpty()) return emptyList()
    val ops = toStrokeOps().toMutableList()
    ops += PathOp.LineTo(Vec2(points.last().seconds, 0f))
    ops += PathOp.LineTo(Vec2(points.first().seconds, 0f))
    ops += PathOp.Close
    return ops
}

private fun RawPoint.toPathOp(): PathOp {
    val cs = controlSeconds
    val ch = controlHeight
    return if (cs != null && ch != null) {
        PathOp.QuadTo(control = Vec2(cs, ch), to = Vec2(seconds, height))
    } else {
        PathOp.LineTo(Vec2(seconds, height))
    }
}
