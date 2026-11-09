# INSTINCT

INSTINCT is the existing Android memory, reminder, preference, and everyday-fix app built with Kotlin, Compose, Room, and Firebase. The Android project lives at the repository root: `app/`, `gradle/`, and the Gradle build files can be opened directly in Android Studio.

## Build and test

Use Android Studio's bundled JDK (Java 17 or newer, compatible with Gradle 9.3.1) and install Android SDK Platform 36.1. Point `ANDROID_HOME` or an untracked `local.properties` file at your SDK.

```sh
./gradlew testDebugUnitTest assembleDebug --no-configuration-cache
```

The Gradle wrapper pins the uploaded project's Gradle 9.3.1 release and verifies its distribution checksum. A normal local Android debug keystore is used unless an existing AI Studio `debug.keystore` is supplied. Release signing still uses the existing environment-based configuration; no signing keys are included.

Firebase runtime setup is required to sign in and sync:

1. Obtain the Android configuration for the existing application ID `com.aistudio.instinct.kzmqr` from the intended Firebase project. Save it locally as `app/google-services.json`; it is ignored by Git.
2. Configure Google Sign-In with the matching Android signing certificate and web OAuth client. The Google Services plugin generates `default_web_client_id`; missing configuration is handled as a sign-in error.
3. Supply a local `app/src/main/res/values/firebase_applet_config.xml` defining `firestore_database_id` for the intended Firestore database. Release App Check may also require `recaptcha_site_key`, as in the existing app setup. These values are not bundled in source control.
4. Configure Firebase Auth, Firestore, and App Check in the intended Firebase project before device testing. The checked-in `firebase.json` preserves the uploaded deployment target; this repair does not deploy it.

`.env.example` contains placeholders only. Never commit `.env`, service-account credentials, API secrets, or signing files. The existing AI service uses Firebase AI and its existing local fallback logic; configure the intended Firebase AI backend before testing those features.

## Firebase rules regression tests

Use Node.js 22 or newer and Java 21 or newer for the pinned emulator tooling:

```sh
npm ci
npm test
```

The tests use `firebase.test.json`, a local Firestore emulator, and a demo project. They do not read or write the live Firebase database. The canonical Android rules are `firestore.rules`; the older web prototype's rules under `legacy/` are historical and must not replace them.

## Preserved work and data-safety changes

- `legacy/web-starter/` preserves the complete earlier web starter as editable source, including its standalone HTML app.
- `docs/reference/` preserves the original development and repair briefs. They describe historical requests and are not executable setup instructions.
- `docs/SOURCE_IMPORT.md` records the import provenance and omitted private/generated files.
- `docs/DATA_SAFETY.md` describes the repairs, verification results, and remaining release checks.

The original repository commits and ZIP versions remain available in Git history. Duplicate archives are removed from the working tree; no history is rewritten. This change does not reset a device database, deploy Firebase rules, or publish an app release.
