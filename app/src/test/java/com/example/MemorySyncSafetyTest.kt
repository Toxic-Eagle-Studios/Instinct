package com.example

import com.example.data.local.UnifiedMemoryEntity
import com.example.data.model.MemoryEntry
import com.example.data.model.UnifiedMemoryItem
import com.example.data.repository.DefaultMemoryRepository
import com.example.data.repository.SyncStatus
import com.example.data.repository.preservedCreationTimestamp
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class MemorySyncSafetyTest {
  @Test
  fun testSnapshotAbsence_neverDeletesCleanLocalOnlyMemory() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(localDao = dao, ioDispatcher = Dispatchers.Unconfined)
    val localOnly = UnifiedMemoryItem(
      id = "legacy-local-only", userId = "alice", title = "Saved before outbox tracking",
      createdAt = Date(1000L), updatedAt = Date(2000L)
    )
    dao.insertMemory(UnifiedMemoryEntity.fromModel(localOnly, isDirty = false))

    repo.reconcileRemoteSnapshot("alice", emptyList())
    assertNotNull(dao.getMemoryById(localOnly.id, "alice"))
    repo.reconcileRemoteSnapshot("alice", listOf(localOnly.copy(id = "remote-other")))
    assertEquals(setOf("legacy-local-only", "remote-other"), dao.getAllMemories("alice").map { it.id }.toSet())
  }

  @Test
  fun testRemoteMerge_preservesDirtyEditsCreationTimeAndUserBoundary() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(localDao = dao, ioDispatcher = Dispatchers.Unconfined)
    val original = UnifiedMemoryItem(
      id = "memory", userId = "alice", title = "Local edit", createdAt = Date(1000L), updatedAt = Date(2000L)
    )
    val dirty = UnifiedMemoryEntity.fromModel(original, isDirty = true)
    dao.insertMemory(dirty)
    repo.reconcileRemoteSnapshot("alice", listOf(original.copy(title = "Stale cloud", updatedAt = Date(5000L))))
    assertEquals(dirty, dao.getMemoryById("memory", "alice"))

    dao.markMemorySyncedIfUnchanged(dirty)
    repo.reconcileRemoteSnapshot("alice", listOf(
      original.copy(title = "New cloud", createdAt = Date(3000L), updatedAt = Date(6000L)),
      original.copy(id = "other-user", userId = "bob"),
      original.copy(id = "missing-timestamps", createdAt = null, updatedAt = null)
    ))
    val merged = dao.getMemoryById("memory", "alice")!!
    assertEquals("New cloud", merged.title)
    assertEquals(1000L, merged.createdAt)
    assertEquals(1, dao.getAllMemories("alice").size)
    assertTrue(dao.getAllMemories("bob").isEmpty())
  }

  @Test
  fun testGlobalIdCollision_doesNotReplaceAnotherAccountsMemory() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val alice = UnifiedMemoryEntity.fromModel(UnifiedMemoryItem(
      id = "shared-id", userId = "alice", title = "Alice original", createdAt = Date(1000L), updatedAt = Date(2000L)
    ))
    dao.insertMemory(alice)
    val bob = alice.copy(userId = "bob", title = "Bob cloud")
    dao.mergeRemoteMemories("bob", listOf(bob))
    assertEquals(alice, dao.getMemoryByIdAnyUser("shared-id"))
    var rejected = false
    try { dao.saveLocalMemories(listOf(bob)) } catch (_: IllegalArgumentException) { rejected = true }
    assertTrue(rejected)
    assertEquals(alice, dao.getMemoryByIdAnyUser("shared-id"))
  }

  @Test
  fun testBatchOwnershipConflict_isRejectedBeforeAnyRowIsWritten() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val alice = UnifiedMemoryEntity.fromModel(UnifiedMemoryItem(
      id = "shared-id", userId = "alice", title = "Alice", createdAt = Date(1000L), updatedAt = Date(2000L)
    ))
    var rejected = false
    try { dao.saveLocalMemories(listOf(alice, alice.copy(userId = "bob"))) }
    catch (_: IllegalArgumentException) { rejected = true }
    assertTrue(rejected)
    assertEquals(null, dao.getMemoryByIdAnyUser("shared-id"))
  }

  @Test
  fun testClearAllWhileOffline_preservesDeletionQueueAndReportsIncompleteCloudClear() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(localDao = dao, ioDispatcher = Dispatchers.Unconfined)
    repo.saveMemoryEntry(MemoryEntry.Reminder(id = "queued", userId = "alice", title = "Keep payload"))
    val result = repo.clearAllMemories("alice")
    assertTrue(result.isFailure)
    assertEquals(0, dao.getMemoryCount("alice"))
    val tombstone = dao.getPendingDeleteMemories("alice").single()
    assertEquals("Keep payload", tombstone.title)
    assertTrue(tombstone.isDirty)
    assertEquals(SyncStatus.Offline, repo.syncStatus.value)
  }

  @Test
  fun testDeleteUncachedMemoryWhileOffline_retainsRetryIntent() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(localDao = dao, ioDispatcher = Dispatchers.Unconfined)
    assertTrue(repo.deleteMemoryEntry("alice", "remote-only-id").isSuccess)
    val tombstone = dao.getPendingDeleteMemories("alice").single()
    assertEquals("remote-only-id", tombstone.id)
    assertTrue(tombstone.isDirty)
    assertEquals(0, dao.getMemoryCount("alice"))
  }

  @Test
  fun testClearAllGuest_removesLocalRowsWithoutCloud() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(localDao = dao, ioDispatcher = Dispatchers.Unconfined)
    repo.saveMemoryEntry(MemoryEntry.Reminder(id = "guest", userId = "guest_user", title = "Guest"))
    assertTrue(repo.clearAllMemories("guest_user").isSuccess)
    assertEquals(0, dao.getMemoryCount("guest_user"))
    assertTrue(dao.getPendingDeleteMemories("guest_user").isEmpty())
  }

  @Test
  fun testUploadAcknowledgment_cannotClearLaterEditOrDeleteResavedMemory() = runBlocking {
    val dao = FakeUnifiedMemoryDao()
    val sent = UnifiedMemoryEntity.fromModel(UnifiedMemoryItem(
      id = "memory", userId = "alice", title = "Sent", createdAt = Date(1000L), updatedAt = Date(2000L)
    ), isDirty = true)
    dao.insertMemory(sent)
    val newer = sent.copy(title = "Edited during upload", updatedAt = 3000L)
    dao.insertMemory(newer)
    dao.markMemorySyncedIfUnchanged(sent)
    assertTrue(dao.getMemoryById(sent.id, sent.userId)!!.isDirty)

    dao.markMemoryPendingDelete(sent.id, sent.userId, 4000L)
    val tombstone = dao.getPendingDeleteMemories(sent.userId).single()
    dao.insertMemory(newer.copy(updatedAt = 5000L))
    dao.deleteConfirmedTombstone(tombstone)
    assertNotNull(dao.getMemoryById(sent.id, sent.userId))
  }

  @Test
  fun testCloudCreationTimestamp_preservesNanosecondsOnExistingDocument() {
    val original = Timestamp(1_700_000_000L, 123456789)
    assertEquals(original, preservedCreationTimestamp(true, original, 9999L, 10000L))
    assertEquals(Timestamp(Date(1000L)), preservedCreationTimestamp(false, null, 1000L, 2000L))
    assertEquals(Timestamp(Date(2000L)), preservedCreationTimestamp(false, null, 3000L, 2000L))
  }

  @Test(expected = IllegalArgumentException::class)
  fun testCloudCreationTimestamp_missingExistingValueRequiresExplicitRepair() {
    preservedCreationTimestamp(true, null, 1000L, 2000L)
  }

}
