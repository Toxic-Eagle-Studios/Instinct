package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContextQuickPreset
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.data.model.SuggestedCompanionTask
import com.example.data.model.WhileYoureAtItSession
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

/**
 * Horizontal strip of Quick Context Mode pills allowing one-tap activation of
 * the "While You're At It" contextual engine for common errands, places, and activities.
 */
@Composable
fun WhileYoureAtItQuickHubStrip(
  presets: List<ContextQuickPreset>,
  activeContextLabel: String?,
  onSelectPreset: (ContextQuickPreset) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("while_youre_at_it_quick_hub_strip")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Bolt,
          contentDescription = null,
          tint = ElectricLime,
          modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = "WHILE YOU'RE AT IT — QUICK CONTEXT MODES",
          fontSize = 10.sp,
          fontWeight = FontWeight.ExtraBold,
          color = ElectricLime,
          letterSpacing = 0.8.sp
        )
      }
      Text(
        text = "Tap to bundle tasks & prefs",
        fontSize = 10.sp,
        color = TextMuted
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      presets.forEach { preset ->
        val isSelected = activeContextLabel != null &&
          (activeContextLabel.contains(preset.label, ignoreCase = true) ||
            preset.query.equals(activeContextLabel, ignoreCase = true))

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) ElectricLime.copy(alpha = 0.18f) else GraphiteCard)
            .border(
              width = 1.dp,
              color = if (isSelected) ElectricLime else GraphiteBorder,
              shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelectPreset(preset) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("context_preset_${preset.id}")
        ) {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(if (isSelected) ElectricLime else CyanElectric)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = preset.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) ElectricLime else TextPrimary
              )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = preset.subtitle,
              fontSize = 10.sp,
              color = TextSecondary
            )
          }
        }
      }
    }
  }
}

/**
 * Interactive "While You're At It" Checklist Card combining:
 * 1. Checkable Contextual Reminders (While You're At It)
 * 2. Proactive AI-Suggested Companion Tasks (with checkbox & one-tap Save to Brain)
 * 3. Matched Personal Preferences (Exactly) with one-tap copy
 * 4. Relevant Past Fixes & Tools Needed (Undo IRL)
 */
