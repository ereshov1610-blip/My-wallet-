package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.domain.CategoryBreakdownItem
import com.example.domain.ChartPeriodPoint
import com.example.domain.CurrencyCode
import com.example.domain.FinanceCalculator
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.LocalFinanceColors
import kotlin.math.max

@Composable
fun IncomeExpenseBarChart(
    points: List<ChartPeriodPoint>,
    currency: CurrencyCode,
    hideBalances: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val financeColors = LocalFinanceColors.current
    var selectedIdx by remember(points) { mutableIntStateOf(points.lastIndex.coerceAtLeast(0)) }

    if (points.isEmpty()) {
        Text(
            text = strings.noDataYet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 24.dp)
        )
        return
    }

    val safeIdx = selectedIdx.coerceIn(0, points.lastIndex)
    val selected = points[safeIdx]
    val maxVal = max(
        1.0,
        points.maxOf { max(it.income, it.expense) }
    ).toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected point readout pill
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "+${FinanceCalculator.formatCurrency(selected.income, currency, hideBalances)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = financeColors.income,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "-${FinanceCalculator.formatCurrency(selected.expense, currency, hideBalances)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = financeColors.expense,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        val incomeColor = financeColors.income
        val expenseColor = financeColors.expense
        val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val step = size.width.toFloat() / points.size.coerceAtLeast(1)
                        val tapped = (offset.x / step).toInt().coerceIn(0, points.lastIndex)
                        selectedIdx = tapped
                    }
                }
        ) {
            val h = size.height
            val w = size.width
            // Grid lines
            for (i in 1..3) {
                val y = h * (i / 4f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            val groupW = w / points.size
            val barW = (groupW * 0.28f).coerceAtLeast(4.dp.toPx()).coerceAtMost(18.dp.toPx())
            val gap = (barW * 0.28f).coerceAtLeast(2.dp.toPx())

            points.forEachIndexed { idx, pt ->
                val centerX = groupW * idx + groupW / 2f
                if (idx == safeIdx) {
                    drawRoundRect(
                        color = highlightBg,
                        topLeft = Offset(groupW * idx + 2f, 0f),
                        size = Size(groupW - 4f, h),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }

                val incH = ((pt.income.toFloat() / maxVal) * (h * 0.9f)).coerceAtLeast(
                    if (pt.income > 0) 4.dp.toPx() else 0f
                )
                val expH = ((pt.expense.toFloat() / maxVal) * (h * 0.9f)).coerceAtLeast(
                    if (pt.expense > 0) 4.dp.toPx() else 0f
                )

                if (incH > 0f) {
                    drawRoundRect(
                        color = incomeColor,
                        topLeft = Offset(centerX - barW - gap / 2f, h - incH),
                        size = Size(barW, incH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                if (expH > 0f) {
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(centerX + gap / 2f, h - expH),
                        size = Size(barW, expH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = max(1, points.size / 5)
            points.forEachIndexed { index, pt ->
                if (index == 0 || index == points.lastIndex || index % step == 0) {
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == safeIdx) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WorkHoursBarChart(
    points: List<ChartPeriodPoint>,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    if (points.isEmpty()) {
        Text(
            text = strings.noDataYet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 16.dp)
        )
        return
    }
    val maxHours = max(1.0, points.maxOf { it.workHours }).toFloat()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val h = size.height
            val w = size.width
            drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1.dp.toPx())
            val groupW = w / points.size
            val barW = (groupW * 0.42f).coerceAtLeast(6.dp.toPx()).coerceAtMost(24.dp.toPx())
            points.forEachIndexed { idx, pt ->
                val centerX = groupW * idx + groupW / 2f
                val barH = ((pt.workHours.toFloat() / maxHours) * (h * 0.88f)).coerceAtLeast(
                    if (pt.workHours > 0) 4.dp.toPx() else 0f
                )
                if (barH > 0f) {
                    drawRoundRect(
                        color = BrandBlue,
                        topLeft = Offset(centerX - barW / 2f, h - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = max(1, points.size / 5)
            points.forEachIndexed { index, pt ->
                if (index == 0 || index == points.lastIndex || index % step == 0) {
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseDonutChart(
    items: List<CategoryBreakdownItem>,
    currency: CurrencyCode,
    hideBalances: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedIdx by remember(items) { mutableIntStateOf(0) }

    if (items.isEmpty()) {
        Text(
            text = strings.noDataYet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 16.dp)
        )
        return
    }

    val safeIdx = selectedIdx.coerceIn(0, items.lastIndex)
    val total = items.sumOf { it.amount }
    val selected = items[safeIdx]

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(136.dp)
        ) {
            Canvas(modifier = Modifier.size(124.dp)) {
                val strokeWidth = 20.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                var startAngle = -90f
                items.forEachIndexed { idx, item ->
                    val sweep = (item.percentage * 360f).coerceAtLeast(2f)
                    val color = Color(item.colorHex)
                    drawArc(
                        color = if (idx == safeIdx) color else color.copy(alpha = 0.72f),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = if (idx == safeIdx) strokeWidth * 1.15f else strokeWidth,
                            cap = StrokeCap.Butt
                        )
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(selected.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = FinanceCalculator.formatCurrency(total, currency, hideBalances),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(6).forEachIndexed { idx, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (idx == safeIdx) Color(item.colorHex).copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { selectedIdx = idx }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(item.colorHex))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = FinanceCalculator.formatCurrency(item.amount, currency, hideBalances),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
