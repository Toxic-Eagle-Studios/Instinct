Room writes the current database schema here during KSP compilation. Commit generated
`com.example.data.local.InstinctDatabase/<version>.json` snapshots when changing the
schema. Never edit an already released snapshot to make a migration test pass.

The uploaded project did not contain historical schema exports. The migration
regression fixtures in `RoomMigrationTest` cover the v3/v4 layouts recoverable from
the source and validate them against Room's generated current schema at runtime.
They do not certify every database that may have been installed by older builds.
