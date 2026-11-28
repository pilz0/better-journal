package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.graphics.Paint
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.text.TextLayoutResult
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
import foo.pilz.freaklog.ui.graph.paint.compileComposeScene
import foo.pilz.freaklog.ui.graph.paint.drawCompiledScene
import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import foo.pilz.freaklog.ui.graph.scene.MarkerRole
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.graph.scene.builders.buildAxisLabels
import foo.pilz.freaklog.ui.graph.scene.builders.buildTimelineScene
import foo.pilz.freaklog.ui.graph.style.GraphStyle
import foo.pilz.freaklog.ui.graph.style.LocalGraphStyle
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.getDurationText
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.getShortTimeText
import foo.pilz.freaklog.ui.utils.rememberHaptic
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private val RATING_LINES: Map<ShulginRatingOption, List<String>> =
    ShulginRatingOption.entries.associateWith { it.verticalSign.split("\n") }

private class CachedBpTick(
    val y: Float,
    val layoutResult: TextLayoutResult,
)

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
    vitalSeries: List<VitalSeries> = emptyList(),
) {
  val isDarkTheme = isSystemInDarkTheme()
  val density = LocalDensity.current
  val graphStyle = LocalGraphStyle.current

  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val surfaceColor = MaterialTheme.colorScheme.surface
  val cardColor = MaterialTheme.colorScheme.surfaceContainerLow

  val axisLabelSize = MaterialTheme.typography.labelMedium.fontSize
  val axisLabelTextPaint =
      remember(density, graphStyle.axisLabelColor) {
        Paint().apply {
          color = graphStyle.axisLabelColor.toArgb()
          textAlign = Paint.Align.CENTER
          textSize = density.run { axisLabelSize.toPx() }
        }
      }

  val dragTimeTextSize = MaterialTheme.typography.titleMedium
  val textMeasurer = rememberTextMeasurer()

  val ratingSize = MaterialTheme.typography.labelLarge.fontSize
  val ratingTextPaint =
      remember(density, onSurfaceColor) {
        Paint().apply {
          color = onSurfaceColor.toArgb()
          textAlign = Paint.Align.CENTER
          textSize = density.run { ratingSize.toPx() }
        }
      }

  val currentTimeState = remember { mutableStateOf(Instant.now()) }

  if (isShowingCurrentTime) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
      currentTimeState.value = Instant.now()
    }
    LaunchedEffect(Unit) {
      while (true) {
        delay(5000L.milliseconds)
        currentTimeState.value = Instant.now()
      }
    }
  }

  val dragPointState = remember { mutableStateOf<Offset?>(null) }
  val verticalDistanceFromFinger = remember(density) { density.run { 60.dp.toPx() } }
  val reusablePath = remember { Path() }

  val scene =
      remember(model, graphStyle, isDarkTheme) {
        buildTimelineScene(model.timelineGroups(isDarkTheme), model.widthInSeconds, graphStyle)
      }

  val heartRatePolylines =
      remember(heartRateSamples, model) {
        buildHeartRatePolylines(heartRateSamples, model.startTime, model.widthInSeconds)
      }

  val vitalPolylines =
      remember(vitalSeries, model) {
        vitalSeries.map { series ->
          series.kind.color to
              buildVitalPolylines(
                  series.samples,
                  model.startTime,
                  model.widthInSeconds,
                  series.kind.minDeviation,
              )
        }
      }

  val bloodPressurePolylines =
      remember(bloodPressureReadings, model) {
        buildBloodPressurePolylines(bloodPressureReadings, model.startTime, model.widthInSeconds)
      }

  val sleepLines =
      remember(sleepSamples, model, showSleepStages) {
        SleepHealthConnect.buildGraphShapes(
            sleepSamples,
            model.startTime,
            model.widthInSeconds,
            withSleepStages = showSleepStages,
        )
      }

  val flattenedSleepStages =
      remember(sleepSamples) {
        SleepHealthConnect.flattenSleepSessions(sleepSamples)
      }

  // Haptic feedback for satisfying timeline scrubbing
  val performHaptic = rememberHaptic()
  val lastHapticX = remember { mutableFloatStateOf(0f) }
  val hapticThreshold = 15f

  Box(
      modifier =
          modifier
              .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                      performHaptic(HapticType.HEAVY_CLICK)
                      lastHapticX.floatValue = startOffset.x
                    },
                    onHorizontalDrag = { change, _ ->
                      change.consume()
                      dragPointState.value = change.position
                      if (abs(change.position.x - lastHapticX.floatValue) >= hapticThreshold) {
                        performHaptic(HapticType.TIMELINE_SCRUB)
                        lastHapticX.floatValue = change.position.x
                      }
                    },
                    onDragEnd = {
                      dragPointState.value = null
                      performHaptic(HapticType.CLICK)
                    },
                    onDragCancel = { dragPointState.value = null },
                )
              }
              .drawWithCache {
                val canvasWithLabelsHeight = size.height
                val labelsHeight = axisLabelSize.toPx()
                val canvasWidth = size.width
                val pixelsPerSec =
                    if (model.widthInSeconds > 0f) canvasWidth / model.widthInSeconds else 0f
                val strokeWidth = 2.dp.toPx()
                val insetBottom = labelsHeight + strokeWidth
                val canvasHeightWithVerticalLine =
                    (canvasWithLabelsHeight - insetBottom).coerceAtLeast(0f)

                val compiledScene =
                    compileComposeScene(
                        scene = scene,
                        style = graphStyle,
                        density = this,
                        width = canvasWidth,
                        height = canvasHeightWithVerticalLine,
                        textMeasurer = textMeasurer,
                    )
                val axisLabels =
                    buildAxisLabels(
                        startTime = model.startTime,
                        widthInSeconds = model.widthInSeconds,
                        canvasWidthPx = canvasWidth,
                    )
                val vitalStroke =
                    Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                val vitalDotRadius = 2.dp.toPx()
                val bpStroke = vitalStroke
                val bpOutlineStroke =
                    Stroke(
                        width = 1.5.dp.toPx() + 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                val bpDotOuterRadius = 3.dp.toPx()
                val bpDotInnerRadius = 2.dp.toPx()
                val bpTickColor = onSurfaceColor.copy(alpha = 0.4f)
                val bpTickLength = 6.dp.toPx()
                val bpTickStrokeWidth = 1.dp.toPx()
                val bpLabelPadding = 8.dp.toPx()
                val bpTickStyle = TextStyle(fontSize = 9.sp, color = graphStyle.axisLabelColor)
                val cachedBpTicks =
                    if (bloodPressurePolylines.hasScale) {
                      roundBloodPressureTicks(
                              bloodPressurePolylines.minPressure,
                              bloodPressurePolylines.maxPressure,
                          )
                          .map { tick ->
                            val y =
                                bloodPressureYFraction(
                                    tick.toDouble(),
                                    bloodPressurePolylines.minPressure,
                                    bloodPressurePolylines.maxPressure,
                                ) * canvasHeightWithVerticalLine
                            CachedBpTick(
                                y = y,
                                layoutResult =
                                    textMeasurer.measure(tick.toString(), style = bpTickStyle),
                            )
                          }
                    } else {
                      emptyList()
                    }
                val markerStrokeWidth = graphStyle.markerStrokeWidth.toPx()
                val timedNoteDash =
                    PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 2, strokeWidth * 2))
                val sleepLineHeight = SLEEP_GRAPH_LINE_HEIGHT.dp.toPx()
                val sleepLineRoundingRadius = SLEEP_GRAPH_LINE_ROUNDING.dp.toPx()
                val sleepLineRounding =
                    CornerRadius(sleepLineRoundingRadius, sleepLineRoundingRadius)
                val sleepZeroRounding = CornerRadius(0f, 0f)
                val sleepLinePos =
                    canvasWithLabelsHeight -
                        labelsHeight -
                        SLEEP_GRAPH_LINE_MARGIN_BOTTOM.dp.toPx() -
                        sleepLineHeight

                onDrawBehind {
                  inset(left = 0f, top = 0f, right = 0f, bottom = insetBottom) {
                    val plotHeight = size.height
                    val marker =
                        currentTimeMarker(
                            model,
                            currentTimeState.value,
                            isShowingCurrentTime,
                            graphStyle,
                        )
                    drawVitalPolylines(
                        polylines = heartRatePolylines,
                        color = graphStyle.heartRateColor,
                        canvasHeight = plotHeight,
                        path = reusablePath,
                        stroke = vitalStroke,
                        dotRadius = vitalDotRadius,
                    )
                    vitalPolylines.forEach { (color, polylines) ->
                      drawVitalPolylines(
                          polylines = polylines,
                          color = color,
                          canvasHeight = plotHeight,
                          path = reusablePath,
                          stroke = vitalStroke,
                          dotRadius = vitalDotRadius,
                      )
                    }

                    drawCompiledScene(compiledScene)
                    if (marker != null) {
                      val markerX = marker.xFraction * size.width
                      drawLine(
                          color = Color(marker.color),
                          start = Offset(markerX, plotHeight),
                          end = Offset(markerX, 0f),
                          strokeWidth = markerStrokeWidth,
                          cap = StrokeCap.Round,
                      )
                    }

                    drawBloodPressurePolyline(
                        points = bloodPressurePolylines.diastolic,
                        color = graphStyle.bloodPressureDiastolicColor,
                        outlineColor = cardColor,
                        canvasHeight = plotHeight,
                        path = reusablePath,
                        stroke = bpStroke,
                        outlineStroke = bpOutlineStroke,
                        outerDotRadius = bpDotOuterRadius,
                        innerDotRadius = bpDotInnerRadius,
                    )
                    drawBloodPressurePolyline(
                        points = bloodPressurePolylines.systolic,
                        color = graphStyle.bloodPressureSystolicColor,
                        outlineColor = cardColor,
                        canvasHeight = plotHeight,
                        path = reusablePath,
                        stroke = bpStroke,
                        outlineStroke = bpOutlineStroke,
                        outerDotRadius = bpDotOuterRadius,
                        innerDotRadius = bpDotInnerRadius,
                    )
                    cachedBpTicks.forEach { tick ->
                      drawLine(
                          color = bpTickColor,
                          start = Offset(0f, tick.y),
                          end = Offset(bpTickLength, tick.y),
                          strokeWidth = bpTickStrokeWidth,
                      )
                      drawText(
                          textLayoutResult = tick.layoutResult,
                          topLeft =
                              Offset(
                                  bpLabelPadding,
                                  tick.y - tick.layoutResult.size.height / 2f,
                              ),
                      )
                    }

                    sleepLines.forEach { line ->
                      reusablePath.reset()
                      reusablePath.addRoundRect(
                          RoundRect(
                              rect =
                                  Rect(
                                      offset = Offset(line.x1 * size.width, sleepLinePos),
                                      size =
                                          Size((line.x2 - line.x1) * size.width, sleepLineHeight),
                                  ),
                              topLeft =
                                  if (line.roundLeft) sleepLineRounding else sleepZeroRounding,
                              bottomLeft =
                                  if (line.roundLeft) sleepLineRounding else sleepZeroRounding,
                              topRight =
                                  if (line.roundRight) sleepLineRounding else sleepZeroRounding,
                              bottomRight =
                                  if (line.roundRight) sleepLineRounding else sleepZeroRounding,
                          )
                      )
                      drawPath(
                          path = reusablePath,
                          color = line.color,
                          alpha = 0.9f,
                      )
                    }

                    model.dataForRatings.forEach { dataForOneRating ->
                      drawRating(
                          startTime = model.startTime,
                          ratingTime = dataForOneRating.time,
                          pixelsPerSec = pixelsPerSec,
                          canvasHeightOuter = plotHeight,
                          rating = dataForOneRating.option,
                          textPaint = ratingTextPaint,
                          strokeWidth = strokeWidth,
                      )
                    }

                    model.timedNotes.forEach { dataForOneTimedNote ->
                      val noteColor = dataForOneTimedNote.color.getComposeColor(isDarkTheme)
                      drawTimedNote(
                          startTime = model.startTime,
                          noteTime = dataForOneTimedNote.time,
                          color = noteColor,
                          pixelsPerSec = pixelsPerSec,
                          canvasHeightOuter = plotHeight,
                          strokeWidth = strokeWidth,
                          pathEffect = timedNoteDash,
                      )
                    }

                    dragPointState.value?.let {
                      drawDragPointLineAndTimeLabel(
                          it,
                          canvasWidth,
                          plotHeight,
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
                          flattenedSleepStages = flattenedSleepStages,
                          showSleepStages = showSleepStages,
                          vitalSeries = vitalSeries,
                      )
                    }
                  }

                  drawContext.canvas.nativeCanvas.apply {
                    axisLabels.forEach { axisLabel ->
                      drawText(
                          axisLabel.label,
                          axisLabel.xFraction * canvasWidth,
                          canvasWithLabelsHeight,
                          axisLabelTextPaint,
                      )
                    }
                  }
                }
              }
  )
}

/** Thin lines for heart rate and the other vitals; an isolated sample becomes a dot. */
@Suppress("LongParameterList")
private fun DrawScope.drawVitalPolylines(
    polylines: List<List<Vec2>>,
    color: Color,
    canvasHeight: Float,
    path: Path,
    stroke: Stroke,
    dotRadius: Float,
) {
  polylines.forEach { polyline ->
    if (polyline.size == 1) {
      val point = polyline.single()
      drawCircle(
          color = color,
          radius = dotRadius,
          center = Offset(point.x * size.width, point.y * canvasHeight),
      )
      return@forEach
    }
    path.reset()
    polyline.forEachIndexed { index, point ->
      val x = point.x * size.width
      val y = point.y * canvasHeight
      if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = color,
        style = stroke,
    )
  }
}

private fun roundBloodPressureTicks(min: Double, max: Double): List<Int> {
  if (!min.isFinite() || !max.isFinite() || max <= min) return emptyList()
  val first = min.roundToInt().toLong()
  return (0 until 32).mapNotNull { index ->
    val value = first + index * 25L
    value.takeIf { it <= max && it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
  }
}

@Suppress("LongParameterList")
private fun DrawScope.drawBloodPressurePolyline(
    points: List<Vec2>,
    color: Color,
    outlineColor: Color,
    canvasHeight: Float,
    path: Path,
    stroke: Stroke,
    outlineStroke: Stroke,
    outerDotRadius: Float,
    innerDotRadius: Float,
) {
  if (points.isEmpty()) return
  if (points.size == 1) {
    val center = Offset(points.single().x * size.width, points.single().y * canvasHeight)
    drawCircle(color = outlineColor, radius = outerDotRadius, center = center)
    drawCircle(color = color, radius = innerDotRadius, center = center)
    return
  }
  path.reset()
  points.forEachIndexed { index, point ->
    val x = point.x * size.width
    val y = point.y * canvasHeight
    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  drawPath(
      path = path,
      color = outlineColor,
      style = outlineStroke,
  )
  drawPath(
      path = path,
      color = color,
      style = stroke,
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
  if (currentTime.isBefore(model.startTime) || currentTime.isAfter(endTime)) return null
  val seconds = Duration.between(model.startTime, currentTime).seconds.toFloat()
  return GraphPrimitive.VerticalMarker(
      xFraction = seconds / model.widthInSeconds,
      color = style.currentTimeColor.toArgb(),
      role = MarkerRole.CurrentTime,
  )
}

@Suppress("LongParameterList")
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
    flattenedSleepStages: List<SleepStageSample>,
    showSleepStages: Boolean,
    vitalSeries: List<VitalSeries>,
) {
  val horizontallyLimitedDragPoint =
      Offset(
          x = dragPoint.x.coerceIn(0f, canvasWidth),
          y = dragPoint.y,
      )

  drawVerticalDragLine(
      lineColor,
      horizontallyLimitedDragPoint,
      canvasHeightWithVerticalLine,
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
      flattenedSleepStages,
      showSleepStages,
      vitalSeries,
  )
}

@Suppress("LongParameterList")
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
    flattenedSleepStages: List<SleepStageSample>,
    showSleepStages: Boolean,
    vitalSeries: List<VitalSeries>,
) {
  val secondsAtDragPoint = horizontallyLimitedDragPoint.x / pixelsPerSec
  val timeAtDragPoint = model.startTime.plusSeconds(secondsAtDragPoint.toLong())
  val textHeight = max(0f, horizontallyLimitedDragPoint.y - dragPointToTextVerticalDistance)

  val timeOnlyLabel =
      when (timeDisplayOption) {
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
              toInstant = timeAtDragPoint,
          ) + " in"
        }

        TimeDisplayOption.TIME_BETWEEN -> timeAtDragPoint.getShortTimeText()
        TimeDisplayOption.REGULAR -> timeAtDragPoint.getShortTimeText()
      }

  val bpmAtDragPoint = nearestBpm(heartRateSamples, timeAtDragPoint)
  val timeLabel =
      if (bpmAtDragPoint != null) {
        "$timeOnlyLabel, $bpmAtDragPoint bpm"
      } else {
        timeOnlyLabel
      }

  val sleepStageAtDraPoint =
      SleepHealthConnect.sleepStateAt(
          flattenedSleepStages,
          timeAtDragPoint,
          withSleepStages = showSleepStages,
      )

  val vitalsLabel =
      vitalSeries
          .mapNotNull { series ->
            nearestVitalValue(series.samples, timeAtDragPoint)?.let(series.kind::format)
          }
          .joinToString(" · ")
          .ifEmpty { null }

  val fullLabel =
      listOfNotNull(
              listOfNotNull(timeLabel, sleepStageAtDraPoint?.stage?.displayText)
                  .joinToString(" · "),
              vitalsLabel,
          )
          .joinToString("\n")

  val labelStyle = dragTimeTextSize.copy(color = textColor, fontSize = 16.sp)
  val measuredText =
      textMeasurer.measure(
          fullLabel,
          style = labelStyle,
      )
  val textSize = measuredText.size
  val padding = 12.dp.toPx()
  val rectSize =
      textSize.toSize().let {
        it.copy(width = it.width + padding * 2, height = it.height + padding * 1.5f)
      }

  val rectTopLeft =
      Offset(
          x =
              (horizontallyLimitedDragPoint.x - rectSize.width / 2).coerceIn(
                  0f,
                  max(0f, canvasWidth - rectSize.width),
              ),
          y =
              (textHeight - rectSize.height / 2).coerceIn(
                  0f,
                  max(0f, canvasHeightWithVerticalLine - rectSize.height),
              ),
      )

  val cornerRadius = 12.dp.toPx()
  drawRoundRect(
      color = backgroundColor,
      size = rectSize,
      topLeft = rectTopLeft,
      cornerRadius = CornerRadius(x = cornerRadius, y = cornerRadius),
  )

  drawText(
      textLayoutResult = measuredText,
      topLeft =
          Offset(
              x = rectTopLeft.x + (rectSize.width - textSize.width) / 2,
              y = rectTopLeft.y + (rectSize.height - textSize.height) / 2,
          ),
  )
}