@Composable
fun WhileYoureAtItChecklistCard(
  session: WhileYoureAtItSession,
  onMarkReminderDone: (Reminder) -> Unit,
  onSnoozeReminder: (Reminder) -> Unit,
  onToggleCompanionTask: (String) -> Unit,
  onSaveCompanionTaskToBrain: (SuggestedCompanionTask) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val animatedProgress by animateFloatAsState(
    targetValue = session.progressFraction,
    label = "checklist_progress"
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(GraphiteCard)
      .border(1.5.dp, ElectricLime, RoundedCornerShape(18.dp))
      .padding(16.dp)
      .testTag("contextual_trigger_banner")
  ) {
    Column {
      // Top Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = "Context trigger",
            tint = ElectricLime,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "WHILE YOU'RE AT IT: ${session.contextLabel.uppercase()}",
              fontSize = 14.sp,
              fontWeight = FontWeight.ExtraBold,
              color = ElectricLime,
              letterSpacing = 0.7.sp
            )
            Text(
              text = "${session.completedChecklistCount} of ${session.totalChecklistCount} checklist items completed · ${session.contextCategory}",
              fontSize = 11.sp,
              color = TextSecondary
            )
          }
        }

        if (session.isGeneratingSuggestions) {
          CircularProgressIndicator(
            modifier = Modifier
              .size(18.dp)
              .padding(end = 6.dp),
            color = ElectricLime,
            strokeWidth = 2.dp
          )
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(28.dp)
            .testTag("dismiss_context_session_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss checklist",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Animated Checklist Progress Bar
      LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = ElectricLime,
        trackColor = DarkCharcoal
      )

      if (session.aiCoachingNote.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = session.aiCoachingNote,
          fontSize = 12.sp,
          color = TextSecondary,
          lineHeight = 17.sp
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // SECTION 1: Matched Contextual Reminders
      if (session.matchedReminders.isNotEmpty()) {
        Text(
          text = "YOUR TRIGGERED REMINDERS (${session.matchedReminders.size})",
          fontSize = 10.sp,
          fontWeight = FontWeight.ExtraBold,
          color = ElectricLime,
          letterSpacing = 0.7.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        session.matchedReminders.forEach { reminder ->
          val isCompletedInSession = reminder.isCompleted || reminder.id in session.completedReminderIds
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(DarkCharcoal)
              .border(
                1.dp,
                if (isCompletedInSession) ElectricLime.copy(alpha = 0.5f) else GraphiteBorder,
                RoundedCornerShape(12.dp)
              )
              .padding(12.dp)
          ) {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (isCompletedInSession) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                  contentDescription = null,
                  tint = if (isCompletedInSession) ElectricLime else TextMuted,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = reminder.title,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (isCompletedInSession) TextSecondary else TextPrimary,
                  textDecoration = if (isCompletedInSession) TextDecoration.LineThrough else TextDecoration.None,
                  modifier = Modifier.weight(1f)
                )
                if (reminder.recurrence == "every_time") {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(ElectricLime.copy(alpha = 0.15f))
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = "Recurring",
                      fontSize = 10.sp,
                      color = ElectricLime,
                      fontWeight = FontWeight.Medium
                    )
                  }
                }
              }

              if (reminder.triggerActivity.isNotBlank()) {
                Text(
                  text = "Triggered by: \"${reminder.triggerActivity}\"",
                  fontSize = 11.sp,
                  color = TextMuted,
                  modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
              } else {
                Spacer(modifier = Modifier.height(6.dp))
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = { onMarkReminderDone(reminder) },
                  colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .testTag("reminder_done_${reminder.id}")
                ) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = JetBlack,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (reminder.recurrence == "every_time") "Noted" else "Done",
                    color = JetBlack,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                OutlinedButton(
                  onClick = { onSnoozeReminder(reminder) },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .testTag("reminder_snooze_${reminder.id}")
                ) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Snooze",
                    color = TextSecondary,
                    fontSize = 12.sp
                  )
                }
              }
            }
          }
        }
      }

      // SECTION 2: Proactive AI Companion Tasks
      if (session.aiSuggestedTasks.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = ElectricLime,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "AI SUGGESTED 'WHILE YOU'RE AT IT' TASKS",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ElectricLime,
            letterSpacing = 0.7.sp
          )
        }
        Spacer(modifier = Modifier.height(6.dp))

        session.aiSuggestedTasks.forEach { task ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(DarkCharcoal)
              .border(
                1.dp,
                if (task.isChecked) ElectricLime.copy(alpha = 0.55f) else GraphiteBorder,
                RoundedCornerShape(10.dp)
              )
              .clickable { onToggleCompanionTask(task.id) }
              .padding(10.dp)
              .testTag("companion_task_${task.id}")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = if (task.isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                  contentDescription = "Toggle companion task",
                  tint = if (task.isChecked) ElectricLime else TextMuted,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = task.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.isChecked) TextSecondary else TextPrimary,
                    textDecoration = if (task.isChecked) TextDecoration.LineThrough else TextDecoration.None
                  )
                  if (task.reason.isNotBlank()) {
                    Text(
                      text = task.reason,
                      fontSize = 11.sp,
                      color = TextMuted
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(
                    if (task.isSavedToBrain) ElectricLime.copy(alpha = 0.16f)
                    else GraphiteSurface
                  )
                  .border(
                    1.dp,
                    if (task.isSavedToBrain) ElectricLime else GraphiteBorder,
                    RoundedCornerShape(8.dp)
                  )
                  .clickable(enabled = !task.isSavedToBrain) {
                    onSaveCompanionTaskToBrain(task)
                  }
                  .padding(horizontal = 8.dp, vertical = 5.dp)
                  .testTag("save_companion_task_${task.id}")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = if (task.isSavedToBrain) Icons.Default.Check else Icons.Default.AddTask,
                    contentDescription = "Save companion task to reminders",
                    tint = if (task.isSavedToBrain) ElectricLime else CyanElectric,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (task.isSavedToBrain) "Saved" else "Save",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.isSavedToBrain) ElectricLime else CyanElectric
                  )
                }
              }
            }
          }
        }
      }

      // SECTION 3: Matched Saved Preferences (EXACTLY)
      if (session.matchedPreferences.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            tint = CyanElectric,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "MATCHED SAVED PREFERENCES (EXACTLY):",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CyanElectric,
            letterSpacing = 0.7.sp
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        session.matchedPreferences.forEach { pref ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(DarkCharcoal)
              .border(1.dp, CyanElectric.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
              .padding(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = pref.title,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextPrimary
                )
                Text(
                  text = pref.description,
                  fontSize = 12.sp,
                  color = TextSecondary
                )
                if (pref.details.isNotBlank()) {
                  Text(
                    text = pref.details,
                    fontSize = 11.sp,
                    color = CyanElectric
                  )
                }
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(GraphiteSurface)
                  .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(pref.title, "${pref.title}: ${pref.description}")
                    clipboard.setPrimaryClip(clip)
                  }
                  .padding(horizontal = 8.dp, vertical = 5.dp)
                  .testTag("checklist_copy_pref_${pref.id}")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy preference spec",
                    tint = CyanElectric,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Copy",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanElectric
                  )
                }
              }
            }
          }
        }
      }

      // SECTION 4: Matched Past Fixes & Tools Needed (UNDO IRL)
      if (session.matchedFixes.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            tint = ToxicGreenGlow,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "RELEVANT FIXES & TOOLS NEEDED (UNDO IRL):",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ToxicGreenGlow,
            letterSpacing = 0.7.sp
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        session.matchedFixes.forEach { fix ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(DarkCharcoal)
              .border(1.dp, ToxicGreenGlow.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
              .padding(10.dp)
          ) {
            Column {
              Text(
                text = fix.problemSummary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
              )
              Text(
                text = fix.bestFix,
                fontSize = 12.sp,
                color = TextSecondary
              )
              if (fix.toolsNeeded.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Supplies / Tools: ${fix.toolsNeeded.joinToString(", ")}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = ToxicGreenGlow
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Backwards-compatible wrapper that renders the interactive checklist card.
 */
@Composable
fun ContextualTriggerBanner(
  reminders: List<Reminder>,
  preferences: List<Preference>,
  onMarkDone: (Reminder) -> Unit,
  onSnooze: (Reminder) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(visible = reminders.isNotEmpty() || preferences.isNotEmpty()) {
    val fallbackSession = WhileYoureAtItSession(
      contextLabel = reminders.firstOrNull()?.triggerActivity?.ifBlank { "Active Moment" } ?: "Active Moment",
      contextQuery = reminders.firstOrNull()?.triggerActivity.orEmpty(),
      aiCoachingNote = "You wanted to remember something for this exact moment:",
      matchedReminders = reminders,
      matchedPreferences = preferences
    )
    WhileYoureAtItChecklistCard(
      session = fallbackSession,
      onMarkReminderDone = onMarkDone,
      onSnoozeReminder = onSnooze,
      onToggleCompanionTask = {},
      onSaveCompanionTaskToBrain = {},
      onDismiss = onDismiss,
      modifier = modifier
    )
  }
}
