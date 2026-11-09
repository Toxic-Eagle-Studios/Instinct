package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MemoryEntry
import com.example.ui.InstinctViewModel
import com.example.ui.NavTab
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

/**
 * Dedicated search bar component placed at the top of the memory list to filter
 * existing [MemoryEntry] records by keyword across title, summary, tags, location,
 * trigger context, category, and troubleshooting steps.
 */
@Composable
fun MemorySearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onClearQuery: () -> Unit,
  matchCount: Int,
  totalCount: Int,
  modifier: Modifier = Modifier,
  placeholderText: String = "Search memories by keyword, tag, or location...",
  inputTestTag: String = "memory_history_search_input",
  clearButtonTestTag: String = "clear_memory_search_button"
) {
  val focusManager = LocalFocusManager.current
  val hasActiveQuery = query.isNotBlank()

  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = "Search memories",
        tint = if (hasActiveQuery) CyanElectric else TextMuted,
        modifier = Modifier.size(18.dp)
      )
    },
    trailingIcon = {
      if (hasActiveQuery) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(end = 4.dp)
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(CyanElectric.copy(alpha = 0.14f))
              .border(1.dp, CyanElectric.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
              .testTag("memory_search_match_badge")
          ) {
            Text(
              text = "$matchCount/$totalCount",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = CyanElectric
            )
          }
          IconButton(
            onClick = {
              onClearQuery()
              focusManager.clearFocus()
            },
            modifier = Modifier
              .size(32.dp)
              .testTag(clearButtonTestTag)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear search keyword",
              tint = TextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    },
    placeholder = {
      Text(
        text = placeholderText,
        fontSize = 12.sp,
        color = TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    },
    singleLine = true,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    shape = RoundedCornerShape(12.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedContainerColor = DarkCharcoal,
      unfocusedContainerColor = DarkCharcoal,
      focusedBorderColor = CyanElectric,
      unfocusedBorderColor = if (hasActiveQuery) CyanElectric.copy(alpha = 0.5f) else GraphiteBorder,
      focusedTextColor = TextPrimary,
      unfocusedTextColor = TextPrimary,
      cursorColor = CyanElectric
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag(inputTestTag)
  )
}

/**
 * Stateful Composable that queries and observes the local Room database (via [InstinctViewModel.memoryEntries]
 * and [InstinctViewModel.memorySearchQuery]) and displays the user's stored [MemoryEntry] history
 * with a top keyword search bar, type filtering, full inline editing, and deletion confirmation.
 */
@Composable
fun RecentMemoryEntriesList(
  viewModel: InstinctViewModel,
  modifier: Modifier = Modifier
) {
  val memoryEntries by viewModel.memoryEntries.collectAsStateWithLifecycle()
  val searchQuery by viewModel.memorySearchQuery.collectAsStateWithLifecycle()
  val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
  val reminders by viewModel.reminders.collectAsStateWithLifecycle()
  val fixes by viewModel.fixes.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()

  RecentMemoryEntriesList(
    memoryEntries = memoryEntries,
    remindersCount = reminders.size,
    fixesCount = fixes.size,
    preferencesCount = preferences.size,
    searchQuery = searchQuery,
    onSearchQueryChange = { viewModel.updateMemorySearchQuery(it) },
    onClearSearchQuery = { viewModel.clearMemorySearchQuery() },
    selectedCategoryFilter = selectedCategoryFilter,
    onCategoryFilterChange = { viewModel.updateSelectedCategoryFilter(it) },
    onNavigateToTab = { viewModel.setTab(it) },
    onToggleReminderDone = { reminderEntry ->
      viewModel.toggleReminderEntryCompletion(reminderEntry)
    },
    onEditEntry = { updatedEntry ->
      viewModel.updateMemoryEntry(updatedEntry)
    },
    onDeleteEntry = { entry ->
      when (entry) {
        is MemoryEntry.Reminder -> viewModel.deleteReminder(entry.id)
        is MemoryEntry.FixHistory -> viewModel.deleteFix(entry.id)
        is MemoryEntry.Preference -> viewModel.deletePreference(entry.id)
      }
      viewModel.deleteUnifiedMemory(entry.id)
    },
    onUpdateEntryCategory = { entry, newCategory ->
      viewModel.updateMemoryEntryCategory(entry, newCategory)
    },
    onQuickSaveMemory = { primaryText, memoryType, secondaryText, tagsCsv, category ->
      viewModel.saveQuickMemoryText(
        primaryText = primaryText,
        memoryType = memoryType,
        secondaryText = secondaryText,
        tagsCsv = tagsCsv,
        category = category
      )
    },
    modifier = modifier
  )
}

/**
 * Stateless Composable displaying a filterable, expandable list of recent [MemoryEntry] records
 * queried from the Room database with a keyword search bar, category filter row, edit dialog, and delete confirmation.
 */
@Composable
fun RecentMemoryEntriesList(
  memoryEntries: List<MemoryEntry>,
  remindersCount: Int,
  fixesCount: Int,
  preferencesCount: Int,
  onNavigateToTab: (NavTab) -> Unit,
  onToggleReminderDone: (MemoryEntry.Reminder) -> Unit,
  onDeleteEntry: (MemoryEntry) -> Unit,
  searchQuery: String? = null,
  onSearchQueryChange: ((String) -> Unit)? = null,
  onClearSearchQuery: (() -> Unit)? = null,
  selectedCategoryFilter: String? = null,
  onCategoryFilterChange: ((String) -> Unit)? = null,
  onUpdateEntryCategory: ((MemoryEntry, String) -> Unit)? = null,
  onEditEntry: ((MemoryEntry) -> Unit)? = null,
  onQuickSaveMemory: ((primaryText: String, memoryType: String, secondaryText: String, tagsCsv: String, category: String) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  var selectedTypeFilter by remember { mutableStateOf("ALL") }
  var localSearchQuery by remember { mutableStateOf("") }
  var localCategoryFilter by remember { mutableStateOf("ALL") }
  var showAllEntries by remember { mutableStateOf(false) }
  var expandedEntryId by remember { mutableStateOf<String?>(null) }
  var editingEntry by remember { mutableStateOf<MemoryEntry?>(null) }
  var pendingDeleteEntry by remember { mutableStateOf<MemoryEntry?>(null) }

  val effectiveQuery = searchQuery ?: localSearchQuery
  val effectiveCategory = selectedCategoryFilter ?: localCategoryFilter

  val handleQueryChange: (String) -> Unit = { newQuery ->
    if (onSearchQueryChange != null) {
      onSearchQueryChange(newQuery)
    } else {
      localSearchQuery = newQuery
    }
  }
  val handleClearQuery: () -> Unit = {
    if (onClearSearchQuery != null) {
      onClearSearchQuery()
    } else {
      localSearchQuery = ""
    }
  }
  val handleCategoryFilterChange: (String) -> Unit = { newCat ->
    if (onCategoryFilterChange != null) {
      onCategoryFilterChange(newCat)
    } else {
      localCategoryFilter = newCat
    }
  }

  // Filter all entries by keyword first (supports multi-word tokens across all fields)
  val keywordMatchedEntries = remember(memoryEntries, effectiveQuery) {
    val trimmed = effectiveQuery.trim()
    if (trimmed.isBlank()) {
      memoryEntries
    } else {
      val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
      memoryEntries.filter { entry ->
        tokens.all { token -> entry.matchesQuery(token) }
      }
    }
  }

  // Filter by selected category
  val categoryMatchedEntries = remember(keywordMatchedEntries, effectiveCategory) {
    if (effectiveCategory.isBlank() || effectiveCategory.equals("ALL", ignoreCase = true)) {
      keywordMatchedEntries
    } else {
      keywordMatchedEntries.filter { entry ->
        entry.category.equals(effectiveCategory, ignoreCase = true)
      }
    }
  }

  // Apply selected MemoryEntry subclass filter on top of keyword + category matches
  val filteredEntries = remember(categoryMatchedEntries, selectedTypeFilter) {
    categoryMatchedEntries.filter { entry ->
      when (selectedTypeFilter) {
        MemoryEntry.TYPE_REMINDER -> entry is MemoryEntry.Reminder
        MemoryEntry.TYPE_FIX_HISTORY -> entry is MemoryEntry.FixHistory
        MemoryEntry.TYPE_PREFERENCE -> entry is MemoryEntry.Preference
        else -> true
      }
    }
  }

  val hasActiveSearch = effectiveQuery.isNotBlank()
  val hasActiveCategory = !effectiveCategory.equals("ALL", ignoreCase = true)
  val displayedEntries = if (showAllEntries || hasActiveSearch || hasActiveCategory) {
    filteredEntries
  } else {
    filteredEntries.take(5)
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(GraphiteSurface)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
      .padding(16.dp)
      .testTag("recent_memories_list")
  ) {
    Column {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = CyanElectric,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "YOUR SHARED MEMORY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 0.8.sp
          )
        }

        Text(
          text = if (hasActiveSearch || hasActiveCategory) {
            "${filteredEntries.size} of ${memoryEntries.size} matched"
          } else {
            "${memoryEntries.size} stored"
          },
          fontSize = 11.sp,
          fontWeight = if (hasActiveSearch || hasActiveCategory) FontWeight.SemiBold else FontWeight.Normal,
          color = if (hasActiveSearch || hasActiveCategory) CyanElectric else TextMuted
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 3 Counter Metrics Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        MemoryCounterPill(
          count = remindersCount,
          label = "Reminders",
          icon = Icons.Default.NotificationsActive,
          color = ElectricLime,
          onClick = { onNavigateToTab(NavTab.REMINDERS) }
        )
        MemoryCounterPill(
          count = fixesCount,
          label = "Saved Fixes",
          icon = Icons.Default.Build,
          color = ToxicGreenGlow,
          onClick = { onNavigateToTab(NavTab.FIXES) }
        )
        MemoryCounterPill(
          count = preferencesCount,
          label = "Preferences",
          icon = Icons.Default.Bookmark,
          color = CyanElectric,
          onClick = { onNavigateToTab(NavTab.MEMORIES) }
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (onQuickSaveMemory != null) {
        DirectMemoryEntryBar(
          onSaveMemory = onQuickSaveMemory
        )
        Spacer(modifier = Modifier.height(14.dp))
      }

      // Dedicated Search Bar at the top of the memory list
      MemorySearchBar(
        query = effectiveQuery,
        onQueryChange = handleQueryChange,
        onClearQuery = handleClearQuery,
        matchCount = filteredEntries.size,
        totalCount = memoryEntries.size,
        modifier = Modifier.padding(bottom = 10.dp)
      )

      // Filter Chips by MemoryEntry Subclass (shows keyword + category filtered counts)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        MemoryFilterTabChip(
          label = "All (${categoryMatchedEntries.size})",
          selected = selectedTypeFilter == "ALL",
          accentColor = TextPrimary,
          onClick = { selectedTypeFilter = "ALL" },
          testTag = "filter_memory_all"
        )
        MemoryFilterTabChip(
          label = "Reminders (${categoryMatchedEntries.count { it is MemoryEntry.Reminder }})",
          selected = selectedTypeFilter == MemoryEntry.TYPE_REMINDER,
          accentColor = ElectricLime,
          onClick = { selectedTypeFilter = MemoryEntry.TYPE_REMINDER },
          testTag = "filter_memory_reminders"
        )
        MemoryFilterTabChip(
          label = "Fixes (${categoryMatchedEntries.count { it is MemoryEntry.FixHistory }})",
          selected = selectedTypeFilter == MemoryEntry.TYPE_FIX_HISTORY,
          accentColor = ToxicGreenGlow,
          onClick = { selectedTypeFilter = MemoryEntry.TYPE_FIX_HISTORY },
          testTag = "filter_memory_fixes"
        )
        MemoryFilterTabChip(
          label = "Preferences (${categoryMatchedEntries.count { it is MemoryEntry.Preference }})",
          selected = selectedTypeFilter == MemoryEntry.TYPE_PREFERENCE,
          accentColor = CyanElectric,
          onClick = { selectedTypeFilter = MemoryEntry.TYPE_PREFERENCE },
          testTag = "filter_memory_preferences"
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Filter Chips by Category (Work, Personal, Shopping, Home, etc.)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        MemoryFilterTabChip(
          label = "All Categories",
          selected = !hasActiveCategory,
          accentColor = CyanElectric,
          onClick = { handleCategoryFilterChange("ALL") },
          testTag = "filter_category_all"
        )
        MemoryEntry.CATEGORIES.forEach { category ->
          val countInCat = keywordMatchedEntries.count {
            it.category.equals(category, ignoreCase = true)
          }
          val isSelected = effectiveCategory.equals(category, ignoreCase = true)
          val slug = category.lowercase().replace(Regex("[^a-z0-9]+"), "_")
          MemoryFilterTabChip(
            label = if (countInCat > 0) "$category ($countInCat)" else category,
            selected = isSelected,
            accentColor = CyanElectric,
            onClick = {
              handleCategoryFilterChange(if (isSelected) "ALL" else category)
            },
            testTag = "filter_category_$slug"
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Recent Entries List
      if (displayedEntries.isNotEmpty()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = when {
              hasActiveSearch && hasActiveCategory ->
                "RESULTS FOR \"${effectiveQuery.trim().uppercase()}\" IN ${effectiveCategory.uppercase()}"
              hasActiveSearch ->
                "SEARCH RESULTS FOR \"${effectiveQuery.trim().uppercase()}\""
              hasActiveCategory ->
                "CATEGORY: ${effectiveCategory.uppercase()} (${filteredEntries.size})"
              else ->
                "RECENT MEMORY ENTRIES"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (hasActiveSearch || hasActiveCategory) CyanElectric else TextMuted,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )

          if (hasActiveSearch || hasActiveCategory) {
            Text(
              text = "Clear filters",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = CyanElectric,
              modifier = Modifier
                .clickable {
                  handleClearQuery()
                  handleCategoryFilterChange("ALL")
                }
                .padding(start = 8.dp)
            )
          }
        }

        displayedEntries.forEach { entry ->
          val isExpanded = expandedEntryId == entry.id
          MemoryEntryHistoryCard(
            entry = entry,
            isExpanded = isExpanded,
            onToggleExpand = {
              expandedEntryId = if (isExpanded) null else entry.id
            },
            onOpenInTab = {
              when (entry) {
                is MemoryEntry.Reminder -> onNavigateToTab(NavTab.REMINDERS)
                is MemoryEntry.FixHistory -> onNavigateToTab(NavTab.FIXES)
                is MemoryEntry.Preference -> onNavigateToTab(NavTab.MEMORIES)
              }
            },
            onToggleReminderDone = {
              if (entry is MemoryEntry.Reminder) {
                onToggleReminderDone(entry)
              }
            },
            onEdit = if (onEditEntry != null) {
              { editingEntry = entry }
            } else null,
            onUpdateCategory = if (onUpdateEntryCategory != null) {
              { newCategory -> onUpdateEntryCategory(entry, newCategory) }
            } else {
              null
            },
            onDelete = { pendingDeleteEntry = entry }
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        if (!hasActiveSearch && !hasActiveCategory && filteredEntries.size > 5) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(DarkCharcoal)
              .clickable { showAllEntries = !showAllEntries }
              .padding(vertical = 8.dp)
              .testTag("toggle_show_all_memories_button"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (showAllEntries) "Show Less" else "View All ${filteredEntries.size} Stored Memories",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = CyanElectric
            )
          }
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCharcoal)
            .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
        ) {
          Text(
            text = when {
              hasActiveSearch && hasActiveCategory ->
                "No stored memories match \"${effectiveQuery.trim()}\" in category \"$effectiveCategory\"."
              hasActiveSearch ->
                "No stored memories match \"${effectiveQuery.trim()}\"."
              hasActiveCategory ->
                "No stored memories assigned to \"$effectiveCategory\" yet."
              else ->
                "No saved memories yet. Ask a question, log a preference, or tell INSTINCT where you're heading above."
            },
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp
          )

          if (hasActiveSearch || hasActiveCategory) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Reset filters",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = CyanElectric,
              modifier = Modifier
                .clickable {
                  handleClearQuery()
                  handleCategoryFilterChange("ALL")
                }
                .testTag("reset_empty_search_button")
            )
          }
        }
      }
    }
  }

  // Edit Memory Dialog (`EditForm` from App.jsx)
  if (editingEntry != null && onEditEntry != null) {
    EditMemoryDialog(
      entry = editingEntry!!,
      onDismiss = { editingEntry = null },
      onSave = { updated ->
        onEditEntry(updated)
        editingEntry = null
      }
    )
  }

  // Delete Confirmation Dialog
  if (pendingDeleteEntry != null) {
    val target = pendingDeleteEntry!!
    AlertDialog(
      onDismissRequest = { pendingDeleteEntry = null },
      containerColor = DarkCharcoal,
      title = {
        Text(
          text = "Delete Memory?",
          color = DeepRedWarning,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      },
      text = {
        Text(
          text = "Delete \"${target.primaryTitle}\"? This cannot be undone unless you exported a backup.",
          color = TextPrimary,
          fontSize = 13.sp,
          lineHeight = 18.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteEntry(target)
            pendingDeleteEntry = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = DeepRedWarning),
          modifier = Modifier.testTag("confirm_delete_memory_button")
        ) {
          Text("Delete", color = TextPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteEntry = null }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryEntryHistoryCard(
  entry: MemoryEntry,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onOpenInTab: () -> Unit,
  onToggleReminderDone: () -> Unit,
  onDelete: () -> Unit,
  onEdit: (() -> Unit)? = null,
  onUpdateCategory: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val (title, icon, accentColor, statusText) = when (entry) {
    is MemoryEntry.Reminder -> QuadrupleInfo(
      title = entry.title.ifBlank { "Reminder: ${entry.triggerActivity}" },
      icon = Icons.Default.NotificationsActive,
      accentColor = ElectricLime,
      statusText = if (entry.isCompleted) "Completed" else "Active Trigger"
    )
    is MemoryEntry.FixHistory -> QuadrupleInfo(
      title = entry.problemSummary.ifBlank { entry.bestFix },
      icon = Icons.Default.Build,
      accentColor = ToxicGreenGlow,
      statusText = if (entry.isVerifiedWorking) "Verified Fix" else "Recorded Fix"
    )
    is MemoryEntry.Preference -> QuadrupleInfo(
      title = entry.title.ifBlank { entry.description },
      icon = Icons.Default.Bookmark,
      accentColor = CyanElectric,
      statusText = "Saved Preference"
    )
  }

  val categoryLabel = entry.category.ifBlank { MemoryEntry.DEFAULT_CATEGORY }
  val isDoneReminder = entry is MemoryEntry.Reminder && entry.isCompleted
  val dueTag = entry.tags.firstOrNull { it.startsWith("due:") }?.removePrefix("due:")

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(DarkCharcoal)
      .border(
        width = 1.dp,
        color = if (isExpanded) accentColor.copy(alpha = 0.6f) else GraphiteBorder,
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(onClick = onToggleExpand)
      .padding(12.dp)
      .testTag("memory_entry_card_${entry.id}")
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(9.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = entry.displayBadge,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDoneReminder) TextMuted else TextPrimary,
            textDecoration = if (isDoneReminder) TextDecoration.LineThrough else null,
            maxLines = if (isExpanded) 3 else 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(3.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = entry.displayBadge,
              fontSize = 9.sp,
              color = accentColor,
              fontWeight = FontWeight.Bold
            )

            // Category Pill Badge
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CyanElectric.copy(alpha = 0.12f))
                .border(0.5.dp, CyanElectric.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
                .testTag("memory_entry_category_${entry.id}")
            ) {
              Text(
                text = categoryLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = CyanElectric
              )
            }

            if (!dueTag.isNullOrBlank()) {
              Text(
                text = "◷ $dueTag",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = ElectricLime
              )
            }

            Text(
              text = "• $statusText",
              fontSize = 10.sp,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )

            Text(
              text = "• ${entry.relativeTimeText}",
              fontSize = 10.sp,
              color = TextMuted,
              modifier = Modifier.testTag("memory_entry_relative_time_${entry.id}")
            )
          }
        }

        Icon(
          imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (isExpanded) "Collapse details" else "Expand details",
          tint = TextMuted,
          modifier = Modifier.size(18.dp)
        )
      }

      // Expanded Subclass Details
      AnimatedVisibility(visible = isExpanded) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(GraphiteCard)
              .padding(10.dp)
          ) {
            when (entry) {
              is MemoryEntry.Reminder -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  if (entry.triggerActivity.isNotBlank()) {
                    Text(
                      text = "Trigger / Context: ${entry.triggerActivity}",
                      fontSize = 12.sp,
                      color = TextPrimary,
                      fontWeight = FontWeight.Medium
                    )
                  }
                  if (entry.relatedLocations.isNotEmpty()) {
                    Text(
                      text = "Locations: ${entry.relatedLocations.joinToString(", ")}",
                      fontSize = 11.sp,
                      color = TextSecondary
                    )
                  }
                  if (entry.relatedPeople.isNotEmpty()) {
                    Text(
                      text = "People: ${entry.relatedPeople.joinToString(", ")}",
                      fontSize = 11.sp,
                      color = TextSecondary
                    )
                  }
                  if (!dueTag.isNullOrBlank()) {
                    Text(
                      text = "Due Date: $dueTag",
                      fontSize = 11.sp,
                      color = ElectricLime,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                  Text(
                    text = "Recurrence: ${entry.recurrence.replace("_", " ")}",
                    fontSize = 11.sp,
                    color = TextMuted
                  )
                }
              }

              is MemoryEntry.FixHistory -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  if (entry.whatHappened.isNotBlank()) {
                    Text(
                      text = entry.whatHappened,
                      fontSize = 11.sp,
                      color = TextSecondary
                    )
                  }
                  Text(
                    text = "Best Fix: ${entry.bestFix}",
                    fontSize = 12.sp,
                    color = ToxicGreenGlow,
                    fontWeight = FontWeight.SemiBold
                  )
                  if (entry.steps.isNotEmpty()) {
                    entry.steps.take(4).forEachIndexed { idx, step ->
                      Text(
                        text = "${idx + 1}. $step",
                        fontSize = 11.sp,
                        color = TextPrimary
                      )
                    }
                  }
                }
              }

              is MemoryEntry.Preference -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  if (entry.description.isNotBlank()) {
                    Text(
                      text = entry.description,
                      fontSize = 12.sp,
                      color = TextPrimary
                    )
                  }
                  if (entry.details.isNotBlank()) {
                    Text(
                      text = "Notes: ${entry.details}",
                      fontSize = 11.sp,
                      color = TextSecondary
                    )
                  }
                }
              }
            }
          }

          // Inline Category Selector to reassign category directly in Room
          if (onUpdateCategory != null) {
            Spacer(modifier = Modifier.height(8.dp))
            MemoryCategorySelectorRow(
              selectedCategory = categoryLabel,
              onCategorySelected = onUpdateCategory,
              accentColor = accentColor,
              testTagPrefix = "card_category_${entry.id}"
            )
          }

          // Tags if present
          val cleanTags = entry.tags.filter { !it.startsWith("due:") }
          if (cleanTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              cleanTags.take(5).forEach { tag ->
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GraphiteSurface)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = "#$tag",
                    fontSize = 10.sp,
                    color = TextMuted
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Action Row inside expanded card: Open in Tab, Mark Done/Reopen, Edit, Delete
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(accentColor.copy(alpha = 0.14f))
                  .clickable(onClick = onOpenInTab)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open tab",
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Open in ${entry.displayBadge}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                  )
                }
              }

              if (entry is MemoryEntry.Reminder) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ElectricLime.copy(alpha = 0.2f))
                    .clickable(onClick = onToggleReminderDone)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("toggle_done_memory_${entry.id}")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = if (entry.isCompleted) Icons.Default.Refresh else Icons.Default.CheckCircle,
                      contentDescription = if (entry.isCompleted) "Reopen" else "Mark Done",
                      tint = ElectricLime,
                      modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = if (entry.isCompleted) "Reopen" else "Mark Done",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = ElectricLime
                    )
                  }
                }
              }

              if (onEdit != null) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyanElectric.copy(alpha = 0.16f))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("edit_memory_${entry.id}")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Edit,
                      contentDescription = "Edit memory",
                      tint = CyanElectric,
                      modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Edit",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = CyanElectric
                    )
                  }
                }
              }
            }

            IconButton(
              onClick = onDelete,
              modifier = Modifier
                .size(28.dp)
                .testTag("delete_card_memory_${entry.id}")
            ) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete entry",
                tint = DeepRedWarning.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Full Memory Editor Dialog (`EditForm` from App.jsx) allowing the user to edit
 * title, content/details, context/trigger, category, comma-separated tags, and optional dueAt.
 */
