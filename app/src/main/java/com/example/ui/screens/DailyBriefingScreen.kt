package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailyBriefingDigest
import com.example.data.model.Fix
import com.example.data.model.MemoryEntry
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.ui.InstinctViewModel
import com.example.ui.NavTab
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.ElectricLimeDim
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreenGlow

private enum class TaskFilterMode(val label: String) {
  UPCOMING("Upcoming"),
  DUE_DATED("Scheduled"),
  COMPLETED("Completed"),
  ALL("All Tasks")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBriefingScreen(
  viewModel: InstinctViewModel,
  onOpenDrawer: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  BackHandler {
    viewModel.setTab(NavTab.HOME)
  }

  val dailyBriefing by viewModel.dailyBriefing.collectAsState()
  val isGeneratingBriefing by viewModel.isGeneratingBriefing.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val preferences by viewModel.preferences.collectAsState()
  val fixes by viewModel.fixes.collectAsState()
  val useAi by viewModel.useAi.collectAsState()
  val selectedAiModel by viewModel.selectedAiModel.collectAsState()
  val syncStatus by viewModel.syncStatus.collectAsState()
  val currentUser by viewModel.authRepository.currentUser.collectAsState()

  var selectedTaskFilter by remember { mutableStateOf(TaskFilterMode.UPCOMING) }
  var selectedCategory by remember { mutableStateOf("ALL") }
  var quickTaskTitle by remember { mutableStateOf("") }
  var quickTaskContext by remember { mutableStateOf("") }
  var quickTaskDueAt by remember { mutableStateOf("") }

  // Auto-generate Daily Briefing on first open if not already synthesized
  LaunchedEffect(Unit) {
    if (dailyBriefing == null && !isGeneratingBriefing) {
      viewModel.generateDailyBriefing()
    }
  }

  val filteredReminders = remember(reminders, selectedTaskFilter, selectedCategory) {
    reminders
      .filter { rem ->
        val matchesStatus = when (selectedTaskFilter) {
          TaskFilterMode.UPCOMING -> !rem.isCompleted
          TaskFilterMode.DUE_DATED -> !rem.isCompleted && rem.dueAt.isNotBlank()
          TaskFilterMode.COMPLETED -> rem.isCompleted
          TaskFilterMode.ALL -> true
        }
        val matchesCategory = selectedCategory == "ALL" ||
          rem.category.equals(selectedCategory, ignoreCase = true)
        matchesStatus && matchesCategory
      }
      .sortedWith(
        compareBy<Reminder> { it.isCompleted }
          .thenByDescending { it.dueAt.isNotBlank() }
          .thenByDescending { it.updatedAt?.time ?: 0L }
      )
  }

  val pendingCount = remember(reminders) { reminders.count { !it.isCompleted } }
  val scheduledCount = remember(reminders) { reminders.count { !it.isCompleted && it.dueAt.isNotBlank() } }
  val completedCount = remember(reminders) { reminders.count { it.isCompleted } }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(JetBlack)
      .testTag("daily_briefing_screen"),
    containerColor = JetBlack,
    topBar = {
      TopAppBar(
        modifier = Modifier.testTag("daily_briefing_top_app_bar"),
        title = {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Daily Briefing",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                modifier = Modifier.testTag("daily_briefing_title")
              )
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(ElectricLime.copy(alpha = 0.16f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = if (useAi) selectedAiModel.badge else "LOCAL",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = ElectricLime
                )
              }
            }
            Text(
              text = "Smart memory synthesis & upcoming tasks",
              fontSize = 11.sp,
              color = TextSecondary
            )
          }
        },
        navigationIcon = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { viewModel.setTab(NavTab.HOME) },
              modifier = Modifier.testTag("daily_briefing_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate back to Home",
                tint = TextPrimary
              )
            }
            if (onOpenDrawer != null) {
              IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.testTag("briefing_open_drawer_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Checklist,
                  contentDescription = "Open Navigation Drawer",
                  tint = CyanElectric
                )
              }
            }
          }
        },
        actions = {
          com.example.ui.components.TopBarSyncIndicator(
            syncStatus = syncStatus,
            isSignedIn = currentUser != null,
            onClick = { viewModel.setTab(NavTab.SETTINGS) },
            testTag = "top_app_bar_sync_indicator",
            modifier = Modifier.padding(end = 4.dp)
          )
          IconButton(
            onClick = { viewModel.generateDailyBriefing() },
            enabled = !isGeneratingBriefing,
            modifier = Modifier.testTag("refresh_daily_briefing_button")
          ) {
            if (isGeneratingBriefing) {
              CircularProgressIndicator(
                color = ElectricLime,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
              )
            } else {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Daily Briefing Synthesis",
                tint = ElectricLime
              )
            }
          }
          IconButton(
            onClick = { viewModel.setTab(NavTab.REMINDERS) },
            modifier = Modifier.testTag("briefing_to_reminders_button")
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = "Open While You're At It Hub",
              tint = CyanElectric
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = DarkCharcoal,
          titleContentColor = TextPrimary,
          navigationIconContentColor = TextPrimary,
          actionIconContentColor = ElectricLime
        )
      )
    }
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(JetBlack)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 14.dp)
          .widthIn(max = 680.dp)
          .align(Alignment.TopCenter),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // 1. Shared Brain Pulse Metrics Strip
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_briefing_metrics_row"),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          BriefingMetricTile(
            label = "Upcoming",
            value = pendingCount.toString(),
            subtitle = if (scheduledCount > 0) "$scheduledCount with due date" else "Active tasks",
            accentColor = ElectricLime,
            icon = Icons.Default.NotificationsActive,
            onClick = { selectedTaskFilter = TaskFilterMode.UPCOMING },
            modifier = Modifier.weight(1f)
          )
          BriefingMetricTile(
            label = "Completed",
            value = completedCount.toString(),
            subtitle = "Checked off",
            accentColor = ToxicGreenGlow,
            icon = Icons.Default.CheckCircle,
            onClick = { selectedTaskFilter = TaskFilterMode.COMPLETED },
            modifier = Modifier.weight(1f)
          )
          BriefingMetricTile(
            label = "Preferences",
            value = preferences.size.toString(),
            subtitle = "Exactly specs",
            accentColor = CyanElectric,
            icon = Icons.Default.Bookmark,
            onClick = { viewModel.setTab(NavTab.MEMORIES) },
            modifier = Modifier.weight(1f)
          )
          BriefingMetricTile(
            label = "Fixes",
            value = fixes.size.toString(),
            subtitle = "Undo IRL",
            accentColor = AmberNotice,
            icon = Icons.Default.Build,
            onClick = { viewModel.setTab(NavTab.FIXES) },
            modifier = Modifier.weight(1f)
          )
        }

        // 1B. Daily Productivity & Task Completion Trend Chart
        com.example.ui.components.ProductivityTrendChartCard(
          reminders = reminders
        )

        // 2. Smart Memory Synthesis Executive Card
        SmartMemorySynthesisCard(
          briefing = dailyBriefing,
          isGenerating = isGeneratingBriefing,
          selectedCategory = selectedCategory,
          onSelectCategory = { cat ->
            selectedCategory = if (selectedCategory == cat) "ALL" else cat
          },
          onRegenerate = { viewModel.generateDailyBriefing() },
          onLaunchErrandChain = { query ->
            viewModel.activateContextSession(query)
            viewModel.setTab(NavTab.HOME)
          }
        )

        // 3. Quick Add Upcoming Task Card
        QuickAddUpcomingTaskCard(
          taskTitle = quickTaskTitle,
          onTaskTitleChanged = { quickTaskTitle = it },
          taskContext = quickTaskContext,
          onTaskContextChanged = { quickTaskContext = it },
          taskDueAt = quickTaskDueAt,
          onTaskDueAtChanged = { quickTaskDueAt = it },
          onAddTask = {
            if (quickTaskTitle.isNotBlank()) {
              viewModel.saveReminderDirectly(
                title = quickTaskTitle.trim(),
                trigger = quickTaskContext.trim().ifBlank { "Next time" },
                recurrence = "next_time_only",
                dueAt = quickTaskDueAt.trim()
              )
              quickTaskTitle = ""
              quickTaskContext = ""
              quickTaskDueAt = ""
            }
          }
        )

        // 4. Upcoming Tasks & Contextual Reminders Queue Section
        UpcomingTasksQueueSection(
          reminders = filteredReminders,
          selectedFilter = selectedTaskFilter,
          onSelectFilter = { selectedTaskFilter = it },
          selectedCategory = selectedCategory,
          onSelectCategory = { selectedCategory = it },
          onToggleReminderDone = { reminder ->
            viewModel.toggleReminderCompletion(reminder)
          },
          onSnoozeReminder = { reminder ->
            viewModel.snoozeReminder(reminder)
          },
          onTriggerContext = { contextQuery ->
            viewModel.activateContextSession(contextQuery)
            viewModel.setTab(NavTab.HOME)
          }
        )

        // 5. Connected Memory Highlights (Recent Preferences & Active Repair Guides)
        ConnectedMemoryHighlightsSection(
          preferences = preferences.take(3),
          fixes = fixes.take(3),
          onOpenMemories = { viewModel.setTab(NavTab.MEMORIES) },
          onOpenFixes = { viewModel.setTab(NavTab.FIXES) }
        )

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun BriefingMetricTile(
  label: String,
  value: String,
  subtitle: String,
  accentColor: Color,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(GraphiteCard)
      .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = accentColor,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = value,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        color = TextPrimary
      )
    }
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = accentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = subtitle,
      fontSize = 9.sp,
      color = TextMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun SmartMemorySynthesisCard(
  briefing: DailyBriefingDigest?,
  isGenerating: Boolean,
  selectedCategory: String,
  onSelectCategory: (String) -> Unit,
  onRegenerate: () -> Unit,
  onLaunchErrandChain: (String) -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(
        Brush.verticalGradient(
          colors = listOf(
            DarkCharcoal,
            GraphiteCard
          )
        )
      )
      .border(1.5.dp, ElectricLime.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
      .padding(16.dp)
      .testTag("daily_briefing_synthesis_card")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
              .background(ElectricLime.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = "Smart Memory Synthesis",
              tint = ElectricLime,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "SMART MEMORY SYNTHESIS",
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              color = ElectricLime,
              letterSpacing = 0.9.sp
            )
            Text(
              text = if (briefing != null) {
                "Updated ${MemoryEntry.formatRelativeTimestamp(briefing.generatedAtMillis)}"
              } else {
                "Synthesizing across Reminders, Preferences & Fixes"
              },
              fontSize = 11.sp,
              color = TextSecondary
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(GraphiteSurface)
            .border(1.dp, ElectricLime.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(enabled = !isGenerating) { onRegenerate() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("regenerate_briefing_button")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (isGenerating) {
              CircularProgressIndicator(
                color = ElectricLime,
                strokeWidth = 2.dp,
                modifier = Modifier.size(12.dp)
              )
            } else {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Synthesize",
                tint = ElectricLime,
                modifier = Modifier.size(13.dp)
              )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = if (isGenerating) "Synthesizing..." else "Resynthesize",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = ElectricLime
            )
          }
        }
      }

      if (briefing == null && isGenerating) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(
            color = ElectricLime,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = "Synthesizing your Shared Brain briefing...",
            fontSize = 13.sp,
            color = TextSecondary
          )
        }
      } else if (briefing != null) {
        Text(
          text = briefing.headline,
          fontSize = 18.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextPrimary
        )

        Text(
          text = briefing.summary,
          fontSize = 13.sp,
          color = TextSecondary,
          lineHeight = 19.sp
        )

        // Priority Highlights from AI Synthesis
        if (briefing.priorityReminders.isNotEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(GraphiteSurface)
              .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Checklist,
                contentDescription = null,
                tint = ElectricLime,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "PRIORITY FOCUS HIGHLIGHTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = ElectricLime,
                letterSpacing = 0.8.sp
              )
            }
            briefing.priorityReminders.forEach { item ->
              Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "•",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = ElectricLime
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = item,
                  fontSize = 12.sp,
                  color = TextPrimary,
                  lineHeight = 17.sp
                )
              }
            }
          }
        }

        // Active Context Chain Suggestion ("While You're At It")
        if (briefing.activeContextChainSuggestion.isNotBlank()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(CyanElectric.copy(alpha = 0.1f))
              .border(1.dp, CyanElectric.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = CyanElectric,
                    modifier = Modifier.size(15.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "WHILE YOU'RE AT IT · SMART ERRAND CHAIN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = CyanElectric,
                    letterSpacing = 0.7.sp
                  )
                }

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanElectric.copy(alpha = 0.2f))
                    .clickable { onLaunchErrandChain("Heading out for errands") }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("launch_briefing_errand_chain_button")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.PlayArrow,
                      contentDescription = "Launch Errand Chain",
                      tint = CyanElectric,
                      modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                      text = "Start Run",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = CyanElectric
                    )
                  }
                }
              }

              Text(
                text = briefing.activeContextChainSuggestion,
                fontSize = 12.sp,
                color = TextPrimary,
                lineHeight = 17.sp
              )
            }
          }
        }

        // Cross-Memory Insight Tip
        if (briefing.insightTip.isNotBlank()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(ElectricLimeDim.copy(alpha = 0.1f))
              .border(1.dp, ElectricLimeDim.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
              .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Lightbulb,
              contentDescription = null,
              tint = AmberNotice,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = briefing.insightTip,
              fontSize = 11.sp,
              color = TextSecondary,
              lineHeight = 16.sp
            )
          }
        }

        // Interactive Category Distribution Pills
        if (briefing.categoryBreakdown.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "MEMORY DISTRIBUTION BY CATEGORY (TAP TO FILTER TASKS)",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = TextMuted,
              letterSpacing = 0.7.sp
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              briefing.categoryBreakdown.entries.forEach { (cat, count) ->
                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ElectricLime.copy(alpha = 0.2f) else GraphiteSurface)
                    .border(
                      1.dp,
                      if (isSelected) ElectricLime else GraphiteBorder,
                      RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectCategory(cat) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                  Text(
                    text = "$cat ($count)",
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) ElectricLime else TextSecondary
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuickAddUpcomingTaskCard(
  taskTitle: String,
  onTaskTitleChanged: (String) -> Unit,
  taskContext: String,
  onTaskContextChanged: (String) -> Unit,
  taskDueAt: String,
  onTaskDueAtChanged: (String) -> Unit,
  onAddTask: () -> Unit
) {
  var showAdvancedFields by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(GraphiteCard)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.AddTask,
          contentDescription = null,
          tint = ElectricLime,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "QUICK ADD UPCOMING TASK",
          fontSize = 11.sp,
          fontWeight = FontWeight.Black,
          color = ElectricLime,
          letterSpacing = 0.8.sp
        )
      }

      Text(
        text = if (showAdvancedFields) "Hide context/date" else "+ Context / Due Date",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = CyanElectric,
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .clickable { showAdvancedFields = !showAdvancedFields }
          .padding(horizontal = 6.dp, vertical = 2.dp)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = taskTitle,
        onValueChange = onTaskTitleChanged,
        placeholder = {
          Text(
            text = "e.g., Ask Dad about Friday or Pick up AA batteries...",
            fontSize = 12.sp,
            color = TextMuted
          )
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = ElectricLime,
          unfocusedBorderColor = GraphiteBorder,
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          cursorColor = ElectricLime,
          focusedContainerColor = GraphiteSurface,
          unfocusedContainerColor = GraphiteSurface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .testTag("briefing_quick_add_task_input")
      )

      Button(
        onClick = onAddTask,
        enabled = taskTitle.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
          containerColor = ElectricLime,
          contentColor = JetBlack,
          disabledContainerColor = GraphiteSurface,
          disabledContentColor = TextMuted
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .height(52.dp)
          .testTag("briefing_quick_add_task_button")
      ) {
        Text(
          text = "Add",
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold
        )
      }
    }

    AnimatedVisibility(visible = showAdvancedFields) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = taskContext,
          onValueChange = onTaskContextChanged,
          label = { Text("Person / Place Context", fontSize = 11.sp) },
          placeholder = { Text("Dad, Walmart, Work...", fontSize = 11.sp) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = taskDueAt,
          onValueChange = onTaskDueAtChanged,
          label = { Text("Optional Due Date", fontSize = 11.sp) },
          placeholder = { Text("Tomorrow 9 AM", fontSize = 11.sp) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AmberNotice,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun UpcomingTasksQueueSection(
  reminders: List<Reminder>,
  selectedFilter: TaskFilterMode,
  onSelectFilter: (TaskFilterMode) -> Unit,
  selectedCategory: String,
  onSelectCategory: (String) -> Unit,
  onToggleReminderDone: (Reminder) -> Unit,
  onSnoozeReminder: (Reminder) -> Unit,
  onTriggerContext: (String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("daily_briefing_upcoming_tasks_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.CalendarToday,
          contentDescription = null,
          tint = CyanElectric,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "UPCOMING TASKS & TRIGGERS",
          fontSize = 12.sp,
          fontWeight = FontWeight.Black,
          color = TextPrimary,
          letterSpacing = 0.8.sp
        )
      }

      if (selectedCategory != "ALL") {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ElectricLime.copy(alpha = 0.16f))
            .clickable { onSelectCategory("ALL") }
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "Filter: $selectedCategory ✕",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ElectricLime
          )
        }
      }
    }

    // Filter Mode Pills
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      TaskFilterMode.entries.forEach { mode ->
        val isSelected = selectedFilter == mode
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) CyanElectric.copy(alpha = 0.18f) else GraphiteCard)
            .border(
              1.dp,
              if (isSelected) CyanElectric else GraphiteBorder,
              RoundedCornerShape(10.dp)
            )
            .clickable { onSelectFilter(mode) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("briefing_task_filter_${mode.name.lowercase()}")
        ) {
          Text(
            text = mode.label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) CyanElectric else TextSecondary
          )
        }
      }
    }

    if (reminders.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(GraphiteCard)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(14.dp))
          .padding(20.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = ElectricLime,
            modifier = Modifier.size(28.dp)
          )
          Text(
            text = "No tasks matching this view",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
          Text(
            text = "Add a contextual reminder above or switch task filters to inspect completed items.",
            fontSize = 12.sp,
            color = TextSecondary
          )
        }
      }
    } else {
      reminders.forEach { reminder ->
        BriefingTaskItemCard(
          reminder = reminder,
          onToggleDone = { onToggleReminderDone(reminder) },
          onSnooze = { onSnoozeReminder(reminder) },
          onTriggerContext = {
            val trigger = reminder.triggerActivity.ifBlank { reminder.title }
            onTriggerContext("I'm heading to $trigger")
          }
        )
      }
    }
  }
}

