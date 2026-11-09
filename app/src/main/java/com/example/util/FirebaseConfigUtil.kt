package com.example.util

import android.content.Context

/** Keep live Firebase identifiers in local configuration without a compile-time resource dependency. */
internal fun Context.configuredFirestoreDatabaseId(): String {
  val resourceId = resources.getIdentifier("firestore_database_id", "string", packageName)
  check(resourceId != 0) { "Firebase configuration missing: firestore_database_id" }
  return getString(resourceId).also {
    check(it.isNotBlank()) { "Firestore database ID is empty" }
  }
}