@Composable
fun EditMemoryDialog(
  entry: MemoryEntry,
  onDismiss: () -> Unit,
  onSave: (MemoryEntry) -> Unit
) {
  val existingDue = entry.tags.firstOrNull { it.startsWith("due:") }?.removePrefix("due:").orEmpty()
  val existingTagsCsv = entry.tags.filter { !it.startsWith("due:") }.joinToString(", ")

  var titleText by remember(entry) { mutableStateOf(entry.primaryTitle) }
  var detailsText by remember(entry) {
    mutableStateOf(
      when (entry) {
        is MemoryEntry.Reminder -> entry.title
        is MemoryEntry.Preference -> entry.description.ifBlank { entry.details }
        is MemoryEntry.FixHistory -> entry.bestFix
      }
    )
  }
  var contextText by remember(entry) {
    mutableStateOf(
      when (entry) {
        is MemoryEntry.Reminder -> entry.triggerActivity
        is MemoryEntry.Preference -> entry.details
        is MemoryEntry.FixHistory -> entry.whatHappened
      }
    )
  }
  var selectedCategory by remember(entry) { mutableStateOf(entry.category.ifBlank { MemoryEntry.DEFAULT_CATEGORY }) }
  var tagsCsvText by remember(entry) { mutableStateOf(existingTagsCsv) }
  var dueAtText by remember(entry) { mutableStateOf(existingDue) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = DarkCharcoal,
    title = {
      Text(
        text = "Edit Memory (${entry.displayBadge})",
        color = CyanElectric,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = titleText,
          onValueChange = { titleText = it },
          label = { Text("Title", fontSize = 11.sp) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GraphiteSurface,
            unfocusedContainerColor = GraphiteSurface,
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_memory_title_input")
        )

        OutlinedTextField(
          value = detailsText,
          onValueChange = { detailsText = it },
          label = { Text("Details / Solution", fontSize = 11.sp) },
          maxLines = 4,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GraphiteSurface,
            unfocusedContainerColor = GraphiteSurface,
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_memory_details_input")
        )

        OutlinedTextField(
          value = contextText,
          onValueChange = { contextText = it },
          label = { Text("Person, place, or context (e.g. Dad, Walmart)", fontSize = 11.sp) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GraphiteSurface,
            unfocusedContainerColor = GraphiteSurface,
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_memory_context_input")
        )

        MemoryCategorySelectorRow(
          selectedCategory = selectedCategory,
          onCategorySelected = { selectedCategory = it },
          accentColor = CyanElectric,
          testTagPrefix = "edit_dialog_category"
        )

        OutlinedTextField(
          value = tagsCsvText,
          onValueChange = { tagsCsvText = it },
          label = { Text("Tags (comma-separated)", fontSize = 11.sp) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GraphiteSurface,
            unfocusedContainerColor = GraphiteSurface,
            focusedBorderColor = CyanElectric,
            unfocusedBorderColor = GraphiteBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_memory_tags_input")
        )

        OutlinedTextField(
          value = dueAtText,
          onValueChange = { dueAtText = it },
          label = { Text("Due date (optional, e.g. Friday 5:00 PM)", fontSize = 11.sp) },
          singleLine = true,
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
            .testTag("edit_memory_due_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedTags = tagsCsvText.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
          if (dueAtText.isNotBlank()) {
            parsedTags.add("due:${dueAtText.trim()}")
          }
          val finalTitle = titleText.trim().ifBlank { entry.primaryTitle }
          val finalDetails = detailsText.trim()
          val finalContext = contextText.trim()

          val updated: MemoryEntry = when (entry) {
            is MemoryEntry.Reminder -> entry.copy(
              title = finalTitle,
              triggerActivity = finalContext.ifBlank { entry.triggerActivity },
              category = selectedCategory,
              tags = parsedTags.distinct()
            )
            is MemoryEntry.Preference -> entry.copy(
              title = finalTitle,
              description = finalDetails.ifBlank { entry.description },
              details = finalContext,
              category = selectedCategory,
              tags = parsedTags.distinct()
            )
            is MemoryEntry.FixHistory -> entry.copy(
              problemSummary = finalTitle,
              bestFix = finalDetails.ifBlank { entry.bestFix },
              whatHappened = finalContext.ifBlank { entry.whatHappened },
              category = selectedCategory,
              tags = parsedTags.distinct()
            )
          }
          onSave(updated)
        },
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
        modifier = Modifier.testTag("save_edited_memory_button")
      ) {
        Text("Save Changes", color = JetBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    }
  )
}

@Composable
private fun MemoryCounterPill(
  count: Int,
  label: String,
  icon: ImageVector,
  color: Color,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 4.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = color,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = count.toString(),
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary
    )
    Text(
      text = label,
      fontSize = 11.sp,
      color = TextSecondary
    )
  }
}

@Composable
private fun MemoryFilterTabChip(
  label: String,
  selected: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  testTag: String
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (selected) accentColor.copy(alpha = 0.16f) else DarkCharcoal)
      .border(
        width = 1.dp,
        color = if (selected) accentColor else GraphiteBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp)
      .testTag(testTag)
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) accentColor else TextSecondary
    )
  }
}

private data class QuadrupleInfo<A, B, C, D>(
  val title: A,
  val icon: B,
  val accentColor: C,
  val statusText: D
)
