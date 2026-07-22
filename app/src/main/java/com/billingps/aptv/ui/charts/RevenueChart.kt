package com.billingps.aptv.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.aptv.ui.theme.*
import kotlin.math.ceil
import kotlin.math.max

data class BarData(val label: String, val value: Float, val color: Color = NeonGreen)
data class PieData(val label: String, val value: Float, val color: Color)

private val chartColors = listOf(
    Color(0xFF39FF14), Color(0xFF00E5FF), Color(0xFFBB00FF),
    Color(0xFFFF6B35), Color(0xFFFFD700), Color(0xFFFF4081),
    Color(0xFF00C9A7), Color(0xFF7C4DFF),
)

@Composable
fun RevenueBarChart(
    data: List<BarData>,
    title: String = "",
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val maxVal = max(data.maxOf { it.value }, 1f)
    val density = LocalDensity.current

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        val barSpacing = 8.dp
        val barWidth = with(density) { (360f / data.size - barSpacing.toPx()).coerceAtMost(48.dp.toPx()).coerceAtLeast(16.dp.toPx()) }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(180.dp)
        ) {
            val chartBottom = size.height - 24.dp.toPx()
            val chartTop = 8.dp.toPx()
            val chartHeight = chartBottom - chartTop
            val stepX = size.width / data.size

            data.forEachIndexed { i, item ->
                val barH = (item.value / maxVal) * chartHeight
                val x = i * stepX + (stepX - barWidth) / 2f
                val y = chartBottom - barH

                drawRect(color = item.color, topLeft = Offset(x, y), size = Size(barWidth, barH))
                drawRect(color = Color.White.copy(alpha = 0.15f), topLeft = Offset(x, y), size = Size(barWidth, barH.coerceAtMost(4.dp.toPx())))

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#9CA3AF")
                        textSize = 10.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val label = if (item.label.length > 5) item.label.take(5) else item.label
                    drawText(label, x + barWidth / 2f, chartBottom + 16.dp.toPx(), paint)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Rp0", style = MaterialTheme.typography.bodySmall, color = TextDim)
            Text(fmtChartRp(maxVal.toInt()), style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

@Composable
fun RevenuePieChart(
    data: List<PieData>,
    title: String = "",
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val total = data.fold(0.0) { acc, item -> acc + item.value.toDouble() }

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val diameter = size.minDimension
                val radius = diameter / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                var startAngle = -90f

                data.forEachIndexed { i, item ->
                    val sweep = ((item.value.toDouble() / total) * 360.0).toFloat()
                    val color = if (i < chartColors.size) chartColors[i] else chartColors[i % chartColors.size]

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(diameter, diameter),
                    )
                    startAngle += sweep
                }

                drawCircle(color = DarkBackground, radius = radius * 0.5f, center = center)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val sorted = data.sortedByDescending { it.value }
                sorted.forEachIndexed { i, item ->
                    val pct = ((item.value.toDouble() / total) * 100.0).toInt()
                    val color = if (i < chartColors.size) chartColors[i] else chartColors[i % chartColors.size]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(10.dp).padding(end = 6.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(color = color)
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${item.label}: $pct%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopPackagesList(
    data: List<Pair<String, Int>>,
    title: String = "",
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val maxVal = max(data.maxOf { it.second.toDouble() }, 1.0)

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        data.take(5).forEachIndexed { i, (name, total) ->
            val fraction = (total.toDouble() / maxVal).toFloat()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.width(20.dp))
                Text(name, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(fmtChartRp(total), style = MaterialTheme.typography.bodySmall, color = NeonGreen, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
            }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).padding(start = 20.dp)) {
                val barColor = if (i < chartColors.size) chartColors[i] else chartColors[i % chartColors.size]
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.3f),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                    drawRoundRect(
                        color = barColor,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun fmtChartRp(amount: Int): String {
    if (amount >= 1_000_000) return "Rp${amount / 1_000_000}jt"
    if (amount >= 1_000) return "Rp${amount / 1_000}rb"
    return "Rp$amount"
}
