package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    ReminderEntity::class,
    PreferenceEntity::class,
    FixEntity::class,
    UnifiedMemoryEntity::class
  ],
  version = 5,
  exportSchema = false
)
@TypeConverters(MemoryTypeConverters::class)
abstract class InstinctDatabase : RoomDatabase() {
  abstract fun reminderDao(): ReminderDao
  abstract fun preferenceDao(): PreferenceDao
  abstract fun fixDao(): FixDao
  abstract fun unifiedMemoryDao(): UnifiedMemoryDao

  /**
   * Alias accessor for the [MemoryEntryDao] (`UnifiedMemoryDao`) that persists [com.example.data.model.MemoryEntry] records.
   */
  fun memoryEntryDao(): MemoryEntryDao = unifiedMemoryDao()

  companion object {
    @Volatile
    private var INSTANCE: InstinctDatabase? = null

    /**
     * Safely inspects `PRAGMA table_info(tableName)` and adds `columnName` if it is not yet present.
     * Ensures non-destructive, idempotent schema upgrades across all legacy database versions.
     */
    internal fun ensureColumnExists(
      db: SupportSQLiteDatabase,
      tableName: String,
      columnName: String,
      columnDefinitionSql: String
    ) {
      var exists = false
      val cursor = db.query("PRAGMA table_info(`$tableName`)")
      try {
        val nameColIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
          if (nameColIndex >= 0 && cursor.getString(nameColIndex).equals(columnName, ignoreCase = true)) {
            exists = true
            break
          }
        }
      } finally {
        cursor.close()
      }
      if (!exists) {
        db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinitionSql")
      }
    }

    /**
     * Ensures the `unified_memories` table and base indices exist when upgrading from v1 or v2.
     */
    private fun ensureUnifiedMemoriesTableExists(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `unified_memories` (
          `id` TEXT NOT NULL,
          `userId` TEXT NOT NULL,
          `type` TEXT NOT NULL,
          `title` TEXT NOT NULL,
          `content` TEXT NOT NULL,
          `tags` TEXT NOT NULL,
          `triggerActivity` TEXT NOT NULL,
          `relatedPeople` TEXT NOT NULL,
          `relatedLocations` TEXT NOT NULL,
          `recurrence` TEXT NOT NULL,
          `isCompleted` INTEGER NOT NULL,
          `category` TEXT NOT NULL,
          `details` TEXT NOT NULL,
          `photoUrl` TEXT,
          `problemSummary` TEXT NOT NULL,
          `whatHappened` TEXT NOT NULL,
          `bestFix` TEXT NOT NULL,
          `steps` TEXT NOT NULL,
          `toolsNeeded` TEXT NOT NULL,
          `alternativeFix` TEXT NOT NULL,
          `isVerifiedWorking` INTEGER NOT NULL,
          `createdAt` INTEGER NOT NULL,
          `updatedAt` INTEGER NOT NULL,
          PRIMARY KEY(`id`)
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_unified_memories_userId` ON `unified_memories` (`userId`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_unified_memories_userId_type` ON `unified_memories` (`userId`, `type`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_unified_memories_userId_category` ON `unified_memories` (`userId`, `category`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_unified_memories_userId_updatedAt` ON `unified_memories` (`userId`, `updatedAt`)")
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        ensureUnifiedMemoriesTableExists(db)
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        ensureUnifiedMemoriesTableExists(db)
      }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        ensureUnifiedMemoriesTableExists(db)
        ensureColumnExists(db, "reminders", "category", "TEXT NOT NULL DEFAULT 'Personal'")
        ensureColumnExists(db, "fixes", "category", "TEXT NOT NULL DEFAULT 'Personal'")
        db.execSQL("UPDATE unified_memories SET category = 'Personal' WHERE category = ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_userId_category` ON `reminders` (`userId`, `category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixes_userId_category` ON `fixes` (`userId`, `category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_unified_memories_userId_category` ON `unified_memories` (`userId`, `category`)")
      }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        ensureColumnExists(db, "unified_memories", "isDirty", "INTEGER NOT NULL DEFAULT 0")
        ensureColumnExists(db, "unified_memories", "pendingDelete", "INTEGER NOT NULL DEFAULT 0")
      }
    }

    val ALL_MIGRATIONS = arrayOf(
      MIGRATION_1_2,
      MIGRATION_2_3,
      MIGRATION_3_4,
      MIGRATION_4_5
    )

    fun getDatabase(context: Context): InstinctDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          InstinctDatabase::class.java,
          "instinct_database"
        )
          .addMigrations(*ALL_MIGRATIONS)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
