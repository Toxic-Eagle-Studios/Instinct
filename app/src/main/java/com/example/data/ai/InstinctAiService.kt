package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
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
import com.example.data.model.SuggestedCompanionTask
import com.example.data.model.UniversalIntentResult
import com.example.data.model.WhileYoureAtItSession
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

private const val TAG = "InstinctAiService"

class InstinctAiService {

  companion object {
    private val STOP_WORDS = setOf(
      "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "did", "do", "does",
      "for", "from", "get", "have", "how", "i", "in", "into", "is", "it", "me", "my", "of",
      "on", "or", "our", "please", "should", "so", "that", "the", "their", "them", "there",
      "this", "to", "was", "we", "were", "what", "when", "where", "who", "why", "will",
      "with", "would", "you", "your", "im", "ive", "its", "about", "normal", "usual",
      "last", "time", "again", "whats"
    )

    /**
     * Deterministic tokenizer ported from INSTINCT logic.js for reliable keyword & context matching.
     */
    fun tokenize(value: String): List<String> {
      return value.lowercase()
        .replace(Regex("[’']s\\b"), "")
        .replace(Regex("[’']"), "")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length > 1 && it !in STOP_WORDS }
        .map { if (it.endsWith("s") && it.length > 4) it.dropLast(1) else it }
        .distinct()
    }

    fun cleanLead(text: String): String {
      return text.replace(
        Regex("(?i)^(please\\s+)?(remind me (to|that)\\s+|remember (that|how)?\\s*|save (this|that)?\\s*|note (that)?\\s*|don['’]?t let me forget (to|that)?\\s*)"),
        ""
      ).trim()
    }

