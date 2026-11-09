package com.example

import com.example.data.ai.InstinctAiService
import com.example.data.model.Fix
import com.example.data.model.IntentType
import com.example.data.model.Preference
import com.example.data.model.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.UUID

class InstinctLogicTest {

  private val aiService = InstinctAiService()
  private val testUserId = "test_user_42"

  @Test
  fun testOne_ContextualReminderTrigger() = runBlocking {
    val savedReminders = listOf(
      Reminder(
        id = UUID.randomUUID().toString(),
        userId = testUserId,
        title = "Ask Dad about his truck",
        triggerActivity = "Calling Dad",
        relatedPeople = listOf("Dad", "father"),
        keywords = listOf("dad", "father", "truck"),
        recurrence = "every_time",
        isCompleted = false,
        createdAt = Date(),
        updatedAt = Date()
      )
    )

    // User says they are about to call their father
    val result = aiService.processUniversalInput(
      userInput = "I'm calling my father",
      savedReminders = savedReminders,
      savedPreferences = emptyList(),
      savedFixes = emptyList(),
      userId = testUserId
    )

    assertEquals(IntentType.TRIGGER_CONTEXT, result.intent)
    assertTrue("Should match saved reminder for Dad", result.matchedReminders.isNotEmpty())
    assertEquals("Ask Dad about his truck", result.matchedReminders.first().title)
  }

  @Test
  fun testTwo_PreferenceExtraction() = runBlocking {
    val result = aiService.processUniversalInput(
      userInput = "Remember my usual coffee order: large iced coffee with two creams",
      savedReminders = emptyList(),
      savedPreferences = emptyList(),
      savedFixes = emptyList(),
      userId = testUserId
    )

    assertEquals(IntentType.SAVE_PREFERENCE, result.intent)
    assertNotNull(result.preferenceCandidate)
    assertEquals("Food & Drinks", result.preferenceCandidate?.category)
  }

  @Test
  fun testThree_ProblemSolvingUndoIrl() = runBlocking {
    val result = aiService.processUniversalInput(
      userInput = "I put too much salt in my soup",
      savedReminders = emptyList(),
      savedPreferences = emptyList(),
      savedFixes = emptyList(),
      userId = testUserId
    )

    assertEquals(IntentType.REPORT_PROBLEM, result.intent)
    assertNotNull(result.fixCandidate)
    assertTrue(result.fixCandidate!!.bestFix.isNotBlank())
    assertTrue(result.fixCandidate!!.steps.isNotEmpty())
  }

  @Test
  fun testFour_CrossFeatureIntelligenceSharedBrain() = runBlocking {
    val savedReminders = listOf(
      Reminder(
        id = "rem_laundry",
        userId = testUserId,
        title = "Wash my work uniform",
        triggerActivity = "doing laundry",
        keywords = listOf("laundry", "wash clothes"),
        recurrence = "every_time",
        isCompleted = false,
        createdAt = Date(),
        updatedAt = Date()
      )
    )

    val savedPreferences = listOf(
      Preference(
        id = "pref_laundry",
        userId = testUserId,
        title = "Laundry detergent settings",
        category = "Home",
        description = "Cold water wash, delicate cycle, hang dry shirts",
        tags = listOf("laundry", "washing"),
        createdAt = Date(),
        updatedAt = Date()
      )
    )

    val result = aiService.processUniversalInput(
      userInput = "I'm washing clothes and doing laundry",
      savedReminders = savedReminders,
      savedPreferences = savedPreferences,
      savedFixes = emptyList(),
      userId = testUserId
    )

    assertEquals(IntentType.TRIGGER_CONTEXT, result.intent)
    assertTrue(result.matchedReminders.any { it.id == "rem_laundry" })
    assertTrue(result.matchedPreferences.any { it.id == "pref_laundry" })
  }
}
