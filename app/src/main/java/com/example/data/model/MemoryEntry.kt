package com.example.data.model

import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID

/**
 * Sealed class hierarchy representing unified memory entries across INSTINCT's three core pillars:
 * - **While You're At It** ([Reminder]) — Context-aware reminders triggered by activities, places, or people.
 * - **Exactly** ([Preference]) — Personal preferences, standards, and repeatable successes.
 * - **Undo IRL** ([FixHistory]) — Problem-solving steps, repair guides, and verified fixes.
 *
 * Each subclass shares a common metadata base:
 * - [id]: Unique identifier for the memory entry
 * - [userId]: Owner user ID (or guest identifier)
 * - [timestamp]: Creation or last-updated timestamp
 * - [type]: Discriminator string ([TYPE_REMINDER], [TYPE_PREFERENCE], or [TYPE_FIX_HISTORY])
 * - [tags]: Semantic keywords and tags for cross-feature retrieval
 */
sealed class MemoryEntry(
  open val id: String,
  open val userId: String,
  open val timestamp: Date,
  open val type: String,
  open val tags: List<String>,
  open val category: String = DEFAULT_CATEGORY
) {

  /**
   * While You're At It: Contextual reminder entry.
   * Contains trigger conditions ([triggerActivity], [relatedPeople], [relatedLocations]),
   * recurrence rules ([recurrence]), completion state ([isCompleted]), and organization [category].
   */
  data class Reminder(
    override val id: String = UUID.randomUUID().toString(),
    override val userId: String = "",
    override val timestamp: Date = Date(),
    override val type: String = TYPE_REMINDER,
    override val tags: List<String> = emptyList(),
    override val category: String = DEFAULT_CATEGORY,
    val title: String = "",
    val triggerActivity: String = "",
    val relatedPeople: List<String> = emptyList(),
    val relatedLocations: List<String> = emptyList(),
    val recurrence: String = "every_time", // every_time, next_time_only, until_completed
    val isCompleted: Boolean = false
  ) : MemoryEntry(id, userId, timestamp, type, tags, category) {

    fun toModelReminder(): com.example.data.model.Reminder = com.example.data.model.Reminder(
      id = id,
      userId = userId,
      title = title,
      triggerActivity = triggerActivity,
      relatedPeople = relatedPeople,
      relatedLocations = relatedLocations,
      keywords = tags,
      recurrence = recurrence,
      isCompleted = isCompleted,
      category = category.ifBlank { DEFAULT_CATEGORY },
      createdAt = timestamp,
      updatedAt = timestamp
    )
  }

  /**
   * Exactly: User preference entry.
   * Contains [category], [description], detailed notes ([details]), and optional [photoUri].
   */
  data class Preference(
    override val id: String = UUID.randomUUID().toString(),
    override val userId: String = "",
    override val timestamp: Date = Date(),
    override val type: String = TYPE_PREFERENCE,
    override val tags: List<String> = emptyList(),
    val title: String = "",
    override val category: String = DEFAULT_CATEGORY,
    val description: String = "",
    val details: String = "",
    val photoUri: String? = null
  ) : MemoryEntry(id, userId, timestamp, type, tags, category) {

    val photoUrl: String? get() = photoUri

    fun toModelPreference(): com.example.data.model.Preference = com.example.data.model.Preference(
      id = id,
      userId = userId,
      title = title,
      category = category.ifBlank { DEFAULT_CATEGORY },
      description = description,
      details = details,
      tags = tags,
      photoUrl = photoUri,
      createdAt = timestamp,
      updatedAt = timestamp
    )
  }

  /**
   * Undo IRL: Problem recovery and diagnosis entry.
   * Contains [problemSummary], [whatHappened], [bestFix], step-by-step [steps],
   * [toolsNeeded], [alternativeFix], [isVerifiedWorking] status, and organization [category].
   */
  data class FixHistory(
    override val id: String = UUID.randomUUID().toString(),
    override val userId: String = "",
    override val timestamp: Date = Date(),
    override val type: String = TYPE_FIX_HISTORY,
    override val tags: List<String> = emptyList(),
    override val category: String = DEFAULT_CATEGORY,
    val problemSummary: String = "",
    val whatHappened: String = "",
    val bestFix: String = "",
    val steps: List<String> = emptyList(),
    val toolsNeeded: List<String> = emptyList(),
    val alternativeFix: String = "",
    val isVerifiedWorking: Boolean = false
  ) : MemoryEntry(id, userId, timestamp, type, tags, category) {

    fun toModelFix(): com.example.data.model.Fix = com.example.data.model.Fix(
      id = id,
      userId = userId,
      problemSummary = problemSummary,
      whatHappened = whatHappened,
      bestFix = bestFix,
      steps = steps,
      toolsNeeded = toolsNeeded,
      alternativeFix = alternativeFix,
      isVerifiedWorking = isVerifiedWorking,
      category = category.ifBlank { DEFAULT_CATEGORY },
      createdAt = timestamp,
      updatedAt = timestamp
    )
  }

  /**
   * Human-readable feature badge for this memory entry.
   */
  val displayBadge: String
    get() = when (this) {
      is Reminder -> "WHILE YOU'RE AT IT"
      is Preference -> "EXACTLY"
      is FixHistory -> "UNDO IRL"
    }

  /**
   * Primary display title across any memory subclass.
   */
  val primaryTitle: String
    get() = when (this) {
      is Reminder -> title
      is Preference -> title
      is FixHistory -> problemSummary
    }

  /**
   * Concise summary of the memory entry.
   */
  val summary: String
    get() = when (this) {
      is Reminder -> if (triggerActivity.isNotBlank()) "When $triggerActivity: $title" else title
      is Preference -> if (description.isNotBlank()) "$title • $description" else title
      is FixHistory -> if (bestFix.isNotBlank()) "$problemSummary: $bestFix" else problemSummary
    }

  /**
   * Human-readable relative time string (e.g., "Just now", "2 minutes ago", "3 hours ago").
   */
  val relativeTimeText: String
    get() = formatRelativeTime()

  /**
   * Formats this entry's [timestamp] into a relative time string compared against [referenceTimeMillis].
   */
  fun formatRelativeTime(referenceTimeMillis: Long = System.currentTimeMillis()): String =
    formatRelativeTimestamp(timestamp.time, referenceTimeMillis)

  /**
   * Returns a copy of this [MemoryEntry] with an updated [timestamp].
   */
  fun withUpdatedTimestamp(newTimestamp: Date = Date()): MemoryEntry = when (this) {
    is Reminder -> copy(timestamp = newTimestamp)
    is Preference -> copy(timestamp = newTimestamp)
    is FixHistory -> copy(timestamp = newTimestamp)
  }

  /**
   * Returns a copy of this [MemoryEntry] with updated [tags].
   */
  fun withTags(newTags: List<String>): MemoryEntry = when (this) {
    is Reminder -> copy(tags = newTags)
    is Preference -> copy(tags = newTags)
    is FixHistory -> copy(tags = newTags)
  }

  /**
   * Returns a copy of this [MemoryEntry] with an updated [category].
   */
  fun withCategory(newCategory: String): MemoryEntry {
    val clean = newCategory.trim().ifBlank { DEFAULT_CATEGORY }
    return when (this) {
      is Reminder -> copy(category = clean)
      is Preference -> copy(category = clean)
      is FixHistory -> copy(category = clean)
    }
  }

  /**
   * Evaluates whether this memory entry matches a natural-language or keyword [query]
   * across both shared metadata and subclass-specific fields.
   */
  fun matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val tokens = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
    if (tokens.isEmpty()) return true

    val searchableText = buildString {
      append(primaryTitle).append(' ')
      append(category).append(' ')
      append(tags.joinToString(" ")).append(' ')
      when (this@MemoryEntry) {
        is Reminder -> {
          append(triggerActivity).append(' ')
          append(relatedPeople.joinToString(" ")).append(' ')
          append(relatedLocations.joinToString(" "))
        }
        is Preference -> {
          append(description).append(' ')
          append(details)
        }
        is FixHistory -> {
          append(whatHappened).append(' ')
          append(bestFix).append(' ')
          append(steps.joinToString(" ")).append(' ')
          append(toolsNeeded.joinToString(" ")).append(' ')
          append(alternativeFix)
        }
      }
    }.lowercase()

    return tokens.any { token -> searchableText.contains(token) }
  }

  /**
   * Convert to [UnifiedMemoryItem] for unified Firestore & Room persistence.
   */
  fun toUnifiedMemoryItem(): UnifiedMemoryItem = when (this) {
    is Reminder -> UnifiedMemoryItem(
      id = id,
      userId = userId,
      type = UnifiedMemoryItem.TYPE_REMINDER,
      title = title,
      content = if (triggerActivity.isNotBlank()) "When $triggerActivity, remind to $title" else title,
      tags = tags,
      category = category.ifBlank { DEFAULT_CATEGORY },
      triggerActivity = triggerActivity,
      relatedPeople = relatedPeople,
      relatedLocations = relatedLocations,
      recurrence = recurrence,
      isCompleted = isCompleted,
      createdAt = timestamp,
      updatedAt = timestamp
    )

    is Preference -> UnifiedMemoryItem(
      id = id,
      userId = userId,
      type = UnifiedMemoryItem.TYPE_PREFERENCE,
      title = title,
      content = description,
      tags = tags,
      category = category.ifBlank { DEFAULT_CATEGORY },
      details = details,
      photoUrl = photoUri,
      createdAt = timestamp,
      updatedAt = timestamp
    )

    is FixHistory -> UnifiedMemoryItem(
      id = id,
      userId = userId,
      type = UnifiedMemoryItem.TYPE_FIX,
      title = problemSummary,
      content = bestFix,
      tags = if (tags.isNotEmpty()) tags else (steps + toolsNeeded).filter { it.length < 30 },
      category = category.ifBlank { DEFAULT_CATEGORY },
      problemSummary = problemSummary,
      whatHappened = whatHappened,
      bestFix = bestFix,
      steps = steps,
      toolsNeeded = toolsNeeded,
      alternativeFix = alternativeFix,
      isVerifiedWorking = isVerifiedWorking,
      createdAt = timestamp,
      updatedAt = timestamp
    )
  }

  /**
   * Convert entry to a Firestore-compatible [Map].
   */
  fun toMap(): Map<String, Any?> = toUnifiedMemoryItem().toMap()

  companion object {
    const val TYPE_REMINDER = "reminder"
    const val TYPE_PREFERENCE = "preference"
    const val TYPE_FIX_HISTORY = "fix"
    const val DEFAULT_CATEGORY = "Personal"

    val CATEGORIES = listOf(
      "Personal",
      "Work",
      "Shopping",
      "Home",
      "Food & Drinks",
      "Health",
      "Tech",
      "Travel"
    )

    /**
     * Map a [UnifiedMemoryItem] from the database to its strongly-typed [MemoryEntry] subclass.
     */
    fun fromUnifiedMemoryItem(item: UnifiedMemoryItem): MemoryEntry = when (item.type.lowercase()) {
      TYPE_REMINDER -> Reminder(
        id = item.id,
        userId = item.userId,
        timestamp = item.createdAt ?: item.updatedAt ?: Date(),
        type = TYPE_REMINDER,
        tags = item.tags,
        category = item.category.ifBlank { DEFAULT_CATEGORY },
        title = item.title,
        triggerActivity = item.triggerActivity,
        relatedPeople = item.relatedPeople,
        relatedLocations = item.relatedLocations,
        recurrence = item.recurrence,
        isCompleted = item.isCompleted
      )

      TYPE_PREFERENCE -> Preference(
        id = item.id,
        userId = item.userId,
        timestamp = item.createdAt ?: item.updatedAt ?: Date(),
        type = TYPE_PREFERENCE,
        tags = item.tags,
        title = item.title,
        category = item.category.ifBlank { DEFAULT_CATEGORY },
        description = item.content,
        details = item.details,
        photoUri = item.photoUrl
      )

      TYPE_FIX_HISTORY -> FixHistory(
        id = item.id,
        userId = item.userId,
        timestamp = item.createdAt ?: item.updatedAt ?: Date(),
        type = TYPE_FIX_HISTORY,
        tags = item.tags,
        category = item.category.ifBlank { DEFAULT_CATEGORY },
        problemSummary = item.problemSummary.ifBlank { item.title },
        whatHappened = item.whatHappened,
        bestFix = item.bestFix.ifBlank { item.content },
        steps = item.steps,
        toolsNeeded = item.toolsNeeded,
        alternativeFix = item.alternativeFix,
        isVerifiedWorking = item.isVerifiedWorking
      )

      else -> Reminder(
        id = item.id,
        userId = item.userId,
        timestamp = item.createdAt ?: item.updatedAt ?: Date(),
        type = item.type,
        tags = item.tags,
        category = item.category.ifBlank { DEFAULT_CATEGORY },
        title = item.title.ifBlank { item.content }
      )
    }

    /**
     * Reconstruct a [MemoryEntry] directly from a Firestore document or key-value [map].
     */
    @Suppress("UNCHECKED_CAST")
    fun fromMap(map: Map<String, Any?>, documentId: String? = null): MemoryEntry {
      val id = (map["id"] as? String)?.takeIf { it.isNotBlank() }
        ?: documentId
        ?: UUID.randomUUID().toString()
      val userId = (map["userId"] as? String).orEmpty()
      val type = ((map["type"] as? String) ?: TYPE_REMINDER).lowercase()
      val tags = (map["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
      val category = ((map["category"] as? String) ?: DEFAULT_CATEGORY).ifBlank { DEFAULT_CATEGORY }
      val timestamp = when (val rawTime = map["createdAt"] ?: map["updatedAt"] ?: map["timestamp"]) {
        is Date -> rawTime
        is Timestamp -> rawTime.toDate()
        is Long -> Date(rawTime)
        else -> Date()
      }

      return when (type) {
        TYPE_PREFERENCE -> Preference(
          id = id,
          userId = userId,
          timestamp = timestamp,
          type = TYPE_PREFERENCE,
          tags = tags,
          title = (map["title"] as? String).orEmpty(),
          category = category,
          description = ((map["description"] as? String) ?: (map["content"] as? String)).orEmpty(),
          details = (map["details"] as? String).orEmpty(),
          photoUri = (map["photoUrl"] as? String) ?: (map["photoUri"] as? String)
        )

        TYPE_FIX_HISTORY -> FixHistory(
          id = id,
          userId = userId,
          timestamp = timestamp,
          type = TYPE_FIX_HISTORY,
          tags = tags,
          category = category,
          problemSummary = ((map["problemSummary"] as? String) ?: (map["title"] as? String)).orEmpty(),
          whatHappened = (map["whatHappened"] as? String).orEmpty(),
          bestFix = ((map["bestFix"] as? String) ?: (map["content"] as? String)).orEmpty(),
          steps = (map["steps"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
          toolsNeeded = (map["toolsNeeded"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
          alternativeFix = (map["alternativeFix"] as? String).orEmpty(),
          isVerifiedWorking = (map["isVerifiedWorking"] as? Boolean) ?: false
        )

        else -> Reminder(
          id = id,
          userId = userId,
          timestamp = timestamp,
          type = TYPE_REMINDER,
          tags = tags,
          category = category,
          title = (map["title"] as? String).orEmpty(),
          triggerActivity = (map["triggerActivity"] as? String).orEmpty(),
          relatedPeople = (map["relatedPeople"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
          relatedLocations = (map["relatedLocations"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
          recurrence = ((map["recurrence"] as? String) ?: "every_time").ifBlank { "every_time" },
          isCompleted = (map["isCompleted"] as? Boolean) ?: false
        )
      }
    }

    fun fromReminder(reminder: com.example.data.model.Reminder): Reminder = Reminder(
      id = reminder.id,
      userId = reminder.userId,
      timestamp = reminder.createdAt ?: reminder.updatedAt ?: Date(),
      type = TYPE_REMINDER,
      tags = reminder.keywords,
      category = reminder.category.ifBlank { DEFAULT_CATEGORY },
      title = reminder.title,
      triggerActivity = reminder.triggerActivity,
      relatedPeople = reminder.relatedPeople,
      relatedLocations = reminder.relatedLocations,
      recurrence = reminder.recurrence,
      isCompleted = reminder.isCompleted
    )

    fun fromPreference(preference: com.example.data.model.Preference): Preference = Preference(
      id = preference.id,
      userId = preference.userId,
      timestamp = preference.createdAt ?: preference.updatedAt ?: Date(),
      type = TYPE_PREFERENCE,
      tags = preference.tags,
      title = preference.title,
      category = preference.category.ifBlank { DEFAULT_CATEGORY },
      description = preference.description,
      details = preference.details,
      photoUri = preference.photoUrl
    )

    fun fromFix(fix: com.example.data.model.Fix): FixHistory = FixHistory(
      id = fix.id,
      userId = fix.userId,
      timestamp = fix.createdAt ?: fix.updatedAt ?: Date(),
      type = TYPE_FIX_HISTORY,
      tags = (fix.steps + fix.toolsNeeded).filter { it.length < 30 },
      category = fix.category.ifBlank { DEFAULT_CATEGORY },
      problemSummary = fix.problemSummary,
      whatHappened = fix.whatHappened,
      bestFix = fix.bestFix,
      steps = fix.steps,
      toolsNeeded = fix.toolsNeeded,
      alternativeFix = fix.alternativeFix,
      isVerifiedWorking = fix.isVerifiedWorking
    )

    /**
     * Converts a raw epoch millisecond timestamp into a natural relative time string
     * (e.g., "Just now", "2 minutes ago", "1 hour ago", "3 days ago").
     */
    fun formatRelativeTimestamp(
      timestampMillis: Long,
      referenceTimeMillis: Long = System.currentTimeMillis()
    ): String {
      val diffMillis = referenceTimeMillis - timestampMillis
      if (diffMillis < 60_000L) {
        return "Just now"
      }

      val minutes = diffMillis / 60_000L
      if (minutes < 60L) {
        return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
      }

      val hours = minutes / 60L
      if (hours < 24L) {
        return if (hours == 1L) "1 hour ago" else "$hours hours ago"
      }

      val days = hours / 24L
      if (days < 7L) {
        return if (days == 1L) "1 day ago" else "$days days ago"
      }

      val weeks = days / 7L
      if (days < 30L) {
        return if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
      }

      val months = days / 30L
      if (days < 365L) {
        return if (months == 1L) "1 month ago" else "$months months ago"
      }

      val years = days / 365L
      return if (years == 1L) "1 year ago" else "$years years ago"
    }
  }
}

/**
 * Extension function to format any [Date] into a readable relative time string.
 */
fun Date.toRelativeTimeString(referenceTimeMillis: Long = System.currentTimeMillis()): String =
  MemoryEntry.formatRelativeTimestamp(this.time, referenceTimeMillis)
