package foo.pilz.freaklog.ui.tabs.search.anodyne

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoleculeStructureViewTest {

  @Test
  fun tallStructureStaysWithinBoundsAndNeverDrawsOverAdjacentText() {
    // Aspect ratio 80:120 (0.67, like Cocaine): narrower than cardWidth / 180dp.
    val tallSvg =
        """<svg viewBox="0 0 80 120"><rect x="0" y="0" width="80" height="120" fill="#ff0000"/></svg>"""

    var structureBounds = Rect.Zero
    var maxAllowedHeightPx = 0f

    val bitmap =
        renderInScrollableCard(widthDp = 340, heightDp = 600) {
          maxAllowedHeightPx = with(LocalDensity.current) { 180.dp.toPx() }
          Box(Modifier.fillMaxWidth().height(160.dp))
          MoleculeStructureView(
              svg = tallSvg,
              substanceName = "Cocaine",
              modifier = Modifier.onGloballyPositioned { structureBounds = it.boundsInRoot() },
          )
          Box(Modifier.fillMaxWidth().height(160.dp))
        }

    assertTrue(
        "Structure height ${structureBounds.height} exceeded max $maxAllowedHeightPx",
        structureBounds.height <= maxAllowedHeightPx + 1f,
    )

    val topEnd = structureBounds.top.roundToInt()
    val bottomStart = structureBounds.bottom.roundToInt()

    assertTrue(
        "Expected molecule to render red pixels inside its bounds $structureBounds",
        hasRedPixel(bitmap, topEnd, bottomStart),
    )
    assertFalse(
        "Molecule bled above its top boundary ($topEnd) into the summary text region",
        hasRedPixel(bitmap, 0, (topEnd - 1).coerceAtLeast(0)),
    )
    assertFalse(
        "Molecule bled below its bottom boundary ($bottomStart) into the text region below",
        hasRedPixel(bitmap, bottomStart + 1, bitmap.height),
    )
  }

  @Test
  fun wideStructureShrinksHeightToMatchAspectRatio() {
    // Aspect ratio 340:68 (5:1): at 340dp width, natural height is 68dp + 16dp vertical padding =
    // 84dp.
    val wideSvg =
        """<svg viewBox="0 0 340 68"><rect x="0" y="0" width="340" height="68" fill="#ff0000"/></svg>"""

    var structureBounds = Rect.Zero
    var expectedMaxPx = 0f

    renderInScrollableCard(widthDp = 340, heightDp = 400) {
      expectedMaxPx = with(LocalDensity.current) { 90.dp.toPx() }
      MoleculeStructureView(
          svg = wideSvg,
          substanceName = "WideChain",
          modifier = Modifier.onGloballyPositioned { structureBounds = it.boundsInRoot() },
      )
    }

    assertTrue("Expected positive structure height", structureBounds.height > 0f)
    assertTrue(
        "Wide structure height ${structureBounds.height} should shrink below $expectedMaxPx",
        structureBounds.height <= expectedMaxPx,
    )
  }

  @Test
  fun pathsExtendingOutsideViewBoxAreClippedToStructureBounds() {
    val straySvg =
        """<svg viewBox="0 0 100 100"><line x1="-500" y1="-500" x2="600" y2="600" stroke="#ff0000" stroke-width="20"/></svg>"""

    var structureBounds = Rect.Zero

    val bitmap =
        renderInScrollableCard(widthDp = 340, heightDp = 600) {
          Box(Modifier.fillMaxWidth().height(160.dp))
          MoleculeStructureView(
              svg = straySvg,
              substanceName = "Rogue",
              modifier = Modifier.onGloballyPositioned { structureBounds = it.boundsInRoot() },
          )
          Box(Modifier.fillMaxWidth().height(160.dp))
        }

    val topEnd = structureBounds.top.roundToInt()
    val bottomStart = structureBounds.bottom.roundToInt()

    assertFalse(
        "Out-of-viewBox path bled above top boundary ($topEnd)",
        hasRedPixel(bitmap, 0, (topEnd - 1).coerceAtLeast(0)),
    )
    assertFalse(
        "Out-of-viewBox path bled below bottom boundary ($bottomStart)",
        hasRedPixel(bitmap, bottomStart + 1, bitmap.height),
    )
  }

  private fun renderInScrollableCard(
      widthDp: Int,
      heightDp: Int,
      content: @androidx.compose.runtime.Composable () -> Unit,
  ): Bitmap {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val density = activity.resources.displayMetrics.density
    val widthPx = (widthDp * density).roundToInt()
    val heightPx = (heightDp * density).roundToInt()

    activity.setContent {
      Column(modifier = Modifier.width(widthDp.dp).verticalScroll(rememberScrollState())) {
        content()
      }
    }
    shadowOf(android.os.Looper.getMainLooper()).idle()

    val rootView = activity.findViewById<View>(android.R.id.content)
    rootView.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
    )
    rootView.layout(0, 0, widthPx, heightPx)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    val bitmap = createBitmap(widthPx, heightPx)
    rootView.draw(Canvas(bitmap))
    return bitmap
  }

  private fun hasRedPixel(bitmap: Bitmap, yStart: Int, yEndExclusive: Int): Boolean {
    val clampedStart = yStart.coerceIn(0, bitmap.height)
    val clampedEnd = yEndExclusive.coerceIn(clampedStart, bitmap.height)
    if (clampedEnd <= clampedStart) return false
    val rows = clampedEnd - clampedStart
    val pixels = IntArray(bitmap.width * rows)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, clampedStart, bitmap.width, rows)
    return pixels.any { px ->
      Color.alpha(px) > 200 && Color.red(px) > 200 && Color.green(px) < 50 && Color.blue(px) < 50
    }
  }
}
