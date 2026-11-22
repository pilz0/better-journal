package foo.pilz.freaklog.ui.tabs.search.anodyne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MoleculeDrawingTest {

  @Test
  fun testParseSvgLinesAndPaths() {
    val svg =
        """<svg viewBox="0 0 100 100"><line x1="10" y1="10" x2="90" y2="90" stroke="#000000" stroke-width="2"/></svg>"""
    val drawing = MoleculeDrawing.parse(svg)
    assertNotNull(drawing)
    assertEquals(100f, drawing?.viewBox?.width ?: 0f, 0.01f)
    assertEquals(1, drawing?.shapes?.size)
  }

  @Test
  fun testParseComplexPath() {
    val svg =
        """<svg viewBox="0 0 50 50"><path d="M 10 10 L 20 20 H 40 V 40 Z" fill="#FF0000"/></svg>"""
    val drawing = MoleculeDrawing.parse(svg)
    assertNotNull(drawing)
    assertEquals(1, drawing?.shapes?.size)
    val shape = drawing?.shapes?.first()
    assertNotNull(shape?.fill)
  }

  /**
   * Regression: CDK draws atom labels with relative quadratic curves. Unsupported `q` commands used
   * to desync the relative `h`/`v` moves after them, scattering the labels.
   */
  @Test
  fun quadraticCurvesKeepLaterRelativeMovesInPlace() {
    val svg = """<svg viewBox="0 0 50 50"><path d="M10 10q10 0 10 10h5t5 5z"/></svg>"""
    val bounds = MoleculeDrawing.parse(svg)!!.shapes.single().path.getBounds()
    assertEquals(10f, bounds.left, 0.01f)
    assertEquals(10f, bounds.top, 0.01f)
    assertEquals(30f, bounds.right, 0.01f)
    assertEquals(25f, bounds.bottom, 0.01f)
  }

  @Test
  fun parsesMinifiedCdkStructure() {
    // svgo output as served in "Structure": implicit line-tos, ".7.5"-style number runs, 100% rect.
    val svg =
        """<svg viewBox="0 0 64.953 70.208"><rect width="100%" height="100%" fill="#fff"/>""" +
            """<g fill="#3050f8" stroke="#000" stroke-width=".7"><g class="mol">""" +
            """<path d="m40.317 12.677 3.843 10.555M42.69 30.383l-6.954 8.29" class="bond"/>""" +
            """<g stroke="none" class="atom"><path d="M5 5h.7.5v1z"/></g></g></g></svg>"""
    val drawing = MoleculeDrawing.parse(svg)!!
    assertEquals(2, drawing.shapes.size)
    assertEquals(6.2f, drawing.shapes[1].path.getBounds().right, 0.01f)
  }
}
