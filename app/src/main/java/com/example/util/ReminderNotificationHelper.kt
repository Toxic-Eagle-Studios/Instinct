package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Reminder
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Helper for real Android notification delivery for scheduled reminders and persistent local
 * storage of captured camera/gallery photos for memory entries.
 */
object ReminderNotificationHelper {
  const val CHANNEL_ID = "instinct_reminder_alerts"
  private const val CHANNEL_NAME = "INSTINCT Reminder Alerts"
  private const val CHANNEL_DESC = "Notifications for scheduled and contextual reminders"

  fun ensureChannelCreated(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
          description = CHANNEL_DESC
        }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  /**
   * Posts a local system notification for due/scheduled reminders if permission is granted.
   * Returns true if a notification was posted.
   */
  fun postDueRemindersNotification(context: Context, dueReminders: List<Reminder>): Boolean {
    if (dueReminders.isEmpty()) return false
    ensureChannelCreated(context)
    if (!hasNotificationPermission(context)) return false

    return try {
      val first = dueReminders.first()
      val title = if (dueReminders.size == 1) {
        "Reminder Due: ${first.title}"
      } else {
        "${dueReminders.size} Scheduled Reminders Active"
      }
      val body = if (dueReminders.size == 1) {
        val trigger = first.triggerActivity.ifBlank { "Scheduled" }
        val due = first.dueAt.ifBlank { "Now" }
        "Context: $trigger • Due: $due"
      } else {
        dueReminders.take(3).joinToString(" • ") { it.title }
      }

      val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        1001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

      NotificationManagerCompat.from(context).notify(1001, notification)
      true
    } catch (_: SecurityException) {
      false
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Persists a captured [Bitmap] into `filesDir/memory_photos/photo_<uuid>.jpg` and returns its
   * durable `file://` URI string so captured photo memories survive app restarts.
   */
  fun saveBitmapToInternalStorage(
    context: Context,
    bitmap: Bitmap,
    fileNamePrefix: String = "photo"
  ): String? {
    return try {
      val dir = File(context.filesDir, "memory_photos")
      if (!dir.exists()) {
        dir.mkdirs()
      }
      val file = File(dir, "${fileNamePrefix}_${UUID.randomUUID()}.jpg")
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        out.flush()
      }
      file.toURI().toString()
    } catch (_: Exception) {
      null
    }
  }
}
