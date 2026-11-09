package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import java.util.UUID

/**
 * Single shared brain collection item representing While You're At It (reminders),
 * Exactly (preferences), and Undo IRL (fix histories) in the unified Firestore collection:
 * /users/{userId}/memories/{memoryId}
 */
@IgnoreExtraProperties
data class UnifiedMemoryItem(
  val id: String = "",
  val userId: String = "",
  val type: String = TYPE_REMINDER, // "reminder", "preference", or "fix"
  val title: String = "",
  val content: String = "",
  val tags: List<String> = emptyList(),

  // While You're At It (Reminder fields)
  val triggerActivity: String = "",
  val relatedPeople: List<String> = emptyList(),
  val relatedLocations: List<String> = emptyList(),
  val recurrence: String = "every_time", // every_time, next_time_only, until_completed
  @get:PropertyName("isCompleted") val isCompleted: Boolean = false,

  // Exactly (Preference fields)
  val category: String = "",
  val details: String = "",
  val photoUrl: String? = null,

  // Undo IRL (Fix fields)
  val problemSummary: String = "",
  val whatHappened: String = "",
  val bestFix: String = "",
  val steps: List<String> = emptyList(),
  val toolsNeeded: List<String> = emptyList(),
  val alternativeFix: String = "",
  @get:PropertyName("isVerifiedWorking") val isVerifiedWorking: Boolean = false,

  @ServerTimestamp val createdAt: Date? = null,
  @ServerTimestamp val updatedAt: Date? = null
) {
  val isReminder: Boolean get() = type.equals(TYPE_REMINDER, ignoreCase = true)
  val isPreference: Boolean get() = type.equals(TYPE_PREFERENCE, ignoreCase = true)
  val isFix: Boolean get() = type.equals(TYPE_FIX, ignoreCase = true)

  val displayBadge: String
    get() = when (type.lowercase()) {
      TYPE_REMINDER -> "WHILE YOU'RE AT IT"
      TYPE_PREFERENCE -> "EXACTLY"
      TYPE_FIX -> "UNDO IRL"
      else -> "MEMORY"
    }

  val displaySubtitle: String
    get() = when (type.lowercase()) {
      TYPE_REMINDER -> if (triggerActivity.isNotBlank()) "When: $triggerActivity" else content
      TYPE_PREFERENCE -> if (category.isNotBlank()) "Category: $category" else content
      TYPE_FIX -> if (problemSummary.isNotBlank()) problemSummary else content
      else -> content
    }

  fun toReminder(): Reminder = Reminder(
    id = id,
    userId = userId,
    title = title,
    triggerActivity = triggerActivity,
    relatedPeople = relatedPeople,
    relatedLocations = relatedLocations,
    keywords = tags,
    recurrence = recurrence,
    isCompleted = isCompleted,
    category = category.ifBlank { "Personal" },
    createdAt = createdAt,
    updatedAt = updatedAt
  )

  fun toPreference(): Preference = Preference(
    id = id,
    userId = userId,
    title = title,
    category = if (category.isBlank()) "Personal" else category,
    description = if (content.isNotBlank()) content else title,
    details = details,
    tags = tags,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt
  )

  fun toFix(): Fix = Fix(
    id = id,
    userId = userId,
    problemSummary = if (problemSummary.isNotBlank()) problemSummary else title,
    whatHappened = whatHappened,
    bestFix = if (bestFix.isNotBlank()) bestFix else content,
    steps = steps,
    toolsNeeded = toolsNeeded,
    alternativeFix = alternativeFix,
    isVerifiedWorking = isVerifiedWorking,
    category = category.ifBlank { "Personal" },
    createdAt = createdAt,
    updatedAt = updatedAt
  )

  fun toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "type" to type,
    "title" to title,
    "content" to content,
    "tags" to tags,
    "triggerActivity" to triggerActivity,
    "relatedPeople" to relatedPeople,
    "relatedLocations" to relatedLocations,
    "recurrence" to recurrence,
    "isCompleted" to isCompleted,
    "category" to category.ifBlank { "Personal" },
    "details" to details,
    "photoUrl" to photoUrl,
    "problemSummary" to problemSummary,
    "whatHappened" to whatHappened,
    "bestFix" to bestFix,
    "steps" to steps,
    "toolsNeeded" to toolsNeeded,
    "alternativeFix" to alternativeFix,
    "isVerifiedWorking" to isVerifiedWorking,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
  ).filterValues { it != null }

  companion object {
    const val TYPE_REMINDER = "reminder"
    const val TYPE_PREFERENCE = "preference"
    const val TYPE_FIX = "fix"

    fun fromReminder(reminder: Reminder): UnifiedMemoryItem = UnifiedMemoryItem(
      id = reminder.id.ifBlank { UUID.randomUUID().toString() },
      userId = reminder.userId,
      type = TYPE_REMINDER,
      title = reminder.title,
      content = if (reminder.triggerActivity.isNotBlank()) "When ${reminder.triggerActivity}, remind to ${reminder.title}" else reminder.title,
      tags = reminder.keywords,
      triggerActivity = reminder.triggerActivity,
      relatedPeople = reminder.relatedPeople,
      relatedLocations = reminder.relatedLocations,
      recurrence = reminder.recurrence,
      isCompleted = reminder.isCompleted,
      category = reminder.category.ifBlank { "Personal" },
      createdAt = reminder.createdAt,
      updatedAt = reminder.updatedAt
    )

    fun fromPreference(preference: Preference): UnifiedMemoryItem = UnifiedMemoryItem(
      id = preference.id.ifBlank { UUID.randomUUID().toString() },
      userId = preference.userId,
      type = TYPE_PREFERENCE,
      title = preference.title,
      content = preference.description,
      tags = preference.tags,
      category = preference.category.ifBlank { "Personal" },
      details = preference.details,
      photoUrl = preference.photoUrl,
      createdAt = preference.createdAt,
      updatedAt = preference.updatedAt
    )

    fun fromFix(fix: Fix): UnifiedMemoryItem = UnifiedMemoryItem(
      id = fix.id.ifBlank { UUID.randomUUID().toString() },
      userId = fix.userId,
      type = TYPE_FIX,
      title = fix.problemSummary,
      content = fix.bestFix,
      tags = (fix.steps + fix.toolsNeeded).filter { it.length < 30 },
      category = fix.category.ifBlank { "Personal" },
      problemSummary = fix.problemSummary,
      whatHappened = fix.whatHappened,
      bestFix = fix.bestFix,
      steps = fix.steps,
      toolsNeeded = fix.toolsNeeded,
      alternativeFix = fix.alternativeFix,
      isVerifiedWorking = fix.isVerifiedWorking,
      createdAt = fix.createdAt,
      updatedAt = fix.updatedAt
    )
  }
}

/**
 * Type-safe sealed representation for pattern matching in UI & ViewModels
 */
sealed class TypedMemory {
  data class ReminderMemory(val item: UnifiedMemoryItem, val reminder: Reminder) : TypedMemory()
  data class PreferenceMemory(val item: UnifiedMemoryItem, val preference: Preference) : TypedMemory()
  data class FixMemory(val item: UnifiedMemoryItem, val fix: Fix) : TypedMemory()
  data class UnknownMemory(val item: UnifiedMemoryItem) : TypedMemory()

  companion object {
    fun from(item: UnifiedMemoryItem): TypedMemory = when (item.type.lowercase()) {
      UnifiedMemoryItem.TYPE_REMINDER -> ReminderMemory(item, item.toReminder())
      UnifiedMemoryItem.TYPE_PREFERENCE -> PreferenceMemory(item, item.toPreference())
      UnifiedMemoryItem.TYPE_FIX -> FixMemory(item, item.toFix())
      else -> UnknownMemory(item)
    }
  }
}
