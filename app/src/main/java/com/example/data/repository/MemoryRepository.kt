package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.InstinctDatabase
import com.example.data.local.UnifiedMemoryDao
import com.example.data.local.UnifiedMemoryEntity
import com.example.data.model.MemoryEntry
import com.example.data.model.UnifiedMemoryItem
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

private const val TAG = "MemoryRepository"

/**
 * Observable synchronization state between local Room and remote Firestore.
 */
sealed class SyncStatus {
  object Idle : SyncStatus()
  object Syncing : SyncStatus()
  data class Synced(val timestamp: Long = System.currentTimeMillis(), val itemCount: Int) : SyncStatus()
  object Offline : SyncStatus()
  data class Error(val message: String, val cause: Throwable? = null) : SyncStatus()
}

/**
 * Interface defining the Single Source of Truth repository for [MemoryEntry] instances.
 * Local Room database acts as the single primary source of truth for UI observation,
 * while Cloud Firestore provides remote cloud persistence and real-time multi-device sync.
 */
interface MemoryRepository {
  val syncStatus: StateFlow<SyncStatus>

  fun getCurrentUserId(): String
  fun isUserSignedIn(): Boolean

  // Observables (backed by Room SQLite single source of truth)
  fun observeMemories(userId: String): Flow<List<MemoryEntry>>
  fun observeMemoriesByType(userId: String, type: String): Flow<List<MemoryEntry>>
  fun observeMemoriesByCategory(userId: String, category: String): Flow<List<MemoryEntry>>
  fun observeReminders(userId: String): Flow<List<MemoryEntry.Reminder>>
  fun observePreferences(userId: String): Flow<List<MemoryEntry.Preference>>
  fun observeFixHistories(userId: String): Flow<List<MemoryEntry.FixHistory>>
  fun searchMemories(userId: String, query: String): Flow<List<MemoryEntry>>

  // Single Item Operations
  suspend fun getMemoryById(userId: String, memoryId: String): MemoryEntry?

  // Mutations (Optimistic local-first write with outbox & tombstone tracking)
  suspend fun saveMemoryEntry(entry: MemoryEntry): Result<Unit>
  suspend fun saveMemoryEntries(entries: List<MemoryEntry>): Result<Unit>
  suspend fun deleteMemoryEntry(userId: String, memoryId: String): Result<Unit>
  suspend fun updateMemoryCategory(userId: String, memoryId: String, category: String): Result<Unit>
  suspend fun toggleReminderStatus(userId: String, memoryId: String, isCompleted: Boolean): Result<Unit>
  suspend fun verifyFixWorking(userId: String, memoryId: String, isVerified: Boolean): Result<Unit>
  suspend fun clearAllMemories(userId: String): Result<Unit>

  // Cloud Synchronization
  suspend fun syncRemoteChanges(userId: String): Result<Int>
  fun startRealtimeSync(userId: String)
  fun stopRealtimeSync()
}

/**
 * Production implementation of [MemoryRepository].
 *
 * Architecture Highlights:
 * 1. Single Source of Truth: UI reads exclusively from Room DAO (`UnifiedMemoryDao`).
 * 2. Zero-Loss Outbox (`isDirty = true`) & Tombstones (`pendingDelete = true`):
 *    - Local writes mark `isDirty = true` until confirmed written to Firestore.
 *    - Local deletes mark `pendingDelete = true, isDirty = true` until remote deletion succeeds.
 *    - `syncRemoteChanges` and realtime snapshot reconciliation NEVER delete or overwrite dirty local records.
 * 3. Immutable `createdAt` Preservation:
 *    - Existing documents preserve their original `createdAt` timestamp on every update so Firestore rules
 *      (`incoming().createdAt == existing().createdAt`) always succeed.
 */
