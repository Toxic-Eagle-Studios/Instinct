# INSTINCT · Your second brain

A functional mobile-first full-stack starter app for the merged idea:
**While You're At It + Undo IRL + Exactly/Remind Me.**

## Already functional

- Save reminders, preferences, solutions, notes and compressed photos
- Type input, plus browser speech-to-text when supported
- Retrieve actual saved memories with keyword-based search
- Context matches: save `Remind me to ask Dad about Friday`, then say `I'm heading to Dad's`
- Recover from common mistakes, and save the recovery steps for next time
- Edit, delete, complete reminders, optional due dates, export/import backup JSON
- Local browser persistence works without API keys or Firebase
- Optional Gemini interpretation through **server-side** API key
- Optional Firebase Google sign-in and Firestore cross-device sync
- Optional notifications **only while the page is open**

## Instant no-install demo

Open `INSTINCT_STANDALONE.html` in a browser or host it as a static web page. It contains the entire local-memory app in one file: no `npm install`, no server, and no key. This is the quickest way to try the save/context/search/recovery workflows. Browser storage availability depends on how the file is opened; using a normal HTTPS website provides more reliable persistence than a `file://` preview.

The standalone mode is deliberately **local-only**: it cannot call Gemini or sync to Firebase. The full-stack project below adds optional secure Gemini and cloud integration.

## Run locally

Requires Node.js 20+.

```sh
npm install
cp .env.example .env
npm run dev
```

Open `http://localhost:5173`. No keys are required for the working local mode.

To enable Gemini, put `GEMINI_API_KEY=...` in `.env` (server only), then restart.
For a deployable build: `npm run build && npm start`.

## Google AI Studio workflow

1. At https://aistudio.google.com/, open **Build**, select **Web app**.
2. Paste `AI_STUDIO_BUILD_PROMPT.txt` as your first Build prompt for AI Studio to generate its own runnable version.
3. The files in this repository are a complete working starter. AI Studio can import an existing GitHub repository under **Add files (+) → Import from GitHub** (subject to your available UI). The ZIP is primarily for extracting, inspecting, running locally, or uploading to your own GitHub repository; it is not itself a pasteable Build prompt.
4. In AI Studio, secrets like GEMINI_API_KEY must stay server-side. Do not place them in client code.

## Optional Firebase setup

1. Create your own Firebase project and register a Web app.
2. Enable **Authentication → Google sign-in** and **Cloud Firestore**.
3. Copy the *web config* values into the `VITE_FIREBASE_*` environment variables in `.env` or the AI Studio app configuration.
4. Publish `firestore.rules` to Firestore. **Do not use test-mode allow-all rules in production.**
5. Add your deployed domain to authorized domains under Firebase Authentication.
6. Restart/rebuild, then sign in on the Settings tab. Local memories do **not** automatically sync; the explicit **Copy device memories to cloud** button does this without deleting the local copies.

The Firebase web config is not an API secret. Access control comes from Google Auth plus the Firestore rules. Gemini API keys ARE secrets and must not have `VITE_` in the variable name.

## Verification

The standalone flows (save reminder, context reminder resurfacing, save preference, retrieve preference, Undo IRL and saved solution) were checked in a Chromium browser with simulated browser storage; deterministic core logic also passed Node tests. Full-stack dependency installation/build could not be completed in this isolated environment, so Google AI Studio / local Node should compile and test the full-stack integration before publishing.

## Known boundaries (v0.1)

- The first version stores data locally until you configure Firebase.
- Without a Gemini key, its interpretation and recovery guidance use offline rules, not LLM reasoning.
- Voice recognition is browser-dependent, and microphone permissions may be blocked in embedded previews.
- No location tracking or always-on microphone. Context suggestions activate when you type or speak context.
- No true scheduled background push notifications. Due reminders notify only while the page is open with permissions.
- Recovery guides cannot guarantee that deleted data can be restored.
- No production-grade user authentication for the `/api/interpret` route yet; do not widely publish it with a paid API key before adding server auth, quotas and abuse prevention.
- Photos and memory previews are sent to Gemini **only if** Gemini is enabled, the user chooses to use it, and an API key has been configured.
- Data is not end-to-end encrypted. Use care when saving highly sensitive information.

## Project structure

- `src/App.jsx`: user interface and all key interactions
- `src/logic.js`: deterministic local brain, context matching, retrieval, recovery
- `src/firebase.js`: optional auth/sync
- `server.js`: Node API layer, secure Gemini integration
- `firestore.rules`: per-user access control
- `AI_STUDIO_BUILD_PROMPT.txt`: Build-mode prompt to recreate/extend project
