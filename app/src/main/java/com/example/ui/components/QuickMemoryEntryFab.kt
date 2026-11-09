package com.example.ui.components

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MemoryEntry
import com.example.ui.InstinctViewModel
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
 * A text input component with an integrated Floating Action Button (FAB) that allows users
 * to type and save new [MemoryEntry] records with assigned organization categories
 * (e.g., Personal, Work, Shopping, Home) directly to the local Room database.
 *
 * Supports all three unified memory types:
 * - [MemoryEntry.TYPE_REMINDER] (While You're At It)
 * - [MemoryEntry.TYPE_PREFERENCE] (Exactly)
 * - [MemoryEntry.TYPE_FIX_HISTORY] (Undo IRL)
 */
@Composable
fun DirectMemoryEntryBar(
  onSaveMemory: (primaryText: String, memoryType: String, secondaryText: String, tagsCsv: String, category: String) -> Unit,
  modifier: Modifier = Modifier,
  initialType: String = MemoryEntry.TYPE_PREFERENCE,
  initialCategory: String = MemoryEntry.DEFAULT_CATEGORY
) {
  var primaryText by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(initialType) }
  var selectedCategory by remember { mutableStateOf(initialCategory) }
  var secondaryText by remember { mutableStateOf("") }
  var tagsText by remember { mutableStateOf("") }
  var showDetails by remember { mutableStateOf(false) }

  val voiceController = rememberVoiceToTextController { targetKey, spokenText ->
    if (targetKey == "secondary") {
      secondaryText = appendSpokenTranscript(secondaryText, spokenText)
    } else {
      val hint = inferVoiceMemoryHint(spokenText)
      if (hint.suggestedCategory != null) {
        selectedCategory = hint.suggestedCategory
      }
      if (hint.suggestedType != null) {
        selectedType = hint.suggestedType
      }
      primaryText = appendSpokenTranscript(primaryText, hint.cleanedText)
    }
  }

  val activeAccentColor = when (selectedType) {
    MemoryEntry.TYPE_REMINDER -> ElectricLime
    MemoryEntry.TYPE_FIX_HISTORY -> ToxicGreenGlow
    else -> CyanElectric
  }

  val placeholderText = when (selectedType) {
    MemoryEntry.TYPE_REMINDER -> "Speak or type a $selectedCategory reminder..."
    MemoryEntry.TYPE_FIX_HISTORY -> "Speak or type a $selectedCategory fix..."
    else -> "Speak or type a $selectedCategory memory..."
  }

  val secondaryPlaceholder = when (selectedType) {
    MemoryEntry.TYPE_REMINDER -> "Trigger context (e.g., Grocery Store, Home, Mom)"
    MemoryEntry.TYPE_FIX_HISTORY -> "Recovery steps (one per line or comma-separated)"
    else -> "Detailed instructions or notes (e.g., Oat milk, extra hot)"
  }

  val handleSave = {
    if (voiceController.isListening) {
      voiceController.stopListening()
    }
    val trimmed = primaryText.trim()
    if (trimmed.isNotEmpty()) {
      onSaveMemory(
        trimmed,
        selectedType,
        secondaryText.trim(),
        tagsText.trim(),
        selectedCategory
      )
      primaryText = ""
      secondaryText = ""
      tagsText = ""
      showDetails = false
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(DarkCharcoal)
      .border(1.dp, activeAccentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
      .padding(12.dp)
      .testTag("direct_memory_entry_component")
  ) {
    // Top row: Subclass selector pills + optional detail toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        MemoryTypeChip(
          label = "Exactly",
          icon = Icons.Default.Bookmark,
          selected = selectedType == MemoryEntry.TYPE_PREFERENCE,
          accentColor = CyanElectric,
          onClick = { selectedType = MemoryEntry.TYPE_PREFERENCE },
          testTag = "quick_type_preference"
        )
        MemoryTypeChip(
          label = "Reminder",
          icon = Icons.Default.NotificationsActive,
          selected = selectedType == MemoryEntry.TYPE_REMINDER,
          accentColor = ElectricLime,
          onClick = { selectedType = MemoryEntry.TYPE_REMINDER },
          testTag = "quick_type_reminder"
        )
        MemoryTypeChip(
          label = "Fix",
          icon = Icons.Default.Build,
          selected = selectedType == MemoryEntry.TYPE_FIX_HISTORY,
          accentColor = ToxicGreenGlow,
          onClick = { selectedType = MemoryEntry.TYPE_FIX_HISTORY },
          testTag = "quick_type_fix"
        )
      }

      IconButton(
        onClick = { showDetails = !showDetails },
        modifier = Modifier
          .size(32.dp)
          .testTag("toggle_quick_memory_details_button")
      ) {
        Icon(
          imageVector = Icons.Default.Tune,
          contentDescription = "Toggle memory context and tags",
          tint = if (showDetails) activeAccentColor else TextMuted,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Category Selector Row (e.g., Personal, Work, Shopping, Home, etc.)
    MemoryCategorySelectorRow(
      selectedCategory = selectedCategory,
      onCategorySelected = { selectedCategory = it },
      accentColor = activeAccentColor,
      testTagPrefix = "input_category"
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Primary text input + Voice-to-Text Mic Button + FloatingActionButton row
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = primaryText,
        onValueChange = { primaryText = it },
        placeholder = {
          Text(
            text = placeholderText,
            fontSize = 12.sp,
            color = TextMuted
          )
        },
        trailingIcon = {
          if (primaryText.isNotEmpty()) {
            IconButton(
              onClick = { primaryText = "" },
              modifier = Modifier
                .size(32.dp)
                .testTag("clear_direct_memory_input_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear memory text",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { handleSave() }),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = GraphiteCard,
          unfocusedContainerColor = GraphiteCard,
          focusedBorderColor = activeAccentColor,
          unfocusedBorderColor = GraphiteBorder,
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          cursorColor = activeAccentColor
        ),
        modifier = Modifier
          .weight(1f)
          .testTag("direct_memory_input_field")
      )

      // Voice-to-Text Microphone Button
      VoiceInputMicButton(
        isListening = voiceController.isListeningFor("primary"),
        onClick = {
          voiceController.toggleListening(
            targetKey = "primary",
            prompt = "Speak your $selectedCategory memory entry..."
          )
        },
        accentColor = activeAccentColor,
        buttonSize = 48.dp,
        iconSize = 20.dp,
        contentDescription = "Speak memory entry using voice-to-text",
        testTag = "direct_memory_voice_button"
      )

      FloatingActionButton(
        onClick = handleSave,
        shape = CircleShape,
        containerColor = if (primaryText.isNotBlank()) activeAccentColor else GraphiteSurface,
        contentColor = if (primaryText.isNotBlank()) JetBlack else TextMuted,
        elevation = FloatingActionButtonDefaults.elevation(
          defaultElevation = 4.dp,
          pressedElevation = 8.dp
        ),
        modifier = Modifier
          .size(48.dp)
          .testTag("save_memory_fab")
      ) {
        Icon(
          imageVector = if (primaryText.isNotBlank()) Icons.Default.Check else Icons.Default.Add,
          contentDescription = "Save memory entry to Room database",
          modifier = Modifier.size(22.dp)
        )
      }
    }

    // Live Voice-to-Text Listening Status & Partial Transcript Banner
    VoiceListeningStatusBanner(
      controller = voiceController,
      accentColor = activeAccentColor,
      listeningLabel = if (voiceController.activeTargetKey == "secondary") {
        "LISTENING FOR DETAILS / CONTEXT"
      } else {
        "LISTENING — SPEAK YOUR ${selectedCategory.uppercase()} MEMORY"
      },
      testTag = "direct_memory_voice_banner"
    )

    // Optional expandable secondary context & tags inputs
    AnimatedVisibility(visible = showDetails) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = secondaryText,
          onValueChange = { secondaryText = it },
          placeholder = {
            Text(
              text = secondaryPlaceholder,
              fontSize = 11.sp,
              color = TextMuted
            )
          },
          trailingIcon = {
            VoiceInputMicButton(
              isListening = voiceController.isListeningFor("secondary"),
              onClick = {
                voiceController.toggleListening(
                  targetKey = "secondary",
                  prompt = "Speak additional context or steps..."
                )
              },
              accentColor = activeAccentColor,
              buttonSize = 36.dp,
              iconSize = 16.dp,
              contentDescription = "Speak secondary details with voice-to-text",
              testTag = "direct_memory_secondary_voice_button"
            )
          },
          singleLine = selectedType != MemoryEntry.TYPE_FIX_HISTORY,
          maxLines = if (selectedType == MemoryEntry.TYPE_FIX_HISTORY) 3 else 1,
          shape = RoundedCornerShape(10.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GraphiteCard,
            unfocusedContainerColor = GraphiteCard,
            focusedBorderColor = activeAccentColor,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = activeAccentColor
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("direct_memory_secondary_input")
        )

        OutlinedTextField(
          value = tagsText,
          onValueChange = { tagsText = it },
          placeholder = {
            Text(
              text = "Optional tags (comma-separated, e.g., daily, home)",
              fontSize = 11.sp,
              color = TextMuted
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GraphiteCard,
            unfocusedContainerColor = GraphiteCard,
            focusedBorderColor = activeAccentColor,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = activeAccentColor
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("direct_memory_tags_input")
        )
      }
    }
  }
}

/**
 * Horizontal scrollable row of category pills (e.g., Personal, Work, Shopping, Home, etc.)
 * allowing users to assign or update a category on a [MemoryEntry].
 */
@Composable
fun MemoryCategorySelectorRow(
  selectedCategory: String,
  onCategorySelected: (String) -> Unit,
  modifier: Modifier = Modifier,
  categories: List<String> = MemoryEntry.CATEGORIES,
  accentColor: Color = CyanElectric,
  testTagPrefix: String = "category_chip"
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "CATEGORY:",
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = TextMuted,
      letterSpacing = 0.6.sp
    )
    categories.forEach { category ->
      val isSelected = selectedCategory.equals(category, ignoreCase = true)
      val slug = category.lowercase().replace(Regex("[^a-z0-9]+"), "_")
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(14.dp))
          .background(if (isSelected) accentColor.copy(alpha = 0.18f) else GraphiteCard)
          .border(
            width = 1.dp,
            color = if (isSelected) accentColor else GraphiteBorder,
            shape = RoundedCornerShape(14.dp)
          )
          .clickable { onCategorySelected(category) }
          .padding(horizontal = 9.dp, vertical = 4.dp)
          .testTag("${testTagPrefix}_${slug}")
      ) {
        Text(
          text = category,
          fontSize = 10.sp,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          color = if (isSelected) accentColor else TextSecondary
        )
      }
    }
  }
}

