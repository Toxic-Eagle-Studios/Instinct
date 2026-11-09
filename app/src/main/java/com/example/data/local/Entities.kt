package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.Fix
import com.example.data.model.MemoryEntry
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.data.model.UnifiedMemoryItem
import java.util.Date

/**
 * Room TypeConverters for serializing collections and dates in local SQLite storage.
 */
class MemoryTypeConverters {
  @TypeConverter
  fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

  @TypeConverter
  fun dateToTimestamp(date: Date?): Long? = date?.time

  @TypeConverter
  fun fromStringList(value: String?): List<String> =
    if (value.isNullOrBlank()) emptyList() else value.split("||").filter { it.isNotEmpty() }

  @TypeConverter
  fun stringListToString(list: List<String>?): String =
    list?.joinToString("||").orEmpty()
}

@Entity(
  tableName = "reminders",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "isCompleted"]),
    Index(value = ["userId", "category"])
  ]
)
data class ReminderEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val title: String,
  val triggerActivity: String,
  val relatedPeople: String,
  val relatedLocations: String,
  val keywords: String,
  val recurrence: String,
  val isCompleted: Boolean,
  val createdAt: Long,
  val updatedAt: Long,
  val category: String = "Personal"
) {
  fun toModel(): Reminder = Reminder(
    id = id,
    userId = userId,
    title = title,
    triggerActivity = triggerActivity,
    relatedPeople = if (relatedPeople.isBlank()) emptyList() else relatedPeople.split("||"),
    relatedLocations = if (relatedLocations.isBlank()) emptyList() else relatedLocations.split("||"),
    keywords = if (keywords.isBlank()) emptyList() else keywords.split("||"),
    recurrence = recurrence,
    isCompleted = isCompleted,
    category = category.ifBlank { "Personal" },
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
  )

  companion object {
    fun fromModel(model: Reminder): ReminderEntity = ReminderEntity(
      id = model.id,
      userId = model.userId,
      title = model.title,
      triggerActivity = model.triggerActivity,
      relatedPeople = model.relatedPeople.joinToString("||"),
      relatedLocations = model.relatedLocations.joinToString("||"),
      keywords = model.keywords.joinToString("||"),
      recurrence = model.recurrence,
      isCompleted = model.isCompleted,
      createdAt = model.createdAt?.time ?: System.currentTimeMillis(),
      updatedAt = model.updatedAt?.time ?: System.currentTimeMillis(),
      category = model.category.ifBlank { "Personal" }
    )
  }
}

@Entity(
  tableName = "preferences",
  indices = [Index(value = ["userId"]), Index(value = ["userId", "category"])]
)
data class PreferenceEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val title: String,
  val category: String,
  val description: String,
  val details: String,
  val tags: String,
  val photoUrl: String?,
  val createdAt: Long,
  val updatedAt: Long
) {
  fun toModel(): Preference = Preference(
    id = id,
    userId = userId,
    title = title,
    category = category.ifBlank { "Personal" },
    description = description,
    details = details,
    tags = if (tags.isBlank()) emptyList() else tags.split("||"),
    photoUrl = photoUrl,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
  )

  companion object {
    fun fromModel(model: Preference): PreferenceEntity = PreferenceEntity(
      id = model.id,
      userId = model.userId,
      title = model.title,
      category = model.category.ifBlank { "Personal" },
      description = model.description,
      details = model.details,
      tags = model.tags.joinToString("||"),
      photoUrl = model.photoUrl,
      createdAt = model.createdAt?.time ?: System.currentTimeMillis(),
      updatedAt = model.updatedAt?.time ?: System.currentTimeMillis()
    )
  }
}

@Entity(
  tableName = "fixes",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "isVerifiedWorking"]),
    Index(value = ["userId", "category"])
  ]
)
data class FixEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val problemSummary: String,
  val whatHappened: String,
  val bestFix: String,
  val steps: String,
  val toolsNeeded: String,
  val alternativeFix: String,
  val isVerifiedWorking: Boolean,
  val createdAt: Long,
  val updatedAt: Long,
  val category: String = "Personal"
) {
  fun toModel(): Fix = Fix(
    id = id,
    userId = userId,
    problemSummary = problemSummary,
    whatHappened = whatHappened,
    bestFix = bestFix,
    steps = if (steps.isBlank()) emptyList() else steps.split("||"),
    toolsNeeded = if (toolsNeeded.isBlank()) emptyList() else toolsNeeded.split("||"),
    alternativeFix = alternativeFix,
    isVerifiedWorking = isVerifiedWorking,
    category = category.ifBlank { "Personal" },
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
  )

  companion object {
    fun fromModel(model: Fix): FixEntity = FixEntity(
      id = model.id,
      userId = model.userId,
      problemSummary = model.problemSummary,
      whatHappened = model.whatHappened,
      bestFix = model.bestFix,
      steps = model.steps.joinToString("||"),
      toolsNeeded = model.toolsNeeded.joinToString("||"),
      alternativeFix = model.alternativeFix,
      isVerifiedWorking = model.isVerifiedWorking,
      createdAt = model.createdAt?.time ?: System.currentTimeMillis(),
      updatedAt = model.updatedAt?.time ?: System.currentTimeMillis(),
      category = model.category.ifBlank { "Personal" }
    )
  }
}

