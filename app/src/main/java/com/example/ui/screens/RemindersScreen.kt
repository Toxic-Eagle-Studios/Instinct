package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Reminder
import com.example.ui.InstinctViewModel
import com.example.ui.components.WhileYoureAtItChecklistCard
import com.example.ui.components.WhileYoureAtItQuickHubStrip
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.DeepRedWarning
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RemindersScreen(
  viewModel: InstinctViewModel,
  modifier: Modifier = Modifier
) {
  val reminders by viewModel.reminders.collectAsState()
  val activeContextSession by viewModel.activeContextSession.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }
  var filterActiveOnly by remember { mutableStateOf(false) }
  var customContextInput by remember { mutableStateOf("") }

  val displayedReminders = reminders.filter {
    if (filterActiveOnly) !it.isCompleted else true
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(JetBlack)
      .statusBarsPadding()
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // 1. Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text(
              text = "WHILE YOU'RE AT IT",
              fontSize = 20.sp,
              fontWeight = FontWeight.ExtraBold,
              color = ElectricLime,
              letterSpacing = 1.sp
            )
            Text(
              text = "Unified contextual hub for errands, places, people & routines",
              fontSize = 12.sp,
              color = TextSecondary
            )
          }

          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(ElectricLime.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = null,
              tint = ElectricLime,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      // 2. Quick Context Mode Pills Strip
      item {
        WhileYoureAtItQuickHubStrip(
          presets = viewModel.contextPresets,
          activeContextLabel = activeContextSession?.contextLabel,
          onSelectPreset = { preset ->
            viewModel.activateContextSession(preset.query)
          }
        )
      }

      // 3. Custom Activity / Errand Context Launcher Bar
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GraphiteCard)
            .border(1.dp, GraphiteBorder, RoundedCornerShape(14.dp))
            .padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = customContextInput,
            onValueChange = { customContextInput = it },
            placeholder = {
              Text(
                text = "Start any activity (e.g., 'Heading to Target', 'Calling Mom')...",
                fontSize = 12.sp,
                color = TextMuted
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
              onGo = {
                if (customContextInput.isNotBlank()) {
                  viewModel.activateContextSession(customContextInput)
                  customContextInput = ""
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
              .testTag("reminders_hub_context_input")
          )

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(ElectricLime)
              .clickable {
                val query = customContextInput.ifBlank { "Grocery store & errands" }
                viewModel.activateContextSession(query)
                customContextInput = ""
              }
              .padding(horizontal = 12.dp, vertical = 14.dp)
              .testTag("reminders_hub_activate_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Activate While You're At It context",
                tint = JetBlack,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Go",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JetBlack
              )
            }
          }
        }
      }

      // 4. Active Interactive "While You're At It" Checklist Card
      if (activeContextSession != null) {
        item {
          WhileYoureAtItChecklistCard(
            session = activeContextSession!!,
            onMarkReminderDone = { viewModel.markReminderDone(it) },
            onSnoozeReminder = { viewModel.snoozeReminder(it) },
            onToggleCompanionTask = { viewModel.toggleCompanionTaskChecked(it) },
            onSaveCompanionTaskToBrain = { viewModel.saveCompanionTaskToBrain(it) },
            onDismiss = { viewModel.dismissActiveTriggers() }
          )
        }
      }

      // 5. Filter Chips
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilterTabChip(
            title = "All (${reminders.size})",
            isSelected = !filterActiveOnly,
            onClick = { filterActiveOnly = false },
            testTag = "filter_all_reminders"
          )
          FilterTabChip(
            title = "Active (${reminders.count { !it.isCompleted }})",
            isSelected = filterActiveOnly,
            onClick = { filterActiveOnly = true },
            testTag = "filter_active_reminders"
          )
        }
      }

      // 6. Reminders List or Empty State
      if (displayedReminders.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "No contextual reminders saved yet",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Tap any Quick Context mode above or add a reminder like \"Next time I go to Walmart, buy AA batteries\"",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
              )
            }
          }
        }
      } else {
        items(displayedReminders, key = { it.id }) { reminder ->
          ReminderCard(
            reminder = reminder,
            onToggleComplete = { viewModel.markReminderDone(reminder) },
            onActivateTriggerContext = {
              val triggerQuery = reminder.triggerActivity.ifBlank { reminder.title }
              viewModel.activateContextSession(triggerQuery)
            },
            onDelete = { viewModel.deleteReminder(reminder.id) }
          )
        }
      }

      item { Spacer(modifier = Modifier.height(88.dp)) }
    }

    // Add FAB
    FloatingActionButton(
      onClick = { showAddDialog = true },
      containerColor = ElectricLime,
      contentColor = JetBlack,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 80.dp, end = 20.dp)
        .testTag("add_reminder_fab")
    ) {
      Icon(imageVector = Icons.Default.Add, contentDescription = "Add new contextual reminder")
    }
  }

  if (showAddDialog) {
    AddReminderDialog(
      onDismiss = { showAddDialog = false },
      onSave = { title, trigger, recurrence ->
        viewModel.saveReminderDirectly(title, trigger, recurrence)
        showAddDialog = false
      }
    )
  }
}

