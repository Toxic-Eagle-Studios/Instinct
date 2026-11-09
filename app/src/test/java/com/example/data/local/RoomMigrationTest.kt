package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UnifiedMemoryItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

/**
 * SQL fixtures capture the v3/v4 layouts recoverable from the uploaded source.
 * Opening with the production factory exercises Room's generated schema validation,
 * in addition to checking that the user's actual field values survive the upgrade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `version 3 upgrades without losing reminders preferences fixes or memories`() {
    assertLegacyUpgradePreservesData(version = 3)
  }

  @Test
  fun `version 4 upgrades without losing reminders preferences fixes or memories`() {
    assertLegacyUpgradePreservesData(version = 4)
  }

  @Test
  fun `generated DAO preserves local rows during remote merge and stale upload acknowledgment`() = runBlocking {
    val room = Room.inMemoryDatabaseBuilder(context, InstinctDatabase::class.java).build()
    try {
      val dao = room.unifiedMemoryDao()
      fun memory(id: String) = UnifiedMemoryEntity.fromModel(UnifiedMemoryItem(
        id = id, userId = "user", title = "Local $id", createdAt = Date(100), updatedAt = Date(200)
      ))
      val dirty = memory("dirty").copy(isDirty = true)
      val deleted = memory("deleted").copy(isDirty = true, pendingDelete = true)
      val localOnly = memory("local-only")
      dao.insertMemories(listOf(dirty, deleted, localOnly))

      dao.mergeRemoteMemories("user", listOf(
        dirty.copy(title = "Cloud dirty", updatedAt = 300, isDirty = false),
        deleted.copy(title = "Cloud deleted", updatedAt = 300, isDirty = false, pendingDelete = false),
        memory("new-cloud")
      ))
      assertEquals(dirty, dao.getMemoryById("dirty", "user"))
      assertEquals(deleted, dao.getPendingDeleteMemories("user").single())
      assertEquals(localOnly, dao.getMemoryById("local-only", "user"))
      assertEquals(setOf("dirty", "local-only", "new-cloud"), dao.getAllMemories("user").map { it.id }.toSet())

      val editedDuringUpload = dirty.copy(title = "New edit", updatedAt = 400)
      dao.insertMemory(editedDuringUpload)
      dao.markMemorySyncedIfUnchanged(dirty)
      assertEquals(editedDuringUpload, dao.getMemoryById("dirty", "user"))
      dao.markMemorySyncedIfUnchanged(editedDuringUpload)
      assertEquals(editedDuringUpload.copy(isDirty = false), dao.getMemoryById("dirty", "user"))

      val restored = deleted.copy(title = "Restored", updatedAt = 500, pendingDelete = false)
      dao.insertMemory(restored)
      dao.deleteConfirmedTombstone(deleted)
      assertEquals(restored, dao.getMemoryById("deleted", "user"))

      dao.mergeRemoteMemories("another-user", listOf(localOnly.copy(userId = "another-user")))
      assertEquals(localOnly, dao.getMemoryByIdAnyUser("local-only"))
      val collision = runCatching {
        dao.saveLocalMemories(listOf(memory("batch-id"), memory("batch-id").copy(userId = "another-user")))
      }.exceptionOrNull()
      assertTrue("A mixed-owner batch must fail before replacing a row", collision is IllegalArgumentException)
      assertEquals(null, dao.getMemoryByIdAnyUser("batch-id"))
    } finally {
      room.close()
    }
  }

  @Test
  fun `unsupported downgrade fails and keeps the original database intact`() {
    val name = "unsupported-${UUID.randomUUID()}.db"
    val file = context.getDatabasePath(name)
    file.parentFile!!.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      db.execSQL("CREATE TABLE valuable_data (id TEXT NOT NULL PRIMARY KEY, content TEXT NOT NULL)")
      db.execSQL("INSERT INTO valuable_data VALUES ('local-only', 'Keep this memory')")
      db.version = 99
    }
    val room = InstinctDatabase.buildDatabase(context, name)
    try {
      val failure = runCatching { room.openHelper.writableDatabase }.exceptionOrNull()
      assertTrue("An unsupported downgrade must fail rather than recreate tables", failure is IllegalStateException)
      SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        assertEquals(99, db.version)
        db.rawQuery("SELECT content FROM valuable_data WHERE id = 'local-only'", null).use { cursor ->
          assertTrue(cursor.moveToFirst())
          assertEquals("Keep this memory", cursor.getString(0))
        }
      }
    } finally {
      room.close()
      context.deleteDatabase(name)
    }
  }

  private fun assertLegacyUpgradePreservesData(version: Int) {
    val name = "migration-$version-${UUID.randomUUID()}.db"
    val file = context.getDatabasePath(name)
    file.parentFile!!.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      createLegacySchema(db, version)
      db.execSQL("""
        INSERT INTO reminders
        (id, userId, title, triggerActivity, relatedPeople, relatedLocations, keywords,
         recurrence, isCompleted, createdAt, updatedAt)
        VALUES ('reminder', 'user', 'Call Dad', 'calling', 'Dad', 'Home', 'truck', 'every_time', 0, 100, 200)
      """.trimIndent())
      db.execSQL("""
        INSERT INTO preferences VALUES
        ('preference', 'user', 'Coffee order', 'Food', 'Two creams', 'Iced', 'coffee', NULL, 101, 201)
      """.trimIndent())
      db.execSQL("""
        INSERT INTO fixes
        (id, userId, problemSummary, whatHappened, bestFix, steps, toolsNeeded,
         alternativeFix, isVerifiedWorking, createdAt, updatedAt)
        VALUES ('fix', 'user', 'Loose hinge', 'Squeaks', 'Tighten', 'Step one||Step two',
                'Screwdriver', 'Replace', 1, 102, 202)
      """.trimIndent())
      db.execSQL("""
        INSERT INTO unified_memories VALUES
        ('memory', 'user', 'preference', 'Saved locally', 'Keep me', 'tag', '', '', '', '',
         0, '', 'details', NULL, '', '', '', '', '', '', 0, 103, 203)
      """.trimIndent())
      db.version = version
    }
    val room = InstinctDatabase.buildDatabase(context, name)
    try {
      // Opening runs every applicable migration and validates all four tables and indices.
      val db = room.openHelper.writableDatabase
      db.query("SELECT title, createdAt, updatedAt, category FROM reminders WHERE id = 'reminder'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Call Dad", cursor.getString(0))
        assertEquals(100L, cursor.getLong(1))
        assertEquals(200L, cursor.getLong(2))
        assertEquals("Personal", cursor.getString(3))
      }
      db.query("SELECT description, createdAt, updatedAt FROM preferences WHERE id = 'preference'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Two creams", cursor.getString(0))
        assertEquals(101L, cursor.getLong(1))
        assertEquals(201L, cursor.getLong(2))
      }
      db.query("SELECT steps, isVerifiedWorking, createdAt, category FROM fixes WHERE id = 'fix'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Step one||Step two", cursor.getString(0))
        assertEquals(1, cursor.getInt(1))
        assertEquals(102L, cursor.getLong(2))
        assertEquals("Personal", cursor.getString(3))
      }
      db.query("SELECT content, createdAt, updatedAt, category, isDirty, pendingDelete FROM unified_memories WHERE id = 'memory'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Keep me", cursor.getString(0))
        assertEquals(103L, cursor.getLong(1))
        assertEquals(203L, cursor.getLong(2))
        assertEquals(if (version == 3) "Personal" else "", cursor.getString(3))
        assertEquals(0, cursor.getInt(4))
        assertEquals(0, cursor.getInt(5))
      }
    } finally {
      room.close()
      context.deleteDatabase(name)
    }
  }

  private fun createLegacySchema(db: SQLiteDatabase, version: Int) {
    val categoryColumn = if (version >= 4) ", category TEXT NOT NULL DEFAULT 'Personal'" else ""
    db.execSQL("""
      CREATE TABLE reminders (
        id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, title TEXT NOT NULL,
        triggerActivity TEXT NOT NULL, relatedPeople TEXT NOT NULL, relatedLocations TEXT NOT NULL,
        keywords TEXT NOT NULL, recurrence TEXT NOT NULL, isCompleted INTEGER NOT NULL,
        createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL $categoryColumn)
    """.trimIndent())
    db.execSQL("""
      CREATE TABLE preferences (
        id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, title TEXT NOT NULL,
        category TEXT NOT NULL, description TEXT NOT NULL, details TEXT NOT NULL,
        tags TEXT NOT NULL, photoUrl TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)
    """.trimIndent())
    db.execSQL("""
      CREATE TABLE fixes (
        id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, problemSummary TEXT NOT NULL,
        whatHappened TEXT NOT NULL, bestFix TEXT NOT NULL, steps TEXT NOT NULL,
        toolsNeeded TEXT NOT NULL, alternativeFix TEXT NOT NULL, isVerifiedWorking INTEGER NOT NULL,
        createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL $categoryColumn)
    """.trimIndent())
    db.execSQL("""
      CREATE TABLE unified_memories (
        id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, type TEXT NOT NULL,
        title TEXT NOT NULL, content TEXT NOT NULL, tags TEXT NOT NULL, triggerActivity TEXT NOT NULL,
        relatedPeople TEXT NOT NULL, relatedLocations TEXT NOT NULL, recurrence TEXT NOT NULL,
        isCompleted INTEGER NOT NULL, category TEXT NOT NULL, details TEXT NOT NULL, photoUrl TEXT,
        problemSummary TEXT NOT NULL, whatHappened TEXT NOT NULL, bestFix TEXT NOT NULL,
        steps TEXT NOT NULL, toolsNeeded TEXT NOT NULL, alternativeFix TEXT NOT NULL,
        isVerifiedWorking INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)
    """.trimIndent())
    val indices = mutableMapOf(
      "reminders" to mutableListOf("userId", "userId,isCompleted"),
      "preferences" to mutableListOf("userId", "userId,category"),
      "fixes" to mutableListOf("userId", "userId,isVerifiedWorking"),
      "unified_memories" to mutableListOf("userId", "userId,type", "userId,category", "userId,updatedAt")
    )
    if (version >= 4) {
      indices.getValue("reminders").add("userId,category")
      indices.getValue("fixes").add("userId,category")
    }
    for ((table, definitions) in indices) {
      for (columns in definitions) {
        val suffix = columns.replace(',', '_')
        db.execSQL("CREATE INDEX index_${table}_$suffix ON $table ($columns)")
      }
    }
  }
}
