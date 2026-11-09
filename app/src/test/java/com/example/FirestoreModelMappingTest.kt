package com.example

import com.example.data.model.Fix
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.data.model.UnifiedMemoryItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/** Exercise the same SDK mapper used by DocumentSnapshot.toObject, without a live Firebase app. */
class FirestoreModelMappingTest {
  private val createdAt = Date(1_700_000_000_123L)
  private val updatedAt = Date(1_700_000_001_456L)

  @Test
  fun reminderStoredPayloadRetainsIdCompletionAndCategory() {
    val expected = Reminder(
      id = "rem_1", userId = "alice", title = "Milk", triggerActivity = "Shopping",
      category = "Shopping", isCompleted = true, keywords = listOf("milk"),
      createdAt = createdAt, updatedAt = updatedAt
    )
    assertEquals(expected, decode(expected.toMap(), Reminder::class.java))
  }

  @Test
  fun preferenceStoredPayloadRetainsIdAndPhoto() {
    val expected = Preference(
      id = "pref_1", userId = "alice", title = "Coffee", description = "Oat milk",
      photoUrl = "file:///example/photo.jpg", createdAt = createdAt, updatedAt = updatedAt
    )
    assertEquals(expected, decode(expected.toMap(), Preference::class.java))
  }

  @Test
  fun fixStoredPayloadRetainsIdVerificationAndCategory() {
    val expected = Fix(
      id = "fix_1", userId = "alice", problemSummary = "Stain", whatHappened = "Oil",
      bestFix = "Baking soda", category = "Home", isVerifiedWorking = true,
      createdAt = createdAt, updatedAt = updatedAt
    )
    assertEquals(expected, decode(expected.toMap(), Fix::class.java))
  }

  @Test
  fun unifiedStoredPayloadRetainsBothBooleanFields() {
    val expected = UnifiedMemoryItem(
      id = "mem_1", userId = "alice", type = "reminder", title = "Call Mom",
      isCompleted = true, isVerifiedWorking = true, category = "Personal",
      createdAt = createdAt, updatedAt = updatedAt
    )
    assertEquals(expected, decode(expected.toMap(), UnifiedMemoryItem::class.java))
  }

  private fun <T> decode(map: Map<String, Any?>, type: Class<T>): T {
    // Firestore snapshots supply Timestamp values, even though the Android models expose Date.
    val firestoreData = map.mapValues { (_, value) -> if (value is Date) Timestamp(value) else value }
    return CustomClassMapper.convertToCustomClass(firestoreData, type, null)
  }
}
