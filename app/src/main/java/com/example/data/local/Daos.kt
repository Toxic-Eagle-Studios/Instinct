package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
  @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY createdAt DESC")
  fun observeReminders(userId: String): Flow<List<ReminderEntity>>

  @Query("SELECT * FROM reminders WHERE userId = :userId AND isCompleted = 0 ORDER BY createdAt DESC")
  fun observeActiveReminders(userId: String): Flow<List<ReminderEntity>>

  @Query("SELECT * FROM reminders WHERE userId = :userId AND LOWER(category) = LOWER(:category) ORDER BY createdAt DESC")
  fun observeRemindersByCategory(userId: String, category: String): Flow<List<ReminderEntity>>

  @Query("SELECT * FROM reminders WHERE userId = :userId")
  suspend fun getAllReminders(userId: String): List<ReminderEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReminder(reminder: ReminderEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReminders(reminders: List<ReminderEntity>)

  @Update
  suspend fun updateReminder(reminder: ReminderEntity)

  @Query("DELETE FROM reminders WHERE id = :id AND userId = :userId")
  suspend fun deleteReminderById(id: String, userId: String)

  @Query("DELETE FROM reminders WHERE userId = :userId")
  suspend fun deleteAllByUserId(userId: String)
}

@Dao
interface PreferenceDao {
  @Query("SELECT * FROM preferences WHERE userId = :userId ORDER BY updatedAt DESC")
  fun observePreferences(userId: String): Flow<List<PreferenceEntity>>

  @Query("SELECT * FROM preferences WHERE userId = :userId AND category = :category ORDER BY updatedAt DESC")
  fun observePreferencesByCategory(userId: String, category: String): Flow<List<PreferenceEntity>>

  @Query("SELECT * FROM preferences WHERE userId = :userId")
  suspend fun getAllPreferences(userId: String): List<PreferenceEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPreference(preference: PreferenceEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPreferences(preferences: List<PreferenceEntity>)

  @Update
  suspend fun updatePreference(preference: PreferenceEntity)

  @Query("DELETE FROM preferences WHERE id = :id AND userId = :userId")
  suspend fun deletePreferenceById(id: String, userId: String)

  @Query("DELETE FROM preferences WHERE userId = :userId")
  suspend fun deleteAllByUserId(userId: String)
}

@Dao
interface FixDao {
  @Query("SELECT * FROM fixes WHERE userId = :userId ORDER BY createdAt DESC")
  fun observeFixes(userId: String): Flow<List<FixEntity>>

  @Query("SELECT * FROM fixes WHERE userId = :userId AND isVerifiedWorking = 1 ORDER BY createdAt DESC")
  fun observeVerifiedFixes(userId: String): Flow<List<FixEntity>>

  @Query("SELECT * FROM fixes WHERE userId = :userId AND LOWER(category) = LOWER(:category) ORDER BY createdAt DESC")
  fun observeFixesByCategory(userId: String, category: String): Flow<List<FixEntity>>

  @Query("SELECT * FROM fixes WHERE userId = :userId")
  suspend fun getAllFixes(userId: String): List<FixEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFix(fix: FixEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFixes(fixes: List<FixEntity>)

  @Update
  suspend fun updateFix(fix: FixEntity)

  @Query("DELETE FROM fixes WHERE id = :id AND userId = :userId")
  suspend fun deleteFixById(id: String, userId: String)

  @Query("DELETE FROM fixes WHERE userId = :userId")
  suspend fun deleteAllByUserId(userId: String)
}

/**
 * Room DAO for persisting and querying polymorphic [com.example.data.model.MemoryEntry] records
 * stored in the `unified_memories` SQLite table.
 */
@Dao
interface UnifiedMemoryDao {
  @Query("SELECT * FROM unified_memories WHERE userId = :userId AND pendingDelete = 0 ORDER BY updatedAt DESC")
  fun observeMemories(userId: String): Flow<List<UnifiedMemoryEntity>>

  @Query("SELECT * FROM unified_memories WHERE userId = :userId AND pendingDelete = 0 AND type = :type ORDER BY updatedAt DESC")
  fun observeMemoriesByType(userId: String, type: String): Flow<List<UnifiedMemoryEntity>>

  @Query("SELECT * FROM unified_memories WHERE userId = :userId AND pendingDelete = 0 AND LOWER(category) = LOWER(:category) ORDER BY updatedAt DESC")
  fun observeMemoriesByCategory(userId: String, category: String): Flow<List<UnifiedMemoryEntity>>

  @Query("SELECT DISTINCT category FROM unified_memories WHERE userId = :userId AND pendingDelete = 0 AND category != '' ORDER BY category ASC")
  fun observeCategories(userId: String): Flow<List<String>>

  @Query(
    """
    SELECT * FROM unified_memories
    WHERE userId = :userId
      AND pendingDelete = 0
      AND (
        title LIKE '%' || :query || '%'
        OR content LIKE '%' || :query || '%'
        OR tags LIKE '%' || :query || '%'
        OR triggerActivity LIKE '%' || :query || '%'
        OR relatedLocations LIKE '%' || :query || '%'
        OR category LIKE '%' || :query || '%'
        OR details LIKE '%' || :query || '%'
        OR problemSummary LIKE '%' || :query || '%'
        OR bestFix LIKE '%' || :query || '%'
      )
    ORDER BY updatedAt DESC
    """
  )
  fun searchMemories(userId: String, query: String): Flow<List<UnifiedMemoryEntity>>

  @Query("SELECT * FROM unified_memories WHERE userId = :userId AND pendingDelete = 0")
  suspend fun getAllMemories(userId: String): List<UnifiedMemoryEntity>

