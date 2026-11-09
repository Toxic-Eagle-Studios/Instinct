# Android data-safety repair

This change strengthens the uploaded Android app. It preserves the application ID, Room version 5, entity schema, existing screens, and existing Firebase deployment configuration.

## Repairs and preserved protections

### Local database upgrades

The newest uploaded ZIP already removed the destructive Room fallback and supplied migrations 1→2→3→4→5. That policy is retained: an unsupported migration or downgrade fails instead of dropping and recreating user tables. Schema export is enabled for future release history, and the database singleton uses the second check inside its synchronized section.

`RoomMigrationTest` opens v3/v4 SQL fixtures through the same factory used by the app, letting Room validate the resulting schema and indices. It checks saved fields and original timestamps, plus refusal to erase an unsupported database. The historical fixtures are reconstructed from source; no original v1/v2 schema exports were provided.

### Snapshot reconciliation and deletion queues

An empty, cached, filtered, or partial Firestore snapshot no longer deletes Room records that are missing from that response. Remote data merges in a Room transaction and cannot replace dirty local edits, pending-deletion rows, or another account's row with the same globally keyed ID. Batch local saves reject conflicting ownership before making any changes.

Uploads and deletes only acknowledge the exact unchanged local revision they sent. The shared remote-write lock and revision checks prevent older queued writes within this process from landing after a newer write. Legacy collection listeners use the protected merge path as well.

Individual deletions, including cloud-only items without a local cached row, retain a local deletion record until cloud success. Clear-all retains payloads and queued deletions on failure, reads the server rather than relying on an incomplete cache, and surfaces incomplete cloud deletion. Guest-only clearing remains local.

### Creation timestamps

All five full-document save paths share a Firestore transaction that reads and preserves an existing document's exact `createdAt` value, including sub-millisecond precision. A failed read is an error; it is not evidence that a document is new. A malformed existing creation timestamp fails safely rather than being replaced. `updatedAt` is still a server timestamp. Local edits retain their original creation time.

### Firebase schema and model mapping

The newest ZIP already included `category` in the affected reminder/fix create schemas. That repair is retained and tested. Updates now use the same supported field sets as creates, document IDs must agree with the path, and ownership/creation-time/type immutability remains enforced.

Stored model IDs no longer also use `@DocumentId`, which conflicts with a stored field of the same name. Readers take the actual document path ID, including for legacy records missing a stored ID. Explicit Kotlin Boolean property annotations preserve `isCompleted` and `isVerifiedWorking` when the Firestore SDK decodes records.

## Verification

- Firestore emulator: **23 passed, 0 failed** against the demo-only local test configuration. Includes complete Android `toMap()` field parity, all four collections, ownership boundaries, category edits, unknown-field rejection, exact timestamp preservation and a rejected one-microsecond mutation.
- Android regression coverage includes the actual Firestore SDK mapper, Room upgrade and DAO behavior, local-only/dirty/deletion-queue preservation, account ownership collisions, stale acknowledgment, offline clear, and guest clear.
- Android `assembleDebug`: **passed** with the pinned Gradle 9.3.1, Android SDK 36.1, and Android Studio bundled JDK 25. The build intentionally excludes private Firebase runtime configuration.
- Android `testDebugUnitTest`: **42 passed, 0 failed, 1 pre-existing skipped smoke test**. All four real Room migration/DAO regressions passed. The suite also includes all 14 focused sync/model-mapping checks; they are not additional to the 42.
- Initial combined validation stopped on a transient Maven DNS error downloading Robolectric. After recovering the official artifact and verifying its checksum, both assembly and the complete unit suite passed separately with `--no-configuration-cache`.
- The restored Gradle 9.3.1 wrapper JAR and distribution checksum match the official Gradle release checksums.

The test configuration explicitly selects a demo project and local emulator. No live Firebase data or deployed rules were changed.

## Remaining release checks and limitations

1. Run the Android app on a device with the intended private Firebase configuration. Verify Google sign-in, App Check, Firebase AI, two-device sync, airplane-mode edits/deletes, reconnection, and account switching against a test backend before release.
2. Preserve a real pre-upgrade device database and validate migrations against it. Source-derived v3/v4 fixtures do not certify all historical installed layouts; v1/v2 exports were not available.
3. Cross-device remote deletion needs an explicit versioned cloud deletion protocol before automatic deletion propagation is safe. This patch deliberately preserves local records when a snapshot omits them. A deletion from another device can therefore leave a local copy visible.
4. The shared write lock orders writes within this process, not across multiple devices. Distributed edit conflict resolution and clock skew require a separate design and test pass. Do not describe this as lossless multi-device synchronization.
5. The existing schema still uses a globally keyed ID. Unified-memory guards reject collisions; the older typed Room tables need a broader account-collision review if imported data can reuse IDs across users.
6. The stricter update allowlists may reject older cloud documents with additional fields. Test representative existing documents before deploying the rules. No rule deployment is included here.
7. Clear-all can complete only some remote batches before a later failure. Cached local payloads/deletion intent remain available for retry; a retry of clear-all is needed to re-enumerate any uncached remote documents. There is no persistent collection-wide deletion job.
8. Existing ZIP versions remain in Git history. Current source excludes live configuration, keys, and generated editor metadata; removing files from this branch does not rewrite earlier public commits.
