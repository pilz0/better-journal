package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.rememberHaptic
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRatingOption
import foo.pilz.freaklog.ui.graph.paint.paintScene
import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import foo.pilz.freaklog.ui.graph.scene.MarkerRole
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.graph.scene.builders.buildAxisLabels
import foo.pilz.freaklog.ui.graph.scene.builders.buildTimelineScene
import foo.pilz.freaklog.ui.graph.style.GraphStyle
import foo.pilz.freaklog.ui.graph.style.LocalGraphStyle
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.getDurationText
import foo.pilz.freaklog.ui.utils.getShortTimeText
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AllTimelines(
    model: AllTimelinesModel,
    isShowingCurrentTime: Boolean,
    timeDisplayOption: TimeDisplayOption,
    modifier: Modifier = Modifier,
    heartRateSamples: List<HeartRateSample> = emptyList(),
    sleepSamples: List<SleepSessionSample> = emptyList(),
    showSleepStages: Boolean = false,
    bloodPressureReadings: List<BloodPressureReading> = emptyList(),
) {
    val isDarkTheme = isSystemInDarkTheme()
    val density = LocalDensity.current
    val graphStyle = LocalGraphStyle.current

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow

    val axisLabelSize = MaterialTheme.typography.labelMedium.fontSize
    val axisLabelTextPaint = remember(density, graphStyle.axisLabelColor) {
        Paint().apply {
            color = graphStyle.axisLabelColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = density.run { axisLabelSize.toPx() }
        }
    }

    val dragTimeTextSize = MaterialTheme.typography.titleMedium
    val textMeasurer = rememberTextMeasurer()

    val ratingSize = MaterialTheme.typography.labelLarge.fontSize
    val ratingTextPaint = remember(density, onSurfaceColor) {
        Paint().apply {
            color = onSurfaceColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = density.run { ratingSize.toPx() }
        }
    }

    var currentTime by remember { mutableStateOf(Instant.now()) }

    if (isShowingCurrentTime) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            currentTime = Instant.now()
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(5000L.milliseconds)
                currentTime = Instant.now()
            }
        }
    }

    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    val verticalDistanceFromFinger = LocalDensity.current.run { 60.dp.toPx() }

    val scene = remember(model, graphStyle, isDarkTheme) {
        buildTimelineScene(model.timelineGroups(isDarkTheme), model.widthInSeconds, graphStyle)
    }

    val heartRatePolylines = remember(heartRateSamples, model) {
        buildHeartRatePolylines(heartRateSamples, model.startTime, model.widthInSeconds)
    }

    val bloodPressurePolylines = remember(bloodPressureReadings, model) {
        buildBloodPressurePolylines(bloodPressureReadings, model.startTime, model.widthInSeconds)
    }

    val sleepLines = remember(sleepSamples, model) {
        SleepHealthConnect.buildGraphShapes(
            sleepSamples,
            model.startTime,
            model.widthInSeconds,
            withSleepStages = showSleepStages
        )
    }


    // Haptic feedback for satisfying timeline scrubbing
    val performHaptic = rememberHaptic()
    var lastHapticX by remember { mutableFloatStateOf(0f) }
    val hapticThreshold = 15f

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { startOffset ->
                    performHaptic(HapticType.HEAVY_CLICK)
                    lastHapticX = startOffset.x
                },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    dragPoint = change.position
                    if (abs(change.position.x - lastHapticX) >= hapticThreshold) {
                        performHaptic(HapticType.TIMELINE_SCRUB)
                        lastHapticX = change.position.x
                    }
                },
                onDragEnd = {
                    dragPoint = null
                    performHaptic(HapticType.CLICK)
                },
                onDragCancel = { dragPoint = null }
            )
        }
    ) {
        val canvasWithLabelsHeight = size.height
        val labelsHeight = axisLabelSize.toPx()
        val canvasWidth = size.width
        val pixelsPerSec = if (model.widthInSeconds > 0f) canvasWidth / model.widthInSeconds else 0f
        val strokeWidth = 2.dp.toPx()

        inset(left = 0f, top = 0f, right = 0f, bottom = labelsHeight + strokeWidth) {
            val canvasHeightWithVerticalLine = size.height

            val marker = currentTimeMarker(model, currentTime, isShowingCurrentTime, graphStyle)
            val sceneToPaint = if (marker != null) {
                scene.copy(primitives = scene.primitives + marker)
            } else {
                scene
            }
            heartRatePolylines.forEach { polyline ->
                if (polyline.size == 1) {
                    val point = polyline.single()
                    drawCircle(
                        color = graphStyle.heartRateColor,
                        radius = 2.dp.toPx(),
                        center = Offset(
                            point.x * size.width,
                            point.y * canvasHeightWithVerticalLine
                        )
                    )
                    return@forEach
                }
                val path = Path()
                polyline.forEachIndexed { index, point ->
                    val x = point.x * size.width
                    val y = point.y * canvasHeightWithVerticalLine
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = graphStyle.heartRateColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            paintScene(sceneToPaint, graphStyle, textMeasurer)

            drawBloodPressurePolyline(
                points = bloodPressurePolylines.diastolic,
                color = graphStyle.bloodPressureDiastolicColor,
                outlineColor = cardColor,
                canvasHeight = canvasHeightWithVerticalLine,
            )
            drawBloodPressurePolyline(
                points = bloodPressurePolylines.systolic,
                color = graphStyle.bloodPressureSystolicColor,
                outlineColor = cardColor,
                canvasHeight = canvasHeightWithVerticalLine,
            )
            if (bloodPressurePolylines.hasScale) {
                drawBloodPressureScale(
                    polylines = bloodPressurePolylines,
                    canvasHeight = canvasHeightWithVerticalLine,
                    textMeasurer = textMeasurer,
                    tickColor = onSurfaceColor,
                    labelColor = graphStyle.axisLabelColor,
                )
            }

            sleepLines.forEach { line ->
                drawPath(
                    Path().apply {
                        val lineHeight = SLEEP_GRAPH_LINE_HEIGHT.dp.toPx()
                        val lineRoundingRadius = SLEEP_GRAPH_LINE_ROUNDING.dp.toPx()
                        val linePos = canvasWithLabelsHeight - labelsHeight - SLEEP_GRAPH_LINE_MARGIN_BOTTOM.dp.toPx() - lineHeight

                        val lineRounding = CornerRadius(lineRoundingRadius, lineRoundingRadius)
                        val zeroRounding = CornerRadius(0f, 0f)
                        addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    offset = Offset(line.x1 * size.width, linePos),
                                    size = Size((line.x2 - line.x1) * size.width, lineHeight
                                    )
                                ),
                                topLeft = if (line.roundLeft)      lineRounding else zeroRounding,
                                bottomLeft = if (line.roundLeft)   lineRounding else zeroRounding,
                                topRight = if (line.roundRight)    lineRounding else zeroRounding,
                                bottomRight = if (line.roundRight) lineRounding else zeroRounding,
                            )
                        )
                    },
                    color = line.color,
                    alpha = 0.9f,
                )
            }

            model.dataForRatings.forEach { dataForOneRating ->
                drawRating(
                    startTime = model.startTime,
                    ratingTime = dataForOneRating.time,
                    pixelsPerSec = pixelsPerSec,
                    canvasHeightOuter = canvasHeightWithVerticalLine,
                    rating = dataForOneRating.option,
                    textPaint = ratingTextPaint
                )
            }

            model.timedNotes.forEach { dataForOneTimedNote ->
                val noteColor = dataForOneTimedNote.color.getComposeColor(isDarkTheme)

                drawTimedNote(
                    startTime = model.startTime,
                    noteTime = dataForOneTimedNote.time,
                    color = noteColor,
                    pixelsPerSec = pixelsPerSec,
                    canvasHeightOuter = canvasHeightWithVerticalLine,
                )
            }

            dragPoint?.let {
                drawDragPointLineAndTimeLabel(
                    it,
                    canvasWidth,
                    canvasHeightWithVerticalLine,
                    pixelsPerSec,
                    model,
                    verticalDistanceFromFinger,
                    textMeasurer,
                    dragTimeTextSize,
                    timeDisplayOption,
                    lineColor = onSurfaceColor,
                    backgroundColor = onSurfaceColor,
                    textColor = surfaceColor,
                    heartRateSamples = heartRateSamples,
                    sleepSamples = sleepSamples,
                    showSleepStages = showSleepStages,
                )
            }
        }

        val axisLabels = buildAxisLabels(
            startTime = model.startTime,
            widthInSeconds = model.widthInSeconds,
            canvasWidthPx = canvasWidth,
        )
        drawContext.canvas.nativeCanvas.apply {
            axisLabels.forEach { axisLabel ->
                drawText(
                    axisLabel.label,
                    axisLabel.xFraction * canvasWidth,
                    canvasWithLabelsHeight,
                    axisLabelTextPaint
                )
            }
        }
    }
}