class DefaultMemoryRepository(
  private val context: Context? = null,
  private val localDao: UnifiedMemoryDao,
  private val firestore: FirebaseFirestore? = null,
  private val auth: FirebaseAuth? = null,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) : MemoryRepository {

  companion object {
    private fun getFirestoreSafe(context: Context): FirebaseFirestore? {
      return try {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
      } catch (e: Exception) {
        null
      }
    }

    private fun getAuthSafe(): FirebaseAuth? {
      return try {
        Firebase.auth
      } catch (e: Exception) {
        null
      }
    }
  }

  /**
   * Primary Android application constructor
   */
  constructor(context: Context) : this(
    context = context.applicationContext,
    localDao = InstinctDatabase.getDatabase(context.applicationContext).unifiedMemoryDao(),
    firestore = getFirestoreSafe(context),
    auth = getAuthSafe()
  )

  private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
  override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

  private var activeListenerRegistration: ListenerRegistration? = null
  private var activeSyncUserId: String? = null

  override fun getCurrentUserId(): String {
    return auth?.currentUser?.uid ?: "guest_user"
  }

  override fun isUserSignedIn(): Boolean {
    return auth?.currentUser != null
  }

  // --------------------------------------------------------------------------
  // OBSERVABLES (Room SSOT)
  // --------------------------------------------------------------------------

  override fun observeMemories(userId: String): Flow<List<MemoryEntry>> {
    startRealtimeSync(userId)

    return localDao.observeMemories(userId)
      .map { list -> list.map { it.toMemoryEntry() } }
      .distinctUntilChanged()
  }

  override fun observeMemoriesByType(userId: String, type: String): Flow<List<MemoryEntry>> {
    startRealtimeSync(userId)

    return localDao.observeMemoriesByType(userId, type)
      .map { list -> list.map { it.toMemoryEntry() } }
      .distinctUntilChanged()
  }

  override fun observeMemoriesByCategory(userId: String, category: String): Flow<List<MemoryEntry>> {
    startRealtimeSync(userId)
    val cleanCategory = category.trim()
    if (cleanCategory.isBlank() || cleanCategory.equals("All", ignoreCase = true)) {
      return observeMemories(userId)
    }

    return localDao.observeMemoriesByCategory(userId, cleanCategory)
      .map { list -> list.map { it.toMemoryEntry() } }
      .distinctUntilChanged()
  }

  override fun observeReminders(userId: String): Flow<List<MemoryEntry.Reminder>> {
    return observeMemoriesByType(userId, MemoryEntry.TYPE_REMINDER)
      .map { list -> list.filterIsInstance<MemoryEntry.Reminder>() }
  }

  override fun observePreferences(userId: String): Flow<List<MemoryEntry.Preference>> {
    return observeMemoriesByType(userId, MemoryEntry.TYPE_PREFERENCE)
      .map { list -> list.filterIsInstance<MemoryEntry.Preference>() }
  }

  override fun observeFixHistories(userId: String): Flow<List<MemoryEntry.FixHistory>> {
    return observeMemoriesByType(userId, MemoryEntry.TYPE_FIX_HISTORY)
      .map { list -> list.filterIsInstance<MemoryEntry.FixHistory>() }
  }

  override fun searchMemories(userId: String, query: String): Flow<List<MemoryEntry>> {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isBlank()) return observeMemories(userId)

    return localDao.searchMemories(userId, cleanQuery)
      .map { list -> list.map { it.toMemoryEntry() } }
  }

  override suspend fun getMemoryById(userId: String, memoryId: String): MemoryEntry? = withContext(ioDispatcher) {
    val local = localDao.getMemoryById(memoryId, userId)
    if (local != null) return@withContext local.toMemoryEntry()

    val db = firestore
    if (db != null && isUserSignedIn() && getCurrentUserId() == userId) {
      try {
        val snap = db.collection("users").document(userId).collection("memories").document(memoryId).get().await()
        if (snap.exists()) {
          val item = snap.toObject(UnifiedMemoryItem::class.java)
          if (item != null) {
            val entry = MemoryEntry.fromUnifiedMemoryItem(item)
            localDao.insertMemory(UnifiedMemoryEntity.fromMemoryEntry(entry, isDirty = false, pendingDelete = false))
            return@withContext entry
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error fetching memory item from Firestore: $memoryId", e)
      }
    }
    null
  }

  // --------------------------------------------------------------------------
  // MUTATIONS (Optimistic Local Write + Outbox & Tombstone Remote Sync)
  // --------------------------------------------------------------------------

  override suspend fun saveMemoryEntry(entry: MemoryEntry): Result<Unit> = withContext(ioDispatcher) {
    try {
      val uid = if (entry.userId.isNotBlank()) entry.userId else getCurrentUserId()
      val memoryId = entry.id.ifBlank { UUID.randomUUID().toString() }

      val normalized = when (entry) {
        is MemoryEntry.Reminder -> entry.copy(id = memoryId, userId = uid)
        is MemoryEntry.Preference -> entry.copy(id = memoryId, userId = uid)
        is MemoryEntry.FixHistory -> entry.copy(id = memoryId, userId = uid)
      }

      // Preserve original createdAt if this record already exists locally
      val existingLocal = localDao.getMemoryById(memoryId, uid)
      val now = System.currentTimeMillis()
      val preservedCreatedAt = existingLocal?.createdAt ?: minOf(normalized.timestamp.time, now)

      // Step 1: Optimistic Local Write marked dirty (`isDirty = true, pendingDelete = false`)
      val entity = UnifiedMemoryEntity.fromMemoryEntry(
        entry = normalized,
        isDirty = true,
        pendingDelete = false
      ).copy(
        createdAt = preservedCreatedAt,
        updatedAt = maxOf(normalized.timestamp.time, now)
      )
      localDao.insertMemory(entity)

      // Step 2: Asynchronous Remote Sync to Firestore
      val db = firestore
      if (db != null && isUserSignedIn() && getCurrentUserId() == uid) {
        repositoryScope.launch {
          try {
            pushEntityToFirestorePreservingCreatedAt(db, uid, entity)
            localDao.markMemorySynced(memoryId, uid)
            _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis(), localDao.getMemoryCount(uid))
          } catch (e: Exception) {
            Log.e(TAG, "Remote Firestore sync failed for memory $memoryId; remains queued in Room outbox", e)
            _syncStatus.value = SyncStatus.Error("Saved locally (queued for cloud sync)", e)
          }
        }
      } else {
        _syncStatus.value = SyncStatus.Offline
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save memory entry", e)
      Result.failure(e)
    }
  }

  override suspend fun saveMemoryEntries(entries: List<MemoryEntry>): Result<Unit> = withContext(ioDispatcher) {
    try {
      if (entries.isEmpty()) return@withContext Result.success(Unit)

      val uid = getCurrentUserId()
      val now = System.currentTimeMillis()
      val entities = entries.map { entry ->
        val effectiveUid = if (entry.userId.isNotBlank()) entry.userId else uid
        val effectiveId = entry.id.ifBlank { UUID.randomUUID().toString() }
        val existingLocal = localDao.getMemoryById(effectiveId, effectiveUid)
        val preservedCreatedAt = existingLocal?.createdAt ?: minOf(entry.timestamp.time, now)

        val norm = when (entry) {
          is MemoryEntry.Reminder -> entry.copy(id = effectiveId, userId = effectiveUid)
          is MemoryEntry.Preference -> entry.copy(id = effectiveId, userId = effectiveUid)
          is MemoryEntry.FixHistory -> entry.copy(id = effectiveId, userId = effectiveUid)
        }
        UnifiedMemoryEntity.fromMemoryEntry(norm, isDirty = true, pendingDelete = false).copy(
          createdAt = preservedCreatedAt,
          updatedAt = maxOf(norm.timestamp.time, now)
        )
      }

      // Bulk local insert marked dirty until remote commit succeeds
      localDao.insertMemories(entities)

      // Remote bulk sync
      val db = firestore
      if (db != null && isUserSignedIn() && getCurrentUserId() == uid) {
        repositoryScope.launch {
          try {
            entities.forEach { entity ->
              pushEntityToFirestorePreservingCreatedAt(db, entity.userId, entity)
              localDao.markMemorySynced(entity.id, entity.userId)
            }
            _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis(), localDao.getMemoryCount(uid))
          } catch (e: Exception) {
            Log.e(TAG, "Remote batch Firestore sync failed; dirty items remain in Room outbox", e)
            _syncStatus.value = SyncStatus.Error("Saved locally (queued for cloud sync)", e)
          }
        }
      } else {
        _syncStatus.value = SyncStatus.Offline
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun deleteMemoryEntry(userId: String, memoryId: String): Result<Unit> = withContext(ioDispatcher) {
    try {
      val now = System.currentTimeMillis()
      // 1. Mark tombstone locally in Room so UI immediately hides the item (`pendingDelete = 1`),
      //    while preserving the tombstone for remote deletion replay if offline.
      localDao.markMemoryPendingDelete(memoryId, userId, now)

      // 2. Delete remotely from Firestore if online & authenticated
      val db = firestore
      if (db != null && isUserSignedIn() && getCurrentUserId() == userId) {
        repositoryScope.launch {
          try {
            db.collection("users").document(userId).collection("memories").document(memoryId)
              .delete()
              .await()
            // Remote delete confirmed -> physically remove the tombstone row
            localDao.deleteMemoryById(memoryId, userId)
            _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis(), localDao.getMemoryCount(userId))
          } catch (e: Exception) {
            Log.e(TAG, "Failed to delete remote memory document $memoryId; tombstone retained for retry", e)
            _syncStatus.value = SyncStatus.Error("Deleted locally (pending cloud sync)", e)
          }
        }
      } else if (userId == "guest_user") {
        // Pure guest local item needs no cloud tombstone
        localDao.deleteMemoryById(memoryId, userId)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete memory entry", e)
      Result.failure(e)
    }
  }

  override suspend fun updateMemoryCategory(userId: String, memoryId: String, category: String): Result<Unit> =
    withContext(ioDispatcher) {
      try {
        val cleanCategory = category.trim().ifBlank { MemoryEntry.DEFAULT_CATEGORY }
        val now = System.currentTimeMillis()
        // Marks isDirty = 1 automatically in DAO
        localDao.updateMemoryCategory(memoryId, userId, cleanCategory, now)

        val db = firestore
        if (db != null && isUserSignedIn() && getCurrentUserId() == userId) {
          repositoryScope.launch {
            try {
              val entity = localDao.getMemoryById(memoryId, userId)
              if (entity != null) {
                pushEntityToFirestorePreservingCreatedAt(db, userId, entity)
                localDao.markMemorySynced(memoryId, userId)
              }
            } catch (e: Exception) {
              Log.w(TAG, "Failed to sync category update to Firestore; kept dirty in Room", e)
            }
          }
        }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun toggleReminderStatus(userId: String, memoryId: String, isCompleted: Boolean): Result<Unit> =
    withContext(ioDispatcher) {
      try {
        val existing = localDao.getMemoryById(memoryId, userId)
        if (existing != null) {
          val updated = existing.copy(
            isCompleted = isCompleted,
            updatedAt = System.currentTimeMillis(),
            isDirty = true
          )
          localDao.updateMemory(updated)

          val db = firestore
          if (db != null && isUserSignedIn() && getCurrentUserId() == userId) {
            repositoryScope.launch {
              try {
                pushEntityToFirestorePreservingCreatedAt(db, userId, updated)
                localDao.markMemorySynced(memoryId, userId)
              } catch (e: Exception) {
                Log.w(TAG, "Failed to sync reminder toggle to Firestore; kept dirty in Room", e)
              }
            }
          }
        }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun verifyFixWorking(userId: String, memoryId: String, isVerified: Boolean): Result<Unit> =
    withContext(ioDispatcher) {
      try {
        val existing = localDao.getMemoryById(memoryId, userId)
        if (existing != null) {
          val updated = existing.copy(
            isVerifiedWorking = isVerified,
            updatedAt = System.currentTimeMillis(),
            isDirty = true
          )
          localDao.updateMemory(updated)

          val db = firestore
          if (db != null && isUserSignedIn() && getCurrentUserId() == userId) {
            repositoryScope.launch {
              try {
                pushEntityToFirestorePreservingCreatedAt(db, userId, updated)
                localDao.markMemorySynced(memoryId, userId)
              } catch (e: Exception) {
                Log.w(TAG, "Failed to sync fix verification to Firestore; kept dirty in Room", e)
              }
            }
          }
        }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun clearAllMemories(userId: String): Result<Unit> = withContext(ioDispatcher) {
    try {
      localDao.deleteAllByUserId(userId)

      val db = firestore
      if (db != null && isUserSignedIn() && getCurrentUserId() == userId) {
        try {
          val snapshot = db.collection("users").document(userId).collection("memories").get().await()
          val batch = db.batch()
          snapshot.documents.forEach { batch.delete(it.reference) }
          batch.commit().await()
        } catch (e: Exception) {
          Log.w(TAG, "Failed to clear remote Firestore memories", e)
        }
      }
      _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis(), 0)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  // --------------------------------------------------------------------------
  // ZERO-LOSS SYNCHRONIZATION ENGINE
  // --------------------------------------------------------------------------

  /**
   * Writes [entity] to `/users/{userId}/memories/{entity.id}` while strictly preserving `createdAt`
   * if the document already exists in Firestore (satisfying `incoming().createdAt == existing().createdAt`).
   */
  private suspend fun pushEntityToFirestorePreservingCreatedAt(
    db: FirebaseFirestore,
    userId: String,
    entity: UnifiedMemoryEntity
  ) {
    val docRef = db.collection("users").document(userId).collection("memories").document(entity.id)
    val existingSnap = try {
      docRef.get().await()
    } catch (_: Exception) {
      null
    }

    val safeCreatedAtMillis = minOf(entity.createdAt, System.currentTimeMillis())
    val createdAtValue: Any = if (existingSnap != null && existingSnap.exists()) {
      existingSnap.getTimestamp("createdAt") ?: Timestamp(Date(safeCreatedAtMillis))
    } else {
      Timestamp(Date(safeCreatedAtMillis))
    }

    val item = entity.toModel()
    val payload = item.toMap().toMutableMap().apply {
      put("createdAt", createdAtValue)
      put("updatedAt", FieldValue.serverTimestamp())
    }
    docRef.set(payload).await()
  }

  /**
   * Reconciles a list of remote [UnifiedMemoryItem] records into local Room storage
   * WITHOUT ever deleting or overwriting local `isDirty = true` or `pendingDelete = true` records.
   * Exposed as `internal` so unit tests can verify deterministic offline/online reconciliation directly.
   */
  internal suspend fun reconcileRemoteSnapshot(
    userId: String,
    remoteItems: List<UnifiedMemoryItem>
  ) = withContext(ioDispatcher) {
    val dirtyIds = localDao.getDirtyMemories(userId).map { it.id }.toSet()
    val pendingDeleteIds = localDao.getPendingDeleteMemories(userId).map { it.id }.toSet()

    val cleanEntitiesToUpsert = mutableListOf<UnifiedMemoryEntity>()
    for (remoteItem in remoteItems) {
      if (remoteItem.id.isBlank()) continue
      // Never overwrite a local dirty edit or resurrect a locally tombstoned item
      if (remoteItem.id in dirtyIds || remoteItem.id in pendingDeleteIds) continue

      val existingLocal = localDao.getMemoryById(remoteItem.id, userId)
      val remoteEntity = UnifiedMemoryEntity.fromModel(
        model = remoteItem,
        isDirty = false,
        pendingDelete = false
      )
      if (existingLocal == null || (!existingLocal.isDirty && remoteEntity.updatedAt >= existingLocal.updatedAt)) {
        cleanEntitiesToUpsert.add(
          if (existingLocal != null) {
            remoteEntity.copy(createdAt = existingLocal.createdAt)
          } else {
            remoteEntity
          }
        )
      }
    }

    if (cleanEntitiesToUpsert.isNotEmpty()) {
      localDao.insertMemories(cleanEntitiesToUpsert)
    }

    // Reconcile remote deletions ONLY for clean (`isDirty = 0 AND pendingDelete = 0`) rows
    val remoteIds = remoteItems.map { it.id }.filter { it.isNotBlank() }
    if (remoteIds.isNotEmpty()) {
      localDao.deleteStaleMemories(userId, remoteIds)
    } else {
      // If remote collection is completely empty, only remove clean non-dirty rows
      localDao.deleteStaleMemories(userId, listOf("__no_remote_ids_placeholder__"))
    }
  }

  override suspend fun syncRemoteChanges(userId: String): Result<Int> = withContext(ioDispatcher) {
    val db = firestore
    if (db == null || !isUserSignedIn() || getCurrentUserId() != userId) {
      _syncStatus.value = SyncStatus.Offline
      return@withContext Result.success(0)
    }

    try {
      _syncStatus.value = SyncStatus.Syncing

      // Phase 1: Flush pending tombstone deletions to Firestore
      val pendingDeletes = localDao.getPendingDeleteMemories(userId)
      for (tombstone in pendingDeletes) {
        try {
          db.collection("users").document(userId).collection("memories").document(tombstone.id)
            .delete()
            .await()
          localDao.deleteMemoryById(tombstone.id, userId)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to flush tombstone delete ${tombstone.id}; will retry next sync", e)
        }
      }

      // Phase 2: Flush dirty local outbox records to Firestore before pulling remote state
      val dirtyMemories = localDao.getDirtyMemories(userId)
      for (dirty in dirtyMemories) {
        try {
          pushEntityToFirestorePreservingCreatedAt(db, userId, dirty)
          localDao.markMemorySynced(dirty.id, userId)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to flush dirty memory ${dirty.id}; keeping local record protected", e)
        }
      }

      // Phase 3: Pull remote snapshot and reconcile safely without losing any remaining dirty items
      val snapshot = db.collection("users").document(userId).collection("memories")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .get()
        .await()

      val remoteItems = snapshot.toObjects(UnifiedMemoryItem::class.java)
      reconcileRemoteSnapshot(userId, remoteItems)

      val totalCount = localDao.getMemoryCount(userId)
      val remainingDirty = localDao.getDirtyMemories(userId).size + localDao.getPendingDeleteMemories(userId).size
      if (remainingDirty == 0) {
        _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis(), totalCount)
      } else {
        _syncStatus.value = SyncStatus.Error("$remainingDirty local change(s) queued for retry")
      }
      Result.success(remoteItems.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Explicit syncRemoteChanges failed for $userId", e)
      handleFirestoreError(e, OperationType.LIST, "users/$userId/memories")
      _syncStatus.value = SyncStatus.Error("Failed to sync with cloud: ${e.message}", e)
      Result.failure(e)
    }
  }

  override fun startRealtimeSync(userId: String) {
    val db = firestore
    if (db == null || !isUserSignedIn() || getCurrentUserId() != userId) return
    if (activeSyncUserId == userId && activeListenerRegistration != null) return

    stopRealtimeSync()
    activeSyncUserId = userId
    _syncStatus.value = SyncStatus.Syncing

    val path = "users/$userId/memories"
    activeListenerRegistration = db.collection("users").document(userId).collection("memories")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Realtime sync listener error on $path", error)
          handleFirestoreError(error, OperationType.LIST, path)
          _syncStatus.value = SyncStatus.Error("Realtime listener error: ${error.message}", error)
          return@addSnapshotListener
        }

        if (snapshot != null) {
          val items = snapshot.toObjects(UnifiedMemoryItem::class.java)
          repositoryScope.launch {
            try {
              reconcileRemoteSnapshot(userId, items)
              val count = localDao.getMemoryCount(userId)
              _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis(), count)
            } catch (e: Exception) {
              Log.w(TAG, "Failed to persist realtime Firestore snapshot into Room", e)
            }
          }
        }
      }
  }

  override fun stopRealtimeSync() {
    activeListenerRegistration?.remove()
    activeListenerRegistration = null
    activeSyncUserId = null
  }
}
