const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { Timestamp, serverTimestamp } = require("firebase/firestore");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-instinct-tests";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
// Firestore stores microseconds. Retain sub-millisecond precision during updates.
const createdAt = new Timestamp(1_700_000_000, 123_456_000);
const updatedAt = new Timestamp(1_700_000_001, 987_654_000);
const [host, portText] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: fs.readFileSync("./firestore.rules", "utf8"), host, port: Number(portText) },
  });
});
after(async () => { if (testEnv) await testEnv.cleanup(); });
beforeEach(async () => { if (testEnv) await testEnv.clearFirestore(); });

// Include every non-null toMap() key, including empty default fields. These are Android
// wire payloads, rather than convenient smaller test documents that could miss schema drift.
const payloads = {
  reminders: {
    id: "rem_1", userId: ALICE_UID, title: "Buy milk", triggerActivity: "At the grocery store",
    relatedPeople: [], relatedLocations: ["grocery store"], keywords: ["milk", "due:Tomorrow"],
    recurrence: "every_time", isCompleted: false, category: "Shopping", createdAt, updatedAt,
  },
  preferences: {
    id: "pref_1", userId: ALICE_UID, title: "Oat Milk Cortado", category: "Food & Drinks",
    description: "Double shot, oat milk", details: "Extra hot", tags: ["coffee"],
    photoUrl: "file:///data/user/0/example/files/photo.jpg", createdAt, updatedAt,
  },
  fixes: {
    id: "fix_1", userId: ALICE_UID, problemSummary: "Grease stain", whatHappened: "Cooking oil spilled",
    bestFix: "Blot, apply baking soda", steps: ["Blot"], toolsNeeded: ["Baking soda"],
    alternativeFix: "", isVerifiedWorking: false, category: "Home", createdAt, updatedAt,
  },
  memories: {
    id: "mem_1", userId: ALICE_UID, type: "reminder", title: "Ask Mom about Friday",
    content: "When calling Mom, remind to ask about Friday", tags: ["family"],
    triggerActivity: "Calling Mom", relatedPeople: ["Mom"], relatedLocations: [],
    recurrence: "every_time", isCompleted: false, category: "Personal", details: "",
    photoUrl: "file:///data/user/0/example/files/photo.jpg", problemSummary: "", whatHappened: "",
    bestFix: "", steps: [], toolsNeeded: [], alternativeFix: "", isVerifiedWorking: false,
    createdAt, updatedAt,
  },
};

const models = { reminders: "Reminder", preferences: "Preference", fixes: "Fix", memories: "UnifiedMemoryItem" };
for (const [collection, payload] of Object.entries(payloads)) {
  const docFor = (uid, id = payload.id) => testEnv.authenticatedContext(uid).firestore()
    .collection("users").doc(ALICE_UID).collection(collection).doc(id);

  test(`${collection}: fixture includes exact Android toMap keys`, () => {
    const source = fs.readFileSync(`app/src/main/java/com/example/data/model/${models[collection]}.kt`, "utf8");
    const mapBody = source.match(/fun toMap\(\)[\s\S]*?mapOf\(([\s\S]*?)\)\.filterValues/);
    assert.ok(mapBody, "Android model must retain an inspectable explicit toMap payload");
    const keys = [...mapBody[1].matchAll(/"([^"]+)"\s+to\s/g)].map(match => match[1]);
    assert.deepEqual(Object.keys(payload).sort(), keys.sort());
  });

  test(`${collection}: owner can create, edit without changing createdAt, and delete`, async () => {
    const doc = docFor(ALICE_UID);
    await assertSucceeds(doc.set(payload));
    await assertSucceeds(doc.update({ category: "Work", updatedAt: serverTimestamp() }));
    const actual = (await doc.get()).data();
    assert.equal(actual.createdAt.seconds, createdAt.seconds);
    assert.equal(actual.createdAt.nanoseconds, createdAt.nanoseconds);
    await assertSucceeds(doc.delete());
  });

  test(`${collection}: unauthenticated and other-user reads, creates, updates and deletes fail`, async () => {
    const owner = docFor(ALICE_UID);
    await assertSucceeds(owner.set(payload));
    const visitors = [
      testEnv.unauthenticatedContext().firestore(),
      testEnv.authenticatedContext(BOB_UID).firestore(),
    ];
    for (const db of visitors) {
      const items = db.collection("users").doc(ALICE_UID).collection(collection);
      await assertFails(items.get());
      await assertFails(items.doc(payload.id).get());
      await assertFails(items.doc("other_id").set({ ...payload, id: "other_id" }));
      await assertFails(items.doc(payload.id).update({ category: "Work", updatedAt: serverTimestamp() }));
      await assertFails(items.doc(payload.id).delete());
    }
  });

  test(`${collection}: rejects altered immutable metadata, unknown fields, and invalid category`, async () => {
    const doc = docFor(ALICE_UID);
    await assertSucceeds(doc.set(payload));
    for (const change of [
      { createdAt: serverTimestamp() },
      { createdAt: new Timestamp(createdAt.seconds, createdAt.nanoseconds + 1_000) },
      { userId: BOB_UID }, { id: "another_id" },
      { unexpectedField: "schema drift" }, { category: 123 },
    ]) await assertFails(doc.update({ ...change, updatedAt: serverTimestamp() }));
    await assertFails(docFor(ALICE_UID, "wrong_path").set(payload));
    await assertFails(docFor(ALICE_UID, "extra_field").set({ ...payload, id: "extra_field", unexpectedField: true }));
  });

  test(`${collection}: accepts server timestamps on a new document`, async () => {
    const doc = docFor(ALICE_UID);
    await assertSucceeds(doc.set({ ...payload, createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    const original = (await doc.get()).data().createdAt;
    // Preserve the full stored timestamp, including its nanoseconds, during the subsequent edit.
    await assertSucceeds(doc.set({ ...payload, createdAt: original, updatedAt: serverTimestamp() }));
  });
}

for (const type of ["preference", "fix"]) {
  test(`memories: full UnifiedMemoryItem payload accepts ${type} and forbids changing its type`, async () => {
    const doc = testEnv.authenticatedContext(ALICE_UID).firestore()
      .collection("users").doc(ALICE_UID).collection("memories").doc("mem_1");
    await assertSucceeds(doc.set({ ...payloads.memories, type }));
    await assertFails(doc.update({ type: "reminder", updatedAt: serverTimestamp() }));
  });
}

test("unmatched collections remain denied", async () => {
  const db = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).collection("unknown").doc("x").set({ value: "x" }));
});
