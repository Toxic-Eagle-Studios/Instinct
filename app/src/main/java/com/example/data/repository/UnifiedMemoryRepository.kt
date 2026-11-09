package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.util.configuredFirestoreDatabaseId
import com.example.data.local.InstinctDatabase
import com.example.data.local.UnifiedMemoryEntity
import com.example.data.model.Fix
import com.example.data.model.MemoryEntry
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.data.model.UnifiedMemoryItem
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

private const val TAG = "UnifiedMemoryRepo"

/**
 * Unified repository managing the single shared brain collection:
 * /users/{userId}/memories/{memoryId}
 *
 * Supports storing and retrieving reminders (While You're At It),
 * preferences (Exactly), and fix histories (Undo IRL) in a single shared collection.
 */
class UnifiedMemoryRepository(
  private val context: Context,
  private val db: FirebaseFirestore,
  private val localDb: InstinctDatabase
) {
  constructor(context: Context) : this(
    context = context.applicationContext,
    db = FirebaseFirestore.getInstance(
      context.configuredFirestoreDatabaseId()
    ),
    localDb = InstinctDatabase.getDatabase(context.applicationContext)
  )

  private val auth = Firebase.auth
  private val memoryDao = localDb.unifiedMemoryDao()
  private val localFirstRepository = DefaultMemoryRepository(context, memoryDao, db, auth)

  private suspend fun mergeRemoteItems(userId: String, items: List<UnifiedMemoryItem>) {
    val valid = items.filter {
      it.userId == userId && it.id.isNotBlank() && it.createdAt != null && it.updatedAt != null
    }
    memoryDao.mergeRemoteMemories(userId, valid.map { UnifiedMemoryEntity.fromModel(it) })
  }

  fun getCurrentUserId(): String {
    return auth.currentUser?.uid ?: "guest_user"
  }

  fun isUserSignedIn(): Boolean = auth.currentUser != null

  /**
   * Observe all unified memories across all 3 types (reminders, preferences, fixes)
   * in real-time from the shared collection /users/{userId}/memories
   */
  fun observeAllMemories(userId: String): Flow<List<UnifiedMemoryItem>> = flow {
    if (auth.currentUser != null && auth.currentUser?.uid == userId) {
      val path = "users/$userId/memories"
      try {
        db.collection("users").document(userId).collection("memories")
          .orderBy("updatedAt", Query.Direction.DESCENDING)
          .snapshots()
          .map { snapshot ->
            val items = snapshot.documents.mapNotNull { document -> document.toObject(UnifiedMemoryItem::class.java)?.copy(id = document.id) }
            mergeRemoteItems(userId, items)
            memoryDao.getAllMemories(userId).map { it.toModel() }
          }
          .catch { error ->
            if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
            emit(memoryDao.getAllMemories(userId).map { it.toModel() })
          }
          .collect { emit(it) }
      } catch (e: Exception) {
        emit(memoryDao.getAllMemories(userId).map { it.toModel() })
      }
    } else {
      memoryDao.observeMemories(userId)
        .map { list -> list.map { it.toModel() } }
        .collect { emit(it) }
    }
  }

  /**
   * Observe unified memories filtered by type ("reminder", "preference", or "fix")
   */
  fun observeMemoriesByType(userId: String, type: String): Flow<List<UnifiedMemoryItem>> = flow {
    if (auth.currentUser != null && auth.currentUser?.uid == userId) {
      val path = "users/$userId/memories"
      try {
        db.collection("users").document(userId).collection("memories")
          .whereEqualTo("type", type)
          .snapshots()
          .map { snapshot ->
            val items = snapshot.documents.mapNotNull { document -> document.toObject(UnifiedMemoryItem::class.java)?.copy(id = document.id) }
            mergeRemoteItems(userId, items)
            memoryDao.getAllMemories(userId).filter { it.type == type }
              .map { it.toModel() }.sortedByDescending { it.updatedAt?.time ?: 0L }
          }
          .catch { error ->
            if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
            emit(memoryDao.getAllMemories(userId).filter { it.type == type }.map { it.toModel() })
          }
          .collect { emit(it) }
      } catch (e: Exception) {
        emit(memoryDao.getAllMemories(userId).filter { it.type == type }.map { it.toModel() })
      }
    } else {
      memoryDao.observeMemoriesByType(userId, type)
        .map { list -> list.map { it.toModel() } }
        .collect { emit(it) }
    }
  }

  /**
   * Search across all memory types in the single shared collection
   */
  fun searchMemories(userId: String, query: String): Flow<List<UnifiedMemoryItem>> {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isBlank()) return observeAllMemories(userId)

    return memoryDao.searchMemories(userId, cleanQuery)
      .map { list -> list.map { it.toModel() } }
  }

  /**
   * Fetch a single memory item by ID
   */
  suspend fun getMemoryById(userId: String, memoryId: String): UnifiedMemoryItem? = withContext(Dispatchers.IO) {
    memoryDao.getMemoryById(memoryId, userId)?.let { return@withContext it.toModel() }
    if (memoryDao.getPendingDeleteMemories(userId).any { it.id == memoryId }) return@withContext null
    if (auth.currentUser != null && auth.currentUser?.uid == userId) {
      try {
        val snap = db.collection("users").document(userId).collection("memories").document(memoryId).get().await()
        if (snap.exists()) {
          snap.toObject(UnifiedMemoryItem::class.java)?.copy(id = snap.id)?.let { mergeRemoteItems(userId, listOf(it)) }
          return@withContext memoryDao.getMemoryById(memoryId, userId)?.toModel()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to get memory from Firestore, checking local cache", e)
      }
    }
    memoryDao.getMemoryById(memoryId, userId)?.toModel()
  }

  /**
   * Save or update a UnifiedMemoryItem in the shared collection while preserving immutable createdAt.
   */
  suspend fun saveMemory(item: UnifiedMemoryItem): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      val memoryId = item.id.ifBlank { UUID.randomUUID().toString() }
      val existingLocal = memoryDao.getMemoryByIdAnyUser(memoryId)?.takeIf { it.userId == uid }
      val preservedCreatedAt = existingLocal?.let { Date(it.createdAt) } ?: item.createdAt ?: Date()
      val toSave = item.copy(
        id = memoryId,
        userId = uid,
        updatedAt = Date(),
        createdAt = preservedCreatedAt
      )

      // Save locally first marked dirty (`isDirty = true`) until Firestore write completes
      val entity = UnifiedMemoryEntity.fromModel(toSave, isDirty = true, pendingDelete = false)
      memoryDao.saveLocalMemories(listOf(entity))

      // Sync to Firestore if authenticated
      if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        writePreservingCreatedAt(db, docRef, toSave.toMap(), preservedCreatedAt.time) {
          memoryDao.getMemoryById(memoryId, uid) == entity
        }
        memoryDao.markMemorySyncedIfUnchanged(entity)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save memory item", e)
      handleFirestoreError(e, OperationType.WRITE, "users/${getCurrentUserId()}/memories/${item.id}")
      Result.failure(e)
    }
  }

  /**
   * Save a While You're At It reminder as a UnifiedMemoryItem in the shared collection
   */
  suspend fun saveReminder(reminder: Reminder): Result<Unit> {
    return saveMemory(UnifiedMemoryItem.fromReminder(reminder))
  }

  /**
   * Save an Exactly preference as a UnifiedMemoryItem in the shared collection
   */
  suspend fun savePreference(preference: Preference): Result<Unit> {
    return saveMemory(UnifiedMemoryItem.fromPreference(preference))
  }

  /**
   * Save an Undo IRL fix as a UnifiedMemoryItem in the shared collection
   */
  suspend fun saveFix(fix: Fix): Result<Unit> {
    return saveMemory(UnifiedMemoryItem.fromFix(fix))
  }

  /**
   * Save any strongly-typed MemoryEntry subclass (Reminder, Preference, FixHistory)
   */
  suspend fun saveMemoryEntry(entry: MemoryEntry): Result<Unit> {
    return saveMemory(entry.toUnifiedMemoryItem())
  }

  /**
   * Observe all memories as strongly typed MemoryEntry subclasses
   */
  fun observeMemoryEntries(userId: String): Flow<List<MemoryEntry>> {
    return observeAllMemories(userId).map { list -> list.map { MemoryEntry.fromUnifiedMemoryItem(it) } }
  }

  /**
   * Fetch single memory as a strongly typed MemoryEntry subclass
   */
  suspend fun getMemoryEntryById(userId: String, memoryId: String): MemoryEntry? {
    return getMemoryById(userId, memoryId)?.let { MemoryEntry.fromUnifiedMemoryItem(it) }
  }

  /**
   * Toggle completion of a reminder memory
   */
  suspend fun toggleReminderStatus(memoryId: String, isCompleted: Boolean): Result<Unit> =
    localFirstRepository.toggleReminderStatus(getCurrentUserId(), memoryId, isCompleted)

  /**
   * Mark an Undo IRL fix as verified working
   */
  suspend fun verifyFixWorking(memoryId: String, isVerified: Boolean): Result<Unit> =
    localFirstRepository.verifyFixWorking(getCurrentUserId(), memoryId, isVerified)

  /**
   * Delete a memory from the shared collection
   */
  suspend fun deleteMemory(memoryId: String): Result<Unit> =
    localFirstRepository.deleteMemoryEntry(getCurrentUserId(), memoryId)

  /**
   * Contextual retrieval: given an ongoing activity (e.g., "calling mom", "going to store"),
   * search across all reminders and related memories to find relevant triggers.
   */
  suspend fun findContextualMatches(userId: String, input: String): List<UnifiedMemoryItem> = withContext(Dispatchers.IO) {
    val clean = input.lowercase()
    val all = memoryDao.getAllMemories(userId).map { it.toModel() }
    all.filter { item ->
      if (item.isReminder && !item.isCompleted) {
        val trigger = item.triggerActivity.lowercase()
        val inTrigger = trigger.isNotBlank() && (clean.contains(trigger) || trigger.contains(clean))
        val inTags = item.tags.any { tag -> clean.contains(tag.lowercase()) }
        val inLocations = item.relatedLocations.any { loc -> clean.contains(loc.lowercase()) }
        val inPeople = item.relatedPeople.any { person -> clean.contains(person.lowercase()) }
        inTrigger || inTags || inLocations || inPeople
      } else false
    }
  }

  /**
   * Export all memories as structured JSON
   */
  suspend fun exportAllMemoriesJson(userId: String): String = withContext(Dispatchers.IO) {
    val memories = memoryDao.getAllMemories(userId).map { it.toModel() }
    val json = JSONObject().apply {
      put("appName", "INSTINCT")
      put("type", "Unified Shared Brain")
      put("exportDate", Date().toString())
      put("userId", userId)
      put("totalMemories", memories.size)
      put("memories", JSONArray(memories.map { JSONObject(it.toMap()) }))
    }
    json.toString(2)
  }

  /**
   * Clear all memories for a user
   */
  suspend fun clearAllMemories(userId: String): Result<Unit> =
    localFirstRepository.clearAllMemories(userId)
}