@Composable
private fun BriefingTaskItemCard(
  reminder: Reminder,
  onToggleDone: () -> Unit,
  onSnooze: () -> Unit,
  onTriggerContext: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(GraphiteCard)
      .border(
        1.dp,
        if (reminder.isCompleted) GraphiteBorder else ElectricLime.copy(alpha = 0.3f),
        RoundedCornerShape(14.dp)
      )
      .padding(14.dp)
      .testTag("briefing_task_card_${reminder.id}"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.Top
      ) {
        IconButton(
          onClick = onToggleDone,
          modifier = Modifier
            .size(28.dp)
            .testTag("briefing_toggle_task_${reminder.id}")
        ) {
          Icon(
            imageVector = if (reminder.isCompleted) {
              Icons.Default.CheckCircle
            } else {
              Icons.Default.RadioButtonUnchecked
            },
            contentDescription = if (reminder.isCompleted) "Mark task incomplete" else "Mark task complete",
            tint = if (reminder.isCompleted) ToxicGreenGlow else ElectricLime,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = reminder.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (reminder.isCompleted) TextMuted else TextPrimary,
            textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Category Pill
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(GraphiteSurface)
                .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
              Text(
                text = reminder.category,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
              )
            }

            // Context Trigger Pill (Tappable to launch While You're At It)
            if (reminder.triggerActivity.isNotBlank()) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(CyanElectric.copy(alpha = 0.14f))
                  .clickable { onTriggerContext() }
                  .padding(horizontal = 7.dp, vertical = 2.dp)
              ) {
                Text(
                  text = "Context: ${reminder.triggerActivity}",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = CyanElectric
                )
              }
            }

            // Due Date Badge
            if (reminder.dueAt.isNotBlank()) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(AmberNotice.copy(alpha = 0.16f))
                  .padding(horizontal = 7.dp, vertical = 2.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = AmberNotice,
                    modifier = Modifier.size(11.dp)
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "Due: ${reminder.dueAt}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberNotice
                  )
                }
              }
            }
          }
        }
      }

      if (!reminder.isCompleted) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(GraphiteSurface)
              .border(1.dp, GraphiteBorder, RoundedCornerShape(8.dp))
              .clickable { onSnooze() }
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "Snooze",
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextSecondary
            )
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(ElectricLime.copy(alpha = 0.14f))
              .border(1.dp, ElectricLime.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
              .clickable { onTriggerContext() }
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "At It",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = ElectricLime
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ConnectedMemoryHighlightsSection(
  preferences: List<Preference>,
  fixes: List<Fix>,
  onOpenMemories: () -> Unit,
  onOpenFixes: () -> Unit
) {
  if (preferences.isEmpty() && fixes.isEmpty()) return

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(GraphiteCard)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Default.Psychology,
        contentDescription = null,
        tint = CyanElectric,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "CONNECTED SHARED BRAIN MEMORIES",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = CyanElectric,
        letterSpacing = 0.8.sp
      )
    }

    if (preferences.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Recent 'Exactly' Preferences",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = CyanElectric
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenMemories() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = "View All",
            fontSize = 11.sp,
            color = CyanElectric,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.width(3.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = CyanElectric,
            modifier = Modifier.size(12.dp)
          )
        }
      }

      preferences.forEach { pref ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GraphiteSurface)
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = pref.title,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            if (pref.description.isNotBlank() && pref.description != pref.title) {
              Text(
                text = pref.description,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(CyanElectric.copy(alpha = 0.14f))
              .padding(horizontal = 7.dp, vertical = 3.dp)
          ) {
            Text(
              text = pref.category,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = CyanElectric
            )
          }
        }
      }
    }

    if (preferences.isNotEmpty() && fixes.isNotEmpty()) {
      HorizontalDivider(color = GraphiteBorder)
    }

    if (fixes.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Recent 'Undo IRL' Recovery Guides",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = AmberNotice
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenFixes() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = "Open Fixes",
            fontSize = 11.sp,
            color = AmberNotice,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.width(3.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = AmberNotice,
            modifier = Modifier.size(12.dp)
          )
        }
      }

      fixes.forEach { fix ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GraphiteSurface)
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = fix.problemSummary,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Text(
              text = fix.bestFix,
              fontSize = 11.sp,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Box(
            modifier = Modifier
              .clip(CircleShape)
              .background(if (fix.isVerifiedWorking) ToxicGreenGlow.copy(alpha = 0.16f) else AmberNotice.copy(alpha = 0.16f))
              .padding(horizontal = 7.dp, vertical = 3.dp)
          ) {
            Text(
              text = if (fix.isVerifiedWorking) "Verified" else "${fix.steps.size} steps",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (fix.isVerifiedWorking) ToxicGreenGlow else AmberNotice
            )
          }
        }
      }
    }
  }
}