@Composable
fun ReminderCard(
  reminder: Reminder,
  onToggleComplete: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
  onActivateTriggerContext: (() -> Unit)? = null
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(GraphiteCard)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(14.dp))
      .padding(14.dp)
      .testTag("reminder_item_${reminder.id}")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top
    ) {
      IconButton(
        onClick = onToggleComplete,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = if (reminder.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
          contentDescription = "Toggle completion",
          tint = if (reminder.isCompleted) ElectricLime else TextMuted,
          modifier = Modifier.size(22.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = reminder.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (reminder.isCompleted) TextMuted else TextPrimary,
            modifier = Modifier.weight(1f, fill = false)
          )
          if (reminder.category.isNotBlank()) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CyanElectric.copy(alpha = 0.14f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = reminder.category,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = CyanElectric
              )
            }
          }
        }

        if (reminder.triggerActivity.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(ElectricLime.copy(alpha = 0.12f))
              .border(1.dp, ElectricLime.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
              .clickable(enabled = onActivateTriggerContext != null) {
                onActivateTriggerContext?.invoke()
              }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("trigger_context_pill_${reminder.id}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Bolt,
              contentDescription = null,
              tint = ElectricLime,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "When: ${reminder.triggerActivity} (Tap to bundle)",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = ElectricLime
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(GraphiteSurface)
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = when (reminder.recurrence) {
                "every_time" -> "Every time"
                "next_time_only" -> "Next time only"
                else -> "Until completed"
              },
              fontSize = 10.sp,
              color = TextSecondary
            )
          }

          if (reminder.relatedLocations.isNotEmpty()) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(GraphiteSurface)
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = reminder.relatedLocations.first(),
                fontSize = 10.sp,
                color = TextSecondary
              )
            }
          }
        }
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete reminder",
          tint = DeepRedWarning.copy(alpha = 0.8f),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun FilterTabChip(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .background(if (isSelected) ElectricLime else GraphiteCard)
      .border(1.dp, if (isSelected) ElectricLime else GraphiteBorder, RoundedCornerShape(20.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag(testTag)
  ) {
    Text(
      text = title,
      fontSize = 12.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      color = if (isSelected) JetBlack else TextSecondary
    )
  }
}

@Composable
fun AddReminderDialog(
  onDismiss: () -> Unit,
  onSave: (title: String, trigger: String, recurrence: String) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var trigger by remember { mutableStateOf("") }
  var recurrence by remember { mutableStateOf("every_time") }

  val voiceController = com.example.ui.components.rememberVoiceToTextController { targetKey, spokenText ->
    if (targetKey == "trigger") {
      trigger = com.example.ui.components.appendSpokenTranscript(trigger, spokenText)
    } else {
      title = com.example.ui.components.appendSpokenTranscript(title, spokenText)
    }
  }

  AlertDialog(
    onDismissRequest = {
      if (voiceController.isListening) voiceController.stopListening()
      onDismiss()
    },
    containerColor = DarkCharcoal,
    title = {
      Text(
        text = "Add Contextual Reminder",
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
      )
    },
    text = {
      Column {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("What to remember", color = TextSecondary) },
          placeholder = { Text("Speak or type (e.g., Buy AA batteries)", color = TextMuted) },
          trailingIcon = {
            com.example.ui.components.VoiceInputMicButton(
              isListening = voiceController.isListeningFor("title"),
              onClick = {
                voiceController.toggleListening(
                  targetKey = "title",
                  prompt = "Speak what you want to remember..."
                )
              },
              accentColor = ElectricLime,
              buttonSize = 36.dp,
              iconSize = 18.dp,
              contentDescription = "Speak reminder title with voice-to-text",
              testTag = "add_reminder_title_voice_button"
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = ElectricLime,
            unfocusedBorderColor = GraphiteBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_reminder_title_input")
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
          value = trigger,
          onValueChange = { trigger = it },
          label = { Text("Trigger activity / place", color = TextSecondary) },
          placeholder = { Text("Speak or type (e.g., Heading to Walmart)", color = TextMuted) },
          trailingIcon = {
            com.example.ui.components.VoiceInputMicButton(
              isListening = voiceController.isListeningFor("trigger"),
              onClick = {
                voiceController.toggleListening(
                  targetKey = "trigger",
                  prompt = "Speak when or where to trigger this reminder..."
                )
              },
              accentColor = ElectricLime,
              buttonSize = 36.dp,
              iconSize = 18.dp,
              contentDescription = "Speak reminder trigger with voice-to-text",
              testTag = "add_reminder_trigger_voice_button"
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = ElectricLime,
            unfocusedBorderColor = GraphiteBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_reminder_trigger_input")
        )

        com.example.ui.components.VoiceListeningStatusBanner(
          controller = voiceController,
          accentColor = ElectricLime,
          listeningLabel = "LISTENING FOR REMINDER",
          testTag = "add_reminder_voice_banner"
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Recurrence:",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          RadioButton(
            selected = recurrence == "every_time",
            onClick = { recurrence = "every_time" },
            colors = RadioButtonDefaults.colors(selectedColor = ElectricLime)
          )
          Text("Every time", color = TextPrimary, fontSize = 12.sp)
          Spacer(modifier = Modifier.width(8.dp))
          RadioButton(
            selected = recurrence == "next_time_only",
            onClick = { recurrence = "next_time_only" },
            colors = RadioButtonDefaults.colors(selectedColor = ElectricLime)
          )
          Text("Next time only", color = TextPrimary, fontSize = 12.sp)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (voiceController.isListening) voiceController.stopListening()
          if (title.isNotBlank()) onSave(title, trigger, recurrence)
        },
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
        enabled = title.isNotBlank()
      ) {
        Text("Save", color = JetBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = {
        if (voiceController.isListening) voiceController.stopListening()
        onDismiss()
      }) {
        Text("Cancel", color = TextSecondary)
      }
    }
  )
}
