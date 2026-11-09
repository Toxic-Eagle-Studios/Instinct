package com.example

import com.example.data.local.UnifiedMemoryDao
import com.example.data.local.UnifiedMemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUnifiedMemoryDao : UnifiedMemoryDao {
  private val list = mutableListOf<UnifiedMemoryEntity>()
  private val flow = MutableStateFlow<List<UnifiedMemoryEntity>>(emptyList())

  private fun emit() {
    flow.value = list.toList().sortedByDescending { it.updatedAt }
  }

  override fun observeMemories(userId: String): Flow<List<UnifiedMemoryEntity>> =
    flow.map { it.filter { item -> item.userId == userId && !item.pendingDelete } }

  override fun observeMemoriesByType(userId: String, type: String): Flow<List<UnifiedMemoryEntity>> =
    flow.map { it.filter { item -> item.userId == userId && !item.pendingDelete && item.type.equals(type, ignoreCase = true) } }

  override fun observeMemoriesByCategory(userId: String, category: String): Flow<List<UnifiedMemoryEntity>> =
    flow.map { it.filter { item -> item.userId == userId && !item.pendingDelete && item.category.equals(category, ignoreCase = true) } }

  override fun observeCategories(userId: String): Flow<List<String>> =
    flow.map {
      it.filter { item -> item.userId == userId && !item.pendingDelete && item.category.isNotBlank() }
        .map { item -> item.category }
        .distinct()
        .sorted()
    }

  override fun searchMemories(userId: String, query: String): Flow<List<UnifiedMemoryEntity>> =
    flow.map {
      it.filter { item ->
        item.userId == userId && !item.pendingDelete && (
          item.title.contains(query, ignoreCase = true) ||
          item.content.contains(query, ignoreCase = true) ||
          item.tags.contains(query, ignoreCase = true) ||
          item.category.contains(query, ignoreCase = true)
        )
      }
    }

  override suspend fun getAllMemories(userId: String): List<UnifiedMemoryEntity> =
    list.filter { it.userId == userId && !it.pendingDelete }

  override suspend fun getMemoryById(id: String, userId: String): UnifiedMemoryEntity? =
    list.find { it.id == id && it.userId == userId && !it.pendingDelete }

  override suspend fun getMemoryByIdAnyUser(id: String): UnifiedMemoryEntity? =
    list.find { it.id == id }

  override suspend fun getDirtyMemories(userId: String): List<UnifiedMemoryEntity> =
    list.filter { it.userId == userId && it.isDirty && !it.pendingDelete }.sortedBy { it.updatedAt }

  override suspend fun getPendingDeleteMemories(userId: String): List<UnifiedMemoryEntity> =
    list.filter { it.userId == userId && it.pendingDelete }.sortedBy { it.updatedAt }

  override suspend fun markMemorySynced(id: String, userId: String) {
    val index = list.indexOfFirst { it.id == id && it.userId == userId }
    if (index >= 0) {
      list[index] = list[index].copy(isDirty = false)
      emit()
    }
  }

  override suspend fun markMemoryPendingDelete(id: String, userId: String, updatedAt: Long) {
    val index = list.indexOfFirst { it.id == id && it.userId == userId }
    if (index >= 0) {
      list[index] = list[index].copy(pendingDelete = true, isDirty = true, updatedAt = updatedAt)
      emit()
    }
  }

  override suspend fun insertMemory(memory: UnifiedMemoryEntity) {
    list.removeAll { it.id == memory.id }
    list.add(memory)
    emit()
  }

  override suspend fun insertMemories(memories: List<UnifiedMemoryEntity>) {
    memories.forEach { memory ->
      list.removeAll { it.id == memory.id }
      list.add(memory)
    }
    emit()
  }

  override suspend fun updateMemory(memory: UnifiedMemoryEntity) {
    val index = list.indexOfFirst { it.id == memory.id && it.userId == memory.userId }
    if (index >= 0) {
      list[index] = memory
      emit()
    }
  }

  override suspend fun updateMemoryCategory(id: String, userId: String, category: String, updatedAt: Long) {
    val index = list.indexOfFirst { it.id == id && it.userId == userId }
    if (index >= 0) {
      list[index] = list[index].copy(category = category, updatedAt = updatedAt, isDirty = true)
      emit()
    }
  }

  override suspend fun deleteMemoryById(id: String, userId: String) {
    list.removeAll { it.id == id && it.userId == userId }
    emit()
  }

  override suspend fun deleteAllByUserId(userId: String) {
    list.removeAll { it.userId == userId }
    emit()
  }

  override suspend fun getMemoryCount(userId: String): Int =
    list.count { it.userId == userId && !it.pendingDelete }

  override suspend fun deleteStaleMemories(userId: String, validIds: List<String>) {
    list.removeAll { it.userId == userId && !it.isDirty && !it.pendingDelete && it.id !in validIds }
    emit()
  }
}
