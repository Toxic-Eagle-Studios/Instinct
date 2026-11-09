package com.example.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

private val memoryWriteMutex = Mutex()

internal suspend fun <T> serializedMemoryWrite(action: suspend () -> T): T =
  memoryWriteMutex.withLock { action() }

/** Atomic read/write prevents a concurrent create or a failed read from replacing createdAt. */
internal suspend fun writePreservingCreatedAt(
  db: FirebaseFirestore,
  document: DocumentReference,
  fields: Map<String, Any?>,
  localCreatedAt: Long,
  isCurrentRevision: suspend () -> Boolean = { true }
) = serializedMemoryWrite {
  // Shared across repositories: an older queued upload cannot land after a newer local revision.
  if (!isCurrentRevision()) return@serializedMemoryWrite
  db.runTransaction { transaction ->
    val existing = transaction.get(document)
    val createdAt = preservedCreationTimestamp(
      exists = existing.exists(),
      remote = existing.getTimestamp("createdAt"),
      localCreatedAt = localCreatedAt
    )
    val payload = fields.toMutableMap().apply {
      put("createdAt", createdAt)
      put("updatedAt", FieldValue.serverTimestamp())
    }
    transaction.set(document, payload)
    Unit
  }.await()
}

internal fun preservedCreationTimestamp(
  exists: Boolean,
  remote: Timestamp?,
  localCreatedAt: Long,
  now: Long = System.currentTimeMillis()
): Timestamp = if (exists) {
  // A malformed remote document must be repaired explicitly; do not replace its immutable field.
  requireNotNull(remote) { "Existing memory is missing createdAt" }
} else {
  Timestamp(Date(minOf(localCreatedAt, now)))
}
