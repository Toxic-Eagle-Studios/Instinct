package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.InstinctAiService
import com.example.data.model.AiChatMessage
import com.example.data.model.AiModelTier
import com.example.data.model.AutoTagResult
import com.example.data.model.ChatPersonaRole
import com.example.data.model.ContextQuickPreset
import com.example.data.model.DailyBriefingDigest
import com.example.data.model.Fix
import com.example.data.model.IntentType
import com.example.data.model.MemoryEntry
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.data.model.RepairSessionState
import com.example.data.model.RepairStatus
import com.example.data.model.SuggestedCompanionTask
import com.example.data.model.UnifiedMemoryItem
import com.example.data.model.UniversalIntentResult
import com.example.data.model.WhileYoureAtItSession
import com.example.data.repository.AuthRepository
import com.example.data.repository.InstinctRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

private const val TAG = "InstinctViewModel"

enum class NavTab {
  HOME,
  BRIEFING,
  REMINDERS,
  FIXES,
  MEMORIES,
  SETTINGS
}

class InstinctViewModel(application: Application) : AndroidViewModel(application) {

  val authRepository = AuthRepository(application)
  val instinctRepository = InstinctRepository(application)
  val memoryRepository: MemoryRepository = instinctRepository.memoryRepository
  private val aiService = InstinctAiService()

  val contextPresets: List<ContextQuickPreset> = InstinctAiService.DEFAULT_CONTEXT_PRESETS

  // Navigation
  private val _currentTab = MutableStateFlow(NavTab.HOME)
  val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

  // Active User ID Flow
  private val _currentUserId = MutableStateFlow(authRepository.getEffectiveUserId())
  val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

  // Sync state from Single Source of Truth repository
  val syncStatus: StateFlow<SyncStatus> = memoryRepository.syncStatus

  // AI Engine & Settings Controls
  private val _useAi = MutableStateFlow(true)
  val useAi: StateFlow<Boolean> = _useAi.asStateFlow()

  private val _selectedAiModel = MutableStateFlow(AiModelTier.GEMINI_3_8_FLASH)
  val selectedAiModel: StateFlow<AiModelTier> = _selectedAiModel.asStateFlow()

  private val _selectedChatPersona = MutableStateFlow(ChatPersonaRole.SHARED_BRAIN)
  val selectedChatPersona: StateFlow<ChatPersonaRole> = _selectedChatPersona.asStateFlow()

  private val _searchGroundingEnabled = MutableStateFlow(true)
  val searchGroundingEnabled: StateFlow<Boolean> = _searchGroundingEnabled.asStateFlow()

  private val _mapsGroundingEnabled = MutableStateFlow(true)
  val mapsGroundingEnabled: StateFlow<Boolean> = _mapsGroundingEnabled.asStateFlow()

  private val _liveVoiceModeActive = MutableStateFlow(false)
  val liveVoiceModeActive: StateFlow<Boolean> = _liveVoiceModeActive.asStateFlow()

  private val _reminderAlertsEnabled = MutableStateFlow(false)
  val reminderAlertsEnabled: StateFlow<Boolean> = _reminderAlertsEnabled.asStateFlow()

  // Keyword Search Query and Category Filter for Memory List
  private val _memorySearchQuery = MutableStateFlow("")
  val memorySearchQuery: StateFlow<String> = _memorySearchQuery.asStateFlow()

  private val _selectedCategoryFilter = MutableStateFlow("ALL")
  val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

  // Selected Category & Optional Due Date for Universal Input
  private val _selectedInputCategory = MutableStateFlow(MemoryEntry.DEFAULT_CATEGORY)
  val selectedInputCategory: StateFlow<String> = _selectedInputCategory.asStateFlow()

  private val _inputDueAt = MutableStateFlow("")
  val inputDueAt: StateFlow<String> = _inputDueAt.asStateFlow()

