const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read reminders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("reminders").get());
});

test("Authenticated user: cannot read another user's reminders", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("reminders").get());
});

test("Authenticated user: can create, update (preserving createdAt), and delete reminder with exact Reminder.toMap() payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const createdAt = new Date(Date.now() - 60_000);
  const updatedAt = new Date(Date.now() - 30_000);

  const reminderPayload = {
    id: "rem_1",
    userId: ALICE_UID,
    title: "Buy milk",
    triggerActivity: "Going to grocery store",
    relatedPeople: ["Partner"],
    relatedLocations: ["grocery store"],
    keywords: ["milk", "store", "due:Tomorrow"],
    recurrence: "every_time",
    isCompleted: false,
    category: "Shopping",
    createdAt,
    updatedAt,
  };

  // Owner create succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1").set(reminderPayload)
  );

  // Non-owner create fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_2").set({
      ...reminderPayload,
      id: "rem_2",
    })
  );

  // Owner update preserving createdAt succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1").set({
      ...reminderPayload,
      title: "Buy organic whole milk",
      isCompleted: true,
      category: "Shopping",
      createdAt, // preserved!
      updatedAt: new Date(),
    })
  );

  // Owner update mutating createdAt MUST fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1").set({
      ...reminderPayload,
      createdAt: new Date(), // mutated createdAt
      updatedAt: new Date(),
    })
  );

  // Non-owner delete fails, owner delete succeeds
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1").delete());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1").delete());
});

test("Authenticated user: can create, update (preserving createdAt), and delete preference with exact Preference.toMap() payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const createdAt = new Date(Date.now() - 120_000);

  const prefPayload = {
    id: "pref_1",
    userId: ALICE_UID,
    title: "Oat Milk Cortado",
    category: "Food & Drinks",
    description: "Double shot espresso with steamed oat milk",
    details: "Extra hot, no foam",
    tags: ["coffee", "cortado"],
    photoUrl: "file:///data/user/0/com.aistudio.instinct.kzmqr/files/memory_photos/photo_1.jpg",
    createdAt,
    updatedAt: createdAt,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("pref_1").set(prefPayload)
  );

  // Update preserving createdAt succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("pref_1").set({
      ...prefPayload,
      details: "Extra hot, cinnamon on top",
      createdAt,
      updatedAt: new Date(),
    })
  );

  // Update mutating createdAt fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("pref_1").set({
      ...prefPayload,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("preferences").doc("pref_1").delete());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("pref_1").delete());
});

test("Authenticated user: can create, update (preserving createdAt), and delete fix with exact Fix.toMap() payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const createdAt = new Date(Date.now() - 90_000);

  const fixPayload = {
    id: "fix_1",
    userId: ALICE_UID,
    problemSummary: "Grease stain on cotton shirt",
    whatHappened: "Cooking oil splashed on cotton fibers",
    bestFix: "Blot excess oil, apply baking soda and dish soap",
    steps: ["Blot excess oil", "Apply baking soda", "Scrub with dish soap"],
    toolsNeeded: ["Baking soda", "Dish soap"],
    alternativeFix: "Use enzyme stain remover",
    isVerifiedWorking: true,
    category: "Home",
    createdAt,
    updatedAt: createdAt,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fixes").doc("fix_1").set(fixPayload)
  );

  // Update preserving createdAt succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fixes").doc("fix_1").set({
      ...fixPayload,
      category: "Personal",
      createdAt,
      updatedAt: new Date(),
    })
  );

  // Update mutating createdAt fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("fixes").doc("fix_1").set({
      ...fixPayload,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("fixes").doc("fix_1").delete());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("fixes").doc("fix_1").delete());
});

test("Unauthenticated user: cannot read memories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
});

test("Authenticated user: cannot read another user's memories", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
});

test("Authenticated user: can create, update (preserving createdAt), and delete unified memory items across reminder, preference, and fix types", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const createdAt = new Date(Date.now() - 120_000);

  const memRemPayload = {
    id: "mem_rem_1",
    userId: ALICE_UID,
    type: "reminder",
    title: "Ask Mom about Friday",
    content: "When calling Mom, remind to ask about Friday",
    tags: ["family", "friday"],
    triggerActivity: "Calling Mom",
    relatedPeople: ["Mom"],
    relatedLocations: ["Home"],
    recurrence: "every_time",
    isCompleted: false,
    category: "People",
    details: "",
    problemSummary: "",
    whatHappened: "",
    bestFix: "",
    steps: [],
    toolsNeeded: [],
    alternativeFix: "",
    isVerifiedWorking: false,
    createdAt,
    updatedAt: createdAt,
  };

  // 1. Reminder type in shared collection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_rem_1").set(memRemPayload)
  );

  // Update preserving createdAt succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_rem_1").set({
      ...memRemPayload,
      isCompleted: true,
      createdAt,
      updatedAt: new Date(),
    })
  );

  // Update mutating createdAt fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_rem_1").set({
      ...memRemPayload,
      isCompleted: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // 2. Preference type in shared collection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_pref_1").set({
      id: "mem_pref_1",
      userId: ALICE_UID,
      type: "preference",
      title: "Oat Milk Cortado",
      content: "Double shot, oat milk, extra hot",
      category: "Coffee & Drinks",
      details: "Two pumps vanilla, no foam",
      tags: ["coffee", "oat milk"],
      photoUrl: "file:///data/user/0/com.aistudio.instinct.kzmqr/files/memory_photos/cortado.jpg",
      createdAt,
      updatedAt: new Date(),
    })
  );

  // 3. Fix type in shared collection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_fix_1").set({
      id: "mem_fix_1",
      userId: ALICE_UID,
      type: "fix",
      title: "Spilled grease on cotton shirt",
      content: "Baking soda and dish soap pre-treatment",
      category: "Home",
      problemSummary: "Spilled grease on cotton shirt",
      whatHappened: "Grease saturated cotton fibers",
      bestFix: "Apply baking soda, then dawn dish soap",
      steps: ["Apply baking soda", "Let sit 10 min", "Rub dish soap"],
      toolsNeeded: ["Baking soda", "Dish soap"],
      alternativeFix: "Use heavy duty detergent",
      isVerifiedWorking: true,
      createdAt,
      updatedAt: new Date(),
    })
  );

  // 4. Query all items together in the single shared collection
  const snapshot = await aliceDb.collection("users").doc(ALICE_UID).collection("memories").get();
  assertSucceeds(Promise.resolve(snapshot));

  // 5. Non-owner cannot delete; owner can delete
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_rem_1").delete());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_rem_1").delete());
});
