package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

sealed class MoleculePaint {
  data object Foreground : MoleculePaint()
  data class Rgb(val red: Float, val green: Float, val blue: Float) : MoleculePaint()

  fun resolve(foregroundColor: Color): Color = when (this) {
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
    private val cache = mutableMapOf<String, MoleculeDrawing>()

    fun parse(svg: String): MoleculeDrawing? {
      cache[svg]?.let { return it }
      return try {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(svg)) }

        var viewBox: Rect? = null
        val shapes = mutableListOf<MoleculeShape>()
        var eventType = parser.eventType

        data class Style(
            val fill: MoleculePaint? = MoleculePaint.Foreground,
            val stroke: MoleculePaint? = null,
            val lineWidth: Float = 1f,
        )

        val styles = mutableListOf(Style())

        while (eventType != XmlPullParser.END_DOCUMENT) {
          if (eventType == XmlPullParser.START_TAG) {
            val name = parser.name.lowercase()
            val parentStyle = styles.last()

            val fillAttr = parser.getAttributeValue(null, "fill")
            val strokeAttr = parser.getAttributeValue(null, "stroke")
            val widthAttr = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull()

            val fill = if (fillAttr != null) parsePaint(fillAttr) else parentStyle.fill
            val stroke = if (strokeAttr != null) parsePaint(strokeAttr) else parentStyle.stroke
            val lineWidth = widthAttr ?: parentStyle.lineWidth

            styles.add(Style(fill, stroke, lineWidth))

            when (name) {
              "svg" -> {
                val vb = parser.getAttributeValue(null, "viewBox")
                if (vb != null) {
                  val nums = vb.split(Regex("[ ,]+")).mapNotNull { it.toFloatOrNull() }
                  if (nums.size == 4 && nums[2] > 0 && nums[3] > 0) {
                    viewBox = Rect(nums[0], nums[1], nums[0] + nums[2], nums[1] + nums[3])
                  }
                }
              }
              "line" -> {
                val x1 = parser.getAttributeValue(null, "x1")?.toFloatOrNull() ?: 0f
                val y1 = parser.getAttributeValue(null, "y1")?.toFloatOrNull() ?: 0f
                val x2 = parser.getAttributeValue(null, "x2")?.toFloatOrNull() ?: 0f
                val y2 = parser.getAttributeValue(null, "y2")?.toFloatOrNull() ?: 0f
                val path = Path().apply {
                  moveTo(x1, y1)
                  lineTo(x2, y2)
                }
                shapes.add(MoleculeShape(path, fill = null, stroke = stroke ?: MoleculePaint.Foreground, lineWidth = lineWidth))
              }
              "rect" -> {
                val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                val w = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 0f
                val h = parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: 0f
                if (w > 0 && h > 0) {
                  val path = Path().apply { addRect(Rect(x, y, x + w, y + h)) }
                  shapes.add(MoleculeShape(path, fill = fill, stroke = stroke, lineWidth = lineWidth))
                }
              }
              "circle" -> {
                val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
                val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
                val r = parser.getAttributeValue(null, "r")?.toFloatOrNull() ?: 0f
                if (r > 0) {
                  val path = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
                  shapes.add(MoleculeShape(path, fill = fill, stroke = stroke, lineWidth = lineWidth))
                }
              }
              "polygon", "polyline" -> {
                val pts = parser.getAttributeValue(null, "points")
                    ?.split(Regex("[ ,]+"))
                    ?.mapNotNull { it.toFloatOrNull() }
                if (pts != null && pts.size >= 4) {
                  val path = Path().apply {
                    moveTo(pts[0], pts[1])
                    for (i in 2 until pts.size step 2) {
                      if (i + 1 < pts.size) lineTo(pts[i], pts[i + 1])
                    }
                    if (name == "polygon") close()
                  }
                  shapes.add(MoleculeShape(path, fill = if (name == "polygon") fill else null, stroke = stroke, lineWidth = lineWidth))
                }
              }
              "path" -> {
                val d = parser.getAttributeValue(null, "d")
                if (!d.isNullOrBlank()) {
                  val path = parseSvgPath(d)
                  if (path != null) {
                    shapes.add(MoleculeShape(path, fill = fill, stroke = stroke, lineWidth = lineWidth))
                  }
                }
              }
            }
          } else if (eventType == XmlPullParser.END_TAG) {
            if (styles.size > 1) styles.removeAt(styles.lastIndex)
          }
          eventType = parser.next()
        }

        val vb = viewBox ?: Rect(0f, 0f, 300f, 300f)
        val drawing = MoleculeDrawing(vb, shapes)
        cache[svg] = drawing
        drawing
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }

    private fun parsePaint(value: String?): MoleculePaint? {
      if (value == null || value == "none") return null
      val clean = value.trim().removePrefix("#").lowercase()
      if (clean == "fff" || clean == "ffffff") return null
      val hex = if (clean.length == 3) clean.map { "$it$it" }.joinToString("") else clean
      val num = hex.toLongOrNull(16) ?: return null
      if (num == 0L) return MoleculePaint.Foreground
      val r = ((num shr 16) and 0xFF) / 255f
      val g = ((num shr 8) and 0xFF) / 255f
      val b = (num and 0xFF) / 255f
      return MoleculePaint.Rgb(r, g, b)
    }

    private fun parseSvgPath(d: String): Path? {
      return try {
        val tokens = Regex("""[MmLlHhVvCcSsQqTtZz]|-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
            .findAll(d)
            .map { it.value }
            .toList()

        var idx = 0
        val path = Path()
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var cmd = 'M'

        fun nextNum(): Float? = if (idx < tokens.size) tokens[idx++].toFloatOrNull() else null

        while (idx < tokens.size) {
          val token = tokens[idx]
          if (token.length == 1 && token[0].isLetter()) {
            cmd = token[0]
            idx++
            if (cmd == 'Z' || cmd == 'z') {
              path.close()
              curX = startX
              curY = startY
              continue
            }
          }

          val rel = cmd.isLowerCase()
          when (cmd.uppercaseChar()) {
            'M' -> {
              val x = (nextNum() ?: break) + (if (rel) curX else 0f)
              val y = (nextNum() ?: break) + (if (rel) curY else 0f)
              path.moveTo(x, y)
              curX = x
              curY = y
              startX = x
              startY = y
              cmd = if (rel) 'l' else 'L'
            }
            'L' -> {
              val x = (nextNum() ?: break) + (if (rel) curX else 0f)
              val y = (nextNum() ?: break) + (if (rel) curY else 0f)
              path.lineTo(x, y)
              curX = x
              curY = y
            }
            'H' -> {
              val x = (nextNum() ?: break) + (if (rel) curX else 0f)
              path.lineTo(x, curY)
              curX = x
            }
            'V' -> {
              val y = (nextNum() ?: break) + (if (rel) curY else 0f)
              path.lineTo(curX, y)
              curY = y
            }
            'C' -> {
              val x1 = (nextNum() ?: break) + (if (rel) curX else 0f)
              val y1 = (nextNum() ?: break) + (if (rel) curY else 0f)
              val x2 = (nextNum() ?: break) + (if (rel) curX else 0f)
              val y2 = (nextNum() ?: break) + (if (rel) curY else 0f)
              val x = (nextNum() ?: break) + (if (rel) curX else 0f)
              val y = (nextNum() ?: break) + (if (rel) curY else 0f)
              path.cubicTo(x1, y1, x2, y2, x, y)
              curX = x
              curY = y
            }
            else -> idx++
          }
        }
        path
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }
  }
}
