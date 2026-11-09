package com.example

import com.example.data.local.UnifiedMemoryDao
import com.example.data.local.UnifiedMemoryEntity
import com.example.data.model.Fix
import com.example.data.model.MemoryEntry
import com.example.data.model.Preference
import com.example.data.model.Reminder
import com.example.data.model.TypedMemory
import com.example.data.model.UnifiedMemoryItem
import com.example.data.repository.DefaultMemoryRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testUnifiedMemoryItem_fromReminder_and_roundtrip() {
    val reminder = Reminder(
      id = "rem_101",
      userId = "user_abc",
      title = "Buy fresh cilantro",
      triggerActivity = "Going to supermarket",
      relatedPeople = listOf("Chef"),
      relatedLocations = listOf("Supermarket"),
      keywords = listOf("cilantro", "grocery"),
      recurrence = "every_time",
      isCompleted = false,
      createdAt = Date(),
      updatedAt = Date()
    )

    val memoryItem = UnifiedMemoryItem.fromReminder(reminder)
    assertEquals("rem_101", memoryItem.id)
    assertEquals("user_abc", memoryItem.userId)
    assertEquals(UnifiedMemoryItem.TYPE_REMINDER, memoryItem.type)
    assertTrue(memoryItem.isReminder)
    assertFalse(memoryItem.isPreference)
    assertFalse(memoryItem.isFix)
    assertEquals("Going to supermarket", memoryItem.triggerActivity)
    assertEquals("WHILE YOU'RE AT IT", memoryItem.displayBadge)

    // Roundtrip back to reminder
    val convertedBack = memoryItem.toReminder()
    assertEquals(reminder.id, convertedBack.id)
    assertEquals(reminder.title, convertedBack.title)
    assertEquals(reminder.triggerActivity, convertedBack.triggerActivity)

    // Polymorphic match
    val typed = TypedMemory.from(memoryItem)
    assertTrue(typed is TypedMemory.ReminderMemory)
  }

  @Test
  fun testUnifiedMemoryItem_fromPreference_and_roundtrip() {
    val preference = Preference(
      id = "pref_202",
      userId = "user_abc",
      title = "Oat Milk Cortado",
      category = "Coffee & Drinks",
      description = "Double shot espresso with steamed oat milk",
      details = "No sugar, extra hot",
      tags = listOf("coffee", "oat milk"),
      createdAt = Date(),
      updatedAt = Date()
    )

    val memoryItem = UnifiedMemoryItem.fromPreference(preference)
    assertEquals("pref_202", memoryItem.id)
    assertEquals(UnifiedMemoryItem.TYPE_PREFERENCE, memoryItem.type)
    assertTrue(memoryItem.isPreference)
    assertEquals("Coffee & Drinks", memoryItem.category)
    assertEquals("EXACTLY", memoryItem.displayBadge)

    // Roundtrip back to preference
    val convertedBack = memoryItem.toPreference()
    assertEquals(preference.id, convertedBack.id)
    assertEquals(preference.title, convertedBack.title)
    assertEquals(preference.category, convertedBack.category)

    // Polymorphic match
    val typed = TypedMemory.from(memoryItem)
    assertTrue(typed is TypedMemory.PreferenceMemory)
  }

  @Test
  fun testUnifiedMemoryItem_fromFix_and_roundtrip() {
    val fix = Fix(
      id = "fix_303",
      userId = "user_abc",
      problemSummary = "Spilled grease on white cotton shirt",
      whatHappened = "Cooking oil soaked deep into cotton threads",
      bestFix = "Dust with cornstarch or baking soda, then scrub with blue dish soap",
      steps = listOf("Blot excess oil", "Sprinkle cornstarch", "Apply dish soap"),
      toolsNeeded = listOf("Cornstarch", "Dish soap", "Toothbrush"),
      alternativeFix = "Use heavy duty stain remover spray",
      isVerifiedWorking = true,
      createdAt = Date(),
      updatedAt = Date()
    )

    val memoryItem = UnifiedMemoryItem.fromFix(fix)
    assertEquals("fix_303", memoryItem.id)
    assertEquals(UnifiedMemoryItem.TYPE_FIX, memoryItem.type)
    assertTrue(memoryItem.isFix)
    assertTrue(memoryItem.isVerifiedWorking)
    assertEquals("UNDO IRL", memoryItem.displayBadge)

    // Roundtrip back to fix
    val convertedBack = memoryItem.toFix()
    assertEquals(fix.id, convertedBack.id)
    assertEquals(fix.problemSummary, convertedBack.problemSummary)
    assertEquals(fix.bestFix, convertedBack.bestFix)
    assertTrue(convertedBack.isVerifiedWorking)

    // Polymorphic match
    val typed = TypedMemory.from(memoryItem)
    assertTrue(typed is TypedMemory.FixMemory)
  }

  @Test
  fun testUnifiedMemoryItem_toMap_serialization() {
    val item = UnifiedMemoryItem(
      id = "test_map_id",
      userId = "user_123",
      type = UnifiedMemoryItem.TYPE_REMINDER,
      title = "Test Reminder",
      content = "Test content",
      tags = listOf("tag1", "tag2"),
      createdAt = Date(),
      updatedAt = Date()
    )

    val map = item.toMap()
    assertEquals("test_map_id", map["id"])
    assertEquals("user_123", map["userId"])
    assertEquals("reminder", map["type"])
    assertEquals("Test Reminder", map["title"])
    assertNotNull(map["createdAt"])
  }

  @Test
  fun testMemoryEntry_sealedClass_subclasses_and_polymorphism() {
    val date = Date(1700000000000L)

    val reminderEntry: MemoryEntry = MemoryEntry.Reminder(
      id = "entry_rem_1",
      userId = "user_eagle",
      timestamp = date,
      tags = listOf("shopping", "hardware"),
      title = "Pick up drywall anchors",
      triggerActivity = "Going to Home Depot",
      relatedPeople = listOf("Contractor"),
      relatedLocations = listOf("Home Depot"),
      recurrence = "next_time_only",
      isCompleted = false
    )

    val preferenceEntry: MemoryEntry = MemoryEntry.Preference(
      id = "entry_pref_1",
      userId = "user_eagle",
      timestamp = date,
      tags = listOf("haircut", "grooming"),
      title = "Low Skin Fade",
      category = "Style & Grooming",
      description = "#1.5 guard on top textured with matte clay",
      details = "Keep the neckline square",
      photoUri = "content://media/external/images/123"
    )

    val fixEntry: MemoryEntry = MemoryEntry.FixHistory(
      id = "entry_fix_1",
      userId = "user_eagle",
      timestamp = date,
      tags = listOf("plumbing", "sink"),
      problemSummary = "P-trap leaking under kitchen sink",
      whatHappened = "Slip joint nut was cross-threaded and cracked",
      bestFix = "Replace with 1-1/2 PVC slip-joint kit and hand-tighten plus quarter turn",
      steps = listOf("Place bucket", "Loosen nuts", "Install replacement", "Test flow"),
      toolsNeeded = listOf("Bucket", "Slip joint kit", "Channel locks"),
      alternativeFix = "Use silicone tape as temporary stopgap",
      isVerifiedWorking = true
    )

    // Verify common metadata base on MemoryEntry
    val entries: List<MemoryEntry> = listOf(reminderEntry, preferenceEntry, fixEntry)
    assertEquals(3, entries.size)

    entries.forEach { entry ->
      assertEquals("user_eagle", entry.userId)
      assertEquals(date, entry.timestamp)
      assertNotNull(entry.id)
      assertNotNull(entry.type)
      assertTrue(entry.tags.isNotEmpty())
    }

    // Verify feature-specific fields and pattern matching
    entries.forEach { entry ->
      when (entry) {
        is MemoryEntry.Reminder -> {
          assertEquals("entry_rem_1", entry.id)
          assertEquals("Pick up drywall anchors", entry.title)
          assertEquals("Going to Home Depot", entry.triggerActivity)
          assertEquals(listOf("Contractor"), entry.relatedPeople)
          assertEquals("next_time_only", entry.recurrence)
          assertFalse(entry.isCompleted)
          assertEquals("WHILE YOU'RE AT IT", entry.displayBadge)
        }
        is MemoryEntry.Preference -> {
          assertEquals("entry_pref_1", entry.id)
          assertEquals("Low Skin Fade", entry.title)
          assertEquals("Style & Grooming", entry.category)
          assertEquals("content://media/external/images/123", entry.photoUri)
          assertEquals("content://media/external/images/123", entry.photoUrl)
          assertEquals("EXACTLY", entry.displayBadge)
        }
        is MemoryEntry.FixHistory -> {
          assertEquals("entry_fix_1", entry.id)
          assertEquals("P-trap leaking under kitchen sink", entry.problemSummary)
          assertEquals("Replace with 1-1/2 PVC slip-joint kit and hand-tighten plus quarter turn", entry.bestFix)
          assertEquals(4, entry.steps.size)
          assertTrue(entry.isVerifiedWorking)
          assertEquals("UNDO IRL", entry.displayBadge)
        }
      }
    }

    // Verify conversions to UnifiedMemoryItem and back
    val unifiedRem = reminderEntry.toUnifiedMemoryItem()
    val restoredRem = MemoryEntry.fromUnifiedMemoryItem(unifiedRem)
    assertTrue(restoredRem is MemoryEntry.Reminder)
    assertEquals((reminderEntry as MemoryEntry.Reminder).triggerActivity, (restoredRem as MemoryEntry.Reminder).triggerActivity)

    val unifiedPref = preferenceEntry.toUnifiedMemoryItem()
    val restoredPref = MemoryEntry.fromUnifiedMemoryItem(unifiedPref)
    assertTrue(restoredPref is MemoryEntry.Preference)
    assertEquals((preferenceEntry as MemoryEntry.Preference).details, (restoredPref as MemoryEntry.Preference).details)

    val unifiedFix = fixEntry.toUnifiedMemoryItem()
    val restoredFix = MemoryEntry.fromUnifiedMemoryItem(unifiedFix)
    assertTrue(restoredFix is MemoryEntry.FixHistory)
    assertEquals((fixEntry as MemoryEntry.FixHistory).problemSummary, (restoredFix as MemoryEntry.FixHistory).problemSummary)
    assertTrue((restoredFix as MemoryEntry.FixHistory).isVerifiedWorking)

    // Verify toMap / fromMap roundtrip for each subclass
    val remFromMap = MemoryEntry.fromMap(reminderEntry.toMap())
    assertTrue(remFromMap is MemoryEntry.Reminder)
    assertEquals(reminderEntry.primaryTitle, remFromMap.primaryTitle)

    val prefFromMap = MemoryEntry.fromMap(preferenceEntry.toMap())
    assertTrue(prefFromMap is MemoryEntry.Preference)
    assertEquals(preferenceEntry.primaryTitle, prefFromMap.primaryTitle)

    val fixFromMap = MemoryEntry.fromMap(fixEntry.toMap())
    assertTrue(fixFromMap is MemoryEntry.FixHistory)
    assertEquals(fixEntry.primaryTitle, fixFromMap.primaryTitle)

    // Verify matchesQuery across shared and subclass-specific fields
    assertTrue(reminderEntry.matchesQuery("Home Depot"))
    assertTrue(reminderEntry.matchesQuery("drywall"))
    assertFalse(reminderEntry.matchesQuery("cortado"))

    assertTrue(preferenceEntry.matchesQuery("neckline square"))
    assertTrue(preferenceEntry.matchesQuery("grooming"))

    assertTrue(fixEntry.matchesQuery("Channel locks"))
    assertTrue(fixEntry.matchesQuery("slip-joint"))

    // Verify polymorphic copy helpers
    val retagged = reminderEntry.withTags(listOf("urgent", "errand"))
    assertEquals(listOf("urgent", "errand"), retagged.tags)
    val newDate = Date(1800000000000L)
    val retimed = fixEntry.withUpdatedTimestamp(newDate)
    assertEquals(newDate, retimed.timestamp)
  }

  @Test
  fun testMemoryRepository_singleSourceOfTruth_optimisticWritesAndFlow() = runBlocking {
    val fakeDao = FakeUnifiedMemoryDao()
    val repository: MemoryRepository = DefaultMemoryRepository(
      localDao = fakeDao,
      ioDispatcher = Dispatchers.Unconfined
    )

    // Initially empty
    val initialMemories = repository.observeMemories("user_123").first()
    assertEquals(0, initialMemories.size)

    // Save reminder entry
    val reminder = MemoryEntry.Reminder(
      id = "repo_rem_1",
      userId = "user_123",
      title = "Change furnace filter",
      triggerActivity = "Heading downstairs",
      tags = listOf("hvac", "maintenance")
    )
    val saveResult = repository.saveMemoryEntry(reminder)
    assertTrue(saveResult.isSuccess)

    // Single source of truth emission: UI receives updated list immediately
    val currentMemories = repository.observeMemories("user_123").first()
    assertEquals(1, currentMemories.size)
    assertEquals("repo_rem_1", currentMemories.first().id)
    assertTrue(currentMemories.first() is MemoryEntry.Reminder)
    assertEquals("Change furnace filter", (currentMemories.first() as MemoryEntry.Reminder).title)

    // Save preference entry
    val preference = MemoryEntry.Preference(
      id = "repo_pref_1",
      userId = "user_123",
      title = "Espresso Order",
      description = "Double shot oat milk flat white, no sugar",
      category = "Coffee",
      tags = listOf("coffee", "morning")
    )
    repository.saveMemoryEntry(preference)

    // Verify both items emitted in unified flow
    val updatedMemories = repository.observeMemories("user_123").first()
    assertEquals(2, updatedMemories.size)
  }

  @Test
  fun testMemoryRepository_polymorphicObservers_filtering() = runBlocking {
    val fakeDao = FakeUnifiedMemoryDao()
    val repository: MemoryRepository = DefaultMemoryRepository(
      localDao = fakeDao,
      ioDispatcher = Dispatchers.Unconfined
    )

    val userId = "user_poly"
    val reminder = MemoryEntry.Reminder(id = "r1", userId = userId, title = "Call mechanic", triggerActivity = "Leaving work")
    val pref = MemoryEntry.Preference(id = "p1", userId = userId, title = "Steak", description = "Medium rare", category = "Food")
    val fix = MemoryEntry.FixHistory(
      id = "f1",
      userId = userId,
      problemSummary = "Running toilet",
      bestFix = "Replace flapper valve",
      steps = listOf("Turn off water", "Unhook chain", "Install new flapper"),
      isVerifiedWorking = true
    )

    repository.saveMemoryEntries(listOf(reminder, pref, fix))

    // Test filtered observers
    val reminders = repository.observeReminders(userId).first()
    assertEquals(1, reminders.size)
    assertEquals("r1", reminders[0].id)
    assertEquals("Call mechanic", reminders[0].title)

    val preferences = repository.observePreferences(userId).first()
    assertEquals(1, preferences.size)
    assertEquals("p1", preferences[0].id)
    assertEquals("Medium rare", preferences[0].description)

    val fixes = repository.observeFixHistories(userId).first()
    assertEquals(1, fixes.size)
    assertEquals("f1", fixes[0].id)
    assertEquals("Running toilet", fixes[0].problemSummary)
    assertTrue(fixes[0].isVerifiedWorking)
  }

  @Test
  fun testMemoryRepository_mutationsAndDeletions() = runBlocking {
    val fakeDao = FakeUnifiedMemoryDao()
    val repository: MemoryRepository = DefaultMemoryRepository(
      localDao = fakeDao,
      ioDispatcher = Dispatchers.Unconfined
    )

    val userId = "user_ops"
    val reminder = MemoryEntry.Reminder(id = "op_rem", userId = userId, title = "Pack gym shoes", isCompleted = false)
    val fix = MemoryEntry.FixHistory(id = "op_fix", userId = userId, problemSummary = "Squeaky door", isVerifiedWorking = false)

    repository.saveMemoryEntry(reminder)
    repository.saveMemoryEntry(fix)

    // Toggle reminder
    repository.toggleReminderStatus(userId, "op_rem", true)
    val updatedRem = repository.getMemoryById(userId, "op_rem") as? MemoryEntry.Reminder
    assertNotNull(updatedRem)
    assertTrue(updatedRem!!.isCompleted)

    // Verify fix
    repository.verifyFixWorking(userId, "op_fix", true)
    val updatedFix = repository.getMemoryById(userId, "op_fix") as? MemoryEntry.FixHistory
    assertNotNull(updatedFix)
    assertTrue(updatedFix!!.isVerifiedWorking)

    // Delete single memory
    repository.deleteMemoryEntry(userId, "op_rem")
    val remaining = repository.observeMemories(userId).first()
    assertEquals(1, remaining.size)
    assertEquals("op_fix", remaining[0].id)

    // Clear all
    repository.clearAllMemories(userId)
    val cleared = repository.observeMemories(userId).first()
    assertEquals(0, cleared.size)
  }

  @Test
  fun testRoomEntity_and_MemoryTypeConverters() {
    val converters = com.example.data.local.MemoryTypeConverters()
    val tags = listOf("coffee", "espresso", "morning")
    val serializedTags = converters.stringListToString(tags)
    assertEquals(tags, converters.fromStringList(serializedTags))

    val now = Date(1710000000000L)
    val ts = converters.dateToTimestamp(now)
    assertEquals(now, converters.fromTimestamp(ts))

    val fixEntry = MemoryEntry.FixHistory(
      id = "room_fix_1",
      userId = "user_room",
      timestamp = now,
      tags = listOf("zipper", "jacket"),
      problemSummary = "Stuck jacket zipper",
      whatHappened = "Fabric caught in slider",
      bestFix = "Rub graphite pencil on teeth",
      steps = listOf("Unpinch fabric", "Apply graphite", "Slide gently"),
      toolsNeeded = listOf("Pencil"),
      alternativeFix = "Use bar soap",
      isVerifiedWorking = true
    )

    val entity = com.example.data.local.MemoryEntryEntity.fromMemoryEntry(fixEntry)
    assertEquals("room_fix_1", entity.id)
    assertEquals("fix", entity.type)
    assertTrue(entity.isVerifiedWorking)

    val restored = entity.toMemoryEntry() as MemoryEntry.FixHistory
    assertEquals(fixEntry.id, restored.id)
    assertEquals(fixEntry.problemSummary, restored.problemSummary)
    assertEquals(fixEntry.steps, restored.steps)
    assertEquals(fixEntry.toolsNeeded, restored.toolsNeeded)
    assertTrue(restored.isVerifiedWorking)
  }

  @Test
  fun testMemoryEntry_relativeTimeFormatting() {
    val refTime = 1_700_000_000_000L

    // Less than 1 minute -> "Just now"
    val justNowEntry = MemoryEntry.Reminder(
      timestamp = Date(refTime - 15_000L),
      title = "Check oven"
    )
    assertEquals("Just now", justNowEntry.formatRelativeTime(refTime))

    // 1 minute ago & 2 minutes ago
    assertEquals("1 minute ago", MemoryEntry.formatRelativeTimestamp(refTime - 60_000L, refTime))
    val twoMinsEntry = MemoryEntry.Preference(
      timestamp = Date(refTime - 2 * 60_000L),
      title = "Iced Americano"
    )
    assertEquals("2 minutes ago", twoMinsEntry.formatRelativeTime(refTime))

    // Hours, days, weeks, months, years
    assertEquals("1 hour ago", MemoryEntry.formatRelativeTimestamp(refTime - 3_600_000L, refTime))
    assertEquals("3 hours ago", MemoryEntry.formatRelativeTimestamp(refTime - 3 * 3_600_000L, refTime))
    assertEquals("1 day ago", MemoryEntry.formatRelativeTimestamp(refTime - 86_400_000L, refTime))
    assertEquals("4 days ago", MemoryEntry.formatRelativeTimestamp(refTime - 4 * 86_400_000L, refTime))
    assertEquals("2 weeks ago", MemoryEntry.formatRelativeTimestamp(refTime - 14 * 86_400_000L, refTime))
    assertEquals("3 months ago", MemoryEntry.formatRelativeTimestamp(refTime - 95L * 86_400_000L, refTime))
    assertEquals("1 year ago", MemoryEntry.formatRelativeTimestamp(refTime - 400L * 86_400_000L, refTime))
  }

  @Test
  fun testMemoryEntry_categoryAssignmentAndFiltering() = runBlocking {
    val fakeDao = FakeUnifiedMemoryDao()
    val repo: MemoryRepository = DefaultMemoryRepository(
      context = null,
      localDao = fakeDao,
      firestore = null,
      auth = null,
      ioDispatcher = Dispatchers.Unconfined
    )

    val workReminder = MemoryEntry.Reminder(
      id = "rem_work_1",
      userId = "u_cat",
      category = "Work",
      title = "Send Q4 sprint slides",
      triggerActivity = "Opening laptop"
    )
    val shoppingPref = MemoryEntry.Preference(
      id = "pref_shop_1",
      userId = "u_cat",
      category = "Shopping",
      title = "Organic Almond Butter",
      description = "Unsalted crunchy jar"
    )
    val personalFix = MemoryEntry.FixHistory(
      id = "fix_pers_1",
      userId = "u_cat",
      category = "Personal",
      problemSummary = "Stiff door lock",
      bestFix = "Apply dry graphite lubricant"
    )

    repo.saveMemoryEntries(listOf(workReminder, shoppingPref, personalFix))

    val workItems = repo.observeMemoriesByCategory("u_cat", "Work").first()
    assertEquals(1, workItems.size)
    assertEquals("Send Q4 sprint slides", workItems.first().primaryTitle)
    assertEquals("Work", workItems.first().category)

    val shoppingItems = repo.observeMemoriesByCategory("u_cat", "Shopping").first()
    assertEquals(1, shoppingItems.size)
    assertEquals("Organic Almond Butter", shoppingItems.first().primaryTitle)

    // Reassign personalFix to "Home"
    repo.updateMemoryCategory("u_cat", "fix_pers_1", "Home")
    val homeItems = repo.observeMemoriesByCategory("u_cat", "Home").first()
    assertEquals(1, homeItems.size)
    assertEquals("Stiff door lock", homeItems.first().primaryTitle)
    assertEquals("Home", homeItems.first().category)
  }

  @Test
  fun testVoiceToText_appendTranscriptAndInferCategoryHints() {
    // 1. Empty existing text capitalizes spoken transcript
    val firstPass = com.example.ui.components.appendSpokenTranscript("", "pick up espresso beans")
    assertEquals("Pick up espresso beans", firstPass)

    // 2. Appending additional voice dictation preserves existing text with clean spacing
    val secondPass = com.example.ui.components.appendSpokenTranscript(firstPass, "and oat milk")
    assertEquals("Pick up espresso beans and oat milk", secondPass)

    // 3. Spoken category & memory type hint parsing
    val shoppingReminderHint = com.example.ui.components.inferVoiceMemoryHint(
      "Shopping: remind me to buy AA batteries"
    )
    assertEquals("Shopping", shoppingReminderHint.suggestedCategory)
    assertEquals(MemoryEntry.TYPE_REMINDER, shoppingReminderHint.suggestedType)
    assertEquals("Buy AA batteries", shoppingReminderHint.cleanedText)

    val workFixHint = com.example.ui.components.inferVoiceMemoryHint(
      "Work category: Fix: restart VPN daemon before connecting"
    )
    assertEquals("Work", workFixHint.suggestedCategory)
    assertEquals(MemoryEntry.TYPE_FIX_HISTORY, workFixHint.suggestedType)
    assertEquals("Restart VPN daemon before connecting", workFixHint.cleanedText)
  }

  @Test
  fun testWhileYoureAtIt_crossMemorySessionBundlingAndAutoTagging() {
    val aiService = com.example.data.ai.InstinctAiService()

    val savedReminders = listOf(
      com.example.data.model.Reminder(
        id = "rem_walmart_1",
        userId = "u1",
        title = "Buy AA batteries",
        triggerActivity = "Heading to Walmart",
        category = "Shopping",
        keywords = listOf("walmart", "batteries")
      ),
      com.example.data.model.Reminder(
        id = "rem_work_1",
        userId = "u1",
        title = "Review pull request",
        triggerActivity = "At desk",
        category = "Work"
      )
    )
    val savedPreferences = listOf(
      com.example.data.model.Preference(
        id = "pref_shop_1",
        userId = "u1",
        title = "Oat Milk Brand",
        category = "Shopping",
        description = "Unsweetened Barista Edition"
      )
    )
    val savedFixes = listOf(
      com.example.data.model.Fix(
        id = "fix_home_1",
        userId = "u1",
        problemSummary = "Grease stain on shirt",
        bestFix = "Blot and apply dish soap",
        toolsNeeded = listOf("Liquid dish soap", "Paper towels")
      )
    )

    val session = aiService.buildInstantContextSession(
      contextQuery = "Heading to Walmart grocery store",
      savedReminders = savedReminders,
      savedPreferences = savedPreferences,
      savedFixes = savedFixes
    )

    assertEquals("Shopping", session.contextCategory)
    assertEquals(1, session.matchedReminders.size)
    assertEquals("Buy AA batteries", session.matchedReminders.first().title)
    assertEquals(1, session.matchedPreferences.size)
    assertEquals("Oat Milk Brand", session.matchedPreferences.first().title)
    assertEquals(1, session.matchedFixes.size)
    assertTrue(session.aiSuggestedTasks.isNotEmpty())
    assertTrue(session.totalChecklistCount >= 2)
    assertEquals(0, session.completedChecklistCount)

    // Verify smart auto-categorization & tag generation
    assertEquals("Shopping", aiService.inferCategoryLocally("Buy espresso beans at Costco", ""))
    assertEquals("Work", aiService.inferCategoryLocally("Send Q4 client sprint slides", "Office meeting"))
    val inferredTags = aiService.inferTagsLocally("Fix leaky bathroom sink", "Need wrench", "Home")
    assertTrue(inferredTags.contains("home"))
  }

  @Test
  fun testInstinctCoreFlows_saveAndContextAndRetrieveAndRecovery() = runBlocking {
    val aiService = com.example.data.ai.InstinctAiService()

    // Flow A: Save Reminder ("Remind me to ask Dad about Friday")
    val flowAResult = aiService.processUniversalInput(
      userInput = "Remind me to ask Dad about Friday",
      userId = "u1",
      savedReminders = emptyList(),
      savedPreferences = emptyList(),
      savedFixes = emptyList(),
      useAi = false
    )
    assertEquals(com.example.data.model.IntentType.SAVE_REMINDER, flowAResult.intent)
    val dadCandidate = flowAResult.reminderCandidate
    assertNotNull(dadCandidate)
    assertEquals("Dad", dadCandidate!!.triggerActivity)
    assertTrue(dadCandidate.relatedPeople.any { it.equals("Dad", ignoreCase = true) })

    // Flow B: Context Trigger ("I'm heading to Dad's")
    val dadReminder = dadCandidate.copy(id = "rem_dad_1")
    val flowBResult = aiService.processUniversalInput(
      userInput = "I'm heading to Dad's",
      userId = "u1",
      savedReminders = listOf(dadReminder),
      savedPreferences = emptyList(),
      savedFixes = emptyList(),
      useAi = false
    )
    assertEquals(com.example.data.model.IntentType.TRIGGER_CONTEXT, flowBResult.intent)
    assertTrue(flowBResult.matchedReminders.any { it.id == "rem_dad_1" })

    // Flow C: Save Preference & Retrieve Preference ("My normal pizza order is pepperoni and mushrooms" -> "What's my normal pizza order?")
    val savePizzaResult = aiService.processUniversalInput(
      userInput = "My normal pizza order is pepperoni and mushrooms",
      userId = "u1",
      savedReminders = listOf(dadReminder),
      savedPreferences = emptyList(),
      savedFixes = emptyList(),
      useAi = false
    )
    assertEquals(com.example.data.model.IntentType.SAVE_PREFERENCE, savePizzaResult.intent)
    val pizzaCandidate = savePizzaResult.preferenceCandidate
    assertNotNull(pizzaCandidate)

    val pizzaPref = pizzaCandidate!!.copy(id = "pref_pizza_1")
    val retrievePizzaResult = aiService.processUniversalInput(
      userInput = "What's my normal pizza order?",
      userId = "u1",
      savedReminders = listOf(dadReminder),
      savedPreferences = listOf(pizzaPref),
      savedFixes = emptyList(),
      useAi = false
    )
    assertEquals(com.example.data.model.IntentType.RETRIEVE_PREFERENCE, retrievePizzaResult.intent)
    assertTrue(retrievePizzaResult.matchedPreferences.any { it.id == "pref_pizza_1" })
    assertTrue(retrievePizzaResult.aiSummary.contains("pepperoni", ignoreCase = true))

    // Unsaved preference query must not invent fake memories
    val missingPrefResult = aiService.processUniversalInput(
      userInput = "What is my passport number?",
      userId = "u1",
      savedReminders = listOf(dadReminder),
      savedPreferences = listOf(pizzaPref),
      savedFixes = emptyList(),
      useAi = false
    )
    assertTrue(missingPrefResult.aiSummary.contains("Nothing matching in your memory", ignoreCase = true))

    // Flow D: Undo IRL Recovery ("I accidentally deleted a file")
    val flowDResult = aiService.processUniversalInput(
      userInput = "I accidentally deleted a file",
      userId = "u1",
      savedReminders = listOf(dadReminder),
      savedPreferences = listOf(pizzaPref),
      savedFixes = emptyList(),
      useAi = false
    )
    assertEquals(com.example.data.model.IntentType.REPORT_PROBLEM, flowDResult.intent)
    assertNotNull(flowDResult.fixCandidate)
    assertTrue(flowDResult.fixCandidate!!.steps.size >= 3)
  }

  @Test
  fun testComputeDailyProductivityTrend_sevenDayWindowAndCompletionRates() {
    val now = 1_762_670_000_000L
    val yesterday = now - 86_400_000L

    val sampleReminders = listOf(
      Reminder(
        id = "r_today_done_1",
        userId = "u1",
        title = "Pick up coffee beans",
        category = "Shopping",
        isCompleted = true,
        createdAt = Date(now - 3_600_000L),
        updatedAt = Date(now - 1_800_000L)
      ),
      Reminder(
        id = "r_today_done_2",
        userId = "u1",
        title = "Ask Dad about Friday",
        category = "Personal",
        isCompleted = true,
        createdAt = Date(now - 7_200_000L),
        updatedAt = Date(now - 900_000L)
      ),
      Reminder(
        id = "r_today_pending_1",
        userId = "u1",
        title = "Drop off package",
        category = "Shopping",
        isCompleted = false,
        createdAt = Date(now - 1_200_000L),
        updatedAt = Date(now - 1_200_000L)
      ),
      Reminder(
        id = "r_yesterday_done_1",
        userId = "u1",
        title = "Send sprint update",
        category = "Work",
        isCompleted = true,
        createdAt = Date(yesterday),
        updatedAt = Date(yesterday)
      )
    )

    val trend = com.example.ui.components.computeDailyProductivityTrend(
      reminders = sampleReminders,
      referenceTimeMillis = now
    )

    assertEquals(7, trend.size)
    val todayPoint = trend.last()
    assertEquals("Today", todayPoint.dayShortLabel)
    assertEquals(2, todayPoint.completedCount)
    assertEquals(1, todayPoint.pendingCount)
    assertEquals(3, todayPoint.totalTasks)
    assertEquals(67, todayPoint.completionRatePercent)
    assertEquals("Shopping", todayPoint.topCategory)

    val yesterdayPoint = trend[5]
    assertEquals(1, yesterdayPoint.completedCount)
    assertEquals("Work", yesterdayPoint.topCategory)
  }

  @Test
  fun testZeroLossSync_dirtyLocalMemoriesPreservedDuringRemoteReconciliation() = runBlocking {
    val fakeDao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(
      context = null,
      localDao = fakeDao,
      firestore = null,
      auth = null,
      ioDispatcher = Dispatchers.Unconfined
    )

    val userId = "alice_123"
    // 1. User saves a new memory while offline -> marked isDirty = true in Room
    val offlineReminder = MemoryEntry.Reminder(
      id = "local_offline_rem_1",
      userId = userId,
      title = "Pick up prescriptions at pharmacy",
      triggerActivity = "Passing Walgreens",
      category = "Health"
    )
    repo.saveMemoryEntry(offlineReminder)

    val dirtyBeforeSync = fakeDao.getDirtyMemories(userId)
    assertEquals(1, dirtyBeforeSync.size)
    assertTrue(dirtyBeforeSync.first().isDirty)

    // 2. Remote snapshot arrives that only knows about an older server item ("server_item_1")
    //    and does NOT yet have "local_offline_rem_1".
    val remoteSnapshotItems = listOf(
      UnifiedMemoryItem(
        id = "server_item_1",
        userId = userId,
        type = UnifiedMemoryItem.TYPE_PREFERENCE,
        title = "Usual Bagel Order",
        content = "Everything bagel toasted with scallion cream cheese",
        category = "Food & Drinks",
        createdAt = Date(1_700_000_000_000L),
        updatedAt = Date(1_700_000_000_000L)
      )
    )
    repo.reconcileRemoteSnapshot(userId, remoteSnapshotItems)

    // 3. Verify ZERO DATA LOSS: both the unsynced local memory AND the remote server memory exist!
    val currentMemories = repo.observeMemories(userId).first()
    assertEquals(2, currentMemories.size)
    assertTrue(currentMemories.any { it.id == "local_offline_rem_1" })
    assertTrue(currentMemories.any { it.id == "server_item_1" })
  }

  @Test
  fun testZeroLossSync_tombstoneDeleteNotResurrectedDuringRemoteReconciliation() = runBlocking {
    val fakeDao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(
      context = null,
      localDao = fakeDao,
      firestore = null,
      auth = null,
      ioDispatcher = Dispatchers.Unconfined
    )

    val userId = "alice_123"
    val serverItem = UnifiedMemoryItem(
      id = "server_item_to_delete",
      userId = userId,
      type = UnifiedMemoryItem.TYPE_REMINDER,
      title = "Old completed task",
      triggerActivity = "Store",
      createdAt = Date(1_700_000_000_000L),
      updatedAt = Date(1_700_000_000_000L)
    )
    repo.reconcileRemoteSnapshot(userId, listOf(serverItem))
    assertEquals(1, repo.observeMemories(userId).first().size)

    // 1. User deletes the item while offline -> marked pendingDelete = true (tombstone)
    repo.deleteMemoryEntry(userId, "server_item_to_delete")

    // Immediately hidden from UI observers and counts
    assertEquals(0, repo.observeMemories(userId).first().size)
    assertEquals(0, fakeDao.getMemoryCount(userId))
    assertEquals(1, fakeDao.getPendingDeleteMemories(userId).size)

    // 2. Remote snapshot arrives that still has "server_item_to_delete" (since offline delete hasn't flushed yet)
    repo.reconcileRemoteSnapshot(userId, listOf(serverItem))

    // 3. Verify tombstoned item is NOT resurrected!
    assertEquals(0, repo.observeMemories(userId).first().size)
    assertEquals(1, fakeDao.getPendingDeleteMemories(userId).size)
  }

  @Test
  fun testCreatedAtImmutability_preservedOnLocalUpdate() = runBlocking {
    val fakeDao = FakeUnifiedMemoryDao()
    val repo = DefaultMemoryRepository(
      context = null,
      localDao = fakeDao,
      firestore = null,
      auth = null,
      ioDispatcher = Dispatchers.Unconfined
    )

    val userId = "alice_123"
    val initialCreatedAt = Date(1_700_000_000_000L)
    val initialEntry = MemoryEntry.Preference(
      id = "pref_immutable_1",
      userId = userId,
      timestamp = initialCreatedAt,
      title = "Coffee Order",
      category = "Food & Drinks",
      description = "Iced oat latte"
    )
    repo.saveMemoryEntry(initialEntry)
    val savedEntity1 = fakeDao.getMemoryById("pref_immutable_1", userId)!!
    assertEquals(initialCreatedAt.time, savedEntity1.createdAt)

    // Update the same memory entry later
    val updatedTimestamp = Date(1_700_500_000_000L)
    val editedEntry = initialEntry.copy(
      timestamp = updatedTimestamp,
      description = "Iced oat latte with cinnamon"
    )
    repo.saveMemoryEntry(editedEntry)

    val savedEntity2 = fakeDao.getMemoryById("pref_immutable_1", userId)!!
    // createdAt MUST remain immutable (equal to initialCreatedAt), while updatedAt advances
    assertEquals(initialCreatedAt.time, savedEntity2.createdAt)
    assertTrue(savedEntity2.updatedAt >= updatedTimestamp.time)
  }

  @Test
  fun testTopBarSyncIndicator_resolvesOnlineOfflineSyncingAndQueuedStates() {
    val onlineModel = com.example.ui.components.resolveSyncUiModel(
      syncStatus = SyncStatus.Synced(timestamp = 1000L, itemCount = 5),
      isSignedIn = true
    )
    assertEquals("Online", onlineModel.badgeLabel)

    val offlineModel = com.example.ui.components.resolveSyncUiModel(
      syncStatus = SyncStatus.Offline,
      isSignedIn = false
    )
    assertEquals("Offline", offlineModel.badgeLabel)

    val syncingModel = com.example.ui.components.resolveSyncUiModel(
      syncStatus = SyncStatus.Syncing,
      isSignedIn = true
    )
    assertEquals("Syncing", syncingModel.badgeLabel)

    val queuedErrorModel = com.example.ui.components.resolveSyncUiModel(
      syncStatus = SyncStatus.Error("Saved locally"),
      isSignedIn = true
    )
    assertEquals("Offline · Queued", queuedErrorModel.badgeLabel)
  }
}

/**
 * In-memory test double for UnifiedMemoryDao to test Single Source of Truth repository logic,
 * including outbox (`isDirty`) and tombstone (`pendingDelete`) semantics.
 */
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
    list.removeAll { it.id == memory.id && it.userId == memory.userId }
    list.add(memory)
    emit()
  }

  override suspend fun insertMemories(memories: List<UnifiedMemoryEntity>) {
    memories.forEach { memory ->
      list.removeAll { it.id == memory.id && it.userId == memory.userId }
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

