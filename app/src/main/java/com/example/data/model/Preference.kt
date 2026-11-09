package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class Preference(
  val id: String = "",
  val userId: String = "",
  val title: String = "",
  val category: String = "Food & Drinks",
  val description: String = "",
  val details: String = "",
  val tags: List<String> = emptyList(),
  val photoUrl: String? = null,
  @ServerTimestamp val createdAt: Date? = null,
  @ServerTimestamp val updatedAt: Date? = null
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "title" to title,
    "category" to category,
    "description" to description,
    "details" to details,
    "tags" to tags,
    "photoUrl" to photoUrl,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
  ).filterValues { it != null }
}
