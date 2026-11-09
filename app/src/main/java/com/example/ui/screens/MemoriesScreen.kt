package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Preference
import com.example.ui.InstinctViewModel
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.DeepRedWarning
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val PREFERENCE_CATEGORIES = listOf(
  "Food & Drinks",
  "Clothing",
  "Personal Care",
  "Home",
  "Technology",
  "Shopping",
  "Routines",
  "Instructions",
  "Custom"
)

@Composable
fun MemoriesScreen(
  viewModel: InstinctViewModel,
  modifier: Modifier = Modifier
) {
  val preferences by viewModel.preferences.collectAsState()
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("All") }
  var showAddDialog by remember { mutableStateOf(false) }

  val filteredPreferences = preferences.filter { pref ->
    val matchesCategory = selectedCategory == "All" || pref.category == selectedCategory
    val matchesSearch = searchQuery.isBlank() ||
      pref.title.contains(searchQuery, ignoreCase = true) ||
      pref.description.contains(searchQuery, ignoreCase = true) ||
      pref.category.contains(searchQuery, ignoreCase = true)
    matchesCategory && matchesSearch
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(JetBlack)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = "EXACTLY",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CyanElectric,
            letterSpacing = 1.sp
          )
          Text(
            text = "Everything you like, exactly how you like it",
            fontSize = 12.sp,
            color = TextSecondary
          )
        }

        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(CyanElectric.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            tint = CyanElectric,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Search bar
      com.example.ui.components.MemorySearchBar(
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        onClearQuery = { searchQuery = "" },
        matchCount = filteredPreferences.size,
        totalCount = preferences.size,
        placeholderText = "Search preferences & instructions...",
        inputTestTag = "memories_search_input",
        clearButtonTestTag = "clear_memories_search_button"
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Categories horizontal row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        CategoryPill(
          title = "All",
          isSelected = selectedCategory == "All",
          onClick = { selectedCategory = "All" }
        )
        PREFERENCE_CATEGORIES.forEach { cat ->
          CategoryPill(
            title = cat,
            isSelected = selectedCategory == cat,
            onClick = { selectedCategory = cat }
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // List of preference cards
      if (filteredPreferences.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Bookmark,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "No saved preferences found",
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Save your favorite coffee order, haircut instructions, or oven settings.",
              fontSize = 12.sp,
              color = TextSecondary,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              modifier = Modifier.padding(horizontal = 24.dp)
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(filteredPreferences, key = { it.id }) { pref ->
            PreferenceCardItem(
              preference = pref,
              onDelete = { viewModel.deletePreference(pref.id) }
            )
          }
          item { Spacer(modifier = Modifier.height(80.dp)) }
        }
      }
    }

    // Add FAB
    FloatingActionButton(
      onClick = { showAddDialog = true },
      containerColor = CyanElectric,
      contentColor = JetBlack,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 80.dp, end = 20.dp)
        .testTag("add_preference_fab")
    ) {
      Icon(imageVector = Icons.Default.Add, contentDescription = "Add new preference")
    }
  }

  if (showAddDialog) {
    AddPreferenceDialog(
      onDismiss = { showAddDialog = false },
      onSave = { title, category, description, details ->
        viewModel.savePreferenceDirectly(title, category, description, details)
        showAddDialog = false
      }
    )
  }
}

@Composable
fun CategoryPill(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .background(if (isSelected) CyanElectric else GraphiteCard)
      .border(1.dp, if (isSelected) CyanElectric else GraphiteBorder, RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(
      text = title,
      fontSize = 11.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) JetBlack else TextSecondary
    )
  }
}