  // Shared Brain Data Streams (backed by Single Source of Truth)
  val memoryEntries: StateFlow<List<MemoryEntry>> = _currentUserId.flatMapLatest { uid ->
    memoryRepository.observeMemories(uid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Keyword-filtered MemoryEntry stream (queries Room searchMemories + multi-token matchesQuery)
  val filteredMemoryEntries: StateFlow<List<MemoryEntry>> = combine(
    _currentUserId,
    _memorySearchQuery
  ) { uid, query ->
    uid to query.trim()
  }.flatMapLatest { (uid, query) ->
    if (query.isBlank()) {
      memoryRepository.observeMemories(uid)
    } else {
      val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
      if (tokens.size <= 1) {
        memoryRepository.searchMemories(uid, query)
      } else {
        memoryRepository.observeMemories(uid).map { entries ->
          entries.filter { entry -> tokens.all { token -> entry.matchesQuery(token) } }
        }
      }
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val reminders: StateFlow<List<Reminder>> = _currentUserId.flatMapLatest { uid ->
    instinctRepository.observeReminders(uid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val preferences: StateFlow<List<Preference>> = _currentUserId.flatMapLatest { uid ->
    instinctRepository.observePreferences(uid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val fixes: StateFlow<List<Fix>> = _currentUserId.flatMapLatest { uid ->
    instinctRepository.observeFixes(uid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val unifiedMemories: StateFlow<List<UnifiedMemoryItem>> = _currentUserId.flatMapLatest { uid ->
    instinctRepository.unifiedRepository.observeAllMemories(uid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Universal Input State
  private val _inputText = MutableStateFlow("")
  val inputText: StateFlow<String> = _inputText.asStateFlow()

  private val _attachedImage = MutableStateFlow<Bitmap?>(null)
  val attachedImage: StateFlow<Bitmap?> = _attachedImage.asStateFlow()

  private val _isProcessingAi = MutableStateFlow(false)
  val isProcessingAi: StateFlow<Boolean> = _isProcessingAi.asStateFlow()

  private val _latestAiResult = MutableStateFlow<UniversalIntentResult?>(null)
  val latestAiResult: StateFlow<UniversalIntentResult?> = _latestAiResult.asStateFlow()

  // Interactive Problem Solving (UNDO IRL)
  private val _activeRepairSession = MutableStateFlow<RepairSessionState?>(null)
  val activeRepairSession: StateFlow<RepairSessionState?> = _activeRepairSession.asStateFlow()

  // Active Contextual Reminder Triggers & Unified "While You're At It" Session
  private val _surfacedContextReminders = MutableStateFlow<List<Reminder>>(emptyList())
  val surfacedContextReminders: StateFlow<List<Reminder>> = _surfacedContextReminders.asStateFlow()

  private val _surfacedContextPreferences = MutableStateFlow<List<Preference>>(emptyList())
  val surfacedContextPreferences: StateFlow<List<Preference>> = _surfacedContextPreferences.asStateFlow()

  private val _activeContextSession = MutableStateFlow<WhileYoureAtItSession?>(null)
  val activeContextSession: StateFlow<WhileYoureAtItSession?> = _activeContextSession.asStateFlow()

  // 1. Smart Memory Synthesis & Daily Briefing State
  private val _dailyBriefing = MutableStateFlow<DailyBriefingDigest?>(null)
  val dailyBriefing: StateFlow<DailyBriefingDigest?> = _dailyBriefing.asStateFlow()

  private val _isGeneratingBriefing = MutableStateFlow(false)
  val isGeneratingBriefing: StateFlow<Boolean> = _isGeneratingBriefing.asStateFlow()

  // 2. Streaming AI Follow-Up Chat State
  private val _isChatPanelOpen = MutableStateFlow(false)
  val isChatPanelOpen: StateFlow<Boolean> = _isChatPanelOpen.asStateFlow()

  private val _chatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
  val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

  private val _isStreamingChat = MutableStateFlow(false)
  val isStreamingChat: StateFlow<Boolean> = _isStreamingChat.asStateFlow()

  // 3. AI Auto-Tagging & Smart Category Organization State
  private val _isAutoOrganizing = MutableStateFlow(false)
  val isAutoOrganizing: StateFlow<Boolean> = _isAutoOrganizing.asStateFlow()

  private val _autoTagResults = MutableStateFlow<List<AutoTagResult>>(emptyList())
  val autoTagResults: StateFlow<List<AutoTagResult>> = _autoTagResults.asStateFlow()

  // Global Feedback Message
  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  // First time onboarding
  private val _showOnboarding = MutableStateFlow(false)
  val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.currentUser.collect { user ->
        _currentUserId.value = user?.uid ?: if (authRepository.isGuestMode.value) "guest_user" else ""
      }
    }
  }

  fun setTab(tab: NavTab) {
    _currentTab.value = tab
  }

  fun setUseAi(enabled: Boolean) {
    _useAi.value = enabled
    _userMessage.value = if (enabled) {
      "Gemini intelligence enabled (${_selectedAiModel.value.displayName})."
    } else {
      "Switched to offline deterministic local rules."
    }
  }

  fun setSelectedAiModel(modelTier: AiModelTier) {
    _selectedAiModel.value = modelTier
    _userMessage.value = "Active AI model set to ${modelTier.displayName}."
  }

  fun setSelectedChatPersona(persona: ChatPersonaRole) {
    _selectedChatPersona.value = persona
  }

  fun setSearchGroundingEnabled(enabled: Boolean) {
    _searchGroundingEnabled.value = enabled
  }

  fun setMapsGroundingEnabled(enabled: Boolean) {
    _mapsGroundingEnabled.value = enabled
    _userMessage.value = if (enabled) {
      "Google Maps grounding enabled for place & errand contexts."
    } else {
      "Google Maps grounding disabled."
    }
  }

  fun setLiveVoiceModeActive(active: Boolean) {
    _liveVoiceModeActive.value = active
  }

  fun setReminderAlertsEnabled(enabled: Boolean) {
    _reminderAlertsEnabled.value = enabled
    if (enabled) {
      val dueNow = reminders.value.filter { !it.isCompleted && it.keywords.any { kw -> kw.startsWith("due:", ignoreCase = true) } }
      if (dueNow.isNotEmpty()) {
        val notified = com.example.util.ReminderNotificationHelper.postDueRemindersNotification(
          context = getApplication(),
          dueReminders = dueNow
        )
        _userMessage.value = if (notified) {
          "Notification posted for ${dueNow.size} scheduled reminder(s) (in-app alerts active)."
        } else {
          "In-app alerts enabled (${dueNow.size} scheduled reminder(s) monitored while app is open)."
        }
      } else {
        com.example.util.ReminderNotificationHelper.ensureChannelCreated(getApplication())
        _userMessage.value = "In-app reminder alerts enabled. Add an optional due date when saving a reminder."
      }
    } else {
      _userMessage.value = "In-app reminder alerts paused."
    }
  }

  fun updateInputText(text: String) {
    _inputText.value = text
  }

  fun updateInputDueAt(dueAt: String) {
    _inputDueAt.value = dueAt
  }

  fun updateMemorySearchQuery(query: String) {
    _memorySearchQuery.value = query
  }

  fun clearMemorySearchQuery() {
    _memorySearchQuery.value = ""
  }

  fun updateSelectedCategoryFilter(category: String) {
    _selectedCategoryFilter.value = category
  }

  fun updateSelectedInputCategory(category: String) {
    _selectedInputCategory.value = category
  }

  fun setAttachedImage(bitmap: Bitmap?) {
    _attachedImage.value = bitmap
  }

  fun dismissUserMessage() {
    _userMessage.value = null
  }

  fun completeOnboarding() {
    _showOnboarding.value = false
  }

  /**
   * AI Audio Note Transcription & Structuring
   */
  fun transcribeAudioNoteIntoInput(rawSpokenText: String) {
    if (rawSpokenText.isBlank()) return
    viewModelScope.launch {
      val structured = aiService.transcribeAndStructureAudioNote(
        rawSpokenText = rawSpokenText,
        useAi = _useAi.value,
        modelName = _selectedAiModel.value.modelId
      )
      _inputText.value = if (_inputText.value.isBlank()) {
        structured
      } else {
        "${_inputText.value.trim()} $structured"
      }
    }
  }

  // Universal Input Submission
  fun submitUniversalInput(overrideQuery: String? = null) {
    val query = (overrideQuery ?: _inputText.value).trim()
    val image = _attachedImage.value
    val dueAt = _inputDueAt.value.trim()
    if (query.isBlank() && image == null) return

    viewModelScope.launch {
      _isProcessingAi.value = true
      _inputText.value = ""
      _attachedImage.value = null
      _inputDueAt.value = ""

      try {
        // Persist attached image to internal filesDir immediately so its URI is durable
        val persistedPhotoUri: String? = if (image != null) {
          com.example.util.ReminderNotificationHelper.saveBitmapToInternalStorage(
            context = getApplication(),
            bitmap = image
          )
        } else {
          null
        }

        val result = aiService.processUniversalInput(
          userInput = query,
          imageBitmap = image,
          savedReminders = reminders.value,
          savedPreferences = preferences.value,
          savedFixes = fixes.value,
          userId = _currentUserId.value,
          useAi = _useAi.value,
          modelName = _selectedAiModel.value.modelId,
          enableSearchGrounding = _searchGroundingEnabled.value
        )
        _latestAiResult.value = result

        when (result.intent) {
          IntentType.TRIGGER_CONTEXT -> {
            val combinedReminders = (result.matchedReminders + aiService.matchRemindersForContext(query, reminders.value))
              .distinctBy { it.id }
            val combinedPrefs = (result.matchedPreferences + aiService.matchPreferencesForContext(query, preferences.value))
              .distinctBy { it.id }
            val combinedFixes = (result.matchedFixes + aiService.matchFixesForContext(query, fixes.value))
              .distinctBy { it.id }

            _surfacedContextReminders.value = combinedReminders
            _surfacedContextPreferences.value = combinedPrefs

            val session = aiService.buildInstantContextSession(
              contextQuery = query,
              savedReminders = reminders.value,
              savedPreferences = preferences.value,
              savedFixes = fixes.value,
              aiSuggestedTasks = result.aiSuggestedTasks
            ).copy(
              matchedReminders = combinedReminders,
              matchedPreferences = combinedPrefs,
              matchedFixes = combinedFixes,
              aiCoachingNote = result.aiSummary
            )
            _activeContextSession.value = session
          }
          IntentType.REPORT_PROBLEM -> {
            result.fixCandidate?.let { candidate ->
              _activeRepairSession.value = RepairSessionState(activeFix = candidate)
            }
          }
          IntentType.SAVE_REMINDER -> {
            result.reminderCandidate?.let { candidate ->
              val autoCat = if (_selectedInputCategory.value != MemoryEntry.DEFAULT_CATEGORY) {
                _selectedInputCategory.value
              } else {
                aiService.inferCategoryLocally(candidate.title, candidate.triggerActivity)
              }
              val dueTag = if (dueAt.isNotBlank()) listOf("due:$dueAt") else emptyList()
              val autoKeywords = (candidate.keywords + aiService.inferTagsLocally(candidate.title, candidate.triggerActivity, autoCat) + dueTag).distinct()
              val withCategory = candidate.copy(
                category = autoCat,
                keywords = autoKeywords
              )
              instinctRepository.saveReminder(withCategory)
              memoryRepository.saveMemoryEntry(MemoryEntry.fromReminder(withCategory))
              if (_reminderAlertsEnabled.value && dueAt.isNotBlank()) {
                com.example.util.ReminderNotificationHelper.postDueRemindersNotification(
                  context = getApplication(),
                  dueReminders = listOf(withCategory)
                )
              }
              _userMessage.value = "Reminder saved ($autoCat): \"${withCategory.title}\"${if (dueAt.isNotBlank()) " [Due: $dueAt]" else ""}"
            }
          }
          IntentType.SAVE_PREFERENCE -> {
            result.preferenceCandidate?.let { candidate ->
              val autoCat = if (_selectedInputCategory.value != MemoryEntry.DEFAULT_CATEGORY) {
                _selectedInputCategory.value
              } else {
                candidate.category.takeIf { it.isNotBlank() && it != "Custom" }
                  ?: aiService.inferCategoryLocally(candidate.title, candidate.description)
              }
              val dueTag = if (dueAt.isNotBlank()) listOf("due:$dueAt") else emptyList()
              val autoTags = (candidate.tags + aiService.inferTagsLocally(candidate.title, candidate.description, autoCat) + dueTag).distinct()
              val withPhotoAndCategory = candidate.copy(
                category = autoCat,
                tags = autoTags,
                photoUrl = persistedPhotoUri ?: candidate.photoUrl
              )
              instinctRepository.savePreference(withPhotoAndCategory)
              memoryRepository.saveMemoryEntry(MemoryEntry.fromPreference(withPhotoAndCategory))
              _userMessage.value = "Preference saved ($autoCat): \"${withPhotoAndCategory.title}\""
            }
          }
          IntentType.RETRIEVE_PREFERENCE, IntentType.RECALL_SOLUTION -> {
            // Ensure surfaced results are visible and clear Active Context / Repair if needed
            _activeContextSession.value = null
            _activeRepairSession.value = null
          }
          else -> {
            // General assist
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error in universal input processing", e)
        _userMessage.value = "Error processing request: ${e.localizedMessage}"
      } finally {
        _isProcessingAi.value = false
      }
    }
  }

  // =========================================================================
  // UNIFIED "WHILE YOU'RE AT IT" HUB & INTERACTIVE CHECKLIST ACTIONS
  // =========================================================================

  fun activateContextSession(contextQuery: String) {
    val cleanQuery = contextQuery.trim()
    if (cleanQuery.isBlank()) return

    val instantSession = aiService.buildInstantContextSession(
      contextQuery = cleanQuery,
      savedReminders = reminders.value,
      savedPreferences = preferences.value,
      savedFixes = fixes.value
    ).copy(isGeneratingSuggestions = _useAi.value)

    _activeContextSession.value = instantSession
    _surfacedContextReminders.value = instantSession.matchedReminders
    _surfacedContextPreferences.value = instantSession.matchedPreferences

    if (!_useAi.value) return

    viewModelScope.launch {
      try {
        val aiSession = aiService.generateWhileYoureAtItSession(
          contextQuery = cleanQuery,
          savedReminders = reminders.value,
          savedPreferences = preferences.value,
          savedFixes = fixes.value,
          useAi = _useAi.value
        )
        val currentCompleted = _activeContextSession.value?.completedReminderIds ?: emptySet()
        _activeContextSession.value = aiSession.copy(
          completedReminderIds = currentCompleted,
          isGeneratingSuggestions = false
        )
        _surfacedContextReminders.value = aiSession.matchedReminders
        _surfacedContextPreferences.value = aiSession.matchedPreferences
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to enrich While You're At It session", e)
        _activeContextSession.value = _activeContextSession.value?.copy(isGeneratingSuggestions = false)
      }
    }
  }

  fun generateErrandChecklist(destinationOrActivity: String) {
    val query = destinationOrActivity.trim().ifBlank { "Grocery store & errands" }
    activateContextSession(query)
  }

  fun toggleCompanionTaskChecked(taskId: String) {
    val session = _activeContextSession.value ?: return
    val updatedTasks = session.aiSuggestedTasks.map { task ->
      if (task.id == taskId) task.copy(isChecked = !task.isChecked) else task
    }
    _activeContextSession.value = session.copy(aiSuggestedTasks = updatedTasks)
  }

  fun saveCompanionTaskToBrain(task: SuggestedCompanionTask) {
    viewModelScope.launch {
      val uid = _currentUserId.value
      val now = Date()
      val reminder = Reminder(
        id = UUID.randomUUID().toString(),
        userId = uid,
        title = task.title,
        triggerActivity = task.triggerContext.ifBlank { "Next errand" },
        category = task.category.ifBlank { MemoryEntry.DEFAULT_CATEGORY },
        keywords = aiService.inferTagsLocally(task.title, task.triggerContext, task.category),
        recurrence = "next_time_only",
        isCompleted = task.isChecked,
        createdAt = now,
        updatedAt = now
      )
      instinctRepository.saveReminder(reminder)
      memoryRepository.saveMemoryEntry(MemoryEntry.fromReminder(reminder))

      val session = _activeContextSession.value
      if (session != null) {
        val updatedTasks = session.aiSuggestedTasks.map {
          if (it.id == task.id) it.copy(isSavedToBrain = true) else it
        }
        _activeContextSession.value = session.copy(aiSuggestedTasks = updatedTasks)
      }
      _userMessage.value = "Saved companion task to While You're At It: \"${task.title}\""
    }
  }

  fun markReminderDone(reminder: Reminder) {
    viewModelScope.launch {
      if (reminder.recurrence == "every_time") {
        _userMessage.value = "Noted! Kept active for next time (${reminder.triggerActivity})."
      } else {
        instinctRepository.toggleReminderCompleted(reminder.id, true)
        memoryRepository.toggleReminderStatus(_currentUserId.value, reminder.id, true)
        _userMessage.value = "Reminder marked as done."
      }
      val session = _activeContextSession.value
      if (session != null) {
        _activeContextSession.value = session.copy(
          completedReminderIds = session.completedReminderIds + reminder.id
        )
      }
      _surfacedContextReminders.value = _surfacedContextReminders.value.filter { it.id != reminder.id }
    }
  }

  fun toggleReminderEntryCompletion(reminderEntry: MemoryEntry.Reminder) {
    viewModelScope.launch {
      val newCompleted = !reminderEntry.isCompleted
      instinctRepository.toggleReminderCompleted(reminderEntry.id, newCompleted)
      memoryRepository.toggleReminderStatus(_currentUserId.value, reminderEntry.id, newCompleted)
      _userMessage.value = if (newCompleted) "Marked \"${reminderEntry.title}\" done." else "Reopened \"${reminderEntry.title}\"."
    }
  }

  fun toggleReminderCompletion(reminder: Reminder) {
    viewModelScope.launch {
      val newCompleted = !reminder.isCompleted
      instinctRepository.toggleReminderCompleted(reminder.id, newCompleted)
      memoryRepository.toggleReminderStatus(_currentUserId.value, reminder.id, newCompleted)
      _userMessage.value = if (newCompleted) "Marked \"${reminder.title}\" done." else "Reopened \"${reminder.title}\"."
    }
  }

  fun snoozeReminder(reminder: Reminder) {
    _surfacedContextReminders.value = _surfacedContextReminders.value.filter { it.id != reminder.id }
    val session = _activeContextSession.value
    if (session != null) {
      _activeContextSession.value = session.copy(
        matchedReminders = session.matchedReminders.filter { it.id != reminder.id }
      )
    }
    _userMessage.value = "Snoozed \"${reminder.title}\" for now."
  }

  fun dismissActiveTriggers() {
    _surfacedContextReminders.value = emptyList()
    _surfacedContextPreferences.value = emptyList()
    _activeContextSession.value = null
  }

  // =========================================================================
  // 1. SMART MEMORY SYNTHESIS & DAILY BRIEFING DIGEST
  // =========================================================================

  fun generateDailyBriefing() {
    viewModelScope.launch {
      _isGeneratingBriefing.value = true
      try {
        val digest = aiService.generateDailyBriefing(
          memories = memoryEntries.value,
          reminders = reminders.value,
          preferences = preferences.value,
          fixes = fixes.value,
          useAi = _useAi.value
        )
        _dailyBriefing.value = digest
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error generating daily briefing", e)
        _userMessage.value = "Could not generate briefing: ${e.localizedMessage}"
      } finally {
        _isGeneratingBriefing.value = false
      }
    }
  }

  fun dismissDailyBriefing() {
    _dailyBriefing.value = null
  }

  // =========================================================================
  // 2. MULTI-MODEL STREAMING AI FOLLOW-UP CHAT + LIVE VOICE CONVERSATION
  // =========================================================================

  fun toggleChatPanel(open: Boolean? = null) {
    val target = open ?: !_isChatPanelOpen.value
    _isChatPanelOpen.value = target
    if (target && _chatMessages.value.isEmpty()) {
      val contextHint = _activeContextSession.value?.contextLabel
        ?: _activeRepairSession.value?.activeFix?.problemSummary
      val welcome = if (contextHint != null) {
        "I'm synced with your active context ($contextHint) and your ${memoryEntries.value.size} Shared Brain memories. Ask me any follow-up question or tap Live Voice to talk!"
      } else {
        "Ask me anything about your stored reminders, exact preferences, or how to fix an everyday problem. Switch models or personas above anytime."
      }
      _chatMessages.value = listOf(
        AiChatMessage(
          id = UUID.randomUUID().toString(),
          role = "assistant",
          text = welcome,
          modelUsed = _selectedAiModel.value.modelId,
          personaLabel = _selectedChatPersona.value.label,
          isGrounded = _searchGroundingEnabled.value
        )
      )
    }
  }

  fun sendFollowUpChatMessage(
    question: String,
    onFinalReplyReady: ((String) -> Unit)? = null
  ) {
    val cleanQuestion = question.trim()
    if (cleanQuestion.isBlank() || _isStreamingChat.value) return

    _isChatPanelOpen.value = true
    val modelTier = _selectedAiModel.value
    val persona = _selectedChatPersona.value
    val grounded = _searchGroundingEnabled.value

    val userMsg = AiChatMessage(
      id = UUID.randomUUID().toString(),
      role = "user",
      text = cleanQuestion,
      modelUsed = modelTier.modelId,
      personaLabel = persona.label,
      isGrounded = grounded
    )
    val assistantMsgId = UUID.randomUUID().toString()
    val assistantPlaceholder = AiChatMessage(
      id = assistantMsgId,
      role = "assistant",
      text = "Thinking...",
      modelUsed = modelTier.modelId,
      personaLabel = persona.label,
      isGrounded = grounded,
      isStreaming = true
    )

    val historyBefore = _chatMessages.value
    _chatMessages.value = historyBefore + userMsg + assistantPlaceholder
    _isStreamingChat.value = true

    viewModelScope.launch {
      var latestReply = ""
      try {
        val activeCtx = _activeContextSession.value?.contextLabel
          ?: _activeRepairSession.value?.activeFix?.problemSummary
        aiService.streamFollowUpChat(
          userQuestion = cleanQuestion,
          chatHistory = historyBefore + userMsg,
          memories = memoryEntries.value,
          activeContextLabel = activeCtx,
          modelTier = modelTier,
          personaRole = persona,
          enableSearchGrounding = grounded,
          useAi = _useAi.value
        ).collect { partialText ->
          latestReply = partialText
          _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.id == assistantMsgId) {
              msg.copy(text = partialText, isStreaming = true)
            } else {
              msg
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error in streaming chat", e)
        latestReply = "Unable to complete streaming response: ${e.localizedMessage ?: "Please try again."}"
        _chatMessages.value = _chatMessages.value.map { msg ->
          if (msg.id == assistantMsgId) {
            msg.copy(
              text = latestReply,
              isStreaming = false
            )
          } else {
            msg
          }
        }
      } finally {
        _chatMessages.value = _chatMessages.value.map { msg ->
          if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
        }
        _isStreamingChat.value = false
        if (latestReply.isNotBlank()) {
          onFinalReplyReady?.invoke(latestReply)
        }
      }
    }
  }

  fun saveChatReplyAsMemory(message: AiChatMessage) {
    if (message.text.isBlank()) return
    val cleanTitle = message.text.lineSequence().firstOrNull()?.take(48)?.trim()
      ?.replace(Regex("^[•\\-*]+\\s*"), "")
      .orEmpty()
      .ifBlank { "Shared Brain AI Insight" }
    val cat = aiService.inferCategoryLocally(cleanTitle, message.text)
    val tags = aiService.inferTagsLocally(cleanTitle, message.text, cat)

    val entry = MemoryEntry.Preference(
      userId = _currentUserId.value,
      timestamp = Date(),
      tags = tags,
      category = cat,
      title = cleanTitle,
      description = message.text,
      details = "Saved from INSTINCT Streaming AI Chat (${message.modelUsed})"
    )
    saveMemoryEntryDirectly(entry)
    _chatMessages.value = _chatMessages.value.map {
      if (it.id == message.id) it.copy(isSavedAsMemory = true) else it
    }
  }

  fun clearChatHistory() {
    _chatMessages.value = emptyList()
  }

  // =========================================================================
  // 3. AI AUTO-TAGGING & SMART CATEGORY ORGANIZATION
  // =========================================================================

  fun autoOrganizeMemories() {
    val currentEntries = memoryEntries.value
    if (currentEntries.isEmpty()) {
      _userMessage.value = "Add some memories first so AI can organize and tag them."
      return
    }
    viewModelScope.launch {
      _isAutoOrganizing.value = true
      try {
        val results = aiService.autoTagAndCategorizeMemories(currentEntries, useAi = _useAi.value)
        var updatedCount = 0

        results.forEach { res ->
          val existing = currentEntries.find { it.id == res.memoryId } ?: return@forEach
          val mergedTags = (existing.tags + res.suggestedTags).map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
          val updatedEntry = existing
            .withCategory(res.suggestedCategory)
            .withTags(mergedTags)

          memoryRepository.saveMemoryEntry(updatedEntry)
          when (updatedEntry) {
            is MemoryEntry.Reminder -> instinctRepository.saveReminder(updatedEntry.toModelReminder())
            is MemoryEntry.Preference -> instinctRepository.savePreference(updatedEntry.toModelPreference())
            is MemoryEntry.FixHistory -> instinctRepository.saveFix(updatedEntry.toModelFix())
          }
          updatedCount++
        }

        _autoTagResults.value = results
        _userMessage.value = "AI Auto-Organized $updatedCount memory entries with smart categories & tags!"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error in AI auto-organization", e)
        _userMessage.value = "AI Auto-Organize encountered an issue: ${e.localizedMessage}"
      } finally {
        _isAutoOrganizing.value = false
      }
    }
  }

  fun dismissAutoTagResults() {
    _autoTagResults.value = emptyList()
  }

  // =========================================================================
  // INTERACTIVE REPAIR ACTIONS (UNDO IRL)
  // =========================================================================

  fun nextRepairStep() {
    val session = _activeRepairSession.value ?: return
    if (!session.isLastStep) {
      _activeRepairSession.value = session.copy(currentStepIndex = session.currentStepIndex + 1)
    }
  }

  fun prevRepairStep() {
    val session = _activeRepairSession.value ?: return
    if (session.currentStepIndex > 0) {
      _activeRepairSession.value = session.copy(currentStepIndex = session.currentStepIndex - 1)
    }
  }

  fun repairWorked() {
    val session = _activeRepairSession.value ?: return
    viewModelScope.launch {
      val verifiedFix = session.activeFix.copy(isVerifiedWorking = true)
      instinctRepository.saveFix(verifiedFix)
      memoryRepository.saveMemoryEntry(MemoryEntry.fromFix(verifiedFix))
      _activeRepairSession.value = session.copy(status = RepairStatus.SAVED)
      _userMessage.value = "Solution verified & saved to your Shared Brain!"
    }
  }

  fun repairDidNotWork() {
    val session = _activeRepairSession.value ?: return
    val alternative = session.activeFix.alternativeFix
    _activeRepairSession.value = session.copy(
      status = RepairStatus.DID_NOT_WORK,
      note = if (alternative.isNotBlank()) "Alternative recommendation: $alternative" else "No further automatic fix found. We recommend specialized assistance."
    )
  }

  fun dismissRepairSession() {
    _activeRepairSession.value = null
  }

  // =========================================================================
  // CRUD, EDITING, IMPORT/EXPORT & CLOUD MIGRATION ACTIONS
  // =========================================================================

  fun saveReminderDirectly(title: String, trigger: String, recurrence: String, dueAt: String = "") {
    viewModelScope.launch {
      val autoCat = aiService.inferCategoryLocally(title, trigger)
      val dueTag = if (dueAt.isNotBlank()) listOf("due:$dueAt") else emptyList()
      val autoTags = (aiService.inferTagsLocally(title, trigger, autoCat) + dueTag).distinct()
      val reminder = Reminder(
        id = UUID.randomUUID().toString(),
        userId = _currentUserId.value,
        title = title,
        triggerActivity = trigger,
        category = autoCat,
        keywords = autoTags,
        recurrence = recurrence,
        isCompleted = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      instinctRepository.saveReminder(reminder)
      memoryRepository.saveMemoryEntry(MemoryEntry.fromReminder(reminder))
      _userMessage.value = "Reminder saved under $autoCat."
    }
  }

  fun deleteReminder(id: String) {
    viewModelScope.launch {
      instinctRepository.deleteReminder(id)
      memoryRepository.deleteMemoryEntry(_currentUserId.value, id)
      _userMessage.value = "Reminder removed."
    }
  }

  fun savePreferenceDirectly(title: String, category: String, description: String, details: String) {
    viewModelScope.launch {
      val autoCat = category.ifBlank { aiService.inferCategoryLocally(title, description) }
      val autoTags = aiService.inferTagsLocally(title, "$description $details", autoCat)
      val pref = Preference(
        id = UUID.randomUUID().toString(),
        userId = _currentUserId.value,
        title = title,
        category = autoCat,
        description = description,
        details = details,
        tags = autoTags,
        createdAt = Date(),
        updatedAt = Date()
      )
      instinctRepository.savePreference(pref)
      memoryRepository.saveMemoryEntry(MemoryEntry.fromPreference(pref))
      _userMessage.value = "Preference saved under $autoCat."
    }
  }

  fun deletePreference(id: String) {
    viewModelScope.launch {
      instinctRepository.deletePreference(id)
      memoryRepository.deleteMemoryEntry(_currentUserId.value, id)
      _userMessage.value = "Preference removed."
    }
  }

  fun deleteFix(id: String) {
    viewModelScope.launch {
      instinctRepository.deleteFix(id)
      memoryRepository.deleteMemoryEntry(_currentUserId.value, id)
      _userMessage.value = "Fix removed."
    }
  }

  fun saveMemoryEntryDirectly(entry: MemoryEntry) {
    viewModelScope.launch {
      val uid = _currentUserId.value
      val normalized = when (entry) {
        is MemoryEntry.Reminder -> entry.copy(userId = uid)
        is MemoryEntry.Preference -> entry.copy(userId = uid)
        is MemoryEntry.FixHistory -> entry.copy(userId = uid)
      }
      memoryRepository.saveMemoryEntry(normalized)
      when (normalized) {
        is MemoryEntry.Reminder -> instinctRepository.saveReminder(normalized.toModelReminder())
        is MemoryEntry.Preference -> instinctRepository.savePreference(normalized.toModelPreference())
        is MemoryEntry.FixHistory -> instinctRepository.saveFix(normalized.toModelFix())
      }
      _userMessage.value = "Saved to ${normalized.displayBadge}: \"${normalized.primaryTitle}\""
    }
  }

  /**
   * Full editing of an existing MemoryEntry (supports changing title, content/details,
   * trigger context, category, tags, optional dueAt, and completion state).
   */
  fun updateMemoryEntry(updatedEntry: MemoryEntry) {
    viewModelScope.launch {
      val uid = _currentUserId.value
      val normalized = when (updatedEntry) {
        is MemoryEntry.Reminder -> updatedEntry.copy(userId = uid, timestamp = Date())
        is MemoryEntry.Preference -> updatedEntry.copy(userId = uid, timestamp = Date())
        is MemoryEntry.FixHistory -> updatedEntry.copy(userId = uid, timestamp = Date())
      }
      memoryRepository.saveMemoryEntry(normalized)
      when (normalized) {
        is MemoryEntry.Reminder -> instinctRepository.saveReminder(normalized.toModelReminder())
        is MemoryEntry.Preference -> instinctRepository.savePreference(normalized.toModelPreference())
        is MemoryEntry.FixHistory -> instinctRepository.saveFix(normalized.toModelFix())
      }
      _userMessage.value = "Memory updated: \"${normalized.primaryTitle}\""
    }
  }

  fun saveQuickMemoryText(
    primaryText: String,
    memoryType: String = MemoryEntry.TYPE_PREFERENCE,
    secondaryText: String = "",
    tagsCsv: String = "",
    category: String = MemoryEntry.DEFAULT_CATEGORY
  ) {
    val cleanTitle = primaryText.trim()
    if (cleanTitle.isBlank()) return
    val cleanDetail = secondaryText.trim()
    val cleanCategory = if (category.isNotBlank() && category != MemoryEntry.DEFAULT_CATEGORY) {
      category.trim()
    } else {
      aiService.inferCategoryLocally(cleanTitle, cleanDetail)
    }
    val parsedTags = tagsCsv.split(",")
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .ifEmpty {
        aiService.inferTagsLocally(cleanTitle, cleanDetail, cleanCategory)
      }

    val now = Date()
    val uid = _currentUserId.value
    val entry: MemoryEntry = when (memoryType.lowercase()) {
      MemoryEntry.TYPE_REMINDER -> MemoryEntry.Reminder(
        userId = uid,
        timestamp = now,
        tags = parsedTags,
        category = cleanCategory,
        title = cleanTitle,
        triggerActivity = cleanDetail.ifBlank { aiService.extractPersonOrPlaceContext(cleanTitle).ifBlank { "Next time" } },
        recurrence = "every_time",
        isCompleted = false
      )
      MemoryEntry.TYPE_FIX_HISTORY -> MemoryEntry.FixHistory(
        userId = uid,
        timestamp = now,
        tags = parsedTags,
        category = cleanCategory,
        problemSummary = cleanTitle,
        whatHappened = "Directly logged solution",
        bestFix = cleanDetail.ifBlank { cleanTitle },
        steps = if (cleanDetail.isNotBlank()) listOf(cleanDetail) else listOf(cleanTitle),
        isVerifiedWorking = true
      )
      else -> MemoryEntry.Preference(
        userId = uid,
        timestamp = now,
        tags = parsedTags,
        title = cleanTitle,
        category = cleanCategory,
        description = cleanDetail.ifBlank { cleanTitle },
        details = cleanDetail
      )
    }
    saveMemoryEntryDirectly(entry)
  }

  fun updateMemoryEntryCategory(entry: MemoryEntry, newCategory: String) {
    viewModelScope.launch {
      val cleanCategory = newCategory.trim().ifBlank { MemoryEntry.DEFAULT_CATEGORY }
      val uid = _currentUserId.value
      val updatedEntry = entry.withCategory(cleanCategory)
      memoryRepository.updateMemoryCategory(uid, entry.id, cleanCategory)
      when (updatedEntry) {
        is MemoryEntry.Reminder -> instinctRepository.saveReminder(updatedEntry.toModelReminder())
        is MemoryEntry.Preference -> instinctRepository.savePreference(updatedEntry.toModelPreference())
        is MemoryEntry.FixHistory -> instinctRepository.saveFix(updatedEntry.toModelFix())
      }
      _userMessage.value = "Moved \"${entry.primaryTitle}\" to $cleanCategory."
    }
  }

  fun saveUnifiedMemory(memory: UnifiedMemoryItem) {
    viewModelScope.launch {
      instinctRepository.unifiedRepository.saveMemory(memory)
      memoryRepository.saveMemoryEntry(MemoryEntry.fromUnifiedMemoryItem(memory))
      _userMessage.value = "Memory saved to unified brain."
    }
  }

  fun deleteUnifiedMemory(memoryId: String) {
    viewModelScope.launch {
      instinctRepository.unifiedRepository.deleteMemory(memoryId)
      memoryRepository.deleteMemoryEntry(_currentUserId.value, memoryId)
      _userMessage.value = "Memory removed from unified brain."
    }
  }

  fun importMemoriesFromJson(jsonString: String) {
    viewModelScope.launch {
      val result = instinctRepository.importDataJson(jsonString, _currentUserId.value)
      if (result.isSuccess) {
        val count = result.getOrDefault(0)
        _userMessage.value = "Imported $count memories into your Shared Brain."
      } else {
        _userMessage.value = "Could not import backup: ${result.exceptionOrNull()?.localizedMessage ?: "Invalid JSON format."}"
      }
    }
  }

  fun copyDeviceMemoriesToCloud() {
    viewModelScope.launch {
      val result = instinctRepository.copyLocalDeviceMemoriesToCloud(_currentUserId.value)
      if (result.isSuccess) {
        val count = result.getOrDefault(0)
        _userMessage.value = "Copied $count local memories to cloud. Original local copies were preserved."
      } else {
        _userMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Cloud copy incomplete. Check connection and try again."
      }
    }
  }

  fun syncMemoriesNow() {
    viewModelScope.launch {
      val result = memoryRepository.syncRemoteChanges(_currentUserId.value)
      if (result.isSuccess) {
        _userMessage.value = "Memory synchronized."
      } else {
        _userMessage.value = "Sync completed using local cache."
      }
    }
  }

  fun clearCompletedReminders() {
    viewModelScope.launch {
      val result = instinctRepository.clearCompletedReminders(_currentUserId.value)
      val count = result.getOrDefault(0)
      _userMessage.value = if (count > 0) "Removed $count completed reminder(s)." else "No completed reminders to remove."
    }
  }

  fun showUserMessage(message: String) {
    _userMessage.value = message
  }

  fun clearAllData() {
    viewModelScope.launch {
      instinctRepository.clearAllData(_currentUserId.value)
      _surfacedContextReminders.value = emptyList()
      _surfacedContextPreferences.value = emptyList()
      _activeContextSession.value = null
      _dailyBriefing.value = null
      _chatMessages.value = emptyList()
      _autoTagResults.value = emptyList()
      _latestAiResult.value = null
      _activeRepairSession.value = null
      _userMessage.value = "Current memory collection cleared."
    }
  }
}
