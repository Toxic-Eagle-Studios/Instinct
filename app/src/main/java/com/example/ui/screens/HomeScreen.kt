package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SyncStatus
import com.example.ui.InstinctViewModel
import com.example.ui.NavTab
import com.example.ui.components.ContextualTriggerBanner
import com.example.ui.components.FeatureCardsRow
import com.example.ui.components.InteractiveRepairCard
import com.example.ui.components.RecentMemoryEntriesList
import com.example.ui.components.SharedBrainAiHubCard
import com.example.ui.components.UniversalInputCard
import com.example.ui.components.WhileYoureAtItChecklistCard
import com.example.ui.components.WhileYoureAtItQuickHubStrip
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DeepRedWarning
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.IconButton

@Composable
fun HomeScreen(
  viewModel: InstinctViewModel,
  onOpenDrawer: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val inputText by viewModel.inputText.collectAsState()
  val attachedImage by viewModel.attachedImage.collectAsState()
  val isProcessing by viewModel.isProcessingAi.collectAsState()
  val latestAiResult by viewModel.latestAiResult.collectAsState()
  val surfacedReminders by viewModel.surfacedContextReminders.collectAsState()
  val surfacedPreferences by viewModel.surfacedContextPreferences.collectAsState()
  val activeContextSession by viewModel.activeContextSession.collectAsState()
  val activeRepairSession by viewModel.activeRepairSession.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val preferences by viewModel.preferences.collectAsState()
  val fixes by viewModel.fixes.collectAsState()
  val syncStatus by viewModel.syncStatus.collectAsState()

  // AI Suite States
  val dailyBriefing by viewModel.dailyBriefing.collectAsState()
  val isGeneratingBriefing by viewModel.isGeneratingBriefing.collectAsState()
  val isChatPanelOpen by viewModel.isChatPanelOpen.collectAsState()
  val chatMessages by viewModel.chatMessages.collectAsState()
  val isStreamingChat by viewModel.isStreamingChat.collectAsState()
  val isAutoOrganizing by viewModel.isAutoOrganizing.collectAsState()
  val autoTagResults by viewModel.autoTagResults.collectAsState()

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(JetBlack)
      .testTag("home_screen")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 12.dp)
        .widthIn(max = 680.dp)
        .align(Alignment.TopCenter)
    ) {
      // 1. Top Brand Header & Sync Status Pill
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (onOpenDrawer != null) {
            IconButton(
              onClick = onOpenDrawer,
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GraphiteCard)
                .border(1.dp, GraphiteBorder, RoundedCornerShape(10.dp))
                .testTag("open_navigation_drawer_button")
            ) {
              Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open Navigation Drawer",
                tint = TextPrimary,
                modifier = Modifier.size(19.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
          }
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "INSTINCT",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 1.5.sp
              )
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(ElectricLime)
              )
            }
            Text(
              text = "Your life. A little less complicated.",
              fontSize = 13.sp,
              color = TextSecondary,
              fontWeight = FontWeight.Medium
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Daily Briefing Dashboard Pill
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(ElectricLime.copy(alpha = 0.14f))
              .border(1.dp, ElectricLime.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
              .clickable { viewModel.setTab(NavTab.BRIEFING) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("open_daily_briefing_button")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Open Daily Briefing Dashboard",
                tint = ElectricLime,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "Briefing",
                fontSize = 11.sp,
                color = ElectricLime,
                fontWeight = FontWeight.Bold
              )
            }
          }

          // Cloud Sync Status Pill
          SyncStatusBadge(syncStatus = syncStatus)

          // Shared Brain Pill
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(GraphiteCard)
              .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
              .clickable { viewModel.setTab(NavTab.SETTINGS) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = "One Shared Brain",
                tint = CyanElectric,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Brain",
                fontSize = 11.sp,
                color = CyanElectric,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      // 2. Universal Input Card: "What's on your mind?"
      val selectedInputCategory by viewModel.selectedInputCategory.collectAsState()
      val inputDueAt by viewModel.inputDueAt.collectAsState()
      UniversalInputCard(
        inputText = inputText,
        onInputTextChanged = { viewModel.updateInputText(it) },
        attachedImage = attachedImage,
        onImageChanged = { viewModel.setAttachedImage(it) },
        isProcessing = isProcessing,
        onSubmit = { viewModel.submitUniversalInput() },
        selectedCategory = selectedInputCategory,
        onCategorySelected = { viewModel.updateSelectedInputCategory(it) },
        dueAt = inputDueAt,
        onDueAtChanged = { viewModel.updateInputDueAt(it) },
        onQuickTrySubmit = { quickPrompt -> viewModel.submitUniversalInput(quickPrompt) },
        onAiTranscribeSpoken = { spoken -> viewModel.transcribeAudioNoteIntoInput(spoken) }
      )

      Spacer(modifier = Modifier.height(14.dp))

      // 2B. "While You're At It" Quick Context Modes Strip
      WhileYoureAtItQuickHubStrip(
        presets = viewModel.contextPresets,
        activeContextLabel = activeContextSession?.contextLabel,
        onSelectPreset = { preset ->
          viewModel.activateContextSession(preset.query)
        }
      )

      Spacer(modifier = Modifier.height(14.dp))

      // 2C. Shared Brain AI Suite Card (Briefing, Multi-Model Gemini Chat + Live Voice, Auto-Organize, Errand Checklist)
      val selectedAiModel by viewModel.selectedAiModel.collectAsState()
      val selectedPersona by viewModel.selectedChatPersona.collectAsState()
      val searchGroundingEnabled by viewModel.searchGroundingEnabled.collectAsState()
      val liveVoiceModeActive by viewModel.liveVoiceModeActive.collectAsState()

      SharedBrainAiHubCard(
        dailyBriefing = dailyBriefing,
        isGeneratingBriefing = isGeneratingBriefing,
        onGenerateBriefing = { viewModel.generateDailyBriefing() },
        onDismissBriefing = { viewModel.dismissDailyBriefing() },
        isChatPanelOpen = isChatPanelOpen,
        chatMessages = chatMessages,
        isStreamingChat = isStreamingChat,
        onToggleChatPanel = { viewModel.toggleChatPanel() },
        onSendChatMessage = { question, onReplyReady ->
          viewModel.sendFollowUpChatMessage(question, onReplyReady)
        },
        onSaveChatReplyAsMemory = { viewModel.saveChatReplyAsMemory(it) },
        isAutoOrganizing = isAutoOrganizing,
        autoTagResults = autoTagResults,
        onAutoOrganize = { viewModel.autoOrganizeMemories() },
        onDismissAutoTagResults = { viewModel.dismissAutoTagResults() },
        onLaunchErrandChecklist = { viewModel.generateErrandChecklist(it) },
        selectedAiModel = selectedAiModel,
        onSelectAiModel = { viewModel.setSelectedAiModel(it) },
        selectedPersona = selectedPersona,
        onSelectPersona = { viewModel.setSelectedChatPersona(it) },
        searchGroundingEnabled = searchGroundingEnabled,
        onToggleSearchGrounding = { viewModel.setSearchGroundingEnabled(it) },
        liveVoiceModeActive = liveVoiceModeActive,
        onToggleLiveVoiceMode = { viewModel.setLiveVoiceModeActive(it) }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // 3. Active Interactive "While You're At It" Checklist Card
      if (activeContextSession != null) {
        WhileYoureAtItChecklistCard(
          session = activeContextSession!!,
          onMarkReminderDone = { viewModel.markReminderDone(it) },
          onSnoozeReminder = { viewModel.snoozeReminder(it) },
          onToggleCompanionTask = { viewModel.toggleCompanionTaskChecked(it) },
          onSaveCompanionTaskToBrain = { viewModel.saveCompanionTaskToBrain(it) },
          onDismiss = { viewModel.dismissActiveTriggers() }
        )
        Spacer(modifier = Modifier.height(16.dp))
      } else if (surfacedReminders.isNotEmpty() || surfacedPreferences.isNotEmpty()) {
        ContextualTriggerBanner(
          reminders = surfacedReminders,
          preferences = surfacedPreferences,
          onMarkDone = { viewModel.markReminderDone(it) },
          onSnooze = { viewModel.snoozeReminder(it) },
          onDismiss = { viewModel.dismissActiveTriggers() }
        )
        Spacer(modifier = Modifier.height(16.dp))
      }

      // 4. Active Interactive Repair Session (Undo IRL)
      if (activeRepairSession != null) {
        InteractiveRepairCard(
          sessionState = activeRepairSession!!,
          onNextStep = { viewModel.nextRepairStep() },
          onPrevStep = { viewModel.prevRepairStep() },
          onWorked = { viewModel.repairWorked() },
          onDidNotWork = { viewModel.repairDidNotWork() },
          onDismiss = { viewModel.dismissRepairSession() }
        )
        Spacer(modifier = Modifier.height(16.dp))
      }

      // 5. Latest AI Reasoning & Result Banner
      if (latestAiResult != null && activeRepairSession == null && activeContextSession == null && surfacedReminders.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GraphiteCard)
            .border(1.dp, CyanElectric.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("ai_result_banner")
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = CyanElectric,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "INSTINCT Response",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = CyanElectric
                )
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(GraphiteSurface)
                  .border(1.dp, ElectricLime.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                  .clickable { viewModel.toggleChatPanel(true) }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("ask_followup_from_banner_button")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = null,
                    tint = ElectricLime,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Follow-Up Chat",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricLime
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = latestAiResult!!.aiSummary,
              fontSize = 14.sp,
              color = TextPrimary,
              lineHeight = 20.sp
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }

      // 6. Three Core Abilities Cards
      Text(
        text = "THREE EVERYDAY ABILITIES",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      FeatureCardsRow(
        onSelectTab = { viewModel.setTab(it) },
        onPrefillPrompt = { viewModel.updateInputText(it) },
        remindersCount = reminders.count { !it.isCompleted },
        fixesCount = fixes.size,
        preferencesCount = preferences.size
      )

      Spacer(modifier = Modifier.height(20.dp))

      // 7. Room-Backed Recent MemoryEntries Composable Section
      RecentMemoryEntriesList(viewModel = viewModel)

      Spacer(modifier = Modifier.height(88.dp)) // padding for bottom nav bar
    }
  }
}

@Composable
private fun SyncStatusBadge(
  syncStatus: SyncStatus,
  modifier: Modifier = Modifier
) {
  com.example.ui.components.TopBarSyncIndicator(
    syncStatus = syncStatus,
    isSignedIn = false,
    testTag = "sync_status_badge",
    modifier = modifier
  )
}

