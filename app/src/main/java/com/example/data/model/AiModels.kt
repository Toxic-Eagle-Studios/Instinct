package com.example.data.model

enum class IntentType {
  SAVE_REMINDER,
  TRIGGER_CONTEXT,
  SAVE_PREFERENCE,
  RETRIEVE_PREFERENCE,
  REPORT_PROBLEM,
  RECALL_SOLUTION,
  GENERAL_ASSIST
}

enum class RepairStatus {
  IN_PROGRESS,
  WORKED,
  DID_NOT_WORK,
  SAVED
}

/**
 * Supported Firebase AI Logic Gemini models for routing tasks by complexity and latency.
 */
enum class AiModelTier(
  val modelId: String,
  val displayName: String,
  val badge: String,
  val description: String
) {
  GEMINI_3_8_FLASH(
    modelId = "gemini-3.8-flash",
    displayName = "Gemini 3.8 Flash",
    badge = "BALANCED",
    description = "Default multimodal model for general reasoning, vision & grounding"
  ),
  GEMINI_4_ARGON(
    modelId = "gemini-4-argon",
    displayName = "Gemini 4 Argon",
    badge = "PRO REASONING",
    description = "Deep multi-step reasoning, complex synthesis & troubleshooting"
  ),
  GEMINI_3_1_FLASH_LITE(
    modelId = "gemini-3.1-flash-lite",
    displayName = "Gemini 3.1 Flash-Lite",
    badge = "FAST LITE",
    description = "Fast low-latency tasks, instant lookups & quick chat"
  ),
  GEMINI_3_5_FLASH_LITE(
    modelId = "gemini-3.5-flash-lite",
    displayName = "Gemini 3.5 Flash-Lite",
    badge = "FAST",
    description = "Ultra-low latency tagging, quick lookups & instant chat"
  );

  companion object {
    const val LIVE_VOICE_MODEL_ID = "gemini-3.8-live"
    const val TRANSCRIBE_MODEL_ID = "gemini-3.5-transcribe"
  }
}

/**
 * Configurable system instruction personas for the multi-turn Gemini Chatbot.
 */
enum class ChatPersonaRole(
  val id: String,
  val label: String,
  val systemInstruction: String
) {
  SHARED_BRAIN(
    id = "shared_brain",
    label = "Shared Brain",
    systemInstruction = "You are INSTINCT's Shared Brain assistant. Connect the user's reminders (While You're At It), exact preferences (Exactly), and verified fixes (Undo IRL). Never invent personal memories that were not saved."
  ),
  UNDO_IRL_COACH(
    id = "undo_irl",
    label = "Undo IRL Coach",
    systemInstruction = "You are INSTINCT's Undo IRL Recovery Coach. Provide 3-5 numbered, prudent, non-destructive recovery steps for digital or physical mistakes. Make uncertainty clear, warn before irreversible actions, and ask which app or device is involved when relevant."
  ),
  ERRAND_PLANNER(
    id = "errand_planner",
    label = "Errand Planner",
    systemInstruction = "You are INSTINCT's 'While You're At It' Errand & Context Planner. Group reminders, shopping specs, and repair supplies by location or person so the user saves extra trips."
  ),
  STRICT_MEMORY_VAULT(
    id = "strict_vault",
    label = "Strict Vault",
    systemInstruction = "You are INSTINCT's Strict Fact-Only Memory Vault. Answer strictly using the user's saved records. If nothing matching is in their saved memories, state clearly that nothing matching was saved and never fabricate details."
  )
}

data class SuggestedCompanionTask(
  val id: String,
  val title: String,
  val reason: String = "",
  val category: String = MemoryEntry.DEFAULT_CATEGORY,
  val triggerContext: String = "",
  val isChecked: Boolean = false,
  val isSavedToBrain: Boolean = false
)

data class UniversalIntentResult(
  val intent: IntentType = IntentType.GENERAL_ASSIST,
  val aiSummary: String = "",
  val reminderCandidate: Reminder? = null,
  val preferenceCandidate: Preference? = null,
  val fixCandidate: Fix? = null,
  val isHazardous: Boolean = false,
  val safetyWarning: String? = null,
  val matchedReminders: List<Reminder> = emptyList(),
  val matchedPreferences: List<Preference> = emptyList(),
  val matchedFixes: List<Fix> = emptyList(),
  val aiSuggestedTasks: List<SuggestedCompanionTask> = emptyList(),
  val mode: String = "gemini", // "gemini" or "local"
  val groundingSources: List<String> = emptyList()
)

data class RepairSessionState(
  val activeFix: Fix,
  val currentStepIndex: Int = 0,
  val status: RepairStatus = RepairStatus.IN_PROGRESS,
  val note: String = ""
) {
  val currentStep: String?
    get() = activeFix.steps.getOrNull(currentStepIndex)
  val isLastStep: Boolean
    get() = currentStepIndex >= activeFix.steps.size - 1
}

/**
 * Active "While You're At It" contextual session combining matched reminders,
 * saved preferences (Exactly), relevant fixes/tools (Undo IRL), and AI-suggested companion tasks.
 */
data class WhileYoureAtItSession(
  val contextLabel: String,
  val contextQuery: String,
  val contextCategory: String = MemoryEntry.DEFAULT_CATEGORY,
  val aiCoachingNote: String = "",
  val matchedReminders: List<Reminder> = emptyList(),
  val completedReminderIds: Set<String> = emptySet(),
  val matchedPreferences: List<Preference> = emptyList(),
  val matchedFixes: List<Fix> = emptyList(),
  val aiSuggestedTasks: List<SuggestedCompanionTask> = emptyList(),
  val isGeneratingSuggestions: Boolean = false
) {
  val totalChecklistCount: Int
    get() = matchedReminders.size + aiSuggestedTasks.size

  val completedChecklistCount: Int
    get() = matchedReminders.count { it.isCompleted || it.id in completedReminderIds } +
      aiSuggestedTasks.count { it.isChecked }

  val progressFraction: Float
    get() = if (totalChecklistCount == 0) 0f else (completedChecklistCount.toFloat() / totalChecklistCount.toFloat()).coerceIn(0f, 1f)
}

/**
 * Preset activity/location context for one-tap activation in the "While You're At It" hub.
 */
data class ContextQuickPreset(
  val id: String,
  val label: String,
  val query: String,
  val category: String,
  val subtitle: String
)

/**
 * AI-synthesized daily briefing digest across all three Shared Brain memory types.
 */
data class DailyBriefingDigest(
  val headline: String,
  val summary: String,
  val priorityReminders: List<String> = emptyList(),
  val activeContextChainSuggestion: String = "",
  val insightTip: String = "",
  val categoryBreakdown: Map<String, Int> = emptyMap(),
  val generatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Message item for the Streaming AI Follow-Up Chat on memories, errands, and repairs.
 */
data class AiChatMessage(
  val id: String,
  val role: String,
  val text: String,
  val modelUsed: String = "gemini-3.8-flash",
  val personaLabel: String = "Shared Brain",
  val isGrounded: Boolean = false,
  val isStreaming: Boolean = false,
  val isSavedAsMemory: Boolean = false,
  val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of AI Auto-Tagging & Smart Category Organization for a MemoryEntry.
 */
data class AutoTagResult(
  val memoryId: String,
  val title: String,
  val oldCategory: String,
  val suggestedCategory: String,
  val suggestedTags: List<String>,
  val reason: String = ""
)
