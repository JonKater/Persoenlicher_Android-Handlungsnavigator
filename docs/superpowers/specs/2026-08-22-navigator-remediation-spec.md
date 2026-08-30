# Navigator V1 Remediation Specification

## Product goal

Turn unstructured text or one selected image into one trustworthy, explainable next action, rank that action against the user's current constraints, and preserve an accurate completion history.

## Product boundary

V1 includes capture, extraction, local prioritization, a Today view, completion, retryable errors, and history. Google Drive scanning, Gmail indexing, voice capture, account sync, automatic deletion, and cross-device collaboration are outside V1 and must not appear as working controls.

## Build and delivery requirements

- The repository contains `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties` for Gradle 9.3.1.
- Android Gradle Plugin stays at 9.1.1, the build runtime is JDK 17, and the project compiles against Android API 36.1.
- A clean checkout can run `./gradlew testDebugUnitTest lintDebug assembleDebug` without a repository-local `debug.keystore`.
- Debug signing uses Android's default debug signing behavior; release signing remains environment-driven.
- No Gemini API key or placeholder secret is compiled into the APK.

## Secure AI boundary

- Android calls Gemini through Firebase AI Logic with Firebase App Check enabled; the APK contains no `GEMINI_API_KEY` BuildConfig field and no Retrofit Gemini endpoint.
- The initial model is `gemini-3.6-flash`, selected for lower latency and cost than a Pro preview model.
- Structured output uses Firebase AI Logic `Schema` and `generationConfig` APIs with `responseMimeType = "application/json"`.
- Text and image requests return a typed `ExtractedAction`; malformed, missing, or out-of-range fields produce a retryable error and are never persisted as an action.
- Images are decoded and resized off the main thread, with a maximum long edge of 2048 pixels and a maximum encoded JPEG payload of 4 MiB.

## Action and scoring model

The model extracts evidence; Kotlin computes the final score. The same extracted data and user context must always yield the same score.

`ExtractedAction` contains:

- `title: String` with 1–80 trimmed characters.
- `description: String` with 1–500 trimmed characters.
- `dueAtEpochMs: Long?` in the future when a hard date can be inferred.
- `consequenceSeverity: Int` from 0 through 10.
- `financialExposureCents: Long` from 0 through 10,000,000.
- `goalId: String?`, matched only against a goal supplied in the prompt.
- `unblocksCount: Int` from 0 through 3.
- `estimatedMinutes: Int` from 1 through 480.
- `requiredEnergy: EnergyLevel`, one of `LOW`, `MEDIUM`, or `HIGH`.
- `confidencePercent: Int` from 0 through 100.
- `rationale: String` with 1–500 trimmed characters.

`ScoringContext` contains `nowEpochMs`, `availableMinutes`, `currentEnergy`, and `primaryGoalIds`.

The deterministic score is:

- Urgency: 20 points due within 24 hours, 15 within 72 hours, 10 within 7 days, 5 within 30 days, otherwise 0.
- Consequence: `consequenceSeverity`, adding 0–10 points. Urgency plus consequence is capped at 30.
- Financial: 25 points for at least €1,000 exposure, 18 for at least €250, 10 for at least €50, 5 for a positive lower exposure, otherwise 0.
- Goal fit: 20 points when `goalId` is in `primaryGoalIds`, otherwise 0.
- Unblocking: `unblocksCount * 5`, producing 0–15 points.
- Context fit: 5 points when `estimatedMinutes <= availableMinutes`, plus 5 points when `requiredEnergy <= currentEnergy`.
- Uncertainty penalty: `(100 - confidencePercent) / 5`, producing 0–20 points.
- Effort mismatch penalty: 0 when the action fits available time; otherwise `min(15, ceil((estimatedMinutes - availableMinutes) / 15.0) * 3)`.
- Total: urgency/consequence + financial + goal fit + unblocking + context fit − uncertainty − effort mismatch.

The UI shows the total and a concise rationale. Component scores remain available in an expandable explanation.

## Persistence and lifecycle

- `ActionEntity.deadlineMs` is nullable and populated from `dueAtEpochMs`.
- `ActionEntity.completedAtMs` is nullable. Completing an action sets it once; reopening clears it.
- History labels and orders entries by `completedAtMs`, not creation time.
- Capture text and selected image remain available until insertion succeeds or the user explicitly clears them.
- Database version 2 includes an explicit migration from the current version 1 schema.
- Until a user-facing backup/privacy choice exists, Android backup excludes the Room database and capture state.

## Today selection

- Main mission is the highest total score among all pending actions, including hard deadlines.
- Deadline warnings are pending actions with a non-null deadline inside the next 7 days; the main mission is not duplicated in the warning list.
- Optional steps are actions other than the main mission with `estimatedMinutes` from 5 through 15, ordered by `unblocksCount` descending and then total score descending, limited to two.
- Empty state offers a direct route to Capture.
- Completion provides an Undo action.

## Areas replacement

- Remove the inert Drive and Gmail cards and the hard-coded “all active” area list.
- Replace the screen with locally persisted goals plus current available time and energy controls that feed `ScoringContext`.
- Do not claim Drive, Gmail, OAuth, or indexing behavior anywhere in V1 copy.

## Error and accessibility requirements

- The UI distinguishes missing setup, network failure, rejected image, invalid model output, and storage failure.
- Errors are in German, remain visible until dismissed or retried, and never erase capture content.
- Capture has a visible text-field label, selected-image preview/removal, progress semantics, and a success announcement.
- Bottom navigation uses single-top state restoration so tab changes do not grow an unbounded back stack.
- Score chips are display-only; no element with an empty click handler is exposed as interactive.

## Verification requirements

- Unit tests cover every scoring boundary, output validation, Today selection, error mapping, and completion timestamps.
- Room migration tests cover version 1 to version 2 with existing pending and completed rows.
- ViewModel tests use fakes and prove input retention on failure and clearing on success.
- Compose tests cover all four tabs, Capture retry, Today completion/Undo, and History timestamps.
- Screenshot tests render real Navigator screens and contain no template `Greeting` reference.
- `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass from a clean checkout.