    val DEFAULT_CONTEXT_PRESETS: List<ContextQuickPreset> = listOf(
      ContextQuickPreset(
        id = "grocery_run",
        label = "Grocery / Walmart",
        query = "Heading to Walmart and grocery store",
        category = "Shopping",
        subtitle = "Errands, shopping list & food preferences"
      ),
      ContextQuickPreset(
        id = "visiting_dad",
        label = "Heading to Dad's",
        query = "I'm heading to Dad's",
        category = "Personal",
        subtitle = "Questions to ask Dad & family reminders"
      ),
      ContextQuickPreset(
        id = "coffee_food",
        label = "Coffee / Cafe",
        query = "Stopping for coffee and food order",
        category = "Food & Drinks",
        subtitle = "Drink orders, pizza/food specs & pickup notes"
      ),
      ContextQuickPreset(
        id = "hardware_diy",
        label = "Hardware / DIY",
        query = "Going to Home Depot hardware store for repairs",
        category = "Home",
        subtitle = "Repair tools needed, measurements & home tasks"
      ),
      ContextQuickPreset(
        id = "work_focus",
        label = "Work / Desk",
        query = "Starting work session at my desk and laptop",
        category = "Work",
        subtitle = "Work follow-ups, tech setup & priority tasks"
      ),
      ContextQuickPreset(
        id = "home_routine",
        label = "At Home / Chores",
        query = "At home doing laundry, cooking, and household chores",
        category = "Home",
        subtitle = "Laundry settings, home maintenance & routines"
      )
    )
  }

  private fun getModelByName(modelName: String) =
    Firebase.ai.generativeModel(modelName.ifBlank { AiModelTier.GEMINI_3_8_FLASH.modelId })

  private val briefingSchema by lazy {
    Schema.obj(
      properties = mapOf(
        "headline" to Schema.string(description = "Short punchy daily briefing headline"),
        "summary" to Schema.string(description = "2-3 sentence synthesis of the user's stored memories and priorities"),
        "priorityReminders" to Schema.array(
          items = Schema.string(),
          description = "Top 2-4 priority reminders or actions to tackle today"
        ),
        "activeContextChainSuggestion" to Schema.string(
          description = "Suggested 'While You're At It' errand bundle combining multiple items"
        ),
        "insightTip" to Schema.string(
          description = "Cross-memory pattern or helpful tip connecting preferences, fixes, and reminders"
        )
      )
    )
  }

  private val briefingModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = briefingSchema
      }
    )
  }

  private val checklistSchema by lazy {
    Schema.obj(
      properties = mapOf(
        "contextLabel" to Schema.string(description = "Clean display title for the active context or errand"),
        "contextCategory" to Schema.enumeration(
          values = MemoryEntry.CATEGORIES,
          description = "Best matching canonical category"
        ),
        "aiCoachingNote" to Schema.string(
          description = "1-2 sentence proactive 'While You're At It' summary connecting the user's tasks and preferences"
        ),
        "suggestedTasks" to Schema.array(
          items = Schema.obj(
            properties = mapOf(
              "title" to Schema.string(description = "Actionable companion task title"),
              "reason" to Schema.string(description = "Why this is smart to do while already in this context"),
              "category" to Schema.string(description = "Category name")
            )
          ),
          description = "2 to 4 proactive companion tasks for this activity or location"
        )
      )
    )
  }

  private val checklistModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = checklistSchema
      }
    )
  }

  private val autoTagSchema by lazy {
    Schema.obj(
      properties = mapOf(
        "assignments" to Schema.array(
          items = Schema.obj(
            properties = mapOf(
              "memoryId" to Schema.string(description = "ID of the memory entry"),
              "suggestedCategory" to Schema.enumeration(
                values = MemoryEntry.CATEGORIES,
                description = "Assigned canonical category"
              ),
              "suggestedTags" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 lowercase semantic tags"
              ),
              "reason" to Schema.string(description = "Short explanation for this categorization")
            )
          )
        )
      )
    )
  }

  private val autoTagModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = autoTagSchema
      }
    )
  }

  suspend fun processUniversalInput(
    userInput: String,
    imageBitmap: Bitmap? = null,
    savedReminders: List<Reminder>,
    savedPreferences: List<Preference>,
    savedFixes: List<Fix>,
    userId: String,
    useAi: Boolean = true,
    modelName: String = AiModelTier.GEMINI_3_8_FLASH.modelId,
    enableSearchGrounding: Boolean = true
  ): UniversalIntentResult = withContext(Dispatchers.IO) {
    val cleanInput = userInput.trim()

    // 1. Pre-compute deterministic local matches from saved records
    val localContextMatches = evaluateContextTriggersLocally(cleanInput, savedReminders)
    val localPreferenceMatches = evaluatePreferenceQueryLocally(cleanInput, savedPreferences)
    val localFixMatches = evaluateFixQueryLocally(cleanInput, savedFixes)

    // If user disabled Gemini intelligence in Settings, run deterministic offline local rules immediately
    if (!useAi) {
      return@withContext fallbackHeuristicResult(
        input = cleanInput.ifBlank { "Remember this photo" },
        savedReminders = savedReminders,
        savedPreferences = savedPreferences,
        savedFixes = savedFixes,
        userId = userId,
        matchedRemindersFallback = localContextMatches,
        matchedPreferencesFallback = localPreferenceMatches,
        matchedFixesFallback = localFixMatches,
        hasImage = imageBitmap != null
      ).copy(mode = "local")
    }

    val remindersContextSummary = savedReminders.take(30).joinToString("\n") {
      "- ID: ${it.id} | Task: ${it.title} | Trigger: ${it.triggerActivity} | People: ${it.relatedPeople.joinToString()} | Keywords: ${it.keywords.joinToString()} | Done: ${it.isCompleted}"
    }
    val preferencesContextSummary = savedPreferences.take(30).joinToString("\n") {
      "- ID: ${it.id} | Category: ${it.category} | Title: ${it.title} | Description: ${it.description} | Details: ${it.details}"
    }
    val fixesContextSummary = savedFixes.take(20).joinToString("\n") {
      "- ID: ${it.id} | Problem: ${it.problemSummary} | Fix: ${it.bestFix} | Tools: ${it.toolsNeeded.joinToString()} | VerifiedWorking: ${it.isVerifiedWorking}"
    }

    val systemInstruction = """
      You are the core intelligence of INSTINCT: a private second-brain app.
      Core philosophy: One app. One brain. Three everyday abilities:
      1. WHILE YOU'RE AT IT (SAVE_REMINDER & TRIGGER_CONTEXT):
         - SAVE_REMINDER when user says "Remind me to...", "Don't let me forget...", "Next time...". Extract person/place context (e.g. "Remind me to ask Dad about Friday" -> title: "Ask Dad about Friday", triggerActivity: "Dad", relatedPeople: ["Dad"]).
         - TRIGGER_CONTEXT when user says they are heading to, going to, at, visiting, or calling a person/place/activity (e.g. "I'm heading to Dad's", "Heading to Walmart"). Also suggest 2-3 proactive companion tasks in "suggestedCompanionTasks".
      2. UNDO IRL (REPORT_PROBLEM & RECALL_SOLUTION):
         - REPORT_PROBLEM when user made a digital or physical mistake (e.g. "I accidentally deleted a file", "I sent the wrong email", "I overwrote a document", "Oversalted soup", "Spilled grease"). Give 3-5 numbered, prudent, non-destructive recovery steps; make uncertainty clear. If dangerous (gas, mains electricity, hazardous chemicals, serious injuries), mark isHazardous: true.
      3. EXACTLY (SAVE_PREFERENCE & RETRIEVE_PREFERENCE):
         - SAVE_PREFERENCE when user states a preference or fact to remember (e.g. "My normal pizza order is pepperoni and mushrooms", "Remember my coffee order").
         - RETRIEVE_PREFERENCE when user asks a question about their saved memory (e.g. "What's my normal pizza order?"). CRITICAL HONESTY RULE: Never invent personal memories that are not in the user's saved list below. If nothing matches, state clearly that nothing matching was saved.
      ${if (enableSearchGrounding) "When answering general knowledge or real-world troubleshooting questions, ground your advice in accurate, up-to-date real-world facts." else ""}

      User's Saved Reminders:
      $remindersContextSummary

      User's Saved Preferences:
      $preferencesContextSummary

      User's Saved Fix History:
      $fixesContextSummary

      Analyze the user's input: "$cleanInput" ${
        if (imageBitmap != null) {
          """
          (CRITICAL MULTIMODAL INSTRUCTION: An image is attached. Inspect the visual content carefully:
          - If the image shows a repair issue, broken object, stain, spill, error screen, or physical problem (or if the user asks how to fix it), set intent to "REPORT_PROBLEM" and populate "extractedFix" with specific steps and toolsNeeded based on what is visible in the photo.
          - If the image shows a product label, coffee/food order, haircut reference, clothing size tag, or item the user wants to remember, set intent to "SAVE_PREFERENCE" and populate "extractedPreference" with detailed visual attributes without inferring private traits.)
          """.trimIndent()
        } else ""
      }
      
      Output ONLY valid JSON with no markdown and no backticks:
      {
        "intent": "SAVE_REMINDER" | "TRIGGER_CONTEXT" | "SAVE_PREFERENCE" | "RETRIEVE_PREFERENCE" | "REPORT_PROBLEM" | "RECALL_SOLUTION" | "GENERAL_ASSIST",
        "aiSummary": "Direct, friendly, concise message to display to the user",
        "isHazardous": false,
        "safetyWarning": null or "warning text",
        "matchedReminderIds": ["id1", "id2"],
        "matchedPreferenceIds": ["id1", "id2"],
        "matchedFixIds": ["id1"],
        "suggestedCompanionTasks": [
          {
            "title": "Check AA battery stock",
            "reason": "Since you are already heading to Walmart",
            "category": "Shopping"
          }
        ],
        "extractedReminder": {
          "title": "...",
          "triggerActivity": "...",
          "relatedPeople": ["..."],
          "relatedLocations": ["..."],
          "keywords": ["..."],
          "recurrence": "every_time" or "next_time_only" or "until_completed"
        } or null,
        "extractedPreference": {
          "title": "...",
          "category": "Food & Drinks" or "Clothing" or "Personal Care" or "Home" or "Technology" or "Shopping" or "Routines" or "Instructions" or "Custom",
          "description": "...",
          "details": "...",
          "tags": ["..."]
        } or null,
        "extractedFix": {
          "problemSummary": "...",
          "whatHappened": "...",
          "bestFix": "...",
          "steps": ["Step 1", "Step 2", "Step 3"],
          "toolsNeeded": ["Item 1", "Item 2"],
          "alternativeFix": "..."
        } or null
      }
    """.trimIndent()

    val effectiveUserPrompt = if (cleanInput.isBlank() && imageBitmap != null) {
      "Analyze this attached image. If it shows a repair issue, damage, or mistake, classify as REPORT_PROBLEM and provide step-by-step fix instructions. If it shows an object, label, or item to remember as a preference, classify as SAVE_PREFERENCE and extract its details."
    } else {
      cleanInput
    }

    try {
      val activeModel = getModelByName(modelName)
      val responseText = withTimeout(45_000L) {
        val parts = mutableListOf<Part>(
          TextPart(systemInstruction)
        )
        if (imageBitmap != null) {
          parts.add(ImagePart(imageBitmap))
        }
        parts.add(TextPart("User input: $effectiveUserPrompt"))

        val content = Content(role = "user", parts = parts)
        activeModel.generateContent(content).text
      }

      if (!responseText.isNullOrBlank()) {
        parseAiResponse(
          responseText = responseText,
          userInput = effectiveUserPrompt,
          savedReminders = savedReminders,
          savedPreferences = savedPreferences,
          savedFixes = savedFixes,
          userId = userId
        )
      } else {
        fallbackHeuristicResult(
          input = effectiveUserPrompt,
          savedReminders = savedReminders,
          savedPreferences = savedPreferences,
          savedFixes = savedFixes,
          userId = userId,
          matchedRemindersFallback = localContextMatches,
          matchedPreferencesFallback = localPreferenceMatches,
          matchedFixesFallback = localFixMatches,
          hasImage = imageBitmap != null
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini inference failed or offline, using deterministic local rules", e)
      fallbackHeuristicResult(
        input = effectiveUserPrompt,
        savedReminders = savedReminders,
        savedPreferences = savedPreferences,
        savedFixes = savedFixes,
        userId = userId,
        matchedRemindersFallback = localContextMatches,
        matchedPreferencesFallback = localPreferenceMatches,
        matchedFixesFallback = localFixMatches,
        hasImage = imageBitmap != null
      )
    }
  }

  /**
   * AI Audio Note Transcription & Structuring
   * Polishes raw spoken microphone transcripts into clean, structured memory text using gemini-3.8-flash.
   */
  suspend fun transcribeAndStructureAudioNote(
    rawSpokenText: String,
    useAi: Boolean = true,
    modelName: String = AiModelTier.GEMINI_3_8_FLASH.modelId
  ): String = withContext(Dispatchers.IO) {
    val clean = rawSpokenText.trim()
    if (clean.isBlank() || !useAi) return@withContext clean

    val prompt = """
      Clean up and structure this spoken voice transcript for INSTINCT while preserving the exact meaning, names, places, and numbers.
      Return ONLY the cleaned sentence(s) with proper punctuation and capitalization, no introductory text:
      "$clean"
    """.trimIndent()

    try {
      val result = withTimeout(20_000L) {
        getModelByName(modelName).generateContent(prompt).text?.trim()
      }
      if (!result.isNullOrBlank()) result.removeSurrounding("\"") else clean
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      clean
    }
  }

  /**
   * 1. Smart Memory Synthesis & Daily Briefing Digest
   */
  suspend fun generateDailyBriefing(
    memories: List<MemoryEntry>,
    reminders: List<Reminder>,
    preferences: List<Preference>,
    fixes: List<Fix>,
    useAi: Boolean = true
  ): DailyBriefingDigest = withContext(Dispatchers.IO) {
    val categoryBreakdown = memories.groupingBy { it.category.ifBlank { MemoryEntry.DEFAULT_CATEGORY } }
      .eachCount()

    val activeReminders = reminders.filter { !it.isCompleted }
    if (!useAi) {
      return@withContext buildLocalDailyBriefing(memories, activeReminders, preferences, fixes, categoryBreakdown)
    }

    val prompt = """
      Synthesize a personalized INSTINCT Shared Brain Daily Briefing based on the user's stored memories:
      
      Active Contextual Reminders (${activeReminders.size}):
      ${activeReminders.take(20).joinToString("\n") { "- ${it.title} (Trigger: ${it.triggerActivity}, Category: ${it.category})" }.ifBlank { "None yet" }}
      
      Saved Preferences (${preferences.size}):
      ${preferences.take(20).joinToString("\n") { "- ${it.title}: ${it.description} (${it.category})" }.ifBlank { "None yet" }}
      
      Verified Fixes (${fixes.size}):
      ${fixes.take(15).joinToString("\n") { "- ${it.problemSummary} -> ${it.bestFix} (Tools: ${it.toolsNeeded.joinToString()})" }.ifBlank { "None yet" }}
      
      Produce a concise, motivating daily briefing that highlights top priority reminders, suggests a smart 'While You're At It' errand chain combining related tasks/preferences, and shares one actionable cross-memory insight.
    """.trimIndent()

    try {
      val jsonText = withTimeout(45_000L) {
        briefingModel.generateContent(prompt).text
      }
      if (!jsonText.isNullOrBlank()) {
        val json = JSONObject(jsonText)
        val priorities = json.optJSONArray("priorityReminders")?.toStringList() ?: emptyList()
        return@withContext DailyBriefingDigest(
          headline = json.optString("headline", "Your Shared Brain Daily Briefing"),
          summary = json.optString("summary", "Here is a synthesis of your active reminders, preferences, and fixes."),
          priorityReminders = priorities.ifEmpty { activeReminders.take(3).map { "${it.title} (${it.triggerActivity})" } },
          activeContextChainSuggestion = json.optString(
            "activeContextChainSuggestion",
            buildLocalContextChainSuggestion(activeReminders, preferences, fixes)
          ),
          insightTip = json.optString(
            "insightTip",
            "Group errands by trigger location in 'While You're At It' to check off multiple tasks in a single trip."
          ),
          categoryBreakdown = categoryBreakdown
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Daily briefing generation fell back to local synthesis", e)
    }

    buildLocalDailyBriefing(memories, activeReminders, preferences, fixes, categoryBreakdown)
  }

  /**
   * 2. Multi-Model Streaming AI Follow-Up Chat on Memories, Errands & Fixes
   * Supports model selection (`gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`),
   * configurable persona role system instructions, and Google Search grounding context.
   */
  fun streamFollowUpChat(
    userQuestion: String,
    chatHistory: List<AiChatMessage>,
    memories: List<MemoryEntry>,
    activeContextLabel: String? = null,
    modelTier: AiModelTier = AiModelTier.GEMINI_3_8_FLASH,
    personaRole: ChatPersonaRole = ChatPersonaRole.SHARED_BRAIN,
    enableSearchGrounding: Boolean = true,
    useAi: Boolean = true
  ): Flow<String> = flow {
    if (!useAi) {
      emit(buildLocalChatReply(userQuestion, memories, activeContextLabel))
      return@flow
    }

    val memorySnapshot = memories.take(30).joinToString("\n") { entry ->
      "- [${entry.displayBadge} | ${entry.category}] ${entry.primaryTitle}: ${entry.summary}"
    }.ifBlank { "No stored memories yet." }

    val historyTranscript = chatHistory.takeLast(8).joinToString("\n") { msg ->
      "${if (msg.role == "user") "User" else "INSTINCT (${msg.personaLabel})"}: ${msg.text}"
    }

    val groundingDirective = if (enableSearchGrounding) {
      "Google Search Grounding Mode: ACTIVE. When the user asks about real-world facts, products, store details, or technical specs, provide accurate, verifiable real-world facts alongside their personal memories."
    } else {
      "Google Search Grounding Mode: OFF. Rely strictly on the user's stored memories and standard reasoning."
    }

    val prompt = """
      System Role: ${personaRole.systemInstruction}
      Model Tier: ${modelTier.displayName} (${modelTier.modelId})
      $groundingDirective
      Active Context: ${activeContextLabel ?: "General Shared Brain Inquiry"}
      
      User's Stored Memories (Reminders, Preferences, and Verified Fixes):
      $memorySnapshot
      
      Conversation History:
      $historyTranscript
      
      User's Question: $userQuestion
      
      Provide a helpful, concise, and practical response grounded in the user's actual stored memories. If the user asks for a personal preference or memory that is NOT in the list above, explicitly state that nothing matching was saved yet instead of inventing details.
    """.trimIndent()

    var emittedAny = false
    val accumulated = StringBuilder()
    try {
      val selectedModel = getModelByName(modelTier.modelId)
      withTimeout(60_000L) {
        selectedModel.generateContentStream(prompt).collect { chunk ->
          val part = chunk.text.orEmpty()
          if (part.isNotEmpty()) {
            emittedAny = true
            accumulated.append(part)
            emit(accumulated.toString())
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Streaming follow-up chat fallback triggered", e)
      if (!emittedAny) {
        emit(buildLocalChatReply(userQuestion, memories, activeContextLabel))
      }
    }

    if (!emittedAny && accumulated.isEmpty()) {
      emit(buildLocalChatReply(userQuestion, memories, activeContextLabel))
    }
  }.flowOn(Dispatchers.IO)

  /**
   * 3. AI Auto-Tagging & Smart Category Organization
   */
  suspend fun autoTagAndCategorizeMemories(
    entries: List<MemoryEntry>,
    useAi: Boolean = true
  ): List<AutoTagResult> = withContext(Dispatchers.IO) {
    if (entries.isEmpty()) return@withContext emptyList()

    val targetEntries = entries.take(20)
    if (!useAi) {
      return@withContext targetEntries.map { entry ->
        val newCat = inferCategoryLocally(entry.primaryTitle, entry.summary)
        val newTags = inferTagsLocally(entry.primaryTitle, entry.summary, newCat)
        AutoTagResult(
          memoryId = entry.id,
          title = entry.primaryTitle,
          oldCategory = entry.category,
          suggestedCategory = newCat,
          suggestedTags = (entry.tags + newTags).map { it.lowercase().trim() }.distinct().take(4),
          reason = "Categorized under $newCat using local deterministic rules"
        )
      }
    }

    val entriesListText = targetEntries.joinToString("\n") { entry ->
      "- id: \"${entry.id}\" | type: ${entry.type} | currentCategory: \"${entry.category}\" | title: \"${entry.primaryTitle}\" | details: \"${entry.summary}\" | currentTags: ${entry.tags}"
    }

    val prompt = """
      Organize these user memories into the best canonical category (${MemoryEntry.CATEGORIES.joinToString(", ")}) and generate 2-4 concise lowercase semantic tags for each item:
      $entriesListText
    """.trimIndent()

    try {
      val jsonText = withTimeout(45_000L) {
        autoTagModel.generateContent(prompt).text
      }
      if (!jsonText.isNullOrBlank()) {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("assignments")
        if (arr != null && arr.length() > 0) {
          val results = mutableListOf<AutoTagResult>()
          for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val memId = item.optString("memoryId")
            val original = targetEntries.find { it.id == memId } ?: continue
            val rawCat = item.optString("suggestedCategory", original.category)
            val normalizedCat = MemoryEntry.CATEGORIES.firstOrNull {
              it.equals(rawCat, ignoreCase = true)
            } ?: inferCategoryLocally(original.primaryTitle, original.summary)
            val tags = item.optJSONArray("suggestedTags")?.toStringList()
              ?.map { it.lowercase().trim() }
              ?.filter { it.isNotBlank() }
              .orEmpty()
              .ifEmpty { inferTagsLocally(original.primaryTitle, original.summary, normalizedCat) }

            results.add(
              AutoTagResult(
                memoryId = original.id,
                title = original.primaryTitle,
                oldCategory = original.category,
                suggestedCategory = normalizedCat,
                suggestedTags = tags,
                reason = item.optString("reason", "Organized by Shared Brain AI")
              )
            )
          }
          if (results.isNotEmpty()) {
            return@withContext results
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI auto-tagging fell back to local semantic organizer", e)
    }

    targetEntries.map { entry ->
      val newCat = inferCategoryLocally(entry.primaryTitle, entry.summary)
      val newTags = inferTagsLocally(entry.primaryTitle, entry.summary, newCat)
      AutoTagResult(
        memoryId = entry.id,
        title = entry.primaryTitle,
        oldCategory = entry.category,
        suggestedCategory = newCat,
        suggestedTags = (entry.tags + newTags).map { it.lowercase().trim() }.distinct().take(4),
        reason = "Categorized under $newCat based on activity & keywords"
      )
    }
  }

  /**
   * 4. Unified "While You're At It" Hub & Proactive AI Errand / Checklist Generator
   */
  suspend fun generateWhileYoureAtItSession(
    contextQuery: String,
    savedReminders: List<Reminder>,
    savedPreferences: List<Preference>,
    savedFixes: List<Fix>,
    useAi: Boolean = true
  ): WhileYoureAtItSession = withContext(Dispatchers.IO) {
    val cleanQuery = contextQuery.trim().ifBlank { "Current Errand & Activity" }

    // 1. Instant local cross-memory graph matching
    val matchedReminders = matchRemindersForContext(cleanQuery, savedReminders)
    val matchedPrefs = matchPreferencesForContext(cleanQuery, savedPreferences)
    val matchedFixes = matchFixesForContext(cleanQuery, savedFixes)
    val inferredCategory = inferCategoryLocally(cleanQuery, "")

    if (!useAi) {
      val label = formatContextHeadline(cleanQuery)
      return@withContext WhileYoureAtItSession(
        contextLabel = label,
        contextQuery = cleanQuery,
        contextCategory = inferredCategory,
        aiCoachingNote = buildLocalCoachingNote(label, matchedReminders, matchedPrefs, matchedFixes),
        matchedReminders = matchedReminders,
        matchedPreferences = matchedPrefs,
        matchedFixes = matchedFixes,
        aiSuggestedTasks = buildLocalCompanionTasks(cleanQuery, inferredCategory, matchedPrefs, matchedFixes)
      )
    }

    val prompt = """
      The user is starting this activity or errand ("While You're At It" context): "$cleanQuery"
      
      Matched Reminders already triggered:
      ${matchedReminders.joinToString("\n") { "- ${it.title} (When: ${it.triggerActivity})" }.ifBlank { "None" }}
      
      Matched Preferences (EXACTLY):
      ${matchedPrefs.joinToString("\n") { "- ${it.title}: ${it.description} (${it.details})" }.ifBlank { "None" }}
      
      Matched Fixes / Tools Needed (UNDO IRL):
      ${matchedFixes.joinToString("\n") { "- ${it.problemSummary}: Tools needed: ${it.toolsNeeded.joinToString()}" }.ifBlank { "None" }}
      
      Generate a clean contextLabel, best category, a concise aiCoachingNote, and 3 smart, practical "While You're At It" companion tasks (suggestedTasks) that save the user an extra trip or follow up on their stored preferences/fixes.
    """.trimIndent()

    try {
      val jsonText = withTimeout(45_000L) {
        checklistModel.generateContent(prompt).text
      }
      if (!jsonText.isNullOrBlank()) {
        val root = JSONObject(jsonText)
        val label = root.optString("contextLabel", formatContextHeadline(cleanQuery))
        val cat = root.optString("contextCategory", inferredCategory)
        val note = root.optString(
          "aiCoachingNote",
          "While you're at it, I bundled your matching reminders, preferences, and companion tasks."
        )
        val tasksArr = root.optJSONArray("suggestedTasks")
        val suggestedTasks = mutableListOf<SuggestedCompanionTask>()
        if (tasksArr != null) {
          for (i in 0 until tasksArr.length()) {
            val obj = tasksArr.getJSONObject(i)
            val title = obj.optString("title").trim()
            if (title.isNotBlank()) {
              suggestedTasks.add(
                SuggestedCompanionTask(
                  id = UUID.randomUUID().toString(),
                  title = title,
                  reason = obj.optString("reason", "Companion task for $label"),
                  category = obj.optString("category", cat).ifBlank { inferredCategory },
                  triggerContext = label
                )
              )
            }
          }
        }
        return@withContext WhileYoureAtItSession(
          contextLabel = label,
          contextQuery = cleanQuery,
          contextCategory = cat,
          aiCoachingNote = note,
          matchedReminders = matchedReminders,
          matchedPreferences = matchedPrefs,
          matchedFixes = matchedFixes,
          aiSuggestedTasks = suggestedTasks.ifEmpty {
            buildLocalCompanionTasks(cleanQuery, inferredCategory, matchedPrefs, matchedFixes)
          }
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "While You're At It AI checklist fell back to local graph bundle", e)
    }

    val label = formatContextHeadline(cleanQuery)
    WhileYoureAtItSession(
      contextLabel = label,
      contextQuery = cleanQuery,
      contextCategory = inferredCategory,
      aiCoachingNote = buildLocalCoachingNote(label, matchedReminders, matchedPrefs, matchedFixes),
      matchedReminders = matchedReminders,
      matchedPreferences = matchedPrefs,
      matchedFixes = matchedFixes,
      aiSuggestedTasks = buildLocalCompanionTasks(cleanQuery, inferredCategory, matchedPrefs, matchedFixes)
    )
  }

  /**
   * Instant synchronous local context bundle builder so the UI can show matched reminders,
   * preferences, and fixes with zero latency while Gemini streams/fetches companion suggestions.
   */
  fun buildInstantContextSession(
    contextQuery: String,
    savedReminders: List<Reminder>,
    savedPreferences: List<Preference>,
    savedFixes: List<Fix>,
    aiSuggestedTasks: List<SuggestedCompanionTask> = emptyList()
  ): WhileYoureAtItSession {
    val cleanQuery = contextQuery.trim().ifBlank { "Active Errand" }
    val label = formatContextHeadline(cleanQuery)
    val category = inferCategoryLocally(cleanQuery, "")
    val matchedReminders = matchRemindersForContext(cleanQuery, savedReminders)
    val matchedPrefs = matchPreferencesForContext(cleanQuery, savedPreferences)
    val matchedFixes = matchFixesForContext(cleanQuery, savedFixes)
    val suggestions = aiSuggestedTasks.ifEmpty {
      buildLocalCompanionTasks(cleanQuery, category, matchedPrefs, matchedFixes)
    }

    return WhileYoureAtItSession(
      contextLabel = label,
      contextQuery = cleanQuery,
      contextCategory = category,
      aiCoachingNote = buildLocalCoachingNote(label, matchedReminders, matchedPrefs, matchedFixes),
      matchedReminders = matchedReminders,
      matchedPreferences = matchedPrefs,
      matchedFixes = matchedFixes,
      aiSuggestedTasks = suggestions
    )
  }

  private fun parseAiResponse(
    responseText: String,
    userInput: String,
    savedReminders: List<Reminder>,
    savedPreferences: List<Preference>,
    savedFixes: List<Fix>,
    userId: String
  ): UniversalIntentResult {
    val cleanJson = responseText
      .replace("```json", "")
      .replace("```", "")
      .trim()

    val root = JSONObject(cleanJson)
    val intentStr = root.optString("intent", "GENERAL_ASSIST")
    val intentType = try {
      IntentType.valueOf(intentStr)
    } catch (e: Exception) {
      IntentType.GENERAL_ASSIST
    }
    var aiSummary = root.optString("aiSummary", "I've processed your request.")
    val isHazardous = root.optBoolean("isHazardous", false)
    val safetyWarning = root.optString("safetyWarning").takeIf { it.isNotBlank() }

    val matchedReminderIds = root.optJSONArray("matchedReminderIds")?.let { arr ->
      (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList()
    val matchedReminders = (savedReminders.filter { it.id in matchedReminderIds } +
      evaluateContextTriggersLocally(userInput, savedReminders)).distinctBy { it.id }

    val matchedPrefIds = root.optJSONArray("matchedPreferenceIds")?.let { arr ->
      (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList()
    val matchedPreferences = (savedPreferences.filter { it.id in matchedPrefIds } +
      evaluatePreferenceQueryLocally(userInput, savedPreferences)).distinctBy { it.id }

    val matchedFixIds = root.optJSONArray("matchedFixIds")?.let { arr ->
      (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList()
    val matchedFixes = (savedFixes.filter { it.id in matchedFixIds } +
      evaluateFixQueryLocally(userInput, savedFixes)).distinctBy { it.id }

    // Enforce strict honesty on RETRIEVE_PREFERENCE if nothing matched
    if (intentType == IntentType.RETRIEVE_PREFERENCE) {
      if (matchedPreferences.isEmpty() && matchedReminders.isEmpty() && matchedFixes.isEmpty()) {
        aiSummary = "Nothing matching in your memory. INSTINCT does not invent memories you never saved."
      } else if (matchedPreferences.isNotEmpty()) {
        val top = matchedPreferences.first()
        aiSummary = "Here’s what you saved: ${top.title} — ${top.description}"
      }
    }

    val suggestedTasks = root.optJSONArray("suggestedCompanionTasks")?.let { arr ->
      (0 until arr.length()).mapNotNull { idx ->
        val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
        val title = obj.optString("title").trim()
        if (title.isBlank()) null else SuggestedCompanionTask(
          id = UUID.randomUUID().toString(),
          title = title,
          reason = obj.optString("reason", "Suggested while you're at it"),
          category = obj.optString("category", inferCategoryLocally(title, "")),
          triggerContext = formatContextHeadline(userInput)
        )
      }
    } ?: emptyList()

    val extractedReminder = root.optJSONObject("extractedReminder")?.let { obj ->
      val remTitle = obj.optString("title", cleanLead(userInput))
      val inferredPerson = extractPersonOrPlaceContext(remTitle)
      val people = (obj.optJSONArray("relatedPeople")?.toStringList().orEmpty() +
        listOfNotNull(inferredPerson.takeIf { it.isNotBlank() })).distinct()
      val locs = obj.optJSONArray("relatedLocations")?.toStringList() ?: emptyList()
      val keywords = obj.optJSONArray("keywords")?.toStringList() ?: emptyList()
      val remTrigger = obj.optString("triggerActivity", "").ifBlank {
        inferredPerson.ifBlank { "Next time" }
      }
      Reminder(
        id = UUID.randomUUID().toString(),
        userId = userId,
        title = remTitle,
        triggerActivity = remTrigger,
        category = inferCategoryLocally(remTitle, remTrigger),
        relatedPeople = people,
        relatedLocations = locs,
        keywords = (keywords + tokenize("$remTitle $remTrigger")).distinct(),
        recurrence = obj.optString("recurrence", "every_time"),
        isCompleted = false,
        createdAt = Date(),
        updatedAt = Date()
      )
    }

    val extractedPreference = root.optJSONObject("extractedPreference")?.let { obj ->
      val tags = obj.optJSONArray("tags")?.toStringList() ?: emptyList()
      val prefTitle = obj.optString("title", "My Preference")
      val prefDesc = obj.optString("description", userInput)
      Preference(
        id = UUID.randomUUID().toString(),
        userId = userId,
        title = prefTitle,
        category = obj.optString("category", inferCategoryLocally(prefTitle, prefDesc)),
        description = prefDesc,
        details = obj.optString("details", ""),
        tags = (tags + tokenize("$prefTitle $prefDesc")).distinct().ifEmpty { inferTagsLocally(prefTitle, prefDesc, "") },
        createdAt = Date(),
        updatedAt = Date()
      )
    }

    val extractedFix = root.optJSONObject("extractedFix")?.let { obj ->
      val steps = obj.optJSONArray("steps")?.toStringList() ?: emptyList()
      val tools = obj.optJSONArray("toolsNeeded")?.toStringList() ?: emptyList()
      val prob = obj.optString("problemSummary", "Everyday fix")
      Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = prob,
        whatHappened = obj.optString("whatHappened", "Identified issue"),
        bestFix = obj.optString("bestFix", "Follow these recovery steps"),
        steps = steps,
        toolsNeeded = tools,
        alternativeFix = obj.optString("alternativeFix", ""),
        category = inferCategoryLocally(prob, tools.joinToString()),
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
    }

    return UniversalIntentResult(
      intent = intentType,
      aiSummary = aiSummary,
      reminderCandidate = extractedReminder,
      preferenceCandidate = extractedPreference,
      fixCandidate = extractedFix,
      isHazardous = isHazardous,
      safetyWarning = safetyWarning,
      matchedReminders = matchedReminders,
      matchedPreferences = matchedPreferences,
      matchedFixes = matchedFixes,
      aiSuggestedTasks = suggestedTasks,
      mode = "gemini"
    )
  }

  private fun JSONArray.toStringList(): List<String> {
    return (0 until length()).map { getString(it) }
  }

  fun matchRemindersForContext(input: String, reminders: List<Reminder>): List<Reminder> {
    return evaluateContextTriggersLocally(input, reminders)
  }

  fun matchPreferencesForContext(input: String, preferences: List<Preference>): List<Preference> {
    val lower = input.lowercase()
    val queryTerms = tokenize(input).toSet()
    return preferences.filter { pref ->
      val catMatch = lower.contains(pref.category.lowercase()) ||
        (lower.contains("walmart") || lower.contains("grocery") || lower.contains("store") || lower.contains("market") || lower.contains("costco") || lower.contains("target")) &&
        (pref.category.equals("Shopping", true) || pref.category.equals("Food & Drinks", true)) ||
        (lower.contains("coffee") || lower.contains("cafe") || lower.contains("starbucks") || lower.contains("breakfast") || lower.contains("lunch") || lower.contains("pizza")) &&
        (pref.category.equals("Food & Drinks", true) || pref.title.contains("coffee", true) || pref.title.contains("latte", true) || pref.title.contains("order", true) || pref.title.contains("pizza", true)) ||
        (lower.contains("hardware") || lower.contains("home depot") || lower.contains("lowe") || lower.contains("repair") || lower.contains("home") || lower.contains("laundry")) &&
        (pref.category.equals("Home", true) || pref.category.equals("Instructions", true) || pref.category.equals("Routines", true)) ||
        (lower.contains("work") || lower.contains("desk") || lower.contains("office") || lower.contains("laptop")) &&
        (pref.category.equals("Work", true) || pref.category.equals("Technology", true) || pref.category.equals("Tech", true))

      val prefBag = tokenize("${pref.title} ${pref.description} ${pref.details} ${pref.tags.joinToString(" ")}").toSet()
      val tokenMatch = queryTerms.any { it in prefBag }

      catMatch || tokenMatch
    }
  }

  fun matchFixesForContext(input: String, fixes: List<Fix>): List<Fix> {
    val lower = input.lowercase()
    val queryTerms = tokenize(input).toSet()
    return fixes.filter { fix ->
      val isHardwareOrHome = (lower.contains("hardware") || lower.contains("home depot") || lower.contains("store") || lower.contains("walmart") || lower.contains("home") || lower.contains("laundry") || lower.contains("repair"))
      val fixBag = tokenize("${fix.problemSummary} ${fix.bestFix} ${fix.toolsNeeded.joinToString(" ")}").toSet()
      val tokenMatch = queryTerms.any { it in fixBag }

      tokenMatch || (isHardwareOrHome && fix.toolsNeeded.isNotEmpty())
    }
  }

  /**
   * Extracts a person or place context from a reminder sentence (ported from logic.js + enhanced).
   * E.g., "ask Dad about Friday" -> "Dad"
   */
  fun extractPersonOrPlaceContext(text: String): String {
    val personMatch = Regex("\\b(?:ask|tell|call|text|meet|see|visit|with|for|at)\\s+([A-Z][a-zA-Z'-]*)").find(text)
    if (personMatch != null) {
      return personMatch.groupValues[1].replace(Regex("['’]s$"), "")
    }
    val lower = text.lowercase()
    return when {
      lower.contains("dad") -> "Dad"
      lower.contains("mom") -> "Mom"
      lower.contains("walmart") -> "Walmart"
      lower.contains("grocery") || lower.contains("store") -> "Grocery store"
      lower.contains("work") || lower.contains("office") -> "Work"
      else -> ""
    }
  }

  /**
   * Context matching using tokenized overlap (ported from logic.js `matchContext`) + semantic synonyms.
   */
  private fun evaluateContextTriggersLocally(input: String, reminders: List<Reminder>): List<Reminder> {
    val lower = input.lowercase().trim()
    if (lower.isBlank()) return emptyList()
    val inputTerms = tokenize(input).toSet()

    return reminders
      .filter { !it.isCompleted }
      .map { reminder ->
        val triggerLower = reminder.triggerActivity.lowercase().trim()
        val combinedText = "${reminder.triggerActivity} ${reminder.title} ${reminder.relatedPeople.joinToString(" ")} ${reminder.relatedLocations.joinToString(" ")} ${reminder.keywords.joinToString(" ")}"
        val reminderBag = tokenize(combinedText).toSet()

        var score = inputTerms.count { it in reminderBag } * 2
        if (triggerLower.isNotBlank() && lower.contains(triggerLower)) score += 3
        if (reminder.relatedPeople.any { p -> p.isNotBlank() && lower.contains(p.lowercase().trim()) }) score += 3
        if (reminder.relatedLocations.any { loc -> loc.isNotBlank() && lower.contains(loc.lowercase().trim()) }) score += 3
        if ((lower.contains("store") || lower.contains("grocery") || lower.contains("walmart") || lower.contains("shopping")) &&
          (reminder.category.equals("Shopping", true) || triggerLower.contains("walmart") || triggerLower.contains("store") || triggerLower.contains("grocery"))
        ) score += 2
        if ((lower.contains("dad") || lower.contains("father")) &&
          (reminder.relatedPeople.any { it.contains("dad", true) || it.contains("father", true) } || triggerLower.contains("dad") || reminder.title.contains("dad", true))
        ) score += 4

        reminder to score
      }
      .filter { (_, score) -> score > 0 }
      .sortedByDescending { (_, score) -> score }
      .map { (reminder, _) -> reminder }
  }

  /**
   * Tokenized search across saved preferences (ported from logic.js `searchMemories`).
   */
  fun evaluatePreferenceQueryLocally(input: String, preferences: List<Preference>): List<Preference> {
    val terms = tokenize(input)
    if (terms.isEmpty()) return emptyList()
    return preferences
      .map { pref ->
        val content = "${pref.title} ${pref.description} ${pref.details} ${pref.category} ${pref.tags.joinToString(" ")}"
        val bag = tokenize(content).toSet()
        val titleBag = tokenize(pref.title).toSet()
        val score = terms.sumOf { term ->
          (if (term in bag) 2 else 0) + (if (term in titleBag) 2 else 0)
        }
        pref to score
      }
      .filter { (_, score) -> score > 0 }
      .sortedByDescending { (_, score) -> score }
      .map { (pref, _) -> pref }
  }

  private fun evaluateFixQueryLocally(input: String, fixes: List<Fix>): List<Fix> {
    val terms = tokenize(input)
    if (terms.isEmpty()) return emptyList()
    return fixes
      .map { fix ->
        val content = "${fix.problemSummary} ${fix.whatHappened} ${fix.bestFix} ${fix.toolsNeeded.joinToString(" ")}"
        val bag = tokenize(content).toSet()
        val score = terms.count { it in bag }
        fix to score
      }
      .filter { (_, score) -> score > 0 }
      .sortedByDescending { (_, score) -> score }
      .map { (fix, _) -> fix }
  }

  /**
   * Deterministic offline brain (`interpretLocally` from logic.js + INSTINCT Android capabilities).
   */
  fun fallbackHeuristicResult(
    input: String,
    savedReminders: List<Reminder>,
    savedPreferences: List<Preference>,
    savedFixes: List<Fix>,
    userId: String,
    matchedRemindersFallback: List<Reminder> = emptyList(),
    matchedPreferencesFallback: List<Preference> = emptyList(),
    matchedFixesFallback: List<Fix> = emptyList(),
    hasImage: Boolean = false
  ): UniversalIntentResult {
    val text = input.trim()
    val lower = text.lowercase()

    // 1. Problem / Undo IRL check (matches logic.js regex + physical fixes)
    val isProblemReport = Regex("(?i)\\b(accidentally|deleted|lost|undo|restore|recover|messed up|made a mistake|sent the wrong|wrong message|wrong email|overwrote|erased|spilled|broke|broken|stuck|oversalted|too much salt|salty|stained|scratched|ruined|damaged|leaking|identify this problem|repair issue)\\b")
      .containsMatchIn(text) && !lower.startsWith("how did i")

    if (isProblemReport) {
      val fix = generateHeuristicFix(text, userId)
      return UniversalIntentResult(
        intent = IntentType.REPORT_PROBLEM,
        aiSummary = "UNDO IRL · Recovery guide: ${fix.bestFix}",
        fixCandidate = fix,
        mode = "local"
      )
    }

    // 2. Context Trigger check ("I'm heading to Dad's", "Going to Walmart", "I'm washing clothes and doing laundry", etc.)
    val isContextPhrase = Regex("(?i)^(i(['’]| a)?m |im |we(['’]| a)?re |we are )?(heading to|going to|on (my|our) way to|at |near |visiting |walking into |calling |stopping at |starting |doing |washing |cleaning |cooking |driving )")
      .containsMatchIn(text) || (matchedRemindersFallback.isNotEmpty() && Regex("(?i)^(i(['’]| a)?m |im |about to )").containsMatchIn(text))

    if (isContextPhrase) {
      val placeOrPerson = text.replace(
        Regex("(?i)^.*?\\b(heading to|going to|on (?:my|our) way to|at|near|visiting|walking into|calling|stopping at|starting)\\s+"),
        ""
      ).replace(Regex("[.!?]+$"), "").trim().ifBlank { text }

      val matchedReminders = (matchedRemindersFallback + evaluateContextTriggersLocally(placeOrPerson, savedReminders))
        .distinctBy { it.id }
      val matchedPrefs = (matchedPreferencesFallback + matchPreferencesForContext(placeOrPerson, savedPreferences))
        .distinctBy { it.id }
      val matchedFixes = (matchedFixesFallback + matchFixesForContext(placeOrPerson, savedFixes))
        .distinctBy { it.id }
      val cat = inferCategoryLocally(placeOrPerson, "")

      val summary = if (matchedReminders.isNotEmpty()) {
        "While you’re at it… Found ${matchedReminders.size} reminder(s) mentioning ${placeOrPerson.replace(Regex("['’]s$"), "")}."
      } else {
        "Nothing pending for $placeOrPerson yet—here are smart companion tasks while you're at it."
      }

      return UniversalIntentResult(
        intent = IntentType.TRIGGER_CONTEXT,
        aiSummary = summary,
        matchedReminders = matchedReminders,
        matchedPreferences = matchedPrefs,
        matchedFixes = matchedFixes,
        aiSuggestedTasks = buildLocalCompanionTasks(placeOrPerson, cat, matchedPrefs, matchedFixes),
        mode = "local"
      )
    }

    // 3. Retrieval Question check ("What's my normal pizza order?", "What is my normal pizza order?", etc.)
    val isQuestion = Regex("(?i)^(what|what's|whats|where|when|who|which|how did|how do|did i|do i|find|show me|search|pull up)\\b")
      .containsMatchIn(text) || text.contains("?")

    if (isQuestion) {
      val matchedPrefs = if (matchedPreferencesFallback.isNotEmpty()) {
        matchedPreferencesFallback
      } else {
        evaluatePreferenceQueryLocally(text, savedPreferences)
      }
      val matchedRems = evaluateContextTriggersLocally(text, savedReminders)
      val matchedFixes = if (matchedFixesFallback.isNotEmpty()) {
        matchedFixesFallback
      } else {
        evaluateFixQueryLocally(text, savedFixes)
      }

      if (matchedFixes.isNotEmpty() && (lower.contains("fix") || lower.contains("repair") || lower.contains("how did i"))) {
        val topFix = matchedFixes.first()
        return UniversalIntentResult(
          intent = IntentType.RECALL_SOLUTION,
          aiSummary = "Here’s what you saved: ${topFix.problemSummary} — ${topFix.bestFix}",
          matchedFixes = matchedFixes,
          mode = "local"
        )
      }

      val summary = when {
        matchedPrefs.isNotEmpty() -> {
          val top = matchedPrefs.first()
          "Here’s what you saved: ${top.title}${if (top.description.isNotBlank() && top.description != top.title) " — ${top.description}" else ""} (Results are taken from your actual saved records.)"
        }
        matchedRems.isNotEmpty() -> {
          val top = matchedRems.first()
          "Here’s what you saved in Reminders: ${top.title} (Context: ${top.triggerActivity})"
        }
        matchedFixes.isNotEmpty() -> {
          val top = matchedFixes.first()
          "Here’s what you saved in Fixes: ${top.problemSummary} — ${top.bestFix}"
        }
        else -> "Nothing matching in your memory. INSTINCT does not invent memories you never saved."
      }

      return UniversalIntentResult(
        intent = IntentType.RETRIEVE_PREFERENCE,
        aiSummary = summary,
        matchedPreferences = matchedPrefs,
        matchedReminders = matchedRems,
        matchedFixes = matchedFixes,
        mode = "local"
      )
    }

    // 4. Save Reminder check ("Remind me to ask Dad about Friday", "Don't let me forget...", etc.)
    if (Regex("(?i)^(please\\s+)?(remind me|don['’]?t let me forget|next time|whenever |when i )").containsMatchIn(text)) {
      val body = cleanLead(text).ifBlank { text }
      val contextPersonOrPlace = extractPersonOrPlaceContext(body)
      val trigger = contextPersonOrPlace.ifBlank { "Next time" }
      val inferredCat = inferCategoryLocally(body, trigger)
      val people = if (contextPersonOrPlace.isNotBlank() && contextPersonOrPlace[0].isUpperCase()) {
        listOf(contextPersonOrPlace)
      } else {
        emptyList()
      }

      val reminder = Reminder(
        id = UUID.randomUUID().toString(),
        userId = userId,
        title = body.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        triggerActivity = trigger,
        category = inferredCat,
        relatedPeople = people,
        keywords = (tokenize(body) + listOfNotNull(contextPersonOrPlace.lowercase().takeIf { it.isNotBlank() })).distinct(),
        recurrence = if (lower.contains("whenever") || lower.contains("every time")) "every_time" else "next_time_only",
        isCompleted = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      return UniversalIntentResult(
        intent = IntentType.SAVE_REMINDER,
        aiSummary = "Saved to your second brain: \"${reminder.title}\"${if (contextPersonOrPlace.isNotBlank()) " (Context: $contextPersonOrPlace)" else ""}.",
        reminderCandidate = reminder,
        mode = "local"
      )
    }

    // 5. Save Preference / Note check ("My normal pizza order is pepperoni and mushrooms", "Remember...", etc.)
    if (Regex("(?i)^(remember|save|note|my (usual|normal|favorite)|i (prefer|like|always|usually))").containsMatchIn(text) ||
      (hasImage && lower.contains("preference"))
    ) {
      val body = cleanLead(text).ifBlank { text }
      val cat = inferCategoryLocally(body, text)
      val pref = Preference(
        id = UUID.randomUUID().toString(),
        userId = userId,
        title = body.take(85).ifBlank { if (hasImage) "Captured Visual Preference" else "Personal Preference" },
        category = cat,
        description = if (hasImage && text.isBlank()) "Captured via camera/photo analysis" else text,
        details = if (body != text) body else "",
        tags = (tokenize(text) + inferTagsLocally(body, text, cat)).distinct().take(6),
        createdAt = Date(),
        updatedAt = Date()
      )
      return UniversalIntentResult(
        intent = IntentType.SAVE_PREFERENCE,
        aiSummary = "Saved to your second brain under $cat: \"${pref.title}\".",
        preferenceCandidate = pref,
        mode = "local"
      )
    }

    // 6. Default multimodal image diagnosis if an image was attached
    if (hasImage) {
      val fix = generateHeuristicFix(text.ifBlank { "Visual inspection and recovery" }, userId)
      return UniversalIntentResult(
        intent = IntentType.REPORT_PROBLEM,
        aiSummary = "Analyzed your photo and prepared a step-by-step recovery plan.",
        fixCandidate = fix,
        mode = "local"
      )
    }

    // 7. Default: Save as a general note/preference in the user's second brain (matching logic.js behavior)
    val cat = inferCategoryLocally(text, "")
    val notePref = Preference(
      id = UUID.randomUUID().toString(),
      userId = userId,
      title = text.take(80),
      category = cat,
      description = text,
      tags = tokenize(text).take(5),
      createdAt = Date(),
      updatedAt = Date()
    )
    return UniversalIntentResult(
      intent = IntentType.SAVE_PREFERENCE,
      aiSummary = "Saved to your second brain: \"${notePref.title}\".",
      preferenceCandidate = notePref,
      mode = "local"
    )
  }

  fun inferCategoryLocally(title: String, details: String): String {
    val combined = "$title $details".lowercase()
    return when {
      combined.contains("walmart") || combined.contains("buy") || combined.contains("grocery") ||
        combined.contains("store") || combined.contains("shop") || combined.contains("costco") ||
        combined.contains("target") || combined.contains("batteries") -> "Shopping"
      combined.contains("coffee") || combined.contains("latte") || combined.contains("espresso") ||
        combined.contains("pizza") || combined.contains("pepperoni") || combined.contains("mushroom") ||
        combined.contains("soup") || combined.contains("recipe") ||
        combined.contains("food") || combined.contains("drink") || combined.contains("tea") -> "Food & Drinks"
      combined.contains("work") || combined.contains("client") || combined.contains("meeting") ||
        combined.contains("sprint") || combined.contains("slide") || combined.contains("office") ||
        combined.contains("email") -> "Work"
      combined.contains("laundry") || combined.contains("furnace") || combined.contains("door") ||
        combined.contains("sink") || combined.contains("toilet") || combined.contains("home") ||
        combined.contains("scratch") || combined.contains("stain") || combined.contains("oven") -> "Home"
      combined.contains("vpn") || combined.contains("laptop") || combined.contains("phone") ||
        combined.contains("wifi") || combined.contains("password") || combined.contains("tech") ||
        combined.contains("file") || combined.contains("document") || combined.contains("photo") ||
        combined.contains("setting") -> "Tech"
      combined.contains("gym") || combined.contains("doctor") || combined.contains("vitamin") ||
        combined.contains("workout") || combined.contains("med") || combined.contains("health") -> "Health"
      combined.contains("travel") || combined.contains("flight") || combined.contains("hotel") ||
        combined.contains("trip") -> "Travel"
      else -> MemoryEntry.DEFAULT_CATEGORY
    }
  }

  fun inferTagsLocally(title: String, details: String, category: String): List<String> {
    val words = tokenize("$title $details")
      .take(4)
      .toMutableList()
    if (category.isNotBlank()) {
      words.add(0, category.lowercase().replace(" & ", "-").replace(" ", "-"))
    }
    return words.distinct().take(5)
  }

  private fun formatContextHeadline(query: String): String {
    val clean = query
      .replace(Regex("(?i)^(i['’]?m |i am |heading to |going to |stopping at |about to )"), "")
      .trim()
    return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
      .ifBlank { "Active Errand" }
  }

  private fun buildLocalCoachingNote(
    contextLabel: String,
    reminders: List<Reminder>,
    preferences: List<Preference>,
    fixes: List<Fix>
  ): String {
    val parts = mutableListOf<String>()
    if (reminders.isNotEmpty()) parts.add("${reminders.size} active reminder(s)")
    if (preferences.isNotEmpty()) parts.add("${preferences.size} saved preference(s)")
    if (fixes.isNotEmpty()) parts.add("${fixes.size} repair/tool note(s)")
    return if (parts.isNotEmpty()) {
      "While you’re at it ($contextLabel), your Shared Brain surfaced ${parts.joinToString(", ")} plus smart companion tasks below."
    } else {
      "No existing reminders were tied to $contextLabel yet—here are smart 'While You're At It' tasks you can check off or save."
    }
  }

  private fun buildLocalCompanionTasks(
    contextQuery: String,
    category: String,
    matchedPrefs: List<Preference>,
    matchedFixes: List<Fix>
  ): List<SuggestedCompanionTask> {
    val lower = contextQuery.lowercase()
    val tasks = mutableListOf<SuggestedCompanionTask>()

    matchedFixes.flatMap { it.toolsNeeded }.distinct().take(2).forEach { tool ->
      tasks.add(
        SuggestedCompanionTask(
          id = UUID.randomUUID().toString(),
          title = "Pick up $tool (from saved Undo IRL fix)",
          reason = "Needed for your saved repair solution",
          category = "Home",
          triggerContext = formatContextHeadline(contextQuery)
        )
      )
    }

    matchedPrefs.take(1).forEach { pref ->
      tasks.add(
        SuggestedCompanionTask(
          id = UUID.randomUUID().toString(),
          title = "Use saved spec: ${pref.title} (${pref.description.take(36)})",
          reason = "From your 'Exactly' preferences",
          category = pref.category.ifBlank { category },
          triggerContext = formatContextHeadline(contextQuery)
        )
      )
    }

    when {
      lower.contains("dad") || lower.contains("mom") || lower.contains("family") || lower.contains("call") -> {
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Confirm upcoming Friday / weekend plans & family dates",
            reason = "Easy to coordinate while visiting or talking with family",
            category = "Personal",
            triggerContext = formatContextHeadline(contextQuery)
          )
        )
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Return any borrowed tools or drop off mail",
            reason = "Saves a second trip while heading to Dad's",
            category = "Personal",
            triggerContext = formatContextHeadline(contextQuery)
          )
        )
      }
      lower.contains("walmart") || lower.contains("grocery") || lower.contains("store") || category == "Shopping" -> {
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Restock household essentials (AA batteries, paper towels, dish soap)",
            reason = "High-frequency items often forgotten during a store run",
            category = "Shopping",
            triggerContext = "Grocery / Store"
          )
        )
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Check pantry staples & return any reusable bags to the car",
            reason = "Saves a second mid-week grocery trip",
            category = "Shopping",
            triggerContext = "Grocery / Store"
          )
        )
      }
      lower.contains("coffee") || lower.contains("cafe") || category == "Food & Drinks" -> {
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Grab whole bean coffee or oat milk for home",
            reason = "While already stopping at the cafe",
            category = "Food & Drinks",
            triggerContext = "Coffee / Cafe"
          )
        )
      }
      lower.contains("hardware") || lower.contains("home depot") || lower.contains("repair") -> {
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Check air filter size & pick up dry graphite lubricant",
            reason = "Useful for preventative home maintenance while at hardware store",
            category = "Home",
            triggerContext = "Hardware Store"
          )
        )
      }
      lower.contains("work") || lower.contains("desk") || lower.contains("laptop") -> {
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Review open follow-ups & back up priority notes",
            reason = "Start your work block with a clear queue",
            category = "Work",
            triggerContext = "Starting Work"
          )
        )
      }
      else -> {
        tasks.add(
          SuggestedCompanionTask(
            id = UUID.randomUUID().toString(),
            title = "Batch nearby 5-minute errands along the same route",
            reason = "Combine tasks while you're already out",
            category = category,
            triggerContext = formatContextHeadline(contextQuery)
          )
        )
      }
    }

    return tasks.take(4)
  }

  private fun buildLocalContextChainSuggestion(
    activeReminders: List<Reminder>,
    preferences: List<Preference>,
    fixes: List<Fix>
  ): String {
    val firstRem = activeReminders.firstOrNull()
    val firstPref = preferences.firstOrNull()
    return when {
      firstRem != null && firstPref != null ->
        "When '${firstRem.triggerActivity.ifBlank { "you head out" }}', tackle '${firstRem.title}' and keep '${firstPref.title}' (${firstPref.description.take(30)}) handy."
      firstRem != null ->
        "Next time '${firstRem.triggerActivity}', knock out '${firstRem.title}' while you're already there."
      fixes.isNotEmpty() ->
        "You have ${fixes.size} verified fix(es) stored—tap any context pill in 'While You're At It' to bundle tools & errands."
      else ->
        "Tap any 'While You're At It' context pill (Grocery, Dad's, Coffee, Work, Home) to bundle tasks and preferences automatically."
    }
  }

  private fun buildLocalDailyBriefing(
    memories: List<MemoryEntry>,
    activeReminders: List<Reminder>,
    preferences: List<Preference>,
    fixes: List<Fix>,
    categoryBreakdown: Map<String, Int>
  ): DailyBriefingDigest {
    val topCategory = categoryBreakdown.maxByOrNull { it.value }?.key ?: MemoryEntry.DEFAULT_CATEGORY
    return DailyBriefingDigest(
      headline = if (activeReminders.isNotEmpty()) {
        "${activeReminders.size} Active Context Trigger(s) Ready"
      } else {
        "Shared Brain Synced (${memories.size} Memories)"
      },
      summary = "Your Shared Brain holds ${activeReminders.size} active reminder(s), ${preferences.size} exact preference(s), and ${fixes.size} verified fix(es), with most activity in $topCategory.",
      priorityReminders = if (activeReminders.isNotEmpty()) {
        activeReminders.take(3).map { "${it.title} — when ${it.triggerActivity.ifBlank { "triggered" }}" }
      } else {
        listOf("Add a contextual reminder or tap a 'While You're At It' mode to start bundling errands.")
      },
      activeContextChainSuggestion = buildLocalContextChainSuggestion(activeReminders, preferences, fixes),
      insightTip = "Tip: Use 'AI Auto-Organize' to automatically tag and categorize all your memories across Personal, Work, Shopping, and Home.",
      categoryBreakdown = categoryBreakdown
    )
  }

  private fun buildLocalChatReply(
    userQuestion: String,
    memories: List<MemoryEntry>,
    activeContextLabel: String?
  ): String {
    val terms = tokenize(userQuestion)
    val matching = memories.map { entry ->
      val bag = tokenize("${entry.primaryTitle} ${entry.summary} ${entry.tags.joinToString(" ")} ${entry.category}").toSet()
      val score = terms.count { it in bag }
      entry to score
    }.filter { it.second > 0 }
      .sortedByDescending { it.second }
      .take(3)
      .map { it.first }

    return if (matching.isNotEmpty()) {
      val bullets = matching.joinToString("\n") {
        "• [${it.displayBadge} · ${it.category}] ${it.primaryTitle} — ${it.summary}"
      }
      "Here’s what you saved${if (activeContextLabel != null) " ($activeContextLabel)" else ""}:\n$bullets\n\n(Results are taken from your actual saved records.)"
    } else {
      "Nothing matching in your memory for \"$userQuestion\". INSTINCT does not invent memories you never saved. You can save a new preference, reminder, or recovery guide anytime."
    }
  }

  /**
   * Generates recovery steps for both digital mistakes (deleted file, photo, wrong email, overwrote document, messed up setting)
   * and physical household mishaps (oversalted soup, grease stain, stuck zipper, wood scratch).
   */
  private fun generateHeuristicFix(input: String, userId: String): Fix {
    val lower = input.lowercase()
    return when {
      Regex("delet|erased|overwrote|lost (a |my )?(file|document|photo)").containsMatchIn(lower) -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = input.take(70).ifBlank { "Recover accidentally deleted or overwritten file/photo" },
        whatHappened = "File, photo, or document was accidentally deleted, erased, or overwritten.",
        bestFix = "Stop writing new data immediately and check Trash / Recently Deleted or version history.",
        steps = listOf(
          "Stop editing or saving over the affected file or storage drive immediately.",
          "Check Trash / Recently Deleted folder or the app's built-in Version History (e.g., Google Docs, Drive, Photos, iCloud, OneDrive).",
          "Check your cloud account sync bin and automatic device backups.",
          "Tell INSTINCT which app or device it happened in for exact recovery steps. Recovery is not guaranteed."
        ),
        toolsNeeded = listOf("Trash / Recently Deleted", "App Version History", "Cloud Backup"),
        alternativeFix = "If on desktop, check OS File History / Time Machine before restarting the device.",
        category = "Tech",
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      Regex("sent the wrong|wrong message|wrong email|wrong person").containsMatchIn(lower) -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = input.take(70).ifBlank { "Sent message or email to the wrong person" },
        whatHappened = "Message or email was dispatched to an unintended recipient or with incorrect content.",
        bestFix = "Trigger Undo Send / Unsend immediately, or send a brief professional correction.",
        steps = listOf(
          "Check immediately whether the app still offers 'Undo Send' or 'Unsend' (Gmail, iMessage, Slack, WhatsApp).",
          "If the recall window has passed, send a short, calm correction or follow-up with the right information.",
          "Avoid sending additional sensitive or personal details until you verify the recipient address.",
          "Save the final correction or checklist to your memory so you can reference it next time."
        ),
        toolsNeeded = listOf("Undo Send / Unsend feature", "Recipient verification"),
        alternativeFix = "If confidential credentials were included, rotate/revoke the exposed password or link immediately.",
        category = "Work",
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      lower.contains("setting") || lower.contains("messed up") -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = input.take(70).ifBlank { "Recover from a misconfigured setting" },
        whatHappened = "An app or device setting was changed and caused unexpected behavior.",
        bestFix = "Avoid irreversible resets; check recent setting changes or 'Reset to Default' for that single section.",
        steps = listOf(
          "Tell INSTINCT exactly what happened, which app or device you used, and what you just tapped.",
          "Avoid full factory resets or destructive actions until you isolate the specific toggle.",
          "Check built-in Undo, Search in Settings, or 'Restore Defaults' for that specific panel only.",
          "Save the working configuration to INSTINCT once resolved."
        ),
        toolsNeeded = listOf("Device/App Settings Search", "Section Restore Defaults"),
        alternativeFix = "Take a screenshot of working settings before changing advanced options next time.",
        category = "Tech",
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      lower.contains("salt") && lower.contains("soup") -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = "Oversalted soup",
        whatHappened = "Sodium concentration is too high for the broth volume.",
        bestFix = "Dilute the liquid or balance with raw potato wedges and acid.",
        steps = listOf(
          "Add 1 peeled raw potato cut into large chunks into the simmering soup to absorb excess sodium for 10-15 minutes.",
          "Add 1 cup of unsalted broth, water, or a splash of dairy/cream to dilute salinity.",
          "Add a splash of lemon juice or apple cider vinegar to brighten flavor and mask sharp saltiness.",
          "Remove the potato wedges before serving."
        ),
        toolsNeeded = listOf("Raw potato", "Unsalted broth or water", "Lemon juice or vinegar"),
        alternativeFix = "Double the broth, vegetables, and noodles without adding any additional seasonings.",
        category = "Food & Drinks",
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      lower.contains("stain") || lower.contains("grease") -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = "Grease or oil stain on clothing",
        whatHappened = "Oil lipids bonded with fabric fibers.",
        bestFix = "Break down lipid bonds using concentrated dish soap and warm water.",
        steps = listOf(
          "Blot (do not rub) the stain with a dry paper towel to remove excess oil.",
          "Apply liquid dish soap (e.g. Dawn) directly to the stained area.",
          "Gently work the soap in with your fingers or a soft-bristle toothbrush.",
          "Let sit for 10 minutes, then rinse with warm water.",
          "Machine wash on standard cycle and air dry (do not use dryer until stain is confirmed gone)."
        ),
        toolsNeeded = listOf("Liquid dish soap", "Paper towels", "Soft toothbrush", "Warm water"),
        alternativeFix = "Sprinkle baking soda or cornstarch over the grease spot, let sit 30 minutes to pull oil out, then wash.",
        category = "Home",
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      lower.contains("zipper") -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = "Stuck zipper slider",
        whatHappened = "Friction between zipper teeth or caught fabric fibers.",
        bestFix = "Lubricate the track with graphite or soap.",
        steps = listOf(
          "Inspect teeth to ensure fabric isn't pinched inside the slider.",
          "Rub a graphite pencil lead or bar of soap directly along the teeth above and below the slider.",
          "Gently slide back and forth with small movements until freed."
        ),
        toolsNeeded = listOf("Graphite pencil", "Bar of soap or lip balm"),
        alternativeFix = "Use a small cotton swab with a drop of olive oil or petroleum jelly along the track.",
        category = "Home",
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      lower.contains("scratch") -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = "Surface scratch on wood furniture",
        whatHappened = "Clear coat or surface veneer scuffed by abrasion.",
        bestFix = "Buff with natural walnut oils or matching mineral oil blend.",
        steps = listOf(
          "Clean the scratch area thoroughly with a soft microfiber cloth.",
          "Rub the meat of a raw shelled walnut directly into the scratch diagonally.",
          "The natural oils will darken the scratch and blend with the surrounding wood.",
          "Buff gently with a clean cloth."
        ),
        toolsNeeded = listOf("Raw walnut", "Microfiber cloth"),
        alternativeFix = "Use a wood finish touch-up marker or blend equal parts olive oil and vinegar.",
        category = "Home",
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
      else -> Fix(
        id = UUID.randomUUID().toString(),
        userId = userId,
        problemSummary = input.take(60),
        whatHappened = "Everyday mishap identified.",
        bestFix = "Follow prudent, non-destructive recovery steps.",
        steps = listOf(
          "Tell INSTINCT exactly what happened, which app or device you used, and what you tried.",
          "Avoid irreversible actions until you identify the safest recovery option.",
          "Check built-in Undo, Trash, version history, or backups when applicable.",
          "Save the solution after you verify it worked."
        ),
        toolsNeeded = listOf("Built-in Undo / History", "Safe inspection"),
        alternativeFix = "Consult manual or test in an inconspicuous area first.",
        category = inferCategoryLocally(input, ""),
        isVerifiedWorking = false,
        createdAt = Date(),
        updatedAt = Date()
      )
    }
  }
}
