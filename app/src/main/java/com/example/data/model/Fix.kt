package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class Fix(
  @DocumentId val id: String = "",
  val userId: String = "",
  val problemSummary: String = "",
  val whatHappened: String = "",
  val bestFix: String = "",
  val steps: List<String> = emptyList(),
  val toolsNeeded: List<String> = emptyList(),
  val alternativeFix: String = "",
  val isVerifiedWorking: Boolean = false,
  val category: String = "Personal",
  @ServerTimestamp val createdAt: Date? = null,
  @ServerTimestamp val updatedAt: Date? = null
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "problemSummary" to problemSummary,
    "whatHappened" to whatHappened,
    "bestFix" to bestFix,
    "steps" to steps,
    "toolsNeeded" to toolsNeeded,
    "alternativeFix" to alternativeFix,
    "isVerifiedWorking" to isVerifiedWorking,
    "category" to category,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
  ).filterValues { it != null }
}