@Composable
fun PreferenceCardItem(
  preference: Preference,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(GraphiteCard)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(14.dp))
      .padding(14.dp)
      .testTag("preference_card_${preference.id}")
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = preference.title,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CyanElectric.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = preference.category,
                fontSize = 10.sp,
                color = CyanElectric,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = preference.description,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp
          )

          if (preference.details.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkCharcoal)
                .padding(8.dp)
            ) {
              Text(
                text = preference.details,
                fontSize = 12.sp,
                color = TextPrimary
              )
            }
          }

          if (!preference.photoUrl.isNullOrBlank()) {
            val savedBitmap = remember(preference.photoUrl) {
              try {
                val rawPath = preference.photoUrl.removePrefix("file://")
                val file = java.io.File(rawPath)
                if (file.exists()) {
                  android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                } else {
                  null
                }
              } catch (_: Exception) {
                null
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (savedBitmap != null) {
              androidx.compose.foundation.Image(
                bitmap = savedBitmap.asImageBitmap(),
                contentDescription = "Saved preference photo for ${preference.title}",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(140.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .border(1.dp, CyanElectric.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
              )
            } else {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(DarkCharcoal)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "Photo Reference: ${preference.photoUrl.substringAfterLast('/')}",
                  fontSize = 10.sp,
                  color = CyanElectric
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
            contentDescription = "Delete preference",
            tint = DeepRedWarning.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // One-tap quick actions: Copy & Share
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GraphiteSurface)
            .clickable {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = ClipData.newPlainText(preference.title, "${preference.title}: ${preference.description}")
              clipboard.setPrimaryClip(clip)
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("copy_pref_${preference.id}")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = null,
              tint = CyanElectric,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Copy", fontSize = 11.sp, color = TextPrimary)
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GraphiteSurface)
            .clickable {
              val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "${preference.title}\n${preference.description}")
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(shareIntent, "Share preference"))
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("share_pref_${preference.id}")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = null,
              tint = CyanElectric,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Share", fontSize = 11.sp, color = TextPrimary)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPreferenceDialog(
  onDismiss: () -> Unit,
  onSave: (title: String, category: String, description: String, details: String) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var category by remember { mutableStateOf(PREFERENCE_CATEGORIES.first()) }
  var description by remember { mutableStateOf("") }
  var details by remember { mutableStateOf("") }
  var isCategoryExpanded by remember { mutableStateOf(false) }

  val voiceController = com.example.ui.components.rememberVoiceToTextController { targetKey, spokenText ->
    when (targetKey) {
      "description" -> description = com.example.ui.components.appendSpokenTranscript(description, spokenText)
      "details" -> details = com.example.ui.components.appendSpokenTranscript(details, spokenText)
      else -> title = com.example.ui.components.appendSpokenTranscript(title, spokenText)
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
        text = "Save to EXACTLY",
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
          label = { Text("Title", color = TextSecondary) },
          placeholder = { Text("Speak or type (e.g., Usual Coffee Order)", color = TextMuted) },
          trailingIcon = {
            com.example.ui.components.VoiceInputMicButton(
              isListening = voiceController.isListeningFor("title"),
              onClick = {
                voiceController.toggleListening(
                  targetKey = "title",
                  prompt = "Speak the preference title..."
                )
              },
              accentColor = CyanElectric,
              buttonSize = 36.dp,
              iconSize = 18.dp,
              contentDescription = "Speak preference title with voice-to-text",
              testTag = "add_pref_title_voice_button"
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_pref_title_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category dropdown
        ExposedDropdownMenuBox(
          expanded = isCategoryExpanded,
          onExpandedChange = { isCategoryExpanded = !isCategoryExpanded }
        ) {
          OutlinedTextField(
            value = category,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category", color = TextSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              focusedBorderColor = CyanElectric,
              unfocusedBorderColor = GraphiteBorder
            ),
            modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
          )
          ExposedDropdownMenu(
            expanded = isCategoryExpanded,
            onDismissRequest = { isCategoryExpanded = false },
            containerColor = GraphiteCard
          ) {
            PREFERENCE_CATEGORIES.forEach { cat ->
              DropdownMenuItem(
                text = { Text(cat, color = TextPrimary) },
                onClick = {
                  category = cat
                  isCategoryExpanded = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description / Exact specification", color = TextSecondary) },
          placeholder = { Text("Speak or type (e.g., Large iced coffee with two creams)", color = TextMuted) },
          trailingIcon = {
            com.example.ui.components.VoiceInputMicButton(
              isListening = voiceController.isListeningFor("description"),
              onClick = {
                voiceController.toggleListening(
                  targetKey = "description",
                  prompt = "Speak your exact preference specification..."
                )
              },
              accentColor = CyanElectric,
              buttonSize = 36.dp,
              iconSize = 18.dp,
              contentDescription = "Speak preference description with voice-to-text",
              testTag = "add_pref_description_voice_button"
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_pref_description_input"),
          maxLines = 3
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = details,
          onValueChange = { details = it },
          label = { Text("Additional notes / measurements (optional)", color = TextSecondary) },
          placeholder = { Text("Speak or type (e.g., No sugar, light ice)", color = TextMuted) },
          trailingIcon = {
            com.example.ui.components.VoiceInputMicButton(
              isListening = voiceController.isListeningFor("details"),
              onClick = {
                voiceController.toggleListening(
                  targetKey = "details",
                  prompt = "Speak additional notes or measurements..."
                )
              },
              accentColor = CyanElectric,
              buttonSize = 36.dp,
              iconSize = 18.dp,
              contentDescription = "Speak preference details with voice-to-text",
              testTag = "add_pref_details_voice_button"
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_pref_details_input")
        )

        com.example.ui.components.VoiceListeningStatusBanner(
          controller = voiceController,
          accentColor = CyanElectric,
          listeningLabel = "LISTENING FOR PREFERENCE",
          testTag = "add_pref_voice_banner"
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (voiceController.isListening) voiceController.stopListening()
          if (title.isNotBlank() && description.isNotBlank()) onSave(title, category, description, details)
        },
        colors = ButtonDefaults.buttonColors(containerColor = CyanElectric),
        enabled = title.isNotBlank() && description.isNotBlank()
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