  @Query("SELECT * FROM unified_memories WHERE id = :id AND userId = :userId AND pendingDelete = 0 LIMIT 1")
  suspend fun getMemoryById(id: String, userId: String): UnifiedMemoryEntity?

  @Query("SELECT * FROM unified_memories WHERE id = :id LIMIT 1")
  suspend fun getMemoryByIdAnyUser(id: String): UnifiedMemoryEntity?

  /** Fail without replacing another account's row while the legacy global-id schema is retained. */
  @Transaction
  suspend fun saveLocalMemories(memories: List<UnifiedMemoryEntity>) {
    require(memories.groupBy { it.id }.values.all { rows -> rows.map { it.userId }.distinct().size == 1 }) {
      "A memory ID cannot belong to multiple local accounts"
    }
    memories.forEach { memory ->
      val existing = getMemoryByIdAnyUser(memory.id)
      require(existing == null || existing.userId == memory.userId) { "Memory ID belongs to another local account" }
    }
    insertMemories(memories.map { memory ->
      val existing = getMemoryByIdAnyUser(memory.id)
      memory.copy(createdAt = existing?.createdAt ?: memory.createdAt)
    })
  }

  @Query("SELECT * FROM unified_memories WHERE userId = :userId AND isDirty = 1 AND pendingDelete = 0 ORDER BY updatedAt ASC")
  suspend fun getDirtyMemories(userId: String): List<UnifiedMemoryEntity>

  @Query("SELECT * FROM unified_memories WHERE userId = :userId AND pendingDelete = 1 ORDER BY updatedAt ASC")
  suspend fun getPendingDeleteMemories(userId: String): List<UnifiedMemoryEntity>

  @Query("UPDATE unified_memories SET isDirty = 0 WHERE id = :id AND userId = :userId")
  suspend fun markMemorySynced(id: String, userId: String)

  /** A completed upload may only acknowledge the exact local revision that was sent. */
  @Transaction
  suspend fun markMemorySyncedIfUnchanged(sent: UnifiedMemoryEntity) {
    if (getMemoryById(sent.id, sent.userId) == sent) {
      markMemorySynced(sent.id, sent.userId)
    }
  }

  /** Keep local-only rows: a cache/query snapshot is never proof of a remote deletion. */
  @Transaction
  suspend fun mergeRemoteMemories(userId: String, incoming: List<UnifiedMemoryEntity>) {
    val tombstones = getPendingDeleteMemories(userId).map { it.id }.toSet()
    incoming.forEach { remote ->
      if (remote.userId != userId || remote.id.isBlank() || remote.id in tombstones) return@forEach
      val local = getMemoryByIdAnyUser(remote.id)
      if (local != null && local.userId != userId) return@forEach
      if (local?.isDirty == true) return@forEach
      if (local == null || remote.updatedAt >= local.updatedAt) {
        insertMemory(remote.copy(
          createdAt = local?.createdAt ?: remote.createdAt,
          isDirty = false,
          pendingDelete = false
        ))
      }
    }
  }

  @Transaction
  suspend fun deleteConfirmedTombstone(sent: UnifiedMemoryEntity) {
    val current = getPendingDeleteMemories(sent.userId).firstOrNull { it.id == sent.id }
    if (current == sent) deleteMemoryById(sent.id, sent.userId)
  }

  @Transaction
  suspend fun stageMemoryDeletion(prototype: UnifiedMemoryEntity): UnifiedMemoryEntity {
    val current = getMemoryByIdAnyUser(prototype.id)
    require(current == null || current.userId == prototype.userId) { "Memory ID belongs to another local account" }
    val tombstone = (current ?: prototype).copy(
      pendingDelete = true, isDirty = true, updatedAt = prototype.updatedAt
    )
    insertMemory(tombstone)
    return tombstone
  }

  @Transaction
  suspend fun markAllMemoriesPendingDelete(userId: String, updatedAt: Long): List<UnifiedMemoryEntity> {
    getAllMemories(userId).forEach { markMemoryPendingDelete(it.id, userId, updatedAt) }
    return getPendingDeleteMemories(userId)
  }

  @Query("UPDATE unified_memories SET pendingDelete = 1, isDirty = 1, updatedAt = :updatedAt WHERE id = :id AND userId = :userId")
  suspend fun markMemoryPendingDelete(id: String, userId: String, updatedAt: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMemory(memory: UnifiedMemoryEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMemories(memories: List<UnifiedMemoryEntity>)

  @Update
  suspend fun updateMemory(memory: UnifiedMemoryEntity)

  @Query("UPDATE unified_memories SET category = :category, updatedAt = :updatedAt, isDirty = 1 WHERE id = :id AND userId = :userId")
  suspend fun updateMemoryCategory(id: String, userId: String, category: String, updatedAt: Long)

  @Query("DELETE FROM unified_memories WHERE id = :id AND userId = :userId")
  suspend fun deleteMemoryById(id: String, userId: String)

  @Query("DELETE FROM unified_memories WHERE userId = :userId")
  suspend fun deleteAllByUserId(userId: String)

  @Query("SELECT COUNT(*) FROM unified_memories WHERE userId = :userId AND pendingDelete = 0")
  suspend fun getMemoryCount(userId: String): Int

  @Query("DELETE FROM unified_memories WHERE userId = :userId AND isDirty = 0 AND pendingDelete = 0 AND id NOT IN (:validIds)")
  suspend fun deleteStaleMemories(userId: String, validIds: List<String>)
}

/**
 * Type alias exposing [UnifiedMemoryDao] as [MemoryEntryDao] for direct domain alignment.
 */
typealias MemoryEntryDao = UnifiedMemoryDao

