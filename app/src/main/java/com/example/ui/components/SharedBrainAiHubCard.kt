package com.example.ui.components

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiChatMessage
import com.example.data.model.AiModelTier
import com.example.data.model.AutoTagResult
import com.example.data.model.ChatPersonaRole
import com.example.data.model.DailyBriefingDigest
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreenGlow
import java.util.Locale

@Composable
fun SharedBrainAiHubCard(
  dailyBriefing: DailyBriefingDigest?,
  isGeneratingBriefing: Boolean,
  onGenerateBriefing: () -> Unit,
  onDismissBriefing: () -> Unit,
  isChatPanelOpen: Boolean,
  chatMessages: List<AiChatMessage>,
  isStreamingChat: Boolean,
  onToggleChatPanel: () -> Unit,
  onSendChatMessage: (String, ((String) -> Unit)?) -> Unit,
  onSaveChatReplyAsMemory: (AiChatMessage) -> Unit,
  isAutoOrganizing: Boolean,
  autoTagResults: List<AutoTagResult>,
  onAutoOrganize: () -> Unit,
  onDismissAutoTagResults: () -> Unit,
  onLaunchErrandChecklist: (String) -> Unit,
  selectedAiModel: AiModelTier = AiModelTier.GEMINI_3_8_FLASH,
  onSelectAiModel: ((AiModelTier) -> Unit)? = null,
  selectedPersona: ChatPersonaRole = ChatPersonaRole.SHARED_BRAIN,
  onSelectPersona: ((ChatPersonaRole) -> Unit)? = null,
  searchGroundingEnabled: Boolean = true,
  onToggleSearchGrounding: ((Boolean) -> Unit)? = null,
  liveVoiceModeActive: Boolean = false,
  onToggleLiveVoiceMode: ((Boolean) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  var showErrandPromptInput by remember { mutableStateOf(false) }
  var errandDestinationText by remember { mutableStateOf("") }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(GraphiteCard)
      .border(1.dp, CyanElectric.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
      .padding(14.dp)
      .testTag("shared_brain_ai_hub_card")
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(CyanElectric.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = CyanElectric,
            modifier = Modifier.size(16.dp)
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "SHARED BRAIN AI SUITE",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CyanElectric,
            letterSpacing = 0.9.sp
          )
          Text(
            text = "Briefing, multi-model Gemini chat, live voice, grounding & errand checklists",
            fontSize = 11.sp,
            color = TextSecondary
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // AI Action Buttons Strip
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      AiActionPill(
        label = "Daily Briefing",
        icon = Icons.Default.Summarize,
        accentColor = CyanElectric,
        isLoading = isGeneratingBriefing,
        isActive = dailyBriefing != null,
        onClick = onGenerateBriefing,
        testTag = "ai_daily_briefing_button"
      )
      AiActionPill(
        label = "Gemini Chat & Voice",
        icon = Icons.Default.Forum,
        accentColor = ElectricLime,
        isLoading = isStreamingChat,
        isActive = isChatPanelOpen,
        onClick = onToggleChatPanel,
        testTag = "ai_streaming_chat_button"
      )
      AiActionPill(
        label = "AI Auto-Organize",
        icon = Icons.Default.Category,
        accentColor = ToxicGreenGlow,
        isLoading = isAutoOrganizing,
        isActive = autoTagResults.isNotEmpty(),
        onClick = onAutoOrganize,
        testTag = "ai_auto_organize_button"
      )
      AiActionPill(
        label = "Errand Checklist",
        icon = Icons.Default.Checklist,
        accentColor = ElectricLime,
        isLoading = false,
        isActive = showErrandPromptInput,
        onClick = { showErrandPromptInput = !showErrandPromptInput },
        testTag = "ai_errand_checklist_button"
      )
    }

    // 4. Proactive AI Errand & Shopping Checklist Generator Drawer
    AnimatedVisibility(visible = showErrandPromptInput) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(DarkCharcoal)
          .border(1.dp, ElectricLime.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
          .padding(12.dp)
          .testTag("ai_errand_generator_panel")
      ) {
        Text(
          text = "PROACTIVE AI ERRAND & SHOPPING CHECKLIST GENERATOR",
          fontSize = 10.sp,
          fontWeight = FontWeight.ExtraBold,
          color = ElectricLime,
          letterSpacing = 0.7.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Enter where you're going or what you're starting—Gemini will bundle your reminders, preferences, repair tools, and smart companion tasks:",
          fontSize = 11.sp,
          color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = errandDestinationText,
            onValueChange = { errandDestinationText = it },
            placeholder = {
              Text(
                text = "E.g., Walmart run, Heading to Dad's, Home Depot, Work block...",
                fontSize = 12.sp,
                color = TextMuted
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
              onDone = {
                val query = errandDestinationText.ifBlank { "Walmart & Grocery Run" }
                onLaunchErrandChecklist(query)
                errandDestinationText = ""
                showErrandPromptInput = false
              }
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = GraphiteSurface,
              unfocusedContainerColor = GraphiteSurface,
              focusedBorderColor = ElectricLime,
              unfocusedBorderColor = GraphiteBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("ai_errand_destination_input")
          )

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(ElectricLime)
              .clickable {
                val query = errandDestinationText.ifBlank { "Walmart & Grocery Run" }
                onLaunchErrandChecklist(query)
                errandDestinationText = ""
                showErrandPromptInput = false
              }
              .padding(horizontal = 12.dp, vertical = 14.dp)
              .testTag("ai_generate_checklist_submit"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Generate errand checklist",
                tint = JetBlack,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Build",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JetBlack
              )
            }
          }
        }
      }
    }

    // 1. Smart Memory Synthesis & Daily Briefing Digest Card
    AnimatedVisibility(visible = dailyBriefing != null) {
      if (dailyBriefing != null) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCharcoal)
            .border(1.dp, CyanElectric.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("ai_daily_briefing_card")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                imageVector = Icons.Default.Summarize,
                contentDescription = null,
                tint = CyanElectric,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = dailyBriefing.headline,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
              )
            }
            IconButton(
              onClick = onDismissBriefing,
              modifier = Modifier
                .size(26.dp)
                .testTag("dismiss_daily_briefing_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss daily briefing",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = dailyBriefing.summary,
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp
          )

          if (dailyBriefing.priorityReminders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "PRIORITY FOCUS:",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = ElectricLime,
              letterSpacing = 0.7.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            dailyBriefing.priorityReminders.forEach { item ->
              Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
              ) {
                Text(
                  text = "• ",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = ElectricLime
                )
                Text(
                  text = item,
                  fontSize = 12.sp,
                  color = TextPrimary
                )
              }
            }
          }

          if (dailyBriefing.activeContextChainSuggestion.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(GraphiteSurface)
                .border(1.dp, ElectricLime.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(10.dp)
            ) {
              Column {
                Text(
                  text = "SUGGESTED 'WHILE YOU'RE AT IT' CHAIN:",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = ElectricLime
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = dailyBriefing.activeContextChainSuggestion,
                  fontSize = 12.sp,
                  color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElectricLime.copy(alpha = 0.18f))
                    .border(1.dp, ElectricLime, RoundedCornerShape(8.dp))
                    .clickable { onLaunchErrandChecklist(dailyBriefing.activeContextChainSuggestion) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("briefing_launch_chain_button")
                ) {
                  Text(
                    text = "Activate This Context Checklist",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricLime
                  )
                }
              }
            }
          }

          if (dailyBriefing.insightTip.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = CyanElectric,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = dailyBriefing.insightTip,
                fontSize = 11.sp,
                color = CyanElectric
              )
            }
          }
        }
      }
    }

    // 2. Multi-Model Streaming AI Follow-Up Chat + Live Voice Conversation Panel
    AnimatedVisibility(visible = isChatPanelOpen) {
      StreamingAiChatSection(
        messages = chatMessages,
        isStreaming = isStreamingChat,
        selectedAiModel = selectedAiModel,
        onSelectAiModel = onSelectAiModel,
        selectedPersona = selectedPersona,
        onSelectPersona = onSelectPersona,
        searchGroundingEnabled = searchGroundingEnabled,
        onToggleSearchGrounding = onToggleSearchGrounding,
        liveVoiceModeActive = liveVoiceModeActive,
        onToggleLiveVoiceMode = onToggleLiveVoiceMode,
        onSendMessage = onSendChatMessage,
        onSaveReplyAsMemory = onSaveChatReplyAsMemory,
        onClose = onToggleChatPanel
      )
    }

    // 3. AI Auto-Tagging & Smart Category Organization Results
    AnimatedVisibility(visible = autoTagResults.isNotEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(DarkCharcoal)
          .border(1.dp, ToxicGreenGlow.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
          .padding(12.dp)
          .testTag("ai_auto_organize_results_card")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Category,
              contentDescription = null,
              tint = ToxicGreenGlow,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "AI AUTO-ORGANIZED (${autoTagResults.size} MEMORIES)",
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              color = ToxicGreenGlow,
              letterSpacing = 0.7.sp
            )
          }
          IconButton(
            onClick = onDismissAutoTagResults,
            modifier = Modifier
              .size(24.dp)
              .testTag("dismiss_auto_organize_button")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Dismiss auto-organize summary",
              tint = TextMuted,
              modifier = Modifier.size(15.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        autoTagResults.take(5).forEach { res ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(GraphiteSurface)
              .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = res.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
              )
              if (res.suggestedTags.isNotEmpty()) {
                Text(
                  text = res.suggestedTags.joinToString(" ") { "#$it" },
                  fontSize = 10.sp,
                  color = TextSecondary
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ToxicGreenGlow.copy(alpha = 0.16f))
                .border(1.dp, ToxicGreenGlow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
              Text(
                text = res.suggestedCategory,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ToxicGreenGlow
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StreamingAiChatSection(
  messages: List<AiChatMessage>,
  isStreaming: Boolean,
  selectedAiModel: AiModelTier,
  onSelectAiModel: ((AiModelTier) -> Unit)?,
  selectedPersona: ChatPersonaRole,
  onSelectPersona: ((ChatPersonaRole) -> Unit)?,
  searchGroundingEnabled: Boolean,
  onToggleSearchGrounding: ((Boolean) -> Unit)?,
  liveVoiceModeActive: Boolean,
  onToggleLiveVoiceMode: ((Boolean) -> Unit)?,
  onSendMessage: (String, ((String) -> Unit)?) -> Unit,
  onSaveReplyAsMemory: (AiChatMessage) -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  var draftQuestion by remember { mutableStateOf("") }
  var ttsReady by remember { mutableStateOf(false) }

  // Android TextToSpeech engine for Live Voice Conversation mode
  val ttsRef = remember {
    var instance: TextToSpeech? = null
    try {
      instance = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          instance?.language = Locale.US
          ttsReady = true
        }
      }
    } catch (_: Exception) {
    }
    instance
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        ttsRef?.stop()
        ttsRef?.shutdown()
      } catch (_: Exception) {
      }
    }
  }

  val speakAloud: (String) -> Unit = { textToSpeak ->
    if (ttsReady && textToSpeak.isNotBlank()) {
      try {
        val cleanSpeech = textToSpeak.replace(Regex("[•#*`]"), "").take(600)
        ttsRef?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "instinct_live_voice")
      } catch (_: Exception) {
      }
    }
  }

  // Voice-to-text controller inside the chat input
  val chatVoiceController = rememberVoiceToTextController { _, spokenText ->
    if (spokenText.isNotBlank()) {
      if (liveVoiceModeActive) {
        onSendMessage(spokenText) { finalReply ->
          speakAloud(finalReply)
        }
      } else {
        draftQuestion = appendSpokenTranscript(draftQuestion, spokenText)
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(DarkCharcoal)
      .border(1.dp, ElectricLime.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
      .padding(12.dp)
      .testTag("ai_streaming_chat_panel")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Forum,
          contentDescription = null,
          tint = ElectricLime,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "GEMINI CHAT & HANDS-FREE VOICE",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = ElectricLime,
          letterSpacing = 0.7.sp
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Hands-Free Voice Mode Toggle Pill (Device SpeechRecognizer + TTS)
        if (onToggleLiveVoiceMode != null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (liveVoiceModeActive) ElectricLime.copy(alpha = 0.22f) else GraphiteSurface)
              .border(
                1.dp,
                if (liveVoiceModeActive) ElectricLime else GraphiteBorder,
                RoundedCornerShape(8.dp)
              )
              .clickable {
                val next = !liveVoiceModeActive
                onToggleLiveVoiceMode(next)
                if (!next) ttsRef?.stop()
              }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("toggle_live_voice_mode_button")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = "Hands-Free Voice Mode (Device Speech + TTS)",
                tint = if (liveVoiceModeActive) ElectricLime else TextSecondary,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (liveVoiceModeActive) "Hands-Free Voice ON" else "Voice + TTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (liveVoiceModeActive) ElectricLime else TextSecondary
              )
            }
          }
        }

        // Contextual Grounding Toggle Pill
        if (onToggleSearchGrounding != null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (searchGroundingEnabled) CyanElectric.copy(alpha = 0.18f) else GraphiteSurface)
              .border(
                1.dp,
                if (searchGroundingEnabled) CyanElectric else GraphiteBorder,
                RoundedCornerShape(8.dp)
              )
              .clickable { onToggleSearchGrounding(!searchGroundingEnabled) }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("toggle_search_grounding_button")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Contextual Grounding",
                tint = if (searchGroundingEnabled) CyanElectric else TextSecondary,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (searchGroundingEnabled) "Context Grounded" else "Memory Only",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (searchGroundingEnabled) CyanElectric else TextSecondary
              )
            }
          }
        }

        IconButton(
          onClick = {
            ttsRef?.stop()
            onClose()
          },
          modifier = Modifier
            .size(24.dp)
            .testTag("close_ai_chat_panel_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close streaming chat",
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Model Selector Row (`gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`)
    if (onSelectAiModel != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "MODEL:",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = TextMuted
        )
        AiModelTier.entries.forEach { tier ->
          val selected = tier == selectedAiModel
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(7.dp))
              .background(if (selected) CyanElectric.copy(alpha = 0.18f) else GraphiteSurface)
              .border(
                1.dp,
                if (selected) CyanElectric else GraphiteBorder,
                RoundedCornerShape(7.dp)
              )
              .clickable { onSelectAiModel(tier) }
              .padding(horizontal = 8.dp, vertical = 3.dp)
              .testTag("chat_model_pill_${tier.modelId}")
          ) {
            Text(
              text = "${tier.displayName} · ${tier.badge}",
              fontSize = 9.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) CyanElectric else TextSecondary
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
    }

    // Persona Role Selector Row
    if (onSelectPersona != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ROLE:",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = TextMuted
        )
        ChatPersonaRole.entries.forEach { role ->
          val selected = role == selectedPersona
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(7.dp))
              .background(if (selected) ElectricLime.copy(alpha = 0.18f) else GraphiteSurface)
              .border(
                1.dp,
                if (selected) ElectricLime else GraphiteBorder,
                RoundedCornerShape(7.dp)
              )
              .clickable { onSelectPersona(role) }
              .padding(horizontal = 8.dp, vertical = 3.dp)
              .testTag("chat_persona_pill_${role.id}")
          ) {
            Text(
              text = role.label,
              fontSize = 9.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) ElectricLime else TextSecondary
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }

    // Quick Follow-Up Suggestion Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      ChatQuickQuestionChip(
        label = "What is my normal pizza order?",
        onClick = {
          onSendMessage("What is my normal pizza order?") { reply ->
            if (liveVoiceModeActive) speakAloud(reply)
          }
        }
      )
      ChatQuickQuestionChip(
        label = "What should I do on my next store run?",
        onClick = {
          onSendMessage("What should I do on my next store run based on my reminders and preferences?") { reply ->
            if (liveVoiceModeActive) speakAloud(reply)
          }
        }
      )
      ChatQuickQuestionChip(
        label = "How do I recover a deleted file?",
        onClick = {
          onSendMessage("I accidentally deleted a file—what are the safest recovery steps?") { reply ->
            if (liveVoiceModeActive) speakAloud(reply)
          }
        }
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Messages list
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      messages.takeLast(6).forEach { msg ->
        val isUser = msg.role == "user"
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isUser) GraphiteSurface else GraphiteCard)
            .border(
              1.dp,
              if (isUser) GraphiteBorder else CyanElectric.copy(alpha = 0.35f),
              RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
            .testTag("ai_chat_message_${msg.id}")
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (isUser) "YOU" else "INSTINCT AI · ${msg.personaLabel.uppercase()} (${msg.modelUsed})",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isUser) ElectricLime else CyanElectric,
                letterSpacing = 0.6.sp
              )
              if (msg.isStreaming) {
                Text(
                  text = "STREAMING...",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = ElectricLime
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = msg.text,
              fontSize = 12.sp,
              color = TextPrimary,
              lineHeight = 17.sp
            )

            if (!isUser && !msg.isStreaming && msg.text.isNotBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // Speak message aloud button
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GraphiteSurface)
                    .clickable { speakAloud(msg.text) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("speak_chat_reply_button_${msg.id}")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                      contentDescription = "Speak reply aloud",
                      tint = ElectricLime,
                      modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Speak",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = ElectricLime
                    )
                  }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                      if (msg.isSavedAsMemory) ToxicGreenGlow.copy(alpha = 0.16f)
                      else CyanElectric.copy(alpha = 0.14f)
                    )
                    .clickable(enabled = !msg.isSavedAsMemory) {
                      onSaveReplyAsMemory(msg)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("save_chat_reply_button_${msg.id}")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = if (msg.isSavedAsMemory) Icons.Default.Check else Icons.Default.BookmarkAdd,
                      contentDescription = null,
                      tint = if (msg.isSavedAsMemory) ToxicGreenGlow else CyanElectric,
                      modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = if (msg.isSavedAsMemory) "Saved to Brain" else "Save Reply as Memory",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (msg.isSavedAsMemory) ToxicGreenGlow else CyanElectric
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    VoiceListeningStatusBanner(
      controller = chatVoiceController,
      accentColor = ElectricLime,
      listeningLabel = if (liveVoiceModeActive) "LIVE VOICE CONVERSATION — SPEAK NOW" else "LISTENING FOR CHAT QUESTION...",
      testTag = "chat_voice_listening_banner"
    )

    // Chat input field + Voice Mic + Send button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      VoiceInputMicButton(
        isListening = chatVoiceController.isListening,
        onClick = {
          chatVoiceController.toggleListening(
            targetKey = "ai_chat_voice",
            prompt = if (liveVoiceModeActive) "Speak to INSTINCT Live Voice..." else "Speak your question..."
          )
        },
        accentColor = ElectricLime,
        buttonSize = 42.dp,
        iconSize = 18.dp,
        contentDescription = "Speak question to Gemini Chatbot",
        testTag = "ai_chat_voice_mic_button"
      )

      OutlinedTextField(
        value = draftQuestion,
        onValueChange = { draftQuestion = it },
        placeholder = {
          Text(
            text = if (liveVoiceModeActive) "Tap mic for hands-free voice chat or type..." else "Ask a follow-up about your memories or fixes...",
            fontSize = 12.sp,
            color = TextMuted
          )
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
          onSend = {
            if (draftQuestion.isNotBlank() && !isStreaming) {
              val q = draftQuestion
              draftQuestion = ""
              onSendMessage(q) { reply ->
                if (liveVoiceModeActive) speakAloud(reply)
              }
            }
          }
        ),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = GraphiteSurface,
          unfocusedContainerColor = GraphiteSurface,
          focusedBorderColor = ElectricLime,
          unfocusedBorderColor = GraphiteBorder,
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary
        ),
        modifier = Modifier
          .weight(1f)
          .testTag("ai_chat_input_field")
      )

      IconButton(
        onClick = {
          if (draftQuestion.isNotBlank() && !isStreaming) {
            val q = draftQuestion
            draftQuestion = ""
            onSendMessage(q) { reply ->
              if (liveVoiceModeActive) speakAloud(reply)
            }
          }
        },
        enabled = draftQuestion.isNotBlank() && !isStreaming,
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(if (draftQuestion.isNotBlank() && !isStreaming) ElectricLime else GraphiteSurface)
          .testTag("ai_chat_send_button")
      ) {
        if (isStreaming) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = ElectricLime,
            strokeWidth = 2.dp
          )
        } else {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send follow-up question",
            tint = if (draftQuestion.isNotBlank()) JetBlack else TextMuted,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun AiActionPill(
  label: String,
  icon: ImageVector,
  accentColor: Color,
  isLoading: Boolean,
  isActive: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isActive) accentColor.copy(alpha = 0.2f) else GraphiteSurface)
      .border(
        width = 1.dp,
        color = if (isActive) accentColor else accentColor.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(enabled = !isLoading, onClick = onClick)
      .padding(horizontal = 11.dp, vertical = 8.dp)
      .testTag(testTag)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (isLoading) {
        CircularProgressIndicator(
          modifier = Modifier.size(14.dp),
          color = accentColor,
          strokeWidth = 2.dp
        )
      } else {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(15.dp)
        )
      }
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (isActive) accentColor else TextPrimary
      )
    }
  }
}

@Composable
private fun ChatQuickQuestionChip(
  label: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(GraphiteSurface)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 5.dp)
  ) {
    Text(
      text = label,
      fontSize = 10.sp,
      color = TextSecondary,
      fontWeight = FontWeight.Medium
    )
  }
}
