package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.core.graphics.createBitmap
import foo.pilz.freaklog.ui.graph.paint.paintScene
import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import foo.pilz.freaklog.ui.graph.scene.MarkerRole
import foo.pilz.freaklog.ui.graph.scene.builders.buildTimelineScene
import foo.pilz.freaklog.ui.graph.style.DefaultGraphStyle
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import java.time.Duration
import java.time.Instant

object TimelineBitmapRenderer {

    fun render(
        model: AllTimelinesModel,
        widthPx: Int,
        heightPx: Int,
        currentTime: Instant,
        density: Float,
        isDarkTheme: Boolean,
    ): Bitmap {
        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        val baseScene = buildTimelineScene(model.timelineGroups(isDarkTheme), model.widthInSeconds, DefaultGraphStyle)
        val marker = currentTimeMarker(model, currentTime)
        val scene = if (marker != null) {
            baseScene.copy(primitives = baseScene.primitives + marker)
        } else {
            baseScene
        }
        canvas.paintScene(scene, DefaultGraphStyle, Density(density))
        return bitmap
    }

    private fun currentTimeMarker(model: AllTimelinesModel, currentTime: Instant): GraphPrimitive.VerticalMarker? {
        if (model.widthInSeconds <= 0f) return null
        val endTime = model.startTime.plusSeconds(model.widthInSeconds.toLong())
        if (!model.startTime.isBefore(currentTime) || !endTime.isAfter(currentTime)) return null
        val seconds = Duration.between(model.startTime, currentTime).seconds.toFloat()
        return GraphPrimitive.VerticalMarker(
            xFraction = seconds / model.widthInSeconds,
            color = DefaultGraphStyle.currentTimeColor.toArgb(),
            role = MarkerRole.CurrentTime,
        )
    }
}
