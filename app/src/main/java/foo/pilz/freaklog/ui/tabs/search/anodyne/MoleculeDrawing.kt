package foo.pilz.freaklog.ui.tabs.search.anodyne

import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

sealed class MoleculePaint {
  data object Foreground : MoleculePaint()

  data class Rgb(val red: Float, val green: Float, val blue: Float) : MoleculePaint()

  fun resolve(foregroundColor: Color): Color =
      when (this) {
        is Foreground -> foregroundColor
        is Rgb -> Color(red, green, blue)
      }
}

data class MoleculeShape(
    val path: Path,
    val fill: MoleculePaint?,
    val stroke: MoleculePaint?,
    val lineWidth: Float,
)

data class MoleculeDrawing(
    val viewBox: Rect,
    val shapes: List<MoleculeShape>,
) {
  companion object {
    private const val DEFAULT_SIZE = 300f
    private const val FULL_BYTE_SHIFT = 8
    private const val TWO_BYTE_SHIFT = 16
    private const val BYTE_MASK = 0xFF
    private const val COLOR_COMPONENT_MAX = 255f
    private const val SHORT_HEX_LENGTH = 3
    private const val HEX_RADIX = 16

    /** rx, ry, x-axis-rotation, large-arc-flag, sweep-flag. */
    private const val ARC_FLAG_PARAMS = 5
    private val cache = mutableMapOf<String, MoleculeDrawing>()

    data class Style(
        val fill: MoleculePaint? = MoleculePaint.Foreground,
        val stroke: MoleculePaint? = null,
        val lineWidth: Float = 1f,
    )

    fun parse(svg: String): MoleculeDrawing? {
      cache[svg]?.let {
        return it
      }
      return try {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(svg)) }

        var viewBox: Rect? = null
        val shapes = mutableListOf<MoleculeShape>()
        val styles = mutableListOf(Style())
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
          when (eventType) {
            XmlPullParser.START_TAG -> {
              val parsedVb = parseStartTag(parser, styles, shapes)
              if (parsedVb != null) viewBox = parsedVb
            }
            XmlPullParser.END_TAG -> {
              if (styles.size > 1) styles.removeAt(styles.lastIndex)
            }
          }
          eventType = parser.next()
        }

        val vb = viewBox ?: Rect(0f, 0f, DEFAULT_SIZE, DEFAULT_SIZE)
        val drawing = MoleculeDrawing(vb, shapes)
        cache[svg] = drawing
        drawing
      } catch (e: Exception) {
        Log.w("MoleculeDrawing", "Failed to parse SVG", e)
        null
      }
    }

    private fun parseStartTag(
        parser: XmlPullParser,
        styles: MutableList<Style>,
        shapes: MutableList<MoleculeShape>,
    ): Rect? {
      val name = parser.name.lowercase()
      val parentStyle = styles.last()

      val fillAttr = parser.getAttributeValue(null, "fill")
      val strokeAttr = parser.getAttributeValue(null, "stroke")
      val widthAttr = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull()

      val fill = if (fillAttr != null) parsePaint(fillAttr) else parentStyle.fill
      val stroke = if (strokeAttr != null) parsePaint(strokeAttr) else parentStyle.stroke
      val lineWidth = widthAttr ?: parentStyle.lineWidth

      styles.add(Style(fill, stroke, lineWidth))
      return parseElement(parser, name, fill, stroke, lineWidth, shapes)
    }

    private fun parseElement(
        parser: XmlPullParser,
        name: String,
        fill: MoleculePaint?,
        stroke: MoleculePaint?,
        lineWidth: Float,
        shapes: MutableList<MoleculeShape>,
    ): Rect? {
      when (name) {
        "svg" -> return parseSvgViewBox(parser)
        "line" -> parseLine(parser, stroke, lineWidth)?.let { shapes.add(it) }
        "rect" -> parseRect(parser, fill, stroke, lineWidth)?.let { shapes.add(it) }
        "circle" -> parseCircle(parser, fill, stroke, lineWidth)?.let { shapes.add(it) }
        "polygon",
        "polyline" -> parsePoly(parser, name, fill, stroke, lineWidth)?.let { shapes.add(it) }
        "path" -> parsePath(parser, fill, stroke, lineWidth)?.let { shapes.add(it) }
      }
      return null
    }

    private fun parseSvgViewBox(parser: XmlPullParser): Rect? {
      val vb = parser.getAttributeValue(null, "viewBox") ?: return null
      val nums = vb.split(Regex("[ ,]+")).mapNotNull { it.toFloatOrNull() }
      if (nums.size == 4 && nums[2] > 0 && nums[3] > 0) {
        return Rect(nums[0], nums[1], nums[0] + nums[2], nums[1] + nums[3])
      }
      return null
    }

    private fun parseLine(
        parser: XmlPullParser,
        stroke: MoleculePaint?,
        lineWidth: Float,
    ): MoleculeShape? {
      val x1 = parser.getAttributeValue(null, "x1")?.toFloatOrNull() ?: 0f
      val y1 = parser.getAttributeValue(null, "y1")?.toFloatOrNull() ?: 0f
      val x2 = parser.getAttributeValue(null, "x2")?.toFloatOrNull() ?: 0f
      val y2 = parser.getAttributeValue(null, "y2")?.toFloatOrNull() ?: 0f
      val path =
          Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
          }
      return MoleculeShape(
          path,
          fill = null,
          stroke = stroke ?: MoleculePaint.Foreground,
          lineWidth = lineWidth,
      )
    }

    private fun parseRect(
        parser: XmlPullParser,
        fill: MoleculePaint?,
        stroke: MoleculePaint?,
        lineWidth: Float,
    ): MoleculeShape? {
      val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
      val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
      val w = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 0f
      val h = parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: 0f
      if (w <= 0 || h <= 0) return null
      val path = Path().apply { addRect(Rect(x, y, x + w, y + h)) }
      return MoleculeShape(path, fill = fill, stroke = stroke, lineWidth = lineWidth)
    }

    private fun parseCircle(
        parser: XmlPullParser,
        fill: MoleculePaint?,
        stroke: MoleculePaint?,
        lineWidth: Float,
    ): MoleculeShape? {
      val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
      val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
      val r = parser.getAttributeValue(null, "r")?.toFloatOrNull() ?: 0f
      if (r <= 0) return null
      val path = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
      return MoleculeShape(path, fill = fill, stroke = stroke, lineWidth = lineWidth)
    }

    private fun parsePoly(
        parser: XmlPullParser,
        name: String,
        fill: MoleculePaint?,
        stroke: MoleculePaint?,
        lineWidth: Float,
    ): MoleculeShape? {
      val pts =
          parser.getAttributeValue(null, "points")?.split(Regex("[ ,]+"))?.mapNotNull {
            it.toFloatOrNull()
          }
      if (pts == null || pts.size < 4) return null
      val path =
          Path().apply {
            moveTo(pts[0], pts[1])
            for (i in 2 until pts.size step 2) {
              if (i + 1 < pts.size) lineTo(pts[i], pts[i + 1])
            }
            if (name == "polygon") close()
          }
      return MoleculeShape(
          path,
          fill = if (name == "polygon") fill else null,
          stroke = stroke,
          lineWidth = lineWidth,
      )
    }

    private fun parsePath(
        parser: XmlPullParser,
        fill: MoleculePaint?,
        stroke: MoleculePaint?,
        lineWidth: Float,
    ): MoleculeShape? {
      val d = parser.getAttributeValue(null, "d")
      if (d.isNullOrBlank()) return null
      val path = parseSvgPath(d) ?: return null
      return MoleculeShape(path, fill = fill, stroke = stroke, lineWidth = lineWidth)
    }

    private fun parsePaint(value: String?): MoleculePaint? {
      if (value == null || value == "none") return null
      val clean = value.trim().removePrefix("#").lowercase()
      if (clean == "fff" || clean == "ffffff") return null
      val hex =
          if (clean.length == SHORT_HEX_LENGTH) clean.map { "$it$it" }.joinToString("") else clean
      val num = hex.toLongOrNull(HEX_RADIX) ?: return null
      if (num == 0L) return MoleculePaint.Foreground
      val r = ((num shr TWO_BYTE_SHIFT) and BYTE_MASK.toLong()) / COLOR_COMPONENT_MAX
      val g = ((num shr FULL_BYTE_SHIFT) and BYTE_MASK.toLong()) / COLOR_COMPONENT_MAX
      val b = (num and BYTE_MASK.toLong()) / COLOR_COMPONENT_MAX
      return MoleculePaint.Rgb(r, g, b)
    }

    private class PathCursor(private val tokens: List<String>) {
      var idx = 0

      /** The next number; a command letter is left for the main loop. */
      fun nextNum(): Float? = tokens.getOrNull(idx)?.toFloatOrNull()?.also { idx++ }

      fun hasMore(): Boolean = idx < tokens.size

      fun peek(): String = tokens[idx]

      fun advance() {
        idx++
      }
    }

    private class PathState {
      val path = Path()
      var curX = 0f
      var curY = 0f
      var startX = 0f
      var startY = 0f
      var cmd = 'M'

      // Control point of the previous curve, reflected by the smooth S/T commands.
      var ctrlX = 0f
      var ctrlY = 0f
      var lastCurve = ' '
    }

    private fun parseSvgPath(d: String): Path? {
      return try {
        val tokens =
            Regex("""[MmLlHhVvCcSsQqTtZz]|-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
                .findAll(d)
                .map { it.value }
                .toList()

        val cursor = PathCursor(tokens)
        val state = PathState()

        while (cursor.hasMore()) {
          val token = cursor.peek()
          if (token.length == 1 && token[0].isLetter()) {
            state.cmd = token[0]
            cursor.advance()
            if (state.cmd == 'Z' || state.cmd == 'z') {
              state.path.close()
              state.curX = state.startX
              state.curY = state.startY
            } else {
              executePathCommand(cursor, state)
            }
          } else {
            executePathCommand(cursor, state)
          }
        }
        state.path
      } catch (e: Exception) {
        Log.w("MoleculeDrawing", "Failed to parse SVG path: $d", e)
        null
      }
    }

    private fun executePathCommand(cursor: PathCursor, state: PathState) {
      val rel = state.cmd.isLowerCase()
      val command = state.cmd.uppercaseChar()
      val before = cursor.idx
      when (command) {
        'M' -> executeMoveTo(cursor, state, rel)
        'L' -> executeLineTo(cursor, state, rel)
        'H' -> executeHorizontal(cursor, state, rel)
        'V' -> executeVertical(cursor, state, rel)
        'C' -> executeCubic(cursor, state, rel, smooth = false)
        'S' -> executeCubic(cursor, state, rel, smooth = true)
        'Q' -> executeQuadratic(cursor, state, rel, smooth = false)
        'T' -> executeQuadratic(cursor, state, rel, smooth = true)
        'A' -> executeArc(cursor, state, rel)
        else -> cursor.advance()
      }
      if (command != 'C' && command != 'S' && command != 'Q' && command != 'T') {
        state.lastCurve = ' '
      }
      // A malformed command that consumed nothing would otherwise loop forever.
      if (cursor.idx == before && cursor.hasMore()) cursor.advance()
    }

    /** Reflection of the last control point, or the current point if [kind] didn't precede. */
    private fun reflectedControl(state: PathState, kind: Char): Pair<Float, Float> =
        if (state.lastCurve == kind) {
          2 * state.curX - state.ctrlX to 2 * state.curY - state.ctrlY
        } else {
          state.curX to state.curY
        }

    private fun executeQuadratic(
        cursor: PathCursor,
        state: PathState,
        rel: Boolean,
        smooth: Boolean,
    ) {
      val dx = if (rel) state.curX else 0f
      val dy = if (rel) state.curY else 0f
      val (x1, y1) =
          if (smooth) {
            reflectedControl(state, 'Q')
          } else {
            (cursor.nextNum() ?: return) + dx to (cursor.nextNum() ?: return) + dy
          }
      val x = (cursor.nextNum() ?: return) + dx
      val y = (cursor.nextNum() ?: return) + dy
      state.path.quadraticTo(x1, y1, x, y)
      state.ctrlX = x1
      state.ctrlY = y1
      state.lastCurve = 'Q'
      state.curX = x
      state.curY = y
    }

    // ponytail: arcs are drawn as straight lines to their end point; CDK only uses them for
    // the odd charge ring. Convert to cubic segments if a structure ever looks wrong.
    private fun executeArc(cursor: PathCursor, state: PathState, rel: Boolean) {
      repeat(ARC_FLAG_PARAMS) { cursor.nextNum() ?: return }
      val x = (cursor.nextNum() ?: return) + (if (rel) state.curX else 0f)
      val y = (cursor.nextNum() ?: return) + (if (rel) state.curY else 0f)
      state.path.lineTo(x, y)
      state.curX = x
      state.curY = y
    }

    private fun executeMoveTo(cursor: PathCursor, state: PathState, rel: Boolean) {
      val nx = cursor.nextNum() ?: return
      val ny = cursor.nextNum() ?: return
      val x = nx + (if (rel) state.curX else 0f)
      val y = ny + (if (rel) state.curY else 0f)
      state.path.moveTo(x, y)
      state.curX = x
      state.curY = y
      state.startX = x
      state.startY = y
      state.cmd = if (rel) 'l' else 'L'
    }

    private fun executeLineTo(cursor: PathCursor, state: PathState, rel: Boolean) {
      val nx = cursor.nextNum() ?: return
      val ny = cursor.nextNum() ?: return
      val x = nx + (if (rel) state.curX else 0f)
      val y = ny + (if (rel) state.curY else 0f)
      state.path.lineTo(x, y)
      state.curX = x
      state.curY = y
    }

    private fun executeHorizontal(cursor: PathCursor, state: PathState, rel: Boolean) {
      val nx = cursor.nextNum() ?: return
      val x = nx + (if (rel) state.curX else 0f)
      state.path.lineTo(x, state.curY)
      state.curX = x
    }

    private fun executeVertical(cursor: PathCursor, state: PathState, rel: Boolean) {
      val ny = cursor.nextNum() ?: return
      val y = ny + (if (rel) state.curY else 0f)
      state.path.lineTo(state.curX, y)
      state.curY = y
    }

    private fun executeCubic(cursor: PathCursor, state: PathState, rel: Boolean, smooth: Boolean) {
      val (x1, y1) =
          if (smooth) {
            reflectedControl(state, 'C')
          } else {
            (cursor.nextNum() ?: return) + (if (rel) state.curX else 0f) to
                (cursor.nextNum() ?: return) + (if (rel) state.curY else 0f)
          }
      val x2 = (cursor.nextNum() ?: return) + (if (rel) state.curX else 0f)
      val y2 = (cursor.nextNum() ?: return) + (if (rel) state.curY else 0f)
      val x = (cursor.nextNum() ?: return) + (if (rel) state.curX else 0f)
      val y = (cursor.nextNum() ?: return) + (if (rel) state.curY else 0f)
      state.path.cubicTo(x1, y1, x2, y2, x, y)
      state.ctrlX = x2
      state.ctrlY = y2
      state.lastCurve = 'C'
      state.curX = x
      state.curY = y
    }
  }
}
