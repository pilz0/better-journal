package foo.pilz.freaklog.ui.graph.scene

data class Vec2(val x: Float, val y: Float)

sealed interface PathOp {
    data class MoveTo(val to: Vec2) : PathOp
    data class LineTo(val to: Vec2) : PathOp
    data class QuadTo(val control: Vec2, val to: Vec2) : PathOp
    data object Close : PathOp
}

enum class StrokeRole { Normal, Dotted }
enum class DotRole { Ingestion, Rating }
enum class CornerRole { Bar, Band }
enum class MarkerRole { CurrentTime, Drag }
enum class TextRole { AxisHour, AxisLabel, RatingSign, Tooltip }
enum class TextAnchor { Start, Center, End }

sealed interface GraphPrimitive {
    data class StrokePath(val ops: List<PathOp>, val color: Int, val role: StrokeRole) : GraphPrimitive
    data class FillPath(val ops: List<PathOp>, val color: Int, val alpha: Float) : GraphPrimitive
    data class Dot(val center: Vec2, val color: Int, val role: DotRole) : GraphPrimitive
    data class RoundRect(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val color: Int, val corner: CornerRole, val filled: Boolean,
    ) : GraphPrimitive
    data class VerticalMarker(val xFraction: Float, val color: Int, val role: MarkerRole) : GraphPrimitive
    data class Text(
        val pos: Vec2, val text: String, val color: Int,
        val role: TextRole, val anchor: TextAnchor,
    ) : GraphPrimitive
    data class Band(val startXFraction: Float, val endXFraction: Float, val color: Int) : GraphPrimitive
}

data class GraphScene(
    val primitives: List<GraphPrimitive>,
    val widthInSeconds: Float,
)
