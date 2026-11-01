package com.reps.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Smooth line chart with a soft gradient fill. Draws nothing meaningful with fewer than 2 points. */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlightIndices: Set<Int> = emptySet(),
    highlightColor: Color = MaterialTheme.colorScheme.tertiary,
    showDots: Boolean = true,
    strokeWidth: Float = 3f
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val padX = 6.dp.toPx()
        val padY = 8.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val stepX = if (values.size > 1) w / (values.size - 1) else 0f
        val points = values.mapIndexed { i, v ->
            Offset(padX + i * stepX, padY + h - ((v - min) / range) * h)
        }
        // grid lines
        for (g in 0..2) {
            val y = padY + h * g / 2f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (points.size == 1) {
            drawCircle(color, radius = 5.dp.toPx(), center = points[0].copy(x = size.width / 2))
            return@Canvas
        }
        val path = Path()
        val fill = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) { path.moveTo(p.x, p.y); fill.moveTo(p.x, size.height); fill.lineTo(p.x, p.y) } else {
                val prev = points[i - 1]
                val cx = (prev.x + p.x) / 2
                path.cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
                fill.cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
            }
        }
        fill.lineTo(points.last().x, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)), startY = padY, endY = size.height))
        drawPath(path, color, style = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showDots) {
            points.forEachIndexed { i, p ->
                val hl = i in highlightIndices
                drawCircle(if (hl) highlightColor else color, radius = (if (hl) 5f else 3.5f).dp.toPx(), center = p)
                if (hl) drawCircle(Color.Black.copy(alpha = 0.6f), radius = 2.dp.toPx(), center = p)
            }
        }
    }
}

/** Tiny inline trend line. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        if (values.size < 2) {
            if (values.size == 1) drawCircle(color, 3.dp.toPx(), Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val min = values.min(); val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pad = 3.dp.toPx()
        val h = size.height - pad * 2
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, pad + h - ((v - min) / range) * h) }
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), pts.last())
    }
}

/** Simple rounded bar chart with labels under each bar. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlightLast: Boolean = true
) {
    val muted = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (values.isEmpty()) return@Canvas
            val max = values.max().takeIf { it > 0f } ?: 1f
            val n = values.size
            val gap = 6.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val hgt = (v / max) * (size.height - 4.dp.toPx())
                val left = i * (barW + gap)
                val top = size.height - hgt
                val c = if (v <= 0f) muted else if (highlightLast && i == n - 1) color else color.copy(alpha = 0.55f)
                val drawH = if (v <= 0f) 3.dp.toPx() else hgt
                drawRoundRect(
                    color = c,
                    topLeft = Offset(left, if (v <= 0f) size.height - drawH else top),
                    size = Size(barW, drawH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach { l ->
                Text(
                    l,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChartPlaceholder(text: String, modifier: Modifier = Modifier) {
    Column(modifier.height(120.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
