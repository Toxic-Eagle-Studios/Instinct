package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MemoryEntry
import com.example.data.model.Reminder
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreenGlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Data point representing a single day's errand & task productivity metrics.
 */
data class DailyProductivityPoint(
  val dayIndex: Int,
  val dayShortLabel: String,
  val fullDateLabel: String,
  val completedCount: Int,
  val pendingCount: Int,
  val totalTasks: Int,
  val completionRatePercent: Int,
  val topCategory: String
)

enum class ProductivityChartStyle(val label: String) {
  AREA_LINE("Area Trend"),
  GROUPED_BARS("Bars")
}

/**
 * Derives a 7-day daily productivity trend from the user's reminders and completed errands.
 */
fun computeDailyProductivityTrend(
  reminders: List<Reminder>,
  referenceTimeMillis: Long = System.currentTimeMillis()
): List<DailyProductivityPoint> {
  val dayFormatter = SimpleDateFormat("EEE", Locale.US)
  val fullDateFormatter = SimpleDateFormat("MMM d", Locale.US)

  val baseCal = Calendar.getInstance().apply {
    timeInMillis = referenceTimeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }

  return (6 downTo 0).mapIndexed { idx, daysAgo ->
    val startCal = (baseCal.clone() as Calendar).apply {
      add(Calendar.DAY_OF_YEAR, -daysAgo)
    }
    val startMillis = startCal.timeInMillis
    val endMillis = startMillis + 86_400_000L

    val matchingDayReminders = reminders.filter { rem ->
      val eventTime = (rem.updatedAt ?: rem.createdAt)?.time ?: referenceTimeMillis
      eventTime in startMillis until endMillis ||
        (daysAgo == 0 && !rem.isCompleted)
    }

    val completedOnDay = reminders.count { rem ->
      val eventTime = (rem.updatedAt ?: rem.createdAt)?.time ?: referenceTimeMillis
      rem.isCompleted && eventTime in startMillis until endMillis
    }

    val pendingOnDay = if (daysAgo == 0) {
      reminders.count { !it.isCompleted }
    } else {
      reminders.count { rem ->
        val createdTime = (rem.createdAt ?: rem.updatedAt)?.time ?: 0L
        !rem.isCompleted && createdTime in startMillis until endMillis
      }
    }

    val total = completedOnDay + pendingOnDay
    val rate = if (total > 0) {
      ((completedOnDay.toFloat() / total.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    } else {
      0
    }

    val topCat = matchingDayReminders
      .groupingBy { it.category.ifBlank { MemoryEntry.DEFAULT_CATEGORY } }
      .eachCount()
      .maxByOrNull { it.value }
      ?.key ?: MemoryEntry.DEFAULT_CATEGORY

    val dayDate = Date(startMillis)
    DailyProductivityPoint(
      dayIndex = idx,
      dayShortLabel = if (daysAgo == 0) "Today" else dayFormatter.format(dayDate),
      fullDateLabel = fullDateFormatter.format(dayDate),
      completedCount = completedOnDay,
      pendingCount = pendingOnDay,
      totalTasks = total,
      completionRatePercent = rate,
      topCategory = topCat
    )
  }
}

@Composable
fun ProductivityTrendChartCard(
  reminders: List<Reminder>,
  modifier: Modifier = Modifier
) {
  val trendPoints = remember(reminders) {
    computeDailyProductivityTrend(reminders)
  }

  var chartStyle by remember { mutableStateOf(ProductivityChartStyle.AREA_LINE) }
  var selectedIndex by remember(trendPoints) {
    mutableIntStateOf((trendPoints.size - 1).coerceAtLeast(0))
  }

  val selectedPoint = trendPoints.getOrElse(selectedIndex) {
    trendPoints.last()
  }

  val totalCompleted7d = remember(trendPoints) {
    trendPoints.sumOf { it.completedCount }
  }
  val totalTasks7d = remember(trendPoints, reminders) {
    max(reminders.size, trendPoints.sumOf { it.completedCount } + reminders.count { !it.isCompleted })
  }
  val overallCompletionRate = remember(reminders) {
    if (reminders.isEmpty()) 0
    else ((reminders.count { it.isCompleted }.toFloat() / reminders.size.toFloat()) * 100f).roundToInt()
  }
  val activeDaysCount = remember(trendPoints) {
    trendPoints.count { it.completedCount > 0 || it.totalTasks > 0 }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(
        Brush.verticalGradient(
          colors = listOf(DarkCharcoal, GraphiteCard)
        )
      )
      .border(1.dp, CyanElectric.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
      .padding(16.dp)
      .testTag("daily_briefing_productivity_chart_card"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Header & Chart Style Toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CyanElectric.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ShowChart,
            contentDescription = "Productivity Trend Chart",
            tint = CyanElectric,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "PRODUCTIVITY & COMPLETION TRENDS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = CyanElectric,
            letterSpacing = 0.8.sp
          )
          Text(
            text = "7-day errand completion velocity · Tap chart to inspect",
            fontSize = 11.sp,
            color = TextSecondary
          )
        }
      }

      // Area vs Bar Mode Switcher
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(GraphiteSurface)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(10.dp))
          .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (chartStyle == ProductivityChartStyle.AREA_LINE) {
                ElectricLime.copy(alpha = 0.2f)
              } else {
                Color.Transparent
              }
            )
            .clickable { chartStyle = ProductivityChartStyle.AREA_LINE }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("chart_style_area")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ShowChart,
              contentDescription = "Area Trend Chart",
              tint = if (chartStyle == ProductivityChartStyle.AREA_LINE) ElectricLime else TextMuted,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Area",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (chartStyle == ProductivityChartStyle.AREA_LINE) ElectricLime else TextMuted
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (chartStyle == ProductivityChartStyle.GROUPED_BARS) {
                CyanElectric.copy(alpha = 0.2f)
              } else {
                Color.Transparent
              }
            )
            .clickable { chartStyle = ProductivityChartStyle.GROUPED_BARS }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("chart_style_bars")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.BarChart,
              contentDescription = "Grouped Bar Chart",
              tint = if (chartStyle == ProductivityChartStyle.GROUPED_BARS) CyanElectric else TextMuted,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Bars",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (chartStyle == ProductivityChartStyle.GROUPED_BARS) CyanElectric else TextMuted
            )
          }
        }
      }
    }

    // 2. Summary Velocity KPI Badges
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      ChartKpiBadge(
        label = "Completion Rate",
        value = "$overallCompletionRate%",
        accent = ElectricLime,
        modifier = Modifier.weight(1f)
      )
      ChartKpiBadge(
        label = "7-Day Completed",
        value = "$totalCompleted7d / $totalTasks7d",
        accent = CyanElectric,
        modifier = Modifier.weight(1f)
      )
      ChartKpiBadge(
        label = "Active Days",
        value = "${activeDaysCount}d pulse",
        accent = AmberNotice,
        icon = Icons.Default.LocalFireDepartment,
        modifier = Modifier.weight(1f)
      )
    }

    // 3. Interactive Canvas Chart Area (uses DrawScope size.width and size.height)
    val maxChartValue = remember(trendPoints) {
      max(4, trendPoints.maxOfOrNull { max(it.totalTasks, it.completedCount) } ?: 4)
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(175.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(GraphiteSurface)
        .border(1.dp, GraphiteBorder, RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 14.dp)
        .testTag("productivity_chart_canvas")
    ) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(trendPoints) {
            detectTapGestures { tapOffset ->
              if (trendPoints.isNotEmpty() && size.width > 0) {
                val slotWidth = size.width.toFloat() / trendPoints.size.toFloat()
                val tappedIdx = (tapOffset.x / slotWidth).toInt().coerceIn(0, trendPoints.size - 1)
                selectedIndex = tappedIdx
              }
            }
          }
      ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val count = trendPoints.size
        if (count == 0 || chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val horizontalPadding = 16.dp.toPx()
        val usableWidth = (chartWidth - horizontalPadding * 2f).coerceAtLeast(1f)
        val topPadding = 10.dp.toPx()
        val bottomPadding = 10.dp.toPx()
        val usableHeight = (chartHeight - topPadding - bottomPadding).coerceAtLeast(1f)

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        // Draw 3 horizontal reference grid lines
        for (gridIdx in 0..2) {
          val fraction = gridIdx / 2f
          val y = topPadding + usableHeight * fraction
          drawLine(
            color = GraphiteBorder.copy(alpha = 0.65f),
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
          )
        }

        val stepX = if (count > 1) usableWidth / (count - 1).toFloat() else usableWidth

        fun pointX(index: Int): Float =
          horizontalPadding + index * stepX

        fun pointY(value: Int): Float {
          val normalized = (value.toFloat() / maxChartValue.toFloat()).coerceIn(0f, 1f)
          return topPadding + usableHeight * (1f - normalized)
        }

        // Draw vertical active crosshair for selected day
        val selX = pointX(selectedIndex)
        drawLine(
          color = CyanElectric.copy(alpha = 0.45f),
          start = Offset(selX, topPadding),
          end = Offset(selX, topPadding + usableHeight),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = dashEffect
        )

        if (chartStyle == ProductivityChartStyle.AREA_LINE) {
          // 1. Secondary Total Tasks Curve (CyanElectric)
          val totalPath = Path()
          trendPoints.forEachIndexed { idx, pt ->
            val x = pointX(idx)
            val y = pointY(pt.totalTasks)
            if (idx == 0) {
              totalPath.moveTo(x, y)
            } else {
              val prevX = pointX(idx - 1)
              val prevY = pointY(trendPoints[idx - 1].totalTasks)
              val ctrlX = (prevX + x) / 2f
              totalPath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
            }
          }
          drawPath(
            path = totalPath,
            color = CyanElectric.copy(alpha = 0.55f),
            style = Stroke(
              width = 2.dp.toPx(),
              cap = StrokeCap.Round,
              join = StrokeJoin.Round,
              pathEffect = dashEffect
            )
          )

          // 2. Primary Completed Errands Smooth Area & Line Curve (ElectricLime)
          val completedLinePath = Path()
          val completedAreaPath = Path()
          val baselineY = topPadding + usableHeight

          trendPoints.forEachIndexed { idx, pt ->
            val x = pointX(idx)
            val y = pointY(pt.completedCount)
            if (idx == 0) {
              completedLinePath.moveTo(x, y)
              completedAreaPath.moveTo(x, baselineY)
              completedAreaPath.lineTo(x, y)
            } else {
              val prevX = pointX(idx - 1)
              val prevY = pointY(trendPoints[idx - 1].completedCount)
              val ctrlX = (prevX + x) / 2f
              completedLinePath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
              completedAreaPath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
            }
            if (idx == count - 1) {
              completedAreaPath.lineTo(x, baselineY)
              completedAreaPath.close()
            }
          }

          drawPath(
            path = completedAreaPath,
            brush = Brush.verticalGradient(
              colors = listOf(
                ElectricLime.copy(alpha = 0.36f),
                ElectricLime.copy(alpha = 0.03f)
              ),
              startY = topPadding,
              endY = baselineY
            )
          )

          drawPath(
            path = completedLinePath,
            color = ElectricLime,
            style = Stroke(
              width = 3.dp.toPx(),
              cap = StrokeCap.Round,
              join = StrokeJoin.Round
            )
          )

          // Data nodes
          trendPoints.forEachIndexed { idx, pt ->
            val x = pointX(idx)
            val yComp = pointY(pt.completedCount)
            val yTot = pointY(pt.totalTasks)
            val isSelected = idx == selectedIndex

            // Total node
            drawCircle(
              color = CyanElectric,
              radius = if (isSelected) 4.5.dp.toPx() else 3.dp.toPx(),
              center = Offset(x, yTot)
            )

            // Completed node with outer glow on selected
            if (isSelected) {
              drawCircle(
                color = ElectricLime.copy(alpha = 0.28f),
                radius = 9.dp.toPx(),
                center = Offset(x, yComp)
              )
            }
            drawCircle(
              color = DarkCharcoal,
              radius = if (isSelected) 5.5.dp.toPx() else 4.dp.toPx(),
              center = Offset(x, yComp)
            )
            drawCircle(
              color = ElectricLime,
              radius = if (isSelected) 4.dp.toPx() else 2.8.dp.toPx(),
              center = Offset(x, yComp)
            )
          }
        } else {
          // Grouped Bars Mode: Completed (ElectricLime) vs Total/Pending (CyanElectric)
          val slotWidth = chartWidth / count.toFloat()
          val barWidth = (slotWidth * 0.28f).coerceAtMost(14.dp.toPx())
          val baselineY = topPadding + usableHeight

          trendPoints.forEachIndexed { idx, pt ->
            val centerX = slotWidth * idx + slotWidth / 2f
            val isSelected = idx == selectedIndex

            if (isSelected) {
              drawRoundRect(
                color = CyanElectric.copy(alpha = 0.08f),
                topLeft = Offset(centerX - slotWidth * 0.42f, topPadding),
                size = Size(slotWidth * 0.84f, usableHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
              )
            }

            // Total bar (left)
            val totalBarTop = pointY(pt.totalTasks)
            val totalBarHeight = (baselineY - totalBarTop).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
              color = CyanElectric.copy(alpha = if (isSelected) 0.85f else 0.45f),
              topLeft = Offset(centerX - barWidth - 2.dp.toPx(), baselineY - totalBarHeight),
              size = Size(barWidth, totalBarHeight),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Completed bar (right)
            val compBarTop = pointY(pt.completedCount)
            val compBarHeight = (baselineY - compBarTop).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
              color = if (isSelected) ToxicGreenGlow else ElectricLime,
              topLeft = Offset(centerX + 2.dp.toPx(), baselineY - compBarHeight),
              size = Size(barWidth, compBarHeight),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
          }
        }
      }
    }

    // 4. X-Axis Interactive Day Selector Pills
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      trendPoints.forEachIndexed { idx, pt ->
        val isSelected = idx == selectedIndex
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ElectricLime.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { selectedIndex = idx }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("chart_day_pill_$idx")
        ) {
          Text(
            text = pt.dayShortLabel,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) ElectricLime else TextSecondary
          )
          Text(
            text = "${pt.completedCount}/${pt.totalTasks}",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) TextPrimary else TextMuted
          )
        }
      }
    }

    // 5. Recharts-Style Interactive Tooltip / Selected Day Detail Banner
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(GraphiteSurface)
        .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .testTag("productivity_chart_tooltip"),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${selectedPoint.dayShortLabel} (${selectedPoint.fullDateLabel})",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(CyanElectric.copy(alpha = 0.14f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "Focus: ${selectedPoint.topCategory}",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = CyanElectric
            )
          }
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          LegendDotLabel(color = ElectricLime, text = "Completed: ${selectedPoint.completedCount}")
          LegendDotLabel(color = CyanElectric, text = "Pending: ${selectedPoint.pendingCount}")
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "${selectedPoint.completionRatePercent}%",
          fontSize = 16.sp,
          fontWeight = FontWeight.Black,
          color = if (selectedPoint.completionRatePercent >= 50) ElectricLime else CyanElectric
        )
        Text(
          text = "day velocity",
          fontSize = 9.sp,
          color = TextMuted
        )
      }
    }
  }
}

@Composable
private fun ChartKpiBadge(
  label: String,
  value: String,
  accent: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(GraphiteSurface)
      .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      Text(
        text = value,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        color = TextPrimary
      )
    }
    Text(
      text = label,
      fontSize = 9.sp,
      fontWeight = FontWeight.SemiBold,
      color = accent
    )
  }
}

@Composable
private fun LegendDotLabel(
  color: Color,
  text: String
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(7.dp)
        .clip(CircleShape)
        .background(color)
    )
    Spacer(modifier = Modifier.width(5.dp))
    Text(
      text = text,
      fontSize = 11.sp,
      color = TextSecondary,
      fontWeight = FontWeight.Medium
    )
  }
}
