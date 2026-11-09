package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiModelTier
import com.example.data.model.MemoryEntry
import com.example.data.repository.SyncStatus
import com.example.ui.InstinctViewModel
import com.example.ui.NavTab
import com.example.ui.theme.AmberNotice
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
import com.example.ui.theme.ToxicGreenGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
  viewModel: InstinctViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val currentUser by viewModel.authRepository.currentUser.collectAsState()
  val currentUserId by viewModel.currentUserId.collectAsState()
  val syncStatus by viewModel.syncStatus.collectAsState()

  val useAi by viewModel.useAi.collectAsState()
  val selectedAiModel by viewModel.selectedAiModel.collectAsState()
  val searchGroundingEnabled by viewModel.searchGroundingEnabled.collectAsState()
  val reminderAlertsEnabled by viewModel.reminderAlertsEnabled.collectAsState()

  val memoryEntries by viewModel.memoryEntries.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val preferences by viewModel.preferences.collectAsState()
  val fixes by viewModel.fixes.collectAsState()

  var showClearConfirmDialog by remember { mutableStateOf(false) }
  var showImportJsonDialog by remember { mutableStateOf(false) }
  var importJsonDraft by remember { mutableStateOf("") }
  var exportedContent by remember { mutableStateOf<String?>(null) }
  var exportTitle by remember { mutableStateOf("Memory Export Ready") }
  var isExporting by remember { mutableStateOf(false) }
  var manageMemoriesExpanded by remember { mutableStateOf(true) }
  var selectedFilter by remember { mutableStateOf("ALL") }

  // Zero-permission SAF file saver for JSON/text export
  val saveDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri: Uri? ->
    val contentToWrite = exportedContent
    if (uri != null && contentToWrite != null) {
      coroutineScope.launch {
        try {
          withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
              output.write(contentToWrite.toByteArray(Charsets.UTF_8))
            }
          }
          viewModel.showUserMessage("Memory backup saved to file.")
        } catch (e: Exception) {
          viewModel.showUserMessage("Failed to write export file.")
        }
      }
    }
  }

  // Zero-permission SAF file picker for JSON backup import
  val openDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      coroutineScope.launch {
        try {
          val jsonString = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
          }
          if (!jsonString.isNullOrBlank()) {
            viewModel.importMemoriesFromJson(jsonString)
          } else {
            viewModel.showUserMessage("Selected backup file is empty.")
          }
        } catch (e: Exception) {
          viewModel.showUserMessage("Could not read backup file.")
        }
      }
    }
  }

  val completedRemindersCount = reminders.count { it.isCompleted }
  val totalItemsCount = maxOf(memoryEntries.size, reminders.size + preferences.size + fixes.size)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(JetBlack)
      .testTag("settings_screen")
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
      // 1. Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = "CONTROL YOUR DATA",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ElectricLime,
            letterSpacing = 0.8.sp
          )
          Text(
            text = "SETTINGS & MEMORY",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = 1.sp
          )
          Text(
            text = "Gemini intelligence, Google cloud sync, SQLite storage & JSON backups",
            fontSize = 12.sp,
            color = TextSecondary
          )
        }

        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(GraphiteCard)
            .border(1.dp, GraphiteBorder, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = ElectricLime,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 2. AI Engine & Gemini Intelligence Card
      Text(
        text = "GEMINI INTELLIGENCE & ROUTING",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp
      )
      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(GraphiteCard)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
          .padding(16.dp)
          .testTag("gemini_intelligence_card")
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = if (useAi) ElectricLime else CyanElectric,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Use Gemini Intelligence",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Send your input and a short preview of recent memories to Gemini for interpretation, or toggle off for 100% offline deterministic local rules.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (useAi) {
                  "Current engine: Gemini ready (${selectedAiModel.modelId})"
                } else {
                  "Current engine: Local rules only (Offline deterministic mode)"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (useAi) ElectricLime else CyanElectric
              )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
              checked = useAi,
              onCheckedChange = { viewModel.setUseAi(it) },
              colors = SwitchDefaults.colors(
                checkedThumbColor = JetBlack,
                checkedTrackColor = ElectricLime,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkCharcoal
              ),
              modifier = Modifier.testTag("use_gemini_intelligence_switch")
            )
          }

          AnimatedVisibility(visible = useAi) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
              Text(
                text = "ACTIVE GEMINI MODEL TIER:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.7.sp
              )
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                AiModelTier.entries.forEach { tier ->
                  val isSelected = tier == selectedAiModel
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(10.dp))
                      .background(if (isSelected) CyanElectric.copy(alpha = 0.18f) else DarkCharcoal)
                      .border(
                        1.dp,
                        if (isSelected) CyanElectric else GraphiteBorder,
                        RoundedCornerShape(10.dp)
                      )
                      .clickable { viewModel.setSelectedAiModel(tier) }
                      .padding(horizontal = 10.dp, vertical = 8.dp)
                      .testTag("settings_model_${tier.modelId}")
                  ) {
                    Column {
                      Text(
                        text = "${tier.displayName} (${tier.badge})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CyanElectric else TextPrimary
                      )
                      Text(
                        text = tier.description,
                        fontSize = 9.sp,
                        color = TextSecondary
                      )
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Google Search Grounding row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = CyanElectric,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Column {
                    Text(
                      text = "Contextual Grounding Guidance",
                      fontSize = 13.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = TextPrimary
                    )
                    Text(
                      text = "Prompt-guided real-world context for troubleshooting (AI-synthesized — verify critical facts)",
                      fontSize = 11.sp,
                      color = TextSecondary
                    )
                  }
                }
                Switch(
                  checked = searchGroundingEnabled,
                  onCheckedChange = { viewModel.setSearchGroundingEnabled(it) },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = JetBlack,
                    checkedTrackColor = CyanElectric,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkCharcoal
                  ),
                  modifier = Modifier.testTag("settings_search_grounding_switch")
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 3. User Profile, Google Sign-In & Cloud Copy Card
      Text(
        text = "GOOGLE SIGN-IN & CLOUD SYNC",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp
      )
      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(GraphiteCard)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
          .padding(16.dp)
          .testTag("profile_card")
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                  if (currentUser != null) ElectricLime.copy(alpha = 0.14f)
                  else CyanElectric.copy(alpha = 0.14f)
                )
                .border(
                  1.dp,
                  if (currentUser != null) ElectricLime else CyanElectric,
                  CircleShape
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "User Profile Avatar",
                tint = if (currentUser != null) ElectricLime else CyanElectric,
                modifier = Modifier.size(30.dp)
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (currentUser != null) {
                    currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Authenticated User"
                  } else {
                    "This Device (Local SQLite)"
                  },
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                      if (currentUser != null) ElectricLime.copy(alpha = 0.15f)
                      else CyanElectric.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = if (currentUser != null) "CLOUD" else "LOCAL",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentUser != null) ElectricLime else CyanElectric
                  )
                }
              }

              Text(
                text = if (currentUser != null) {
                  "Signed in as ${currentUser?.email ?: "Google Account"}. Cloud records sync via Firestore."
                } else {
                  "Sign in with Google to sync memories across devices. Local records stay private on this device."
                },
                fontSize = 12.sp,
                color = TextSecondary
              )

              Spacer(modifier = Modifier.height(2.dp))

              Text(
                text = "ID: ${currentUserId.ifBlank { "guest_user" }}",
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Sync Status & Auth Action Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val (syncLabel, syncIcon, syncColor) = when (syncStatus) {
              is SyncStatus.Synced -> Triple("Synced with Cloud", Icons.Default.CloudDone, ElectricLime)
              is SyncStatus.Syncing -> Triple("Syncing Memories...", Icons.Default.CloudSync, AmberNotice)
              is SyncStatus.Offline -> Triple("On-Device SQLite", Icons.Default.CloudOff, CyanElectric)
              is SyncStatus.Error -> Triple("Sync Alert (Local Safe)", Icons.Default.Warning, DeepRedWarning)
              is SyncStatus.Idle -> Triple(
                if (currentUser != null) "Cloud Sync Ready" else "100% On-Device Privacy",
                if (currentUser != null) Icons.Default.CloudDone else Icons.Default.Security,
                if (currentUser != null) ElectricLime else CyanElectric
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = syncIcon,
                contentDescription = syncLabel,
                tint = syncColor,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = syncLabel,
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { viewModel.syncMemoriesNow() },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .height(34.dp)
                  .testTag("sync_now_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = "Sync now",
                  tint = CyanElectric,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sync", fontSize = 11.sp, color = CyanElectric)
              }

              if (currentUser != null) {
                OutlinedButton(
                  onClick = {
                    viewModel.authRepository.signOut(coroutineScope) {
                      viewModel.showUserMessage("Signed out.")
                    }
                  },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .height(34.dp)
                    .testTag("auth_action_button")
                ) {
                  Text("Sign Out", fontSize = 11.sp, color = TextSecondary)
                }
              } else {
                Button(
                  onClick = {
                    (context as? Activity)?.let { activity ->
                      viewModel.authRepository.signInWithGoogle(
                        activity = activity,
                        onSuccess = { viewModel.showUserMessage("Signed in with Google.") },
                        onError = { err -> viewModel.showUserMessage(err) },
                        onCancelled = {},
                        scope = coroutineScope
                      )
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .height(34.dp)
                    .testTag("auth_action_button")
                ) {
                  Text(
                    text = "Sign In with Google",
                    fontSize = 11.sp,
                    color = JetBlack,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }

          // Explicit "Copy device memories to cloud" row (`moveLocalToCloud` from App.jsx)
          Spacer(modifier = Modifier.height(12.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(DarkCharcoal)
              .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Copy Device Memories to Cloud",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
                Text(
                  text = "Copies your on-device Room SQLite memories into your Google Cloud account while preserving all local copies.",
                  fontSize = 11.sp,
                  color = TextSecondary
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              OutlinedButton(
                onClick = { viewModel.copyDeviceMemoriesToCloud() },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .height(36.dp)
                  .testTag("copy_to_cloud_button")
              ) {
                Icon(
                  imageVector = Icons.Default.CloudUpload,
                  contentDescription = null,
                  tint = ElectricLime,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy to Cloud", fontSize = 11.sp, color = ElectricLime, fontWeight = FontWeight.Bold)
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Android 13+ runtime permission launcher for POST_NOTIFICATIONS
          val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
          ) { granted ->
            viewModel.setReminderAlertsEnabled(true)
            if (!granted) {
              viewModel.showUserMessage("In-app reminder alerts active (system notification permission was declined).")
            }
          }

          // Reminder Alerts (while INSTINCT is open) row
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(DarkCharcoal)
              .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "In-App Reminder Alerts",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
                Text(
                  text = "Monitors reminders with an optional due date while INSTINCT is open and posts a local notification.",
                  fontSize = 11.sp,
                  color = TextSecondary
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Switch(
                checked = reminderAlertsEnabled,
                onCheckedChange = { enabled ->
                  if (enabled &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    !com.example.util.ReminderNotificationHelper.hasNotificationPermission(context)
                  ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    viewModel.setReminderAlertsEnabled(enabled)
                  }
                },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = JetBlack,
                  checkedTrackColor = ElectricLime,
                  uncheckedThumbColor = TextSecondary,
                  uncheckedTrackColor = GraphiteCard
                ),
                modifier = Modifier.testTag("reminder_alerts_switch")
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 4. Personal Data & Stored Memory Breakdown (Room SQLite + Firestore Inspector)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "STORED MEMORY OVERVIEW ($totalItemsCount)",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = TextMuted,
          letterSpacing = 0.8.sp
        )

        Text(
          text = if (manageMemoriesExpanded) "Hide Inspector" else "Inspect Items",
          fontSize = 11.sp,
          color = CyanElectric,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .clickable { manageMemoriesExpanded = !manageMemoriesExpanded }
            .padding(4.dp)
        )
      }
      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(GraphiteCard)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
          .padding(14.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            DataCategoryTile(
              title = "Reminders",
              count = reminders.size,
              subtitle = "While You're At It",
              icon = Icons.Default.NotificationsActive,
              accent = ElectricLime,
              onClick = { viewModel.setTab(NavTab.REMINDERS) },
              modifier = Modifier.weight(1f)
            )
            DataCategoryTile(
              title = "Fixes",
              count = fixes.size,
              subtitle = "Undo IRL",
              icon = Icons.Default.Build,
              accent = ToxicGreenGlow,
              onClick = { viewModel.setTab(NavTab.FIXES) },
              modifier = Modifier.weight(1f)
            )
            DataCategoryTile(
              title = "Preferences",
              count = preferences.size,
              subtitle = "Exactly",
              icon = Icons.Default.Bookmark,
              accent = CyanElectric,
              onClick = { viewModel.setTab(NavTab.MEMORIES) },
              modifier = Modifier.weight(1f)
            )
          }

          // Interactive Memory Inspector & Individual Deletion
          AnimatedVisibility(visible = manageMemoriesExpanded) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                FilterChipButton(
                  label = "All (${memoryEntries.size})",
                  selected = selectedFilter == "ALL",
                  onClick = { selectedFilter = "ALL" }
                )
                FilterChipButton(
                  label = "Reminders (${memoryEntries.count { it is MemoryEntry.Reminder }})",
                  selected = selectedFilter == "REMINDER",
                  onClick = { selectedFilter = "REMINDER" }
                )
                FilterChipButton(
                  label = "Fixes (${memoryEntries.count { it is MemoryEntry.FixHistory }})",
                  selected = selectedFilter == "FIX",
                  onClick = { selectedFilter = "FIX" }
                )
                FilterChipButton(
                  label = "Preferences (${memoryEntries.count { it is MemoryEntry.Preference }})",
                  selected = selectedFilter == "PREF",
                  onClick = { selectedFilter = "PREF" }
                )
              }

              Spacer(modifier = Modifier.height(10.dp))

              val filteredEntries = remember(memoryEntries, selectedFilter) {
                when (selectedFilter) {
                  "REMINDER" -> memoryEntries.filterIsInstance<MemoryEntry.Reminder>()
                  "FIX" -> memoryEntries.filterIsInstance<MemoryEntry.FixHistory>()
                  "PREF" -> memoryEntries.filterIsInstance<MemoryEntry.Preference>()
                  else -> memoryEntries
                }
              }

              if (filteredEntries.isEmpty()) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkCharcoal)
                    .padding(12.dp)
                ) {
                  Text(
                    text = "No stored memories in this category.",
                    fontSize = 12.sp,
                    color = TextMuted
                  )
                }
              } else {
                filteredEntries.take(8).forEach { entry ->
                  ManageableMemoryRow(
                    entry = entry,
                    onDelete = {
                      when (entry) {
                        is MemoryEntry.Reminder -> viewModel.deleteReminder(entry.id)
                        is MemoryEntry.FixHistory -> viewModel.deleteFix(entry.id)
                        is MemoryEntry.Preference -> viewModel.deletePreference(entry.id)
                      }
                      viewModel.deleteUnifiedMemory(entry.id)
                    }
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 5. Data Export, Import & Portability Controls
      Text(
        text = "BACKUP EXPORT & RESTORE (JSON)",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp
      )
      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(GraphiteCard)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
          .padding(16.dp)
      ) {
        Column {
          Text(
            text = "Download or Restore Your Memories",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Export a complete JSON backup (`{ app: \"INSTINCT\", memories: [...] }`), generate a readable summary, or restore an existing INSTINCT JSON backup into your active memory collection.",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                coroutineScope.launch {
                  isExporting = true
                  exportTitle = "Portable JSON Memory Export"
                  exportedContent = viewModel.instinctRepository.exportAllDataJson(currentUserId)
                  isExporting = false
                }
              },
              enabled = !isExporting,
              colors = ButtonDefaults.buttonColors(containerColor = CyanElectric),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .testTag("export_data_button")
            ) {
              Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = JetBlack,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Export JSON",
                color = JetBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }

            OutlinedButton(
              onClick = { showImportJsonDialog = true },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .testTag("import_json_button")
            ) {
              Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = null,
                tint = ElectricLime,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Import JSON",
                color = ElectricLime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }

            OutlinedButton(
              onClick = {
                coroutineScope.launch {
                  isExporting = true
                  exportTitle = "Readable Memory Summary"
                  exportedContent = viewModel.instinctRepository.exportReadableReport(currentUserId)
                  isExporting = false
                }
              },
              enabled = !isExporting,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .testTag("export_report_button")
            ) {
              Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = CyanElectric,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Summary",
                color = CyanElectric,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 6. Data Deletion & Privacy Controls
      Text(
        text = "DELETE & RESET CONTROLS",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp
      )
      Spacer(modifier = Modifier.height(8.dp))

      // Clear Completed Reminders Option
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(GraphiteCard)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(14.dp))
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Clear Completed Reminders",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Text(
              text = if (completedRemindersCount > 0) {
                "Remove $completedRemindersCount completed reminder(s) while keeping active items."
              } else {
                "No completed reminders waiting for cleanup."
              },
              fontSize = 12.sp,
              color = TextSecondary
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          OutlinedButton(
            onClick = { viewModel.clearCompletedReminders() },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .height(36.dp)
              .testTag("clear_completed_reminders_button")
          ) {
            Icon(
              imageVector = Icons.Default.CleaningServices,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clean Up", color = TextSecondary, fontSize = 11.sp)
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Wipe All Personal Data Card
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(GraphiteCard)
          .border(1.dp, DeepRedWarning.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Delete Current Collection",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = DeepRedWarning
            )
            Text(
              text = "Deletes all $totalItemsCount records in ${if (currentUser != null) "your cloud account & device" else "this device"} after confirmation. Export a backup first.",
              fontSize = 12.sp,
              color = TextSecondary
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          OutlinedButton(
            onClick = { showClearConfirmDialog = true },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .height(36.dp)
              .testTag("clear_data_button")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteForever,
              contentDescription = null,
              tint = DeepRedWarning,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Delete All", color = DeepRedWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      Spacer(modifier = Modifier.height(100.dp))
    }
  }

  // Import JSON Backup Dialog (File Picker + Paste JSON)
  if (showImportJsonDialog) {
    AlertDialog(
      onDismissRequest = { showImportJsonDialog = false },
      containerColor = DarkCharcoal,
      title = {
        Text(
          text = "Restore INSTINCT JSON Backup",
          color = ElectricLime,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Choose a .json backup file from your device or paste an INSTINCT JSON backup below:",
            color = TextSecondary,
            fontSize = 12.sp
          )
          OutlinedButton(
            onClick = {
              showImportJsonDialog = false
              openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pick_json_file_button")
          ) {
            Icon(
              imageVector = Icons.Default.FileUpload,
              contentDescription = null,
              tint = CyanElectric,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Choose JSON File from Device", color = CyanElectric, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedTextField(
            value = importJsonDraft,
            onValueChange = { importJsonDraft = it },
            placeholder = {
              Text(
                text = "{\"app\":\"INSTINCT\",\"memories\":[{\"type\":\"reminder\",\"title\":\"Ask Dad about Friday\",\"context\":\"Dad\"}]}",
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
              )
            },
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = GraphiteSurface,
              unfocusedContainerColor = GraphiteSurface,
              focusedBorderColor = ElectricLime,
              unfocusedBorderColor = GraphiteBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(130.dp)
              .testTag("import_json_paste_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (importJsonDraft.isNotBlank()) {
              viewModel.importMemoriesFromJson(importJsonDraft)
              importJsonDraft = ""
              showImportJsonDialog = false
            }
          },
          enabled = importJsonDraft.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
          modifier = Modifier.testTag("confirm_import_json_button")
        ) {
          Text("Restore Backup", color = JetBlack, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showImportJsonDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // Confirmation Dialog for Full Memory Deletion
  if (showClearConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showClearConfirmDialog = false },
      containerColor = DarkCharcoal,
      title = {
        Text(
          text = "Permanently Delete All Stored Memories?",
          color = DeepRedWarning,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Text(
          text = "This will permanently delete all saved contextual reminders, personal preferences, and problem-solving fixes from your device and cloud storage. This action cannot be undone unless you exported a backup.",
          color = TextPrimary,
          fontSize = 13.sp,
          lineHeight = 18.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearAllData()
            showClearConfirmDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = DeepRedWarning),
          modifier = Modifier.testTag("confirm_clear_data_button")
        ) {
          Text("Delete Everything", color = TextPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirmDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // Export Preview, Copy, Share & Save File Dialog
  if (exportedContent != null) {
    AlertDialog(
      onDismissRequest = { exportedContent = null },
      containerColor = DarkCharcoal,
      title = {
        Text(
          text = exportTitle,
          color = CyanElectric,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp
        )
      },
      text = {
        Column {
          Text(
            text = "Copy to clipboard, share with another app, or save directly to a file on your device.",
            color = TextSecondary,
            fontSize = 12.sp
          )
          Spacer(modifier = Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(190.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(GraphiteSurface)
              .border(1.dp, GraphiteBorder, RoundedCornerShape(10.dp))
              .padding(10.dp)
              .verticalScroll(rememberScrollState())
              .testTag("export_preview_box")
          ) {
            Text(
              text = exportedContent!!,
              color = TextPrimary,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Action Row: Save File & Share
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                saveDocumentLauncher.launch("instinct-backup.json")
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag("save_export_file_button")
            ) {
              Icon(
                imageVector = Icons.Default.SaveAlt,
                contentDescription = "Save to file",
                tint = ElectricLime,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Save File", fontSize = 11.sp, color = ElectricLime)
            }

            OutlinedButton(
              onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "INSTINCT Memory Export")
                  putExtra(Intent.EXTRA_TEXT, exportedContent)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share INSTINCT Export"))
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag("share_export_button")
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share export",
                tint = CyanElectric,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Share", fontSize = 11.sp, color = CyanElectric)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("INSTINCT Memory Export", exportedContent)
            clipboard.setPrimaryClip(clip)
            viewModel.showUserMessage("Export copied to clipboard.")
            exportedContent = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanElectric),
          modifier = Modifier.testTag("copy_export_button")
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            tint = JetBlack,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy", color = JetBlack, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { exportedContent = null }) {
          Text("Close", color = TextSecondary)
        }
      }
    )
  }
}

@Composable
private fun DataCategoryTile(
  title: String,
  count: Int,
  subtitle: String,
  icon: ImageVector,
  accent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(DarkCharcoal)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(10.dp)
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = accent,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = count.toString(),
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextPrimary
      )
      Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
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
}

@Composable
private fun FilterChipButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (selected) CyanElectric.copy(alpha = 0.18f) else DarkCharcoal)
      .border(
        1.dp,
        if (selected) CyanElectric else GraphiteBorder,
        RoundedCornerShape(8.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp)
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) CyanElectric else TextSecondary
    )
  }
}

@Composable
private fun ManageableMemoryRow(
  entry: MemoryEntry,
  onDelete: () -> Unit
) {
  val accentColor = when (entry) {
    is MemoryEntry.Reminder -> ElectricLime
    is MemoryEntry.FixHistory -> ToxicGreenGlow
    is MemoryEntry.Preference -> CyanElectric
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(DarkCharcoal)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.primaryTitle.ifBlank { entry.summary },
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${entry.displayBadge} • ${entry.relativeTimeText} • ${entry.summary}",
          fontSize = 10.sp,
          color = accentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier
          .size(32.dp)
          .testTag("delete_memory_${entry.id}")
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete memory entry",
          tint = DeepRedWarning.copy(alpha = 0.85f),
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}