/**
 * Floating Action Button for the application Scaffold that opens a quick-entry modal
 * allowing the user to type and save a new [MemoryEntry] directly to the Room database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoryEntryFab(
  viewModel: InstinctViewModel,
  modifier: Modifier = Modifier
) {
  var showDialog by remember { mutableStateOf(false) }

  FloatingActionButton(
    onClick = { showDialog = true },
    shape = CircleShape,
    containerColor = ElectricLime,
    contentColor = JetBlack,
    elevation = FloatingActionButtonDefaults.elevation(
      defaultElevation = 6.dp,
      pressedElevation = 10.dp
    ),
    modifier = modifier.testTag("quick_add_memory_fab")
  ) {
    Icon(
      imageVector = Icons.Default.Add,
      contentDescription = "Quick add memory to Room database"
    )
  }

  if (showDialog) {
    BasicAlertDialog(
      onDismissRequest = { showDialog = false }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .background(GraphiteSurface)
          .border(1.dp, ElectricLime.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
          .padding(16.dp)
          .testTag("quick_memory_modal_dialog")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "QUICK SAVE TO ROOM",
              fontSize = 13.sp,
              fontWeight = FontWeight.ExtraBold,
              color = ElectricLime,
              letterSpacing = 0.8.sp
            )
            Text(
              text = "Assign a category and save directly to local storage",
              fontSize = 11.sp,
              color = TextSecondary
            )
          }

          IconButton(
            onClick = { showDialog = false },
            modifier = Modifier
              .size(32.dp)
              .testTag("close_quick_memory_modal_button")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close quick memory dialog",
              tint = TextSecondary,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        DirectMemoryEntryBar(
          onSaveMemory = { primaryText, memoryType, secondaryText, tagsCsv, category ->
            viewModel.saveQuickMemoryText(
              primaryText = primaryText,
              memoryType = memoryType,
              secondaryText = secondaryText,
              tagsCsv = tagsCsv,
              category = category
            )
            showDialog = false
          }
        )
      }
    }
  }
}

@Composable
private fun MemoryTypeChip(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  testTag: String
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .background(if (selected) accentColor.copy(alpha = 0.16f) else GraphiteCard)
      .border(
        width = 1.dp,
        color = if (selected) accentColor else GraphiteBorder,
        shape = RoundedCornerShape(20.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (selected) accentColor else TextMuted,
      modifier = Modifier.size(12.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) accentColor else TextSecondary
    )
  }
}