private fun DrawScope.drawVerticalDragLine(
    dragLineColor: Color,
    horizontallyLimitedDragPoint: Offset,
    canvasHeightWithVerticalLine: Float,
) {
  drawLine(
      color = dragLineColor,
      start = Offset(x = horizontallyLimitedDragPoint.x, y = canvasHeightWithVerticalLine),
      end = Offset(x = horizontallyLimitedDragPoint.x, y = 0f),
      strokeWidth = 3.dp.toPx(),
      cap = StrokeCap.Round,
  )
}

fun DrawScope.drawRating(
    startTime: Instant,
    ratingTime: Instant,
    pixelsPerSec: Float,
    canvasHeightOuter: Float,
    rating: ShulginRatingOption,
    textPaint: Paint,
    strokeWidth: Float = 2.dp.toPx(),
) {
  val timeStartInSec = Duration.between(startTime, ratingTime).seconds
  val timeStartX = timeStartInSec * pixelsPerSec
  val lineHeight = textPaint.textSize
  val lines = RATING_LINES[rating] ?: rating.verticalSign.split("\n")
  val signHeight = lines.size * lineHeight
  val verticalLineHeight = (canvasHeightOuter - 2 * lineHeight - signHeight) / 2

  drawLine(
      color = Color.Gray,
      start = Offset(x = timeStartX, y = 0f),
      end = Offset(x = timeStartX, y = verticalLineHeight),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
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
      cap = StrokeCap.Round,
  )
}

fun DrawScope.drawTimedNote(
    startTime: Instant,
    noteTime: Instant,
    pixelsPerSec: Float,
    canvasHeightOuter: Float,
    color: Color,
    strokeWidth: Float = 2.dp.toPx(),
    pathEffect: PathEffect =
        PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 2, strokeWidth * 2)),
) {
  val timeStartInSec = Duration.between(startTime, noteTime).seconds
  val timeStartX = timeStartInSec * pixelsPerSec

  drawLine(
      color = color,
      start = Offset(x = timeStartX, y = 0f),
      end = Offset(x = timeStartX, y = canvasHeightOuter),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
      pathEffect = pathEffect,
  )
}