private fun DrawScope.drawBloodPressureScale(
    polylines: BloodPressurePolyLines,
    canvasHeight: Float,
    textMeasurer: TextMeasurer,
    tickColor: Color,
    labelColor: Color,
) {
    val ticks = roundBloodPressureTicks(polylines.minPressure, polylines.maxPressure)
    val tickLength = 6.dp.toPx()
    val labelPadding = 8.dp.toPx()
    ticks.forEach { tick ->
        val y = bloodPressureYFraction(tick.toDouble(), polylines.minPressure, polylines.maxPressure) * canvasHeight
        drawLine(
            color = tickColor.copy(alpha = 0.4f),
            start = Offset(0f, y),
            end = Offset(tickLength, y),
            strokeWidth = 1.dp.toPx(),
        )
        val measured = textMeasurer.measure(
            tick.toString(),
            style = TextStyle(fontSize = 9.sp, color = labelColor)
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(labelPadding, y - measured.size.height / 2f),
        )
    }
}

private fun roundBloodPressureTicks(min: Double, max: Double): List<Int> {
    if (max <= min) return emptyList()
    val ticks = mutableListOf<Int>()
    var value = min.roundToInt()
    while (value <= max) {
        ticks.add(value)
        value += 25
    }
    return ticks
}

private fun DrawScope.drawBloodPressurePolyline(
    points: List<Vec2>,
    color: Color,
    outlineColor: Color,
    canvasHeight: Float,
) {
    if (points.isEmpty()) return
    val lineWidth = 1.5.dp.toPx()
    val outlineWidth = lineWidth + 2.dp.toPx()
    if (points.size == 1) {
        val center = Offset(points.single().x * size.width, points.single().y * canvasHeight)
        drawCircle(color = outlineColor, radius = 3.dp.toPx(), center = center)
        drawCircle(color = color, radius = 2.dp.toPx(), center = center)
        return
    }
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * canvasHeight
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = outlineColor,
        style = Stroke(width = outlineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun currentTimeMarker(
    model: AllTimelinesModel,
    currentTime: Instant,
    isShowingCurrentTime: Boolean,
    style: GraphStyle,
): GraphPrimitive.VerticalMarker? {
    if (!isShowingCurrentTime || model.widthInSeconds <= 0f) return null
    val endTime = model.startTime.plusSeconds(model.widthInSeconds.toLong())
    if (!model.startTime.isBefore(currentTime) || !endTime.isAfter(currentTime)) return null
    val seconds = Duration.between(model.startTime, currentTime).seconds.toFloat()
    return GraphPrimitive.VerticalMarker(
        xFraction = seconds / model.widthInSeconds,
        color = style.currentTimeColor.toArgb(),
        role = MarkerRole.CurrentTime,
    )
}

private fun DrawScope.drawDragPointLineAndTimeLabel(
    dragPoint: Offset,
    canvasWidth: Float,
    canvasHeightWithVerticalLine: Float,
    pixelsPerSec: Float,
    model: AllTimelinesModel,
    dragPointToTextVerticalDistance: Float,
    textMeasurer: TextMeasurer,
    dragTimeTextSize: TextStyle,
    timeDisplayOption: TimeDisplayOption,
    lineColor: Color,
    backgroundColor: Color,
    textColor: Color,
    heartRateSamples: List<HeartRateSample>,
    sleepSamples: List<SleepSessionSample>,
    showSleepStages: Boolean,
) {
    val horizontallyLimitedDragPoint = Offset(
        x = dragPoint.x.coerceIn(0f, canvasWidth),
        y = dragPoint.y
    )

    drawVerticalDragLine(
        lineColor,
        horizontallyLimitedDragPoint,
        canvasHeightWithVerticalLine
    )

    drawDragTimeLabelWithBackground(
        horizontallyLimitedDragPoint,
        pixelsPerSec,
        model,
        dragPointToTextVerticalDistance,
        textMeasurer,
        canvasWidth,
        canvasHeightWithVerticalLine,
        backgroundColor,
        dragTimeTextSize,
        textColor,
        timeDisplayOption,
        heartRateSamples,
        sleepSamples,
        showSleepStages
    )
}

private fun DrawScope.drawDragTimeLabelWithBackground(
    horizontallyLimitedDragPoint: Offset,
    pixelsPerSec: Float,
    model: AllTimelinesModel,
    dragPointToTextVerticalDistance: Float,
    textMeasurer: TextMeasurer,
    canvasWidth: Float,
    canvasHeightWithVerticalLine: Float,
    backgroundColor: Color,
    dragTimeTextSize: TextStyle,
    textColor: Color,
    timeDisplayOption: TimeDisplayOption,
    heartRateSamples: List<HeartRateSample>,
    sleepSamples: List<SleepSessionSample>,
    showSleepStages: Boolean,
) {
    val secondsAtDragPoint = horizontallyLimitedDragPoint.x / pixelsPerSec
    val timeAtDragPoint = model.startTime.plusSeconds(secondsAtDragPoint.toLong())
    val textHeight = max(0f, horizontallyLimitedDragPoint.y - dragPointToTextVerticalDistance)

    val timeOnlyLabel = when (timeDisplayOption) {
        TimeDisplayOption.RELATIVE_TO_NOW -> {
            val now = Instant.now()
            val isInPast = timeAtDragPoint < now
            if (isInPast) {
                getDurationText(fromInstant = timeAtDragPoint, toInstant = now) + " ago"
            } else {
                "in " + getDurationText(fromInstant = timeAtDragPoint, toInstant = now)
            }
        }

        TimeDisplayOption.RELATIVE_TO_START -> {
            getDurationText(
                fromInstant = model.startTime,
                toInstant = timeAtDragPoint
            ) + " in"
        }

        TimeDisplayOption.TIME_BETWEEN -> timeAtDragPoint.getShortTimeText()
        TimeDisplayOption.REGULAR -> timeAtDragPoint.getShortTimeText()
    }

    val bpmAtDragPoint = nearestBpm(heartRateSamples, timeAtDragPoint)
    val timeLabel = if (bpmAtDragPoint != null) {
        "$timeOnlyLabel, $bpmAtDragPoint bpm"
    } else {
        timeOnlyLabel
    }

    val sleepStageAtDraPoint = SleepHealthConnect.sleepStateAt(
        SleepHealthConnect.flattenSleepSessions(sleepSamples),
        timeAtDragPoint,
        withSleepStages = showSleepStages,

    )

    val fullLabel = listOfNotNull(timeLabel, sleepStageAtDraPoint?.stage?.displayText)
        .joinToString(" · ")

    val measuredText = textMeasurer.measure(
        fullLabel,
        style = TextStyle(fontSize = 16.sp)
    )
    val textSize = measuredText.size
    val padding = 12.dp.toPx()
    val rectSize = textSize.toSize().let {
        it.copy(width = it.width + padding * 2, height = it.height + padding * 1.5f)
    }

    val rectTopLeft = Offset(
        x = (horizontallyLimitedDragPoint.x - rectSize.width / 2).coerceIn(
            0f,
            canvasWidth - rectSize.width
        ),
        y = (textHeight - rectSize.height / 2).coerceIn(
            0f,
            canvasHeightWithVerticalLine - rectSize.height
        )
    )

    val cornerRadius = 12.dp.toPx()
    drawRoundRect(
        color = backgroundColor,
        size = rectSize,
        topLeft = rectTopLeft,
        cornerRadius = CornerRadius(x = cornerRadius, y = cornerRadius)
    )

    drawText(
        textMeasurer,
        text = fullLabel,
        topLeft = Offset(
            x = rectTopLeft.x + (rectSize.width - textSize.width) / 2,
            y = rectTopLeft.y + (rectSize.height - textSize.height) / 2
        ),
        style = dragTimeTextSize.copy(color = textColor, fontSize = 16.sp),
    )
}

private fun DrawScope.drawVerticalDragLine(
    dragLineColor: Color,
    horizontallyLimitedDragPoint: Offset,
    canvasHeightWithVerticalLine: Float
) {
    drawLine(
        color = dragLineColor,
        start = Offset(x = horizontallyLimitedDragPoint.x, y = canvasHeightWithVerticalLine),
        end = Offset(x = horizontallyLimitedDragPoint.x, y = 0f),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawRating(
    startTime: Instant,
    ratingTime: Instant,
    pixelsPerSec: Float,
    canvasHeightOuter: Float,
    rating: ShulginRatingOption,
    textPaint: Paint
) {
    val timeStartInSec = Duration.between(startTime, ratingTime).seconds
    val timeStartX = timeStartInSec * pixelsPerSec
    val lineHeight = textPaint.textSize
    val lines = rating.verticalSign.split("\n")
    val signHeight = lines.size * lineHeight
    val verticalLineHeight = (canvasHeightOuter - 2 * lineHeight - signHeight) / 2
    val strokeWidth = 2.dp.toPx()

    drawLine(
        color = Color.Gray,
        start = Offset(x = timeStartX, y = 0f),
        end = Offset(x = timeStartX, y = verticalLineHeight),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    var y = verticalLineHeight + 1.5f * lineHeight
    drawContext.canvas.nativeCanvas.apply {
        for (line in lines) {
            drawText(line, timeStartX, y, textPaint)
            y += lineHeight
        }
    }

    drawLine(
        color = Color.Gray,
        start = Offset(x = timeStartX, y = y),
        end = Offset(x = timeStartX, y = canvasHeightOuter),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawTimedNote(
    startTime: Instant,
    noteTime: Instant,
    pixelsPerSec: Float,
    canvasHeightOuter: Float,
    color: Color,
) {
    val timeStartInSec = Duration.between(startTime, noteTime).seconds
    val timeStartX = timeStartInSec * pixelsPerSec
    val strokeWidth = 2.dp.toPx()

    drawLine(
        color = color,
        start = Offset(x = timeStartX, y = 0f),
        end = Offset(x = timeStartX, y = canvasHeightOuter),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 2, strokeWidth * 2))
    )
}