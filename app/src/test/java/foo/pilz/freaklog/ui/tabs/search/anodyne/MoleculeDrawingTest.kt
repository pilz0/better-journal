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
}
