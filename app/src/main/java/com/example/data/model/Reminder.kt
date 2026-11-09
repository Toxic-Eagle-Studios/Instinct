package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class Reminder(
  val id: String = "",
  val userId: String = "",
  val title: String = "",
  val triggerActivity: String = "",
  val relatedPeople: List<String> = emptyList(),
  val relatedLocations: List<String> = emptyList(),
  val keywords: List<String> = emptyList(),
  val recurrence: String = "every_time", // every_time, next_time_only, until_completed
  @get:PropertyName("isCompleted") val isCompleted: Boolean = false,
  val category: String = "Personal",
  @ServerTimestamp val createdAt: Date? = null,
  @ServerTimestamp val updatedAt: Date? = null
) {
  val dueAt: String
    get() = keywords.firstOrNull { it.startsWith("due:", ignoreCase = true) }
      ?.substringAfter(":")
      ?.trim()
      .orEmpty()

  fun toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "title" to title,
    "triggerActivity" to triggerActivity,
    "relatedPeople" to relatedPeople,
    "relatedLocations" to relatedLocations,
    "keywords" to keywords,
    "recurrence" to recurrence,
    "isCompleted" to isCompleted,
    "category" to category,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
  ).filterValues { it != null }
}
