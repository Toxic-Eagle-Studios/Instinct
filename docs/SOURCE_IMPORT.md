# Source import provenance

Import date: November 9, 2026.

The starting repository branch was `main` at `286fe34` (Add files via upload). It contained three source ZIPs plus prompts, a standalone prototype, and a short README.

## Android source selected

`instinct (1).zip` is the newest uploaded Android source. Its SHA-256 is:

`96228c2c6f2bfaff5688e612facc8fe6a9f9680df6487fba0c140f888efb30a1`

The attachment recovered from the referenced “Build Instinct App Code” conversation is byte-for-byte identical to that repository ZIP. It contains Room database version 5 and 102 files. It is the baseline for the root Android source, not a replacement project.

The older `instinct.zip` has SHA-256:

`c1367a0f6545ac1ae80738481b0bc8072cff823b8f81c77b11aa5dac4a481600`

Every file path in that older Android ZIP is present in the newer source. Its changed files are superseded by the newer upload, including the v4 database with destructive migration fallback. The older ZIP remains recoverable from the original Git commit.

## Earlier web source preserved

`INSTINCT_Complete_Starter_Code.zip` has SHA-256:

`f168bea05bb1756d976c2cbd12f4764f6185c77dfc808f6c15b9b7437afc18f2`

All 15 files are extracted under `legacy/web-starter/`. The Android upload's `public/assets/aistudio/` files were individually compared with this source and the root prompts: every file was an exact duplicate. Those redundant hashed asset copies and duplicate prompt/standalone copies are omitted. The web starter is preserved as historical source and is not migrated to the Android data schema by this repair.

## Files intentionally omitted

- `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`: live project/client configuration, kept local rather than republished as source.
- `.aistudio/`, `metadata.json`, and `firebase-blueprint.json`: generated editor history and project metadata, not needed to build the Android app.
- ZIP archives and exact duplicate prompt/assets: replaced by editable source and canonical reference documents.

No service-account keys, private keys, or signing keystores were needed or imported. Ignore rules exclude credentials, local configuration, generated builds, dependency folders, and future ZIP uploads. Removing files from this branch does not remove their earlier copies from public Git history; the repository history is preserved.

`docs/ANDROID_ORIGINAL_README.md` retains the original Android setup notes for reference. The root README provides the current build instructions.