/**
 * Primary Room Entity for persisting [MemoryEntry] subclasses ([MemoryEntry.Reminder],
 * [MemoryEntry.Preference], and [MemoryEntry.FixHistory]) in a single unified SQLite table.
 */
@Entity(
  tableName = "unified_memories",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "type"]),
    Index(value = ["userId", "category"]),
    Index(value = ["userId", "updatedAt"])
  ]
)
data class UnifiedMemoryEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val type: String,
  val title: String,
  val content: String,
  val tags: String,
  val triggerActivity: String,
  val relatedPeople: String,
  val relatedLocations: String,
  val recurrence: String,
  val isCompleted: Boolean,
  val category: String,
  val details: String,
  val photoUrl: String?,
  val problemSummary: String,
  val whatHappened: String,
  val bestFix: String,
  val steps: String,
  val toolsNeeded: String,
  val alternativeFix: String,
  val isVerifiedWorking: Boolean,
  val createdAt: Long,
  val updatedAt: Long,
  val isDirty: Boolean = false,
  val pendingDelete: Boolean = false
) {
  fun toModel(): UnifiedMemoryItem = UnifiedMemoryItem(
    id = id,
    userId = userId,
    type = type,
    title = title,
    content = content,
    tags = if (tags.isBlank()) emptyList() else tags.split("||"),
    triggerActivity = triggerActivity,
    relatedPeople = if (relatedPeople.isBlank()) emptyList() else relatedPeople.split("||"),
    relatedLocations = if (relatedLocations.isBlank()) emptyList() else relatedLocations.split("||"),
    recurrence = recurrence,
    isCompleted = isCompleted,
    category = category.ifBlank { "Personal" },
    details = details,
    photoUrl = photoUrl,
    problemSummary = problemSummary,
    whatHappened = whatHappened,
    bestFix = bestFix,
    steps = if (steps.isBlank()) emptyList() else steps.split("||"),
    toolsNeeded = if (toolsNeeded.isBlank()) emptyList() else toolsNeeded.split("||"),
    alternativeFix = alternativeFix,
    isVerifiedWorking = isVerifiedWorking,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
  )

  fun toMemoryEntry(): MemoryEntry = MemoryEntry.fromUnifiedMemoryItem(toModel())

  companion object {
    fun fromModel(
      model: UnifiedMemoryItem,
      isDirty: Boolean = false,
      pendingDelete: Boolean = false
    ): UnifiedMemoryEntity = UnifiedMemoryEntity(
      id = model.id,
      userId = model.userId,
      type = model.type,
      title = model.title,
      content = model.content,
      tags = model.tags.joinToString("||"),
      triggerActivity = model.triggerActivity,
      relatedPeople = model.relatedPeople.joinToString("||"),
      relatedLocations = model.relatedLocations.joinToString("||"),
      recurrence = model.recurrence,
      isCompleted = model.isCompleted,
      category = model.category.ifBlank { "Personal" },
      details = model.details,
      photoUrl = model.photoUrl,
      problemSummary = model.problemSummary,
      whatHappened = model.whatHappened,
      bestFix = model.bestFix,
      steps = model.steps.joinToString("||"),
      toolsNeeded = model.toolsNeeded.joinToString("||"),
      alternativeFix = model.alternativeFix,
      isVerifiedWorking = model.isVerifiedWorking,
      createdAt = model.createdAt?.time ?: System.currentTimeMillis(),
      updatedAt = model.updatedAt?.time ?: System.currentTimeMillis(),
      isDirty = isDirty,
      pendingDelete = pendingDelete
    )

    fun fromMemoryEntry(
      entry: MemoryEntry,
      isDirty: Boolean = false,
      pendingDelete: Boolean = false
    ): UnifiedMemoryEntity =
      fromModel(entry.toUnifiedMemoryItem(), isDirty = isDirty, pendingDelete = pendingDelete)
  }
}

/**
 * Type alias exposing [UnifiedMemoryEntity] as [MemoryEntryEntity] for direct domain alignment.
 */
typealias MemoryEntryEntity = UnifiedMemoryEntity

