package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.FixEntity
import com.example.data.local.InstinctDatabase
import com.example.data.local.PreferenceEntity
import com.example.data.local.ReminderEntity
import com.example.data.model.Fix
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

private const val TAG = "InstinctRepository"

class InstinctRepository(
  private val context: Context,
  private val db: FirebaseFirestore,
  private val localDb: InstinctDatabase
) {
  constructor(context: Context) : this(
    context = context.applicationContext,
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    localDb = InstinctDatabase.getDatabase(context.applicationContext)
  )

  private val auth = Firebase.auth
  private val reminderDao = localDb.reminderDao()
  private val preferenceDao = localDb.preferenceDao()
  private val fixDao = localDb.fixDao()
  val unifiedRepository = UnifiedMemoryRepository(context, db, localDb)
  val memoryRepository: MemoryRepository = DefaultMemoryRepository(context, localDb.unifiedMemoryDao(), db, auth)

  fun getCurrentUserId(): String {
    return auth.currentUser?.uid ?: "guest_user"
  }

  fun isUserSignedIn(): Boolean = auth.currentUser != null

  // ----------------------------------------------------
  // REMINDERS
  // ----------------------------------------------------
  fun observeReminders(userId: String): Flow<List<Reminder>> = flow {
    if (auth.currentUser != null && auth.currentUser?.uid == userId) {
      val path = "users/$userId/reminders"
      try {
        db.collection("users").document(userId).collection("reminders")
          .snapshots()
          .map { snapshot ->
            val items = snapshot.toObjects(Reminder::class.java)
            // Mirror to local Room database for offline cache
            CoroutineScope(Dispatchers.IO).launch {
              try {
                reminderDao.insertReminders(items.map { ReminderEntity.fromModel(it) })
              } catch (e: Exception) {
                Log.w(TAG, "Cache mirror reminder failed", e)
              }
            }
            items
          }
          .catch { error ->
            if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
            // Fallback to local database cache if network error occurs
            emit(reminderDao.getAllReminders(userId).map { it.toModel() })
          }
          .collect { emit(it) }
      } catch (e: Exception) {
        emit(reminderDao.getAllReminders(userId).map { it.toModel() })
      }
    } else {
      // Guest or local mode
      reminderDao.observeReminders(userId)
        .map { list -> list.map { it.toModel() } }
        .collect { emit(it) }
    }
  }

  suspend fun saveReminder(reminder: Reminder): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      val existingLocal = reminderDao.getAllReminders(uid).firstOrNull { it.id == reminder.id }
      val preservedCreatedAt = existingLocal?.let { Date(it.createdAt) } ?: reminder.createdAt ?: Date()
      val toSave = reminder.copy(
        userId = uid,
        updatedAt = Date(),
        createdAt = preservedCreatedAt
      )
      // Save locally first
      reminderDao.insertReminder(ReminderEntity.fromModel(toSave))

      // If signed in, sync to Firestore while preserving immutable createdAt
      if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        val docRef = db.collection("users").document(uid).collection("reminders").document(toSave.id)
        val existingSnap = try { docRef.get().await() } catch (_: Exception) { null }
        val safeCreatedMillis = minOf(preservedCreatedAt.time, System.currentTimeMillis())
        val createdAtValue: Any = if (existingSnap != null && existingSnap.exists()) {
          existingSnap.getTimestamp("createdAt") ?: com.google.firebase.Timestamp(Date(safeCreatedMillis))
        } else {
          com.google.firebase.Timestamp(Date(safeCreatedMillis))
        }
        val payload = toSave.toMap().toMutableMap().apply {
          put("createdAt", createdAtValue)
          put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
      }

      // Sync to unified shared memory collection
      try {
        unifiedRepository.saveReminder(toSave)
      } catch (e: Exception) {
        Log.w(TAG, "Unified memory sync reminder warning", e)
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save reminder", e)
      handleFirestoreError(e, OperationType.WRITE, "users/${getCurrentUserId()}/reminders/${reminder.id}")
      Result.failure(e)
    }
  }

  suspend fun toggleReminderCompleted(reminderId: String, completed: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      val existing = reminderDao.getAllReminders(uid).firstOrNull { it.id == reminderId }
      if (existing != null) {
        val updated = existing.copy(isCompleted = completed, updatedAt = System.currentTimeMillis())
        reminderDao.updateReminder(updated)

        if (auth.currentUser != null && auth.currentUser?.uid == uid) {
          db.collection("users").document(uid).collection("reminders").document(reminderId)
            .update(
              mapOf(
                "isCompleted" to completed,
                "updatedAt" to FieldValue.serverTimestamp()
              )
            )
            .await()
        }

        try {
          unifiedRepository.toggleReminderStatus(reminderId, completed)
        } catch (e: Exception) {
          Log.w(TAG, "Unified memory toggle warning", e)
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to toggle reminder completion", e)
      Result.failure(e)
    }
  }

  suspend fun deleteReminder(reminderId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      reminderDao.deleteReminderById(reminderId, uid)
      if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        db.collection("users").document(uid).collection("reminders").document(reminderId)
          .delete()
          .await()
      }
      try {
        unifiedRepository.deleteMemory(reminderId)
      } catch (e: Exception) {
        Log.w(TAG, "Unified memory delete reminder warning", e)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete reminder", e)
      Result.failure(e)
    }
  }

  // ----------------------------------------------------
  // PREFERENCES (EXACTLY)
  // ----------------------------------------------------
  fun observePreferences(userId: String): Flow<List<Preference>> = flow {
    if (auth.currentUser != null && auth.currentUser?.uid == userId) {
      val path = "users/$userId/preferences"
      try {
        db.collection("users").document(userId).collection("preferences")
          .snapshots()
          .map { snapshot ->
            val items = snapshot.toObjects(Preference::class.java)
            CoroutineScope(Dispatchers.IO).launch {
              try {
                preferenceDao.insertPreferences(items.map { PreferenceEntity.fromModel(it) })
              } catch (e: Exception) {
                Log.w(TAG, "Cache mirror preference failed", e)
              }
            }
            items
          }
          .catch { error ->
            if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
            emit(preferenceDao.getAllPreferences(userId).map { it.toModel() })
          }
          .collect { emit(it) }
      } catch (e: Exception) {
        emit(preferenceDao.getAllPreferences(userId).map { it.toModel() })
      }
    } else {
      preferenceDao.observePreferences(userId)
        .map { list -> list.map { it.toModel() } }
        .collect { emit(it) }
    }
  }

  suspend fun savePreference(preference: Preference): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      val existingLocal = preferenceDao.getAllPreferences(uid).firstOrNull { it.id == preference.id }
      val preservedCreatedAt = existingLocal?.let { Date(it.createdAt) } ?: preference.createdAt ?: Date()
      val toSave = preference.copy(
        userId = uid,
        updatedAt = Date(),
        createdAt = preservedCreatedAt
      )
      preferenceDao.insertPreference(PreferenceEntity.fromModel(toSave))

      if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        val docRef = db.collection("users").document(uid).collection("preferences").document(toSave.id)
        val existingSnap = try { docRef.get().await() } catch (_: Exception) { null }
        val safeCreatedMillis = minOf(preservedCreatedAt.time, System.currentTimeMillis())
        val createdAtValue: Any = if (existingSnap != null && existingSnap.exists()) {
          existingSnap.getTimestamp("createdAt") ?: com.google.firebase.Timestamp(Date(safeCreatedMillis))
        } else {
          com.google.firebase.Timestamp(Date(safeCreatedMillis))
        }
        val payload = toSave.toMap().toMutableMap().apply {
          put("createdAt", createdAtValue)
          put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
      }

      // Sync to unified shared memory collection
      try {
        unifiedRepository.savePreference(toSave)
      } catch (e: Exception) {
        Log.w(TAG, "Unified memory sync preference warning", e)
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save preference", e)
      Result.failure(e)
    }
  }

  suspend fun deletePreference(preferenceId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      preferenceDao.deletePreferenceById(preferenceId, uid)
      if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        db.collection("users").document(uid).collection("preferences").document(preferenceId)
          .delete()
          .await()
      }
      try {
        unifiedRepository.deleteMemory(preferenceId)
      } catch (e: Exception) {
        Log.w(TAG, "Unified memory delete preference warning", e)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete preference", e)
      Result.failure(e)
    }
  }

  // ----------------------------------------------------
  // FIXES (UNDO IRL)
  // ----------------------------------------------------
  fun observeFixes(userId: String): Flow<List<Fix>> = flow {
    if (auth.currentUser != null && auth.currentUser?.uid == userId) {
      val path = "users/$userId/fixes"
      try {
        db.collection("users").document(userId).collection("fixes")
          .snapshots()
          .map { snapshot ->
            val items = snapshot.toObjects(Fix::class.java)
            CoroutineScope(Dispatchers.IO).launch {
              try {
                fixDao.insertFixes(items.map { FixEntity.fromModel(it) })
              } catch (e: Exception) {
                Log.w(TAG, "Cache mirror fix failed", e)
              }
            }
            items
          }
          .catch { error ->
            if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
            emit(fixDao.getAllFixes(userId).map { it.toModel() })
          }
          .collect { emit(it) }
      } catch (e: Exception) {
        emit(fixDao.getAllFixes(userId).map { it.toModel() })
      }
    } else {
      fixDao.observeFixes(userId)
        .map { list -> list.map { it.toModel() } }
        .collect { emit(it) }
    }
  }

  suspend fun saveFix(fix: Fix): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      val existingLocal = fixDao.getAllFixes(uid).firstOrNull { it.id == fix.id }
      val preservedCreatedAt = existingLocal?.let { Date(it.createdAt) } ?: fix.createdAt ?: Date()
      val toSave = fix.copy(
        userId = uid,
        updatedAt = Date(),
        createdAt = preservedCreatedAt
      )
      fixDao.insertFix(FixEntity.fromModel(toSave))

      if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        val docRef = db.collection("users").document(uid).collection("fixes").document(toSave.id)
        val existingSnap = try { docRef.get().await() } catch (_: Exception) { null }
        val safeCreatedMillis = minOf(preservedCreatedAt.time, System.currentTimeMillis())
        val createdAtValue: Any = if (existingSnap != null && existingSnap.exists()) {
          existingSnap.getTimestamp("createdAt") ?: com.google.firebase.Timestamp(Date(safeCreatedMillis))
        } else {
          com.google.firebase.Timestamp(Date(safeCreatedMillis))
        }
        val payload = toSave.toMap().toMutableMap().apply {
          put("createdAt", createdAtValue)
          put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
      }

      // Sync to unified shared memory collection
      try {
        unifiedRepository.saveFix(toSave)
      } catch (e: Exception) {
        Log.w(TAG, "Unified memory sync fix warning", e)
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save fix", e)
      Result.failure(e)
    }
  }

  suspend fun deleteFix(fixId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = getCurrentUserId()
      fixDao.deleteFixById(fixId, uid)
      if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        db.collection("users").document(uid).collection("fixes").document(fixId)
          .delete()
          .await()
      }
      try {
        unifiedRepository.deleteMemory(fixId)
      } catch (e: Exception) {
        Log.w(TAG, "Unified memory delete fix warning", e)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete fix", e)
      Result.failure(e)
    }
  }

  // ----------------------------------------------------
  // SHARED BRAIN MANAGEMENT & EXPORT
  // ----------------------------------------------------
  suspend fun getAllRemindersList(userId: String): List<Reminder> = withContext(Dispatchers.IO) {
    reminderDao.getAllReminders(userId).map { it.toModel() }
  }

  suspend fun getAllPreferencesList(userId: String): List<Preference> = withContext(Dispatchers.IO) {
    preferenceDao.getAllPreferences(userId).map { it.toModel() }
  }

  suspend fun getAllFixesList(userId: String): List<Fix> = withContext(Dispatchers.IO) {
    fixDao.getAllFixes(userId).map { it.toModel() }
  }

  suspend fun exportAllDataJson(userId: String): String = withContext(Dispatchers.IO) {
    val reminders = reminderDao.getAllReminders(userId).map { it.toModel() }
    val preferences = preferenceDao.getAllPreferences(userId).map { it.toModel() }
    val fixes = fixDao.getAllFixes(userId).map { it.toModel() }
    val unifiedEntries = localDb.unifiedMemoryDao().getAllMemories(userId).map { it.toMemoryEntry() }

    val portableMemories = JSONArray()
    unifiedEntries.forEach { entry ->
      val obj = JSONObject().apply {
        put("id", entry.id)
        put(
          "type",
          when (entry) {
            is com.example.data.model.MemoryEntry.Reminder -> "reminder"
            is com.example.data.model.MemoryEntry.Preference -> "preference"
            is com.example.data.model.MemoryEntry.FixHistory -> "solution"
          }
        )
        put("title", entry.primaryTitle)
        put(
          "content",
          when (entry) {
            is com.example.data.model.MemoryEntry.Reminder -> entry.title
            is com.example.data.model.MemoryEntry.Preference -> entry.description.ifBlank { entry.details }
            is com.example.data.model.MemoryEntry.FixHistory ->
              (listOf(entry.bestFix) + entry.steps).filter { it.isNotBlank() }.joinToString("\n")
          }
        )
        put(
          "context",
          when (entry) {
            is com.example.data.model.MemoryEntry.Reminder -> entry.triggerActivity
            else -> entry.category
          }
        )
        put("category", entry.category)
        put("tags", JSONArray(entry.tags))
        put(
          "done",
          when (entry) {
            is com.example.data.model.MemoryEntry.Reminder -> entry.isCompleted
            else -> false
          }
        )
        put("createdAt", entry.timestamp.toInstant().toString())
      }
      portableMemories.put(obj)
    }

    val json = JSONObject().apply {
      put("app", "INSTINCT")
      put("appName", "INSTINCT")
      put("exportedAt", Date().toInstant().toString())
      put("exportDate", Date().toString())
      put("userId", userId)
      put("totalUnifiedEntries", unifiedEntries.size)
      put("memories", portableMemories)
      put("reminders", JSONArray(reminders.map { JSONObject(it.toMap()) }))
      put("preferences", JSONArray(preferences.map { JSONObject(it.toMap()) }))
      put("fixes", JSONArray(fixes.map { JSONObject(it.toMap()) }))
      put("unifiedMemories", JSONArray(unifiedEntries.map { entry -> JSONObject(entry.toMap()) }))
    }
    json.toString(2)
  }

  /**
   * Restores a JSON backup (supports both `{ app: "INSTINCT", memories: [...] }`, raw array `[...]`,
   * and multi-collection `{ reminders: [...], preferences: [...], fixes: [...] }`).
   */
  suspend fun importDataJson(jsonString: String, targetUserId: String): Result<Int> = withContext(Dispatchers.IO) {
    try {
      val trimmed = jsonString.trim()
      if (trimmed.isBlank()) return@withContext Result.failure(IllegalArgumentException("Backup file is empty."))

      var importedCount = 0
      val uid = targetUserId.ifBlank { getCurrentUserId() }

      val portableArray: JSONArray? = when {
        trimmed.startsWith("[") -> JSONArray(trimmed)
        else -> {
          val root = JSONObject(trimmed)
          root.optJSONArray("memories") ?: root.optJSONArray("unifiedMemories")
        }
      }

      if (portableArray != null && portableArray.length() > 0) {
        if (portableArray.length() > 5000) {
          return@withContext Result.failure(IllegalArgumentException("Oversized backup (max 5000 items)."))
        }
        for (i in 0 until portableArray.length()) {
          val obj = portableArray.optJSONObject(i) ?: continue
          val rawType = obj.optString("type", "note").lowercase().trim()
          val rawContent = obj.optString("content", obj.optString("description", obj.optString("body", ""))).trim().take(5000)
          val rawTitle = obj.optString("title", obj.optString("problemSummary", rawContent.take(70))).trim().take(100)
          if (rawTitle.isBlank() && rawContent.isBlank()) continue

          val id = obj.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
          val contextVal = obj.optString("context", obj.optString("triggerActivity", "")).trim().take(150)
          val dueAtVal = obj.optString("dueAt", "").trim()
          val categoryVal = obj.optString("category", "").trim().ifBlank {
            if (contextVal in com.example.data.model.MemoryEntry.CATEGORIES) contextVal else com.example.data.model.MemoryEntry.DEFAULT_CATEGORY
          }
          val doneVal = obj.optBoolean("done", obj.optBoolean("isCompleted", false))
          val tagsList = mutableListOf<String>()
          val tagsArr = obj.optJSONArray("tags") ?: obj.optJSONArray("keywords")
          if (tagsArr != null) {
            for (t in 0 until minOf(tagsArr.length(), 10)) {
              val tag = tagsArr.optString(t).trim().take(40)
              if (tag.isNotBlank()) tagsList.add(tag)
            }
          }
          if (dueAtVal.isNotBlank()) {
            tagsList.add("due:$dueAtVal")
          }

          val now = Date()
          when (rawType) {
            "reminder" -> {
              val reminder = Reminder(
                id = id,
                userId = uid,
                title = rawTitle.ifBlank { rawContent.take(80) },
                triggerActivity = contextVal.ifBlank { "Next time" },
                category = categoryVal,
                keywords = tagsList.distinct(),
                recurrence = obj.optString("recurrence", "every_time"),
                isCompleted = doneVal,
                createdAt = now,
                updatedAt = now
              )
              saveReminder(reminder)
              memoryRepository.saveMemoryEntry(com.example.data.model.MemoryEntry.fromReminder(reminder))
              importedCount++
            }
            "solution", "fix" -> {
              val stepsArr = obj.optJSONArray("steps")
              val parsedSteps = if (stepsArr != null && stepsArr.length() > 0) {
                (0 until stepsArr.length()).mapNotNull { idx -> stepsArr.optString(idx).takeIf { it.isNotBlank() } }
              } else {
                rawContent.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf(rawTitle) }
              }
              val fix = Fix(
                id = id,
                userId = uid,
                problemSummary = rawTitle.ifBlank { "Restored Solution" },
                whatHappened = obj.optString("whatHappened", "Restored from INSTINCT backup"),
                bestFix = obj.optString("bestFix", parsedSteps.firstOrNull() ?: rawContent.take(140)),
                steps = parsedSteps,
                toolsNeeded = emptyList(),
                category = categoryVal,
                isVerifiedWorking = true,
                createdAt = now,
                updatedAt = now
              )
              saveFix(fix)
              memoryRepository.saveMemoryEntry(com.example.data.model.MemoryEntry.fromFix(fix))
              importedCount++
            }
            else -> {
              // preference or note
              val pref = Preference(
                id = id,
                userId = uid,
                title = rawTitle.ifBlank { rawContent.take(80) },
                category = categoryVal,
                description = rawContent.ifBlank { rawTitle },
                details = obj.optString("details", if (contextVal.isNotBlank()) "Context: $contextVal" else ""),
                tags = tagsList.distinct(),
                createdAt = now,
                updatedAt = now
              )
              savePreference(pref)
              memoryRepository.saveMemoryEntry(com.example.data.model.MemoryEntry.fromPreference(pref))
              importedCount++
            }
          }
        }
      }

      Result.success(importedCount)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to import JSON backup", e)
      Result.failure(e)
    }
  }

  /**
   * Returns total count of local on-device memories (including guest_user records) that can be copied to cloud.
   */
  suspend fun getLocalDeviceMemoryCount(currentUserId: String): Int = withContext(Dispatchers.IO) {
    val guestEntries = localDb.unifiedMemoryDao().getAllMemories("guest_user")
    val userEntries = if (currentUserId != "guest_user") {
      localDb.unifiedMemoryDao().getAllMemories(currentUserId)
    } else emptyList()
    (guestEntries + userEntries).distinctBy { it.id }.size
  }

  /**
   * Explicit "Copy device memories to cloud" action (`moveLocalToCloud` from App.jsx):
   * Copies all local Room memories (from `guest_user` and current device cache) into the signed-in
   * Google user's Firestore collections while preserving the local copies on device.
   */
  suspend fun copyLocalDeviceMemoriesToCloud(cloudUserId: String): Result<Int> = withContext(Dispatchers.IO) {
    try {
      if (auth.currentUser == null || cloudUserId.isBlank() || cloudUserId == "guest_user") {
        return@withContext Result.failure(IllegalStateException("Sign in with Google first to copy memories to cloud."))
      }

      val guestReminders = reminderDao.getAllReminders("guest_user").map { it.toModel() }
      val userReminders = reminderDao.getAllReminders(cloudUserId).map { it.toModel() }
      val allReminders = (guestReminders + userReminders).distinctBy { it.id }

      val guestPrefs = preferenceDao.getAllPreferences("guest_user").map { it.toModel() }
      val userPrefs = preferenceDao.getAllPreferences(cloudUserId).map { it.toModel() }
      val allPrefs = (guestPrefs + userPrefs).distinctBy { it.id }

      val guestFixes = fixDao.getAllFixes("guest_user").map { it.toModel() }
      val userFixes = fixDao.getAllFixes(cloudUserId).map { it.toModel() }
      val allFixes = (guestFixes + userFixes).distinctBy { it.id }

      val guestUnified = localDb.unifiedMemoryDao().getAllMemories("guest_user").map { it.toMemoryEntry() }
      val userUnified = localDb.unifiedMemoryDao().getAllMemories(cloudUserId).map { it.toMemoryEntry() }
      val allUnified = (guestUnified + userUnified).distinctBy { it.id }

      var copiedCount = 0

      allReminders.forEach { rem ->
        val cloudRem = rem.copy(userId = cloudUserId)
        saveReminder(cloudRem)
        copiedCount++
      }
      allPrefs.forEach { pref ->
        val cloudPref = pref.copy(userId = cloudUserId)
        savePreference(cloudPref)
        copiedCount++
      }
      allFixes.forEach { fix ->
        val cloudFix = fix.copy(userId = cloudUserId)
        saveFix(cloudFix)
        copiedCount++
      }
      allUnified.forEach { entry ->
        val normalized = when (entry) {
          is com.example.data.model.MemoryEntry.Reminder -> entry.copy(userId = cloudUserId)
          is com.example.data.model.MemoryEntry.Preference -> entry.copy(userId = cloudUserId)
          is com.example.data.model.MemoryEntry.FixHistory -> entry.copy(userId = cloudUserId)
        }
        memoryRepository.saveMemoryEntry(normalized)
      }

      Result.success(maxOf(copiedCount, allUnified.size))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to copy local memories to cloud", e)
      Result.failure(e)
    }
  }

  suspend fun exportReadableReport(userId: String): String = withContext(Dispatchers.IO) {
    val reminders = reminderDao.getAllReminders(userId).map { it.toModel() }
    val preferences = preferenceDao.getAllPreferences(userId).map { it.toModel() }
    val fixes = fixDao.getAllFixes(userId).map { it.toModel() }

    buildString {
      appendLine("==========================================")
      appendLine("INSTINCT — PERSONAL MEMORY EXPORT")
      appendLine("Exported: ${Date()}")
      appendLine("User ID: $userId")
      appendLine("==========================================")
      appendLine()
      appendLine("1. WHILE YOU'RE AT IT (Contextual Reminders: ${reminders.size})")
      if (reminders.isEmpty()) {
        appendLine("   - None stored")
      } else {
        reminders.forEach { rem ->
          val status = if (rem.isCompleted) "[DONE]" else "[ACTIVE]"
          appendLine("   • $status ${rem.title} (Trigger: ${rem.triggerActivity.ifBlank { "General" }})")
        }
      }
      appendLine()
      appendLine("2. EXACTLY (Personal Preferences: ${preferences.size})")
      if (preferences.isEmpty()) {
        appendLine("   - None stored")
      } else {
        preferences.forEach { pref ->
          appendLine("   • [${pref.category}] ${pref.title}: ${pref.description}")
          if (pref.details.isNotBlank()) {
            appendLine("     Details: ${pref.details}")
          }
        }
      }
      appendLine()
      appendLine("3. UNDO IRL (Saved Fixes & Troubleshooting: ${fixes.size})")
      if (fixes.isEmpty()) {
        appendLine("   - None stored")
      } else {
        fixes.forEach { fix ->
          val verified = if (fix.isVerifiedWorking) "[VERIFIED]" else "[RECORDED]"
          appendLine("   • $verified ${fix.problemSummary}")
          appendLine("     Best Fix: ${fix.bestFix}")
        }
      }
    }
  }

  suspend fun clearCompletedReminders(userId: String): Result<Int> = withContext(Dispatchers.IO) {
    try {
      val completed = reminderDao.getAllReminders(userId).filter { it.isCompleted }
      completed.forEach { entity ->
        deleteReminder(entity.id)
      }
      Result.success(completed.size)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to clear completed reminders", e)
      Result.failure(e)
    }
  }

  suspend fun clearAllData(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      reminderDao.deleteAllByUserId(userId)
      preferenceDao.deleteAllByUserId(userId)
      fixDao.deleteAllByUserId(userId)

      try {
        memoryRepository.clearAllMemories(userId)
      } catch (e: Exception) {
        Log.w(TAG, "MemoryRepository clear warning", e)
      }

      try {
        unifiedRepository.clearAllMemories(userId)
      } catch (e: Exception) {
        Log.w(TAG, "Unified clear warning", e)
      }

      if (auth.currentUser != null && auth.currentUser?.uid == userId) {
        // Clear collections in Firestore
        val batch = db.batch()
        val remindersSnap = db.collection("users").document(userId).collection("reminders").get().await()
        remindersSnap.documents.forEach { batch.delete(it.reference) }

        val prefsSnap = db.collection("users").document(userId).collection("preferences").get().await()
        prefsSnap.documents.forEach { batch.delete(it.reference) }

        val fixesSnap = db.collection("users").document(userId).collection("fixes").get().await()
        fixesSnap.documents.forEach { batch.delete(it.reference) }

        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to clear all data", e)
      Result.failure(e)
    }
  }
}
