package com.cryptomacro.app.ui.chart

/**
 * BEGINNER: We draw the chart ourselves on a Canvas (no TradingView SDK).
 * scale = pinch zoom (1..12). offsetIdx = which candle is on the left after panning.
 * Long-press sets a crosshair (OHLC text above the chart).
 * If candles.size < 2 we still occupy the height and show "Loading chart…".
 */
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.cryptomacro.app.domain.model.Candle
import com.cryptomacro.app.domain.model.ChartKind
import com.cryptomacro.app.domain.model.ShemitahBand
import com.cryptomacro.app.domain.util.ema
import com.cryptomacro.app.domain.util.sma
import com.cryptomacro.app.ui.theme.Bear
import com.cryptomacro.app.ui.theme.Bull
import com.cryptomacro.app.ui.theme.Gold
import com.cryptomacro.app.ui.theme.Teal
import kotlin.math.max
import kotlin.math.min

data class ChartCrosshair(val candle: Candle, val x: Float, val y: Float)

@Composable
fun PriceChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    kind: ChartKind = ChartKind.CANDLESTICK,
    showSma: Boolean = true,
    showEma: Boolean = false,
    showVolume: Boolean = true,
    shemitahBands: List<ShemitahBand> = emptyList(),
    onCrosshair: (ChartCrosshair?) -> Unit = {},
) {
    if (candles.size < 2) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (candles.isEmpty()) "Loading chart…" else "Not enough data for this timeframe",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }
    val outline = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    var scale by remember(candles.size, candles.firstOrNull()?.time, candles.lastOrNull()?.time) { mutableFloatStateOf(1f) }
    var offsetIdx by remember(candles.size, candles.firstOrNull()?.time) { mutableFloatStateOf(0f) }
    var cross by remember { mutableStateOf<ChartCrosshair?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(candles) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 12f)
                    val vis = candles.size / scale
                    offsetIdx = (offsetIdx - pan.x / size.width * vis).coerceIn(0f, (candles.size - vis).coerceAtLeast(0f))
                }
            }
            .pointerInput(candles, scale, offsetIdx) {
                detectTapGestures(
                    onLongPress = { pos ->
                        val visCount = (candles.size / scale).toInt().coerceIn(2, candles.size)
                        val start = offsetIdx.toInt().coerceIn(0, candles.size - visCount)
                        val axisW = 64f
                        val plotW = (size.width - axisW).coerceAtLeast(1f)
                        val i = start + (((pos.x - axisW).coerceAtLeast(0f) / plotW) * visCount).toInt().coerceIn(0, visCount - 1)
                        val c = candles.getOrNull(i) ?: return@detectTapGestures
                        val ch = ChartCrosshair(c, pos.x, pos.y)
                        cross = ch
                        onCrosshair(ch)
                    },
                    onTap = {
                        cross = null
                        onCrosshair(null)
                    },
                )
            }
            .pointerInput(candles) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val vis = candles.size / scale
                    offsetIdx = (offsetIdx - drag.x / size.width * vis).coerceIn(0f, (candles.size - vis).coerceAtLeast(0f))
                }
            },
    ) {
        val visCount = (candles.size / scale).toInt().coerceIn(2, candles.size)
        val start = offsetIdx.toInt().coerceIn(0, candles.size - visCount)
        val view = candles.subList(start, start + visCount)
        val axisW = 64f
        val volH = if (showVolume) size.height * 0.16f else 0f
        val chartH = size.height - volH - 4f
        val plotW = (size.width - axisW).coerceAtLeast(1f)
        val minP = view.minOf { it.low }
        val maxP = view.maxOf { it.high }
        val pad = (maxP - minP) * 0.08f + 1e-9
        val lo = minP - pad
        val hi = maxP + pad
        val range = (hi - lo).coerceAtLeast(1e-9)
        fun x(i: Int) = axisW + (i + 0.5f) / view.size * plotW
        fun y(p: Double) = ((hi - p) / range * chartH).toFloat()
        val slot = plotW / view.size

        shemitahBands.forEach { band ->
            val i0 = view.indexOfFirst { it.time >= band.startEpochSec }
            val i1 = view.indexOfLast { it.time <= band.endEpochSec }
            if (i0 >= 0 && i1 >= i0) {
                val left = x(i0) - slot / 2
                val right = x(i1) + slot / 2
                drawRect(Gold.copy(alpha = 0.08f), Offset(left, 0f), Size(right - left, chartH))
            }
        }

        if (kind == ChartKind.AREA || kind == ChartKind.LINE) {
            val path = Path()
            view.forEachIndexed { i, c ->
                val px = x(i)
                val py = y(c.close)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            if (kind == ChartKind.AREA) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(x(view.lastIndex), chartH)
                    lineTo(x(0), chartH)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(Teal.copy(0.35f), Color.Transparent), endY = chartH))
            }
            drawPath(path, Teal, style = Stroke(width = 3f))
        } else {
            view.forEachIndexed { i, c ->
                val cx = x(i)
                val color = if (c.close >= c.open) Bull else Bear
                drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), strokeWidth = 2f)
                val top = y(max(c.open, c.close))
                val bot = y(min(c.open, c.close))
                val body = (bot - top).coerceAtLeast(2f)
                drawRect(color, Offset(cx - slot * 0.32f, top), Size((slot * 0.64f).coerceAtLeast(2f), body))
            }
        }

        val closes = view.map { it.time to it.close }
        if (showSma && view.size > 20) {
            val line = sma(closes, 20)
            val p = Path()
            var started = false
            line.forEach { (t, v) ->
                val idx = view.indexOfFirst { it.time == t }
                if (idx < 0) return@forEach
                if (!started) {
                    p.moveTo(x(idx), y(v))
                    started = true
                } else p.lineTo(x(idx), y(v))
            }
            if (started) drawPath(p, Gold, style = Stroke(2.4f))
        }
        if (showEma && view.size > 12) {
            val line = ema(closes, 12)
            val p = Path()
            var started = false
            line.forEach { (t, v) ->
                val idx = view.indexOfFirst { it.time == t }
                if (idx < 0) return@forEach
                if (!started) {
                    p.moveTo(x(idx), y(v))
                    started = true
                } else p.lineTo(x(idx), y(v))
            }
            if (started) drawPath(p, Teal, style = Stroke(2.4f))
        }

        if (showVolume) {
            val maxV = view.maxOf { it.volume ?: 0.0 }.coerceAtLeast(1.0)
            view.forEachIndexed { i, c ->
                val h = ((c.volume ?: 0.0) / maxV * volH).toFloat()
                drawRect(
                    (if (c.close >= c.open) Bull else Bear).copy(0.45f),
                    Offset(x(i) - slot * 0.28f, size.height - h),
                    Size((slot * 0.56f).coerceAtLeast(1f), h),
                )
            }
        }

        // Dedicated left gutter so axis labels never sit on candles.
        drawRect(surface, Offset(0f, 0f), Size(axisW - 4f, size.height))
        val style = TextStyle(color = muted, fontSize = 9.sp)
        val hiLabel = compactAxis(hi)
        val loLabel = compactAxis(lo)
        val hiLayout = measurer.measure(hiLabel, style)
        val loLayout = measurer.measure(loLabel, style)
        drawText(hiLayout, topLeft = Offset(4f, 4f))
        drawText(loLayout, topLeft = Offset(4f, (chartH - loLayout.size.height - 4f).coerceAtLeast(4f)))

        cross?.let { ch ->
            val cx = ch.x.coerceIn(axisW, size.width)
            val cy = y(ch.candle.close)
            drawLine(outline, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.2f)
            drawLine(outline, Offset(axisW, cy), Offset(size.width, cy), strokeWidth = 1.2f)
            drawCircle(Gold, 5f, Offset(cx, cy))
        }
    }
}

private fun compactAxis(value: Double): String = when {
    kotlin.math.abs(value) >= 1e12 -> "%.2fT".format(value / 1e12)
    kotlin.math.abs(value) >= 1e9 -> "%.2fB".format(value / 1e9)
    kotlin.math.abs(value) >= 1e6 -> "%.2fM".format(value / 1e6)
    kotlin.math.abs(value) >= 1000 -> "%,.0f".format(value)
    kotlin.math.abs(value) >= 1 -> "%.2f".format(value)
    else -> "%.4f".format(value)
}

@Composable
fun Sparkline(candles: List<Candle>, modifier: Modifier = Modifier, positive: Boolean) {
    val color = if (positive) Bull else Bear
    Canvas(modifier) {
        if (candles.size < 2) return@Canvas
        val minP = candles.minOf { it.close }
        val maxP = candles.maxOf { it.close }
        val range = (maxP - minP).coerceAtLeast(1e-9)
        val path = Path()
        candles.forEachIndexed { i, c ->
            val x = i / (candles.lastIndex.toFloat()) * size.width
            val y = ((maxP - c.close) / range * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5f))
    }
}
