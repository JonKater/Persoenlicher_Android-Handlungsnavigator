# Navigator V1 Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a buildable, secure Android V1 that turns text or one image into a validated action, ranks it deterministically, preserves failed captures, and records completion accurately.

**Architecture:** Replace direct Retrofit access with Firebase AI Logic plus App Check. Treat Gemini as an extractor of bounded evidence and keep scoring, Today selection, persistence, and lifecycle behavior deterministic in Kotlin. Reuse the existing Compose/Room shape, replace the inert Areas prototype with the small amount of user context required for meaningful ranking, and protect each boundary with unit, Room, and Compose tests.

**Tech Stack:** Kotlin 2.2.10, Android Gradle Plugin 9.1.1, Gradle 9.3.1, JDK 17, Jetpack Compose, Room 2.7.0, Firebase BoM 34.17.0, Firebase AI Logic, Firebase App Check, kotlinx.serialization, JUnit 4, Robolectric, Compose UI Test, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-08-22-navigator-remediation-spec.md`

## Global Constraints

- Keep the Android application ID `com.aistudio.navigator.xptqmz`, minimum SDK 24, target SDK 36, and compile SDK 36.1.
- Keep Gradle 9.3.1 and Android Gradle Plugin 9.1.1; run the build on JDK 17.
- Never compile `GEMINI_API_KEY` or another reusable Gemini credential into the APK.
- Use Firebase AI Logic with Firebase App Check and model `gemini-3.6-flash`.
- Accept one text capture, one selected image, or both; do not add voice, Drive, Gmail, or account sync in V1.
- Resize images off the main thread to a 2048-pixel maximum long edge and a 4 MiB maximum JPEG payload.
- Gemini extracts typed evidence; `ActionScorer` computes every score locally from the exact rubric in the spec.
- Do not erase capture content until action insertion succeeds or the user explicitly clears it.
- Do not back up the Room database until a user-facing privacy/backup choice exists.

---

## File Structure

- `app/src/main/java/com/example/domain/ActionModels.kt`: extraction, context, score, validation, and energy types shared by AI, persistence, and UI.
- `app/src/main/java/com/example/domain/ActionScorer.kt`: pure deterministic scoring and Today selection.
- `app/src/main/java/com/example/domain/ActionValidator.kt`: strict validation before persistence.
- `app/src/main/java/com/example/ai/NavigatorAnalyzer.kt`: testable analyzer interface and typed errors.
- `app/src/main/java/com/example/ai/FirebaseNavigatorAnalyzer.kt`: Firebase AI Logic request, prompt, schema, and response parsing.
- `app/src/main/java/com/example/ai/ImagePreprocessor.kt`: bounded off-main-thread image decode/resize/compression.
- `app/src/main/java/com/example/data/ActionEntity.kt`: version-2 Room row with deadline, rationale, duration, score components, and completion time.
- `app/src/main/java/com/example/data/UserContextEntity.kt`: locally persisted goals, available time, and energy.
- `app/src/main/java/com/example/data/AppDatabase.kt`: version-2 schema and migration.
- `app/src/main/java/com/example/ui/NavigatorUiState.kt`: immutable capture/Today/error state.
- `app/src/main/java/com/example/ui/NavigatorViewModel.kt`: orchestration only; no scoring or API parsing.
- `app/src/main/java/com/example/ui/screens/AreasScreen.kt`: local goals and current-context editor.
- `app/src/test/java/com/example/...`: pure domain, analyzer, ViewModel, Room, Compose, and screenshot tests organized beside the responsibility they verify.

---

### Task 1: Restore a Reproducible Build and Honest Baseline Tests

**Files:**
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `app/build.gradle.kts:27-50`
- Delete: `app/src/test/java/com/example/ExampleUnitTest.kt`
- Delete: `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- Delete: `app/src/test/java/com/example/GreetingScreenshotTest.kt`
- Delete: `app/src/test/screenshots/greeting.png`
- Create: `app/src/test/java/com/example/AppResourcesTest.kt`

**Interfaces:**
- Consumes: Android SDK 36.1, Build Tools 36.0.0, JDK 17, Gradle 9.3.1.
- Produces: working `gradlew` entrypoints and a unit-test source set with no template-only failures.

- [ ] **Step 1: Generate the complete Gradle wrapper**

Run from a shell using JDK 17 and a Gradle 9.3.1 installation:

```powershell
gradle wrapper --gradle-version 9.3.1 --distribution-type bin
```

Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are created, and `distributionUrl` remains `https\://services.gradle.org/distributions/gradle-9.3.1-bin.zip`.

- [ ] **Step 2: Remove the repository-local debug signing dependency**

Delete `create("debugConfig")` and change the build types to:

```kotlin
buildTypes {
  release {
    isCrunchPngs = false
    isMinifyEnabled = false
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = signingConfigs.getByName("release")
  }
}
```

Expected: Android's default debug signing configuration is used and no root `debug.keystore` is required.

- [ ] **Step 3: Replace the stale template tests with a real resource smoke test**

Create `AppResourcesTest.kt`:

```kotlin
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppResourcesTest {
  @Test fun appNameIsNavigator() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Navigator", context.getString(R.string.app_name))
  }
}
```

Remove the three generated example test files and the corrupt `greeting.png` baseline.

- [ ] **Step 4: Run the baseline checks**

Run:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --stacktrace
```

Expected: all three tasks pass; a missing `google-services.json` may emit the configured warning at this stage but must not stop Task 1.

- [ ] **Step 5: Commit the build baseline**

```powershell
git add gradlew gradlew.bat gradle/wrapper app/build.gradle.kts app/src/test
git commit -m "build: restore reproducible Android baseline"
```

---

### Task 2: Implement Validated Deterministic Scoring

**Files:**
- Create: `app/src/main/java/com/example/domain/ActionModels.kt`
- Create: `app/src/main/java/com/example/domain/ActionScorer.kt`
- Create: `app/src/main/java/com/example/domain/ActionValidator.kt`
- Create: `app/src/test/java/com/example/domain/ActionScorerTest.kt`
- Create: `app/src/test/java/com/example/domain/ActionValidatorTest.kt`

**Interfaces:**
- Consumes: no Android types; all functions are pure Kotlin.
- Produces: `EnergyLevel`, `GoalOption`, `ExtractedAction`, `ScoringContext`, `ScoreBreakdown`, `ValidationResult`, `ActionScorer.score()`, and `ActionValidator.validate()`.

- [ ] **Step 1: Write scoring boundary tests**

Create `ActionScorerTest.kt` with a shared action factory and these exact assertions:

```kotlin
package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionScorerTest {
  private val now = 1_800_000_000_000L
  private val context = ScoringContext(now, 30, EnergyLevel.MEDIUM, setOf("income"))

  private fun action(
    dueAt: Long? = null,
    consequence: Int = 0,
    cents: Long = 0,
    goalId: String? = null,
    unblocks: Int = 0,
    minutes: Int = 15,
    energy: EnergyLevel = EnergyLevel.MEDIUM,
    confidence: Int = 100,
  ) = ExtractedAction("Call", "Make the call", dueAt, consequence, cents, goalId,
    unblocks, minutes, energy, confidence, "Deadline and impact")

  @Test fun dueWithin24HoursGetsTwentyUrgencyPoints() {
    assertEquals(20, ActionScorer.score(action(dueAt = now + 86_400_000L), context).urgencyRisk)
  }

  @Test fun urgencyAndConsequenceAreCappedAtThirty() {
    assertEquals(30, ActionScorer.score(action(now + 1_000L, consequence = 10), context).urgencyRisk)
  }

  @Test fun financialBucketsMatchTheSpecification() {
    assertEquals(listOf(0, 5, 10, 18, 25), listOf(0L, 1L, 5_000L, 25_000L, 100_000L)
      .map { ActionScorer.score(action(cents = it), context).financial })
  }

  @Test fun matchingPrimaryGoalGetsTwentyPoints() {
    assertEquals(20, ActionScorer.score(action(goalId = "income"), context).goalFit)
  }

  @Test fun uncertaintyAndEffortPenaltiesMatchTheSpecification() {
    val score = ActionScorer.score(action(minutes = 61, confidence = 75), context)
    assertEquals(5, score.uncertainty)
    assertEquals(9, score.effortMismatch)
  }
}
```

- [ ] **Step 2: Run the scoring tests and confirm the missing-type failure**

Run:

```powershell
./gradlew.bat testDebugUnitTest --tests com.example.domain.ActionScorerTest
```

Expected: compilation fails because the domain types and `ActionScorer` do not exist.

- [ ] **Step 3: Add the shared domain types**

Create `ActionModels.kt`:

```kotlin
package com.example.domain

enum class EnergyLevel(val rank: Int) { LOW(0), MEDIUM(1), HIGH(2) }

data class GoalOption(val id: String, val name: String)

data class ExtractedAction(
  val title: String,
  val description: String,
  val dueAtEpochMs: Long?,
  val consequenceSeverity: Int,
  val financialExposureCents: Long,
  val goalId: String?,
  val unblocksCount: Int,
  val estimatedMinutes: Int,
  val requiredEnergy: EnergyLevel,
  val confidencePercent: Int,
  val rationale: String,
)

data class ScoringContext(
  val nowEpochMs: Long,
  val availableMinutes: Int,
  val currentEnergy: EnergyLevel,
  val primaryGoalIds: Set<String>,
)

data class ScoreBreakdown(
  val urgencyRisk: Int,
  val financial: Int,
  val goalFit: Int,
  val unblock: Int,
  val contextFit: Int,
  val uncertainty: Int,
  val effortMismatch: Int,
) {
  val total: Int get() = urgencyRisk + financial + goalFit + unblock + contextFit - uncertainty - effortMismatch
}

sealed interface ValidationResult {
  data class Valid(val action: ExtractedAction) : ValidationResult
  data class Invalid(val fields: List<String>) : ValidationResult
}

data class TodaySelection<T>(val main: T?, val warnings: List<T>, val optional: List<T>)
```

- [ ] **Step 4: Implement the scoring formula**

Create `ActionScorer.kt`:

```kotlin
package com.example.domain

import kotlin.math.ceil
import kotlin.math.min

object ActionScorer {
  fun score(action: ExtractedAction, context: ScoringContext): ScoreBreakdown {
    val untilDue = action.dueAtEpochMs?.minus(context.nowEpochMs)
    val urgency = when {
      untilDue == null || untilDue < 0 -> 0
      untilDue <= 86_400_000L -> 20
      untilDue <= 259_200_000L -> 15
      untilDue <= 604_800_000L -> 10
      untilDue <= 2_592_000_000L -> 5
      else -> 0
    }
    val urgencyRisk = min(30, urgency + action.consequenceSeverity)
    val financial = when {
      action.financialExposureCents >= 100_000L -> 25
      action.financialExposureCents >= 25_000L -> 18
      action.financialExposureCents >= 5_000L -> 10
      action.financialExposureCents > 0L -> 5
      else -> 0
    }
    val goalFit = if (action.goalId in context.primaryGoalIds) 20 else 0
    val unblock = action.unblocksCount * 5
    val contextFit = (if (action.estimatedMinutes <= context.availableMinutes) 5 else 0) +
      (if (action.requiredEnergy.rank <= context.currentEnergy.rank) 5 else 0)
    val uncertainty = (100 - action.confidencePercent) / 5
    val overrun = action.estimatedMinutes - context.availableMinutes
    val effortMismatch = if (overrun <= 0) 0 else min(15, ceil(overrun / 15.0).toInt() * 3)
    return ScoreBreakdown(urgencyRisk, financial, goalFit, unblock, contextFit, uncertainty, effortMismatch)
  }
}
```

- [ ] **Step 5: Write validation tests**

Create tests proving blank titles, 81-character titles, past deadlines, confidence 101, negative money, minute 0, and `unblocksCount = 4` are invalid, while boundary values are valid. Use one assertion that lists the exact invalid fields:

```kotlin
val result = ActionValidator.validate(invalidAction, nowEpochMs = now)
assertEquals(
  listOf("title", "dueAtEpochMs", "financialExposureCents", "unblocksCount", "estimatedMinutes", "confidencePercent"),
  (result as ValidationResult.Invalid).fields,
)
```

- [ ] **Step 6: Implement strict validation**

Create `ActionValidator.kt`:

```kotlin
package com.example.domain

object ActionValidator {
  fun validate(action: ExtractedAction, nowEpochMs: Long): ValidationResult {
    val normalized = action.copy(
      title = action.title.trim(),
      description = action.description.trim(),
      goalId = action.goalId?.trim()?.takeIf(String::isNotEmpty),
      rationale = action.rationale.trim(),
    )
    val invalid = buildList {
      if (normalized.title.length !in 1..80) add("title")
      if (normalized.description.length !in 1..500) add("description")
      if (normalized.dueAtEpochMs != null && normalized.dueAtEpochMs <= nowEpochMs) add("dueAtEpochMs")
      if (normalized.consequenceSeverity !in 0..10) add("consequenceSeverity")
      if (normalized.financialExposureCents !in 0..10_000_000L) add("financialExposureCents")
      if (normalized.unblocksCount !in 0..3) add("unblocksCount")
      if (normalized.estimatedMinutes !in 1..480) add("estimatedMinutes")
      if (normalized.confidencePercent !in 0..100) add("confidencePercent")
      if (normalized.rationale.length !in 1..500) add("rationale")
    }
    return if (invalid.isEmpty()) ValidationResult.Valid(normalized) else ValidationResult.Invalid(invalid)
  }
}
```

- [ ] **Step 7: Run domain tests and commit**

```powershell
./gradlew.bat testDebugUnitTest --tests "com.example.domain.*"
git add app/src/main/java/com/example/domain app/src/test/java/com/example/domain
git commit -m "feat: add deterministic action scoring"
```

Expected: all domain tests pass.

---

### Task 3: Replace the Embedded API Key with Firebase AI Logic

**Files:**
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Delete: `app/src/main/java/com/example/ai/GeminiModels.kt`
- Delete: `app/src/main/java/com/example/ai/GeminiService.kt`
- Create: `app/src/main/java/com/example/ai/NavigatorAnalyzer.kt`
- Create: `app/src/main/java/com/example/ai/FirebaseNavigatorAnalyzer.kt`
- Create: `app/src/main/java/com/example/ai/ImagePreprocessor.kt`
- Create: `app/src/test/java/com/example/ai/ImagePreprocessorTest.kt`
- Create: `app/src/test/java/com/example/ai/AnalyzerResponseParserTest.kt`

**Interfaces:**
- Consumes: `ExtractedAction`, `ScoringContext`, a list of `GoalOption`, text, and an optional processed bitmap.
- Produces: `NavigatorAnalyzer.analyze(payload, context, goals): AnalysisResult` and `ImagePreprocessor.prepare(uri): PreparedImage`.

- [ ] **Step 1: Add testable analyzer contracts**

Create `NavigatorAnalyzer.kt`:

```kotlin
package com.example.ai

import android.graphics.Bitmap
import com.example.domain.ExtractedAction
import com.example.domain.GoalOption
import com.example.domain.ScoringContext

data class CapturePayload(val text: String, val bitmap: Bitmap?)

sealed interface AnalysisResult {
  data class Success(val action: ExtractedAction) : AnalysisResult
  data class Failure(val error: AnalysisError) : AnalysisResult
}

enum class AnalysisError { SETUP_MISSING, NETWORK, IMAGE_TOO_LARGE, INVALID_OUTPUT, SERVICE }

interface NavigatorAnalyzer {
  suspend fun analyze(payload: CapturePayload, context: ScoringContext, goals: List<GoalOption>): AnalysisResult
}
```

- [ ] **Step 2: Replace direct networking dependencies**

In `build.gradle.kts`, remove `alias(libs.plugins.secrets) apply false`. In `app/build.gradle.kts`, remove the Secrets plugin, its `secrets {}` block, Retrofit, OkHttp, Moshi, converter, and logging-interceptor dependencies. Keep `implementation(libs.firebase.ai)` and add:

```toml
firebase-appcheck-debug = { group = "com.google.firebase", name = "firebase-appcheck-debug" }
firebase-appcheck-playintegrity = { group = "com.google.firebase", name = "firebase-appcheck-playintegrity" }
```

Then add:

```kotlin
debugImplementation(libs.firebase.appcheck.debug)
releaseImplementation(libs.firebase.appcheck.playintegrity)
```

Delete `.env.example` and every reference to `GEMINI_API_KEY` after the Firebase implementation is green.

- [ ] **Step 3: Add the Firebase project configuration**

Register Android application `com.aistudio.navigator.xptqmz` in Firebase, enable AI Logic with the Gemini Developer API, enable App Check debug for debug builds and Play Integrity for release, download `google-services.json`, and place it at `app/google-services.json`.

Expected: `./gradlew.bat processDebugGoogleServices` passes without the missing-services warning. The Firebase console owns the Gemini credential; no key is copied into the repository.

- [ ] **Step 4: Write response-parser failure tests**

Cover invalid JSON, missing fields, unknown energy, and out-of-range values. The parser must return `AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)` for each. A valid JSON fixture must produce the exact `ExtractedAction` values supplied in the fixture.

- [ ] **Step 5: Implement Firebase structured extraction**

Create `FirebaseNavigatorAnalyzer.kt` using the Firebase schema builders:

```kotlin
package com.example.ai

import com.example.domain.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
private data class ExtractedActionDto(
  val title: String,
  val description: String,
  val dueAtEpochMs: Long? = null,
  val consequenceSeverity: Int,
  val financialExposureCents: Long,
  val goalId: String? = null,
  val unblocksCount: Int,
  val estimatedMinutes: Int,
  val requiredEnergy: String,
  val confidencePercent: Int,
  val rationale: String,
)

class FirebaseNavigatorAnalyzer(
  private val json: Json = Json { ignoreUnknownKeys = false; explicitNulls = false },
) : NavigatorAnalyzer {
  private val schema = Schema.obj(mapOf(
    "title" to Schema.string(),
    "description" to Schema.string(),
    "dueAtEpochMs" to Schema.integer(nullable = true),
    "consequenceSeverity" to Schema.integer(),
    "financialExposureCents" to Schema.integer(),
    "goalId" to Schema.string(nullable = true),
    "unblocksCount" to Schema.integer(),
    "estimatedMinutes" to Schema.integer(),
    "requiredEnergy" to Schema.enumeration(listOf("LOW", "MEDIUM", "HIGH")),
    "confidencePercent" to Schema.integer(),
    "rationale" to Schema.string(),
  ))

  private val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
    modelName = "gemini-3.6-flash",
    generationConfig = generationConfig {
      responseMimeType = "application/json"
      responseSchema = schema
      temperature = 0.1f
    },
  )

  override suspend fun analyze(payload: CapturePayload, context: ScoringContext, goals: List<GoalOption>): AnalysisResult = try {
    val goalLines = goals.joinToString("\n") { "${it.id}: ${it.name}" }.ifBlank { "none" }
    val prompt = content {
      payload.bitmap?.let(::image)
      text("""
        Extract exactly one concrete next action from the user's capture.
        Current epoch milliseconds: ${context.nowEpochMs}
        Allowed goals (return only one listed id or null):
        $goalLines
        Estimate duration from 1 to 480 minutes, energy as LOW/MEDIUM/HIGH,
        consequence severity from 0 to 10, unblocked follow-on actions from 0 to 3,
        confidence from 0 to 100, and non-negative financial exposure in euro cents.
        User capture:
        ${payload.text.trim()}
      """.trimIndent())
    }
    val responseText = model.generateContent(prompt).text
      ?: return AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    parseAndValidate(responseText, context.nowEpochMs, goals.map { it.id }.toSet())
  } catch (error: IOException) {
    AnalysisResult.Failure(AnalysisError.NETWORK)
  } catch (error: Exception) {
    AnalysisResult.Failure(AnalysisError.SERVICE)
  }

  internal fun parseAndValidate(text: String, now: Long, allowedGoals: Set<String>): AnalysisResult {
    val dto = runCatching { json.decodeFromString<ExtractedActionDto>(text) }.getOrNull()
      ?: return AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    if (dto.goalId != null && dto.goalId !in allowedGoals) return AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    val energy = runCatching { EnergyLevel.valueOf(dto.requiredEnergy) }.getOrNull()
      ?: return AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    val candidate = ExtractedAction(dto.title, dto.description, dto.dueAtEpochMs,
      dto.consequenceSeverity, dto.financialExposureCents, dto.goalId, dto.unblocksCount,
      dto.estimatedMinutes, energy, dto.confidencePercent, dto.rationale)
    return when (val validation = ActionValidator.validate(candidate, now)) {
      is ValidationResult.Valid -> AnalysisResult.Success(validation.action)
      is ValidationResult.Invalid -> AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    }
  }
}
```

- [ ] **Step 6: Write image-boundary tests**

Create a 4000×3000 bitmap, run it through `ImagePreprocessor`, and assert that its long edge is 2048 and JPEG bytes are at most `4 * 1024 * 1024`. Also assert that a corrupt stream maps to `AnalysisError.INVALID_OUTPUT` and processing runs with a test dispatcher rather than the caller thread.

- [ ] **Step 7: Implement bounded image preprocessing**

Decode image bounds first, choose an `inSampleSize` power of two, decode on `Dispatchers.IO`, scale the decoded bitmap so its long edge is at most 2048, and compress at qualities 85, 75, 65, 55, and 45 until the byte array is at most 4 MiB. If all qualities exceed 4 MiB, return `AnalysisError.IMAGE_TOO_LARGE`. Expose:

```kotlin
data class PreparedImage(val bitmap: Bitmap, val jpegBytes: ByteArray)

class ImagePreprocessor(
  private val contentResolver: ContentResolver,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun prepare(uri: Uri): Result<PreparedImage>
}
```

Use `BitmapFactory.Options.inJustDecodeBounds` for the first pass and `Bitmap.createScaledBitmap` for the final 2048-pixel bound; never call `BitmapFactory.decodeStream` from a composable callback.

- [ ] **Step 8: Run AI tests and commit**

```powershell
./gradlew.bat testDebugUnitTest --tests "com.example.ai.*"
./gradlew.bat lintDebug
git add build.gradle.kts gradle/libs.versions.toml app/build.gradle.kts app/google-services.json app/src/main/java/com/example/ai app/src/test/java/com/example/ai .env.example
git commit -m "feat: secure Gemini extraction with Firebase AI Logic"
```

Expected: parser and image tests pass, lint passes, and `rg "GEMINI_API_KEY|generativelanguage.googleapis.com" .` returns no production match.

---

### Task 4: Migrate Room to Accurate Action and Context State

**Files:**
- Modify: `app/src/main/java/com/example/data/ActionEntity.kt`
- Modify: `app/src/main/java/com/example/data/ActionDao.kt`
- Modify: `app/src/main/java/com/example/data/ActionRepository.kt`
- Modify: `app/src/main/java/com/example/data/AppDatabase.kt`
- Create: `app/src/main/java/com/example/data/UserContextEntity.kt`
- Create: `app/src/main/java/com/example/data/UserContextDao.kt`
- Create: `app/src/test/java/com/example/data/AppDatabaseMigrationTest.kt`
- Create: `app/schemas/com.example.data.AppDatabase/1.json`
- Create: `app/schemas/com.example.data.AppDatabase/2.json`

**Interfaces:**
- Consumes: `ExtractedAction` and `ScoreBreakdown` from Task 2.
- Produces: `ActionEntity.from(extracted, score, now)`, `complete(id, completedAtMs)`, `reopen(id)`, and a `Flow<UserContextEntity>`.

- [ ] **Step 1: Add the migration test before changing the schema**

Create a version-1 database row, run `MIGRATION_1_2`, and assert:

```kotlin
fun Cursor.nullableLong(column: String): Long? {
  val index = getColumnIndexOrThrow(column)
  return if (isNull(index)) null else getLong(index)
}

assertEquals(null, row.nullableLong("completedAtMs"))
assertEquals(null, row.nullableLong("deadlineMs"))
assertEquals(15, row.getInt("estimatedMinutes"))
assertEquals("Legacy action", row.getString("rationale"))
```

For a version-1 row where `isCompleted = 1`, assert `completedAtMs == timestamp`; this preserves the only available historical time while all new completions use the real completion time.

- [ ] **Step 2: Run the migration test and verify it fails**

```powershell
./gradlew.bat testDebugUnitTest --tests com.example.data.AppDatabaseMigrationTest
```

Expected: failure because database version 2 and `MIGRATION_1_2` do not exist.

- [ ] **Step 3: Replace the action row with version-2 fields**

Keep `id`, `title`, `description`, `source`, `timestamp`, and the seven score columns. Change/add:

```kotlin
val rationale: String,
val estimatedMinutes: Int,
val unblocksCount: Int,
val deadlineMs: Long?,
val completedAtMs: Long?,
```

Remove `isHardDeadline` and `isCompleted`; derive `isCompleted` as `completedAtMs != null` in UI/domain mapping. Add a factory that copies every score component from `ScoreBreakdown` and every lifecycle field from `ExtractedAction`.

- [ ] **Step 4: Add user context persistence**

Create a single-row entity:

```kotlin
@Entity(tableName = "user_context")
data class UserContextEntity(
  @PrimaryKey val id: Int = 1,
  val availableMinutes: Int = 30,
  val energy: String = "MEDIUM",
  val goalsJson: String = "[]",
)
```

Expose `observe(): Flow<UserContextEntity>` and `upsert(context)` in `UserContextDao`. Serialize goals as a list of `{id,name,primary}` records with kotlinx.serialization.

- [ ] **Step 5: Implement the migration and DAO lifecycle queries**

Use these SQL statements in `MIGRATION_1_2`:

```sql
ALTER TABLE actions ADD COLUMN rationale TEXT NOT NULL DEFAULT 'Legacy action';
ALTER TABLE actions ADD COLUMN estimatedMinutes INTEGER NOT NULL DEFAULT 15;
ALTER TABLE actions ADD COLUMN unblocksCount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE actions ADD COLUMN completedAtMs INTEGER;
UPDATE actions SET completedAtMs = timestamp WHERE isCompleted = 1;
CREATE TABLE IF NOT EXISTS user_context (
  id INTEGER NOT NULL PRIMARY KEY,
  availableMinutes INTEGER NOT NULL,
  energy TEXT NOT NULL,
  goalsJson TEXT NOT NULL
);
INSERT OR IGNORE INTO user_context (id, availableMinutes, energy, goalsJson)
VALUES (1, 30, 'MEDIUM', '[]');
```

Because SQLite cannot drop the old `isCompleted` and non-null `deadlineMs` columns in place, create `actions_new` with the exact version-2 schema, copy mapped data using `NULLIF(deadlineMs, 0)`, drop `actions`, and rename `actions_new` to `actions`. Export both schemas and let Room verify the final table shape.

Change DAO filters to `completedAtMs IS NULL` and `completedAtMs IS NOT NULL`; order completed actions by `completedAtMs DESC`. Implement completion atomically:

```kotlin
@Query("UPDATE actions SET completedAtMs = :completedAtMs WHERE id = :id AND completedAtMs IS NULL")
suspend fun complete(id: Int, completedAtMs: Long): Int

@Query("UPDATE actions SET completedAtMs = NULL WHERE id = :id")
suspend fun reopen(id: Int): Int
```

- [ ] **Step 6: Run migration and DAO tests**

```powershell
./gradlew.bat testDebugUnitTest --tests "com.example.data.*"
```

Expected: version-1 pending/completed rows migrate, new deadlines are nullable, completion is timestamped once, reopen clears completion, and history ordering follows `completedAtMs`.

- [ ] **Step 7: Commit persistence changes**

```powershell
git add app/src/main/java/com/example/data app/src/test/java/com/example/data app/schemas
git commit -m "feat: persist deadlines context and completion time"
```

---

### Task 5: Make Capture Retryable and ViewModel State Explicit

**Files:**
- Create: `app/src/main/java/com/example/ui/NavigatorUiState.kt`
- Modify: `app/src/main/java/com/example/ui/NavigatorViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/CaptureScreen.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Create: `app/src/test/java/com/example/ui/NavigatorViewModelTest.kt`

**Interfaces:**
- Consumes: `NavigatorAnalyzer`, `ImagePreprocessor`, `ActionRepository`, user context, and a clock `() -> Long`.
- Produces: `StateFlow<CaptureUiState>`, `submitCapture()`, `selectImage(uri)`, `removeImage()`, `dismissError()`, and `consumeSuccess()`.

- [ ] **Step 1: Write ViewModel failure/success tests with fakes**

Use a fake analyzer and repository. Assert:

```kotlin
viewModel.updateCaptureText("Call the insurer tomorrow")
fakeAnalyzer.next = AnalysisResult.Failure(AnalysisError.NETWORK)
viewModel.submitCapture()
advanceUntilIdle()
assertEquals("Call the insurer tomorrow", viewModel.captureState.value.text)
assertEquals(CaptureError.NETWORK, viewModel.captureState.value.error)

fakeAnalyzer.next = AnalysisResult.Success(validAction)
viewModel.submitCapture()
advanceUntilIdle()
assertEquals("", viewModel.captureState.value.text)
assertEquals(null, viewModel.captureState.value.imageUri)
assertEquals(true, viewModel.captureState.value.saved)
```

Also assert that repository insertion failure retains the text and maps to `CaptureError.STORAGE`.

- [ ] **Step 2: Run the ViewModel tests and confirm failure**

```powershell
./gradlew.bat testDebugUnitTest --tests com.example.ui.NavigatorViewModelTest
```

Expected: compilation fails because `CaptureUiState` and injected dependencies do not exist.

- [ ] **Step 3: Add immutable UI state and German error mapping**

Create:

```kotlin
enum class CaptureError { SETUP, NETWORK, IMAGE, OUTPUT, SERVICE, STORAGE }

data class CaptureUiState(
  val text: String = "",
  val imageUri: Uri? = null,
  val imagePreview: Bitmap? = null,
  val processing: Boolean = false,
  val error: CaptureError? = null,
  val saved: Boolean = false,
)

fun CaptureError.message(): String = when (this) {
  CaptureError.SETUP -> "Firebase ist noch nicht eingerichtet."
  CaptureError.NETWORK -> "Keine Verbindung. Dein Eingang bleibt für einen neuen Versuch erhalten."
  CaptureError.IMAGE -> "Das Bild konnte nicht verarbeitet werden oder ist zu groß."
  CaptureError.OUTPUT -> "Die Antwort war unvollständig. Bitte erneut versuchen oder den Text präzisieren."
  CaptureError.SERVICE -> "Der Analysedienst ist derzeit nicht verfügbar."
  CaptureError.STORAGE -> "Die Mission konnte lokal nicht gespeichert werden."
}
```

- [ ] **Step 4: Refactor ViewModel orchestration**

Inject `NavigatorAnalyzer`, `ImagePreprocessor`, `ActionRepository`, and `clock`. In `submitCapture()`:

1. Copy the current state to a local snapshot.
2. Set `processing = true`, leaving text and image unchanged.
3. Preprocess the selected URI off-main-thread.
4. Load current goals/context and call the analyzer.
5. Validate, score with `ActionScorer`, and insert with `ActionEntity.from(...)`.
6. Clear text/image only after `repository.insert` returns normally.
7. Map each failure to `CaptureError` and keep the snapshot in state.

Use `update {}` on a private `MutableStateFlow` and expose it with `asStateFlow()`.

- [ ] **Step 5: Make CaptureScreen a pure state renderer**

Move all URI decoding out of `onClick`. Add a visible `label = { Text("Eingang") }`, image preview with a labeled “Bild entfernen” button, an error `Snackbar` with “Erneut versuchen” and “Schließen”, and an `aria`-equivalent Compose live announcement through `LocalAccessibilityManager` when `saved` becomes true. The submit button calls only `viewModel.submitCapture()`.

Do not import `BitmapFactory`, `LocalContext`, or `CameraAlt` in `CaptureScreen.kt`.

- [ ] **Step 6: Provide dependencies through a ViewModel factory**

Create the Room database once in `MainActivity`, build `ActionRepository`, `ImagePreprocessor(contentResolver)`, and `FirebaseNavigatorAnalyzer`, then supply them through `viewModel(factory = NavigatorViewModel.factory(...))`. Close the database in `Application` process teardown only; do not recreate it during recomposition.

- [ ] **Step 7: Run tests and commit**

```powershell
./gradlew.bat testDebugUnitTest --tests com.example.ui.NavigatorViewModelTest
./gradlew.bat lintDebug
git add app/src/main/java/com/example/MainActivity.kt app/src/main/java/com/example/ui app/src/test/java/com/example/ui
git commit -m "feat: preserve and retry capture input"
```

---

### Task 6: Align Today, History, Context, and Navigation with the Product Promise

**Files:**
- Modify: `app/src/main/java/com/example/ui/NavigatorApp.kt`
- Modify: `app/src/main/java/com/example/ui/screens/TodayScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/HistoryScreen.kt`
- Replace: `app/src/main/java/com/example/ui/screens/AreasScreen.kt`
- Create: `app/src/test/java/com/example/domain/TodaySelectorTest.kt`
- Create: `app/src/test/java/com/example/ui/NavigatorScreensTest.kt`

**Interfaces:**
- Consumes: pending/completed action flows, `TodaySelection<ActionEntity>`, user context, completion/reopen methods.
- Produces: accurate main/warning/optional sections, Undo completion, accurate History, editable goals/context, and bounded tab navigation.

- [ ] **Step 1: Write Today selection tests**

Cover these scenarios:

```kotlin
assertEquals(highDeadline.id, TodaySelector.select(actions, now).main?.id)
assertFalse(TodaySelector.select(actions, now).warnings.any { it.id == highDeadline.id })
assertEquals(listOf(shortHighUnblock.id, shortLowerUnblock.id),
  TodaySelector.select(actions, now).optional.map { it.id })
assertTrue(TodaySelector.select(actions.filter { it.estimatedMinutes > 15 }, now).optional.isEmpty())
```

- [ ] **Step 2: Implement TodaySelector**

Add to `ActionScorer.kt`:

```kotlin
object TodaySelector {
  fun <T> select(
    actions: List<T>,
    now: Long,
    id: (T) -> Int,
    score: (T) -> Int,
    deadline: (T) -> Long?,
    estimatedMinutes: (T) -> Int,
    unblocks: (T) -> Int,
  ): TodaySelection<T> {
    val main = actions.maxByOrNull(score)
    val mainId = main?.let(id)
    val warnings = actions.filter { action ->
      id(action) != mainId && deadline(action)?.let { it in now..(now + 604_800_000L) } == true
    }.sortedBy { deadline(it) }
    val optional = actions.filter { id(it) != mainId && estimatedMinutes(it) in 5..15 }
      .sortedWith(compareByDescending<T> { unblocks(it) }.thenByDescending { score(it) })
      .take(2)
    return TodaySelection(main, warnings, optional)
  }
}
```

- [ ] **Step 3: Update Today cards and completion Undo**

Show the score, rationale, deadline, and estimated minutes as display text. Replace the empty-click `AssistChip` with `SuggestionChip(enabled = false, ...)` or plain `Text`. Completing calls `complete(id, clock())`, removes the card, and shows a Snackbar action “Rückgängig” that calls `reopen(id)`. The empty state includes a button that navigates to `capture`.

- [ ] **Step 4: Correct History**

Format `completedAtMs` and render `"Erledigt: ${format(completedAtMs)}"`. Never use `timestamp` for the completion label. Add a semantic empty state when the completed list is empty.

- [ ] **Step 5: Replace Areas prototype with goals and current context**

Render:

- A numeric available-minutes input constrained to 5–480.
- A single-choice energy control for `LOW`, `MEDIUM`, and `HIGH`.
- A goal list with name and primary toggle.
- Add, rename, toggle, and delete operations persisted through `UserContextDao`.

Use stable goal IDs generated with `UUID.randomUUID().toString()`. Remove all Drive, Gmail, OAuth, scan, and indexing copy and remove the hard-coded seven-area list.

- [ ] **Step 6: Bound bottom-navigation history**

Replace each tab navigation call with:

```kotlin
navController.navigate(route) {
  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
  launchSingleTop = true
  restoreState = true
}
```

Import `androidx.navigation.NavGraph.Companion.findStartDestination`. Preserve the four current labels: Heute, Eingang, Bereiche, Verlauf.

- [ ] **Step 7: Add Compose behavior tests**

Use `createComposeRule` with fake state to assert:

- all four bottom tabs navigate without duplicate destinations;
- Capture retains text after a fake network failure;
- Today includes a highest-scoring deadline as main;
- optional steps all show a duration from 5 through 15 minutes;
- completion shows Undo and restores the action;
- History displays `completedAtMs` rather than `timestamp`;
- Areas contains no Drive or Gmail text.

- [ ] **Step 8: Run UI tests and commit**

```powershell
./gradlew.bat testDebugUnitTest --tests "com.example.domain.TodaySelectorTest" --tests "com.example.ui.NavigatorScreensTest"
./gradlew.bat lintDebug
git add app/src/main/java/com/example/ui app/src/test/java/com/example/domain/TodaySelectorTest.kt app/src/test/java/com/example/ui/NavigatorScreensTest.kt
git commit -m "feat: align navigator screens with ranked action flow"
```

---

### Task 7: Lock Privacy, Screenshots, Documentation, and Release Checks

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Create: `app/src/test/java/com/example/NavigatorScreenshotTest.kt`
- Create: `app/src/test/screenshots/navigator-today.png`
- Create: `app/src/test/screenshots/navigator-capture-error.png`
- Modify: `README.md`

**Interfaces:**
- Consumes: completed V1 screens and Firebase configuration from Tasks 1–6.
- Produces: explicit privacy defaults, real visual regressions, and accurate setup/run documentation.

- [ ] **Step 1: Exclude local action data from backup**

Set `android:allowBackup="false"` in the manifest. Replace the XML files with explicit exclusions:

```xml
<full-backup-content>
    <exclude domain="database" path="." />
    <exclude domain="sharedpref" path="." />
</full-backup-content>
```

```xml
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 2: Add real Navigator screenshot tests**

Render `TodayScreen` with one warning, one main mission, and one optional step, then render `CaptureScreen` with retained input and a network Snackbar. Capture to the two exact PNG paths above with Pixel 8 qualifiers at SDK 36. Remove every `Greeting` name from test code and baselines.

- [ ] **Step 3: Rewrite local setup documentation**

Document these exact prerequisites and commands:

```text
Android Studio Panda 3 or newer
JDK 17
Android SDK Platform 36.1
Android SDK Build Tools 36.0.0
Firebase Android app com.aistudio.navigator.xptqmz
Firebase AI Logic enabled with Gemini Developer API
Firebase App Check debug provider for debug and Play Integrity for release
app/google-services.json present locally
```

Use `./gradlew.bat testDebugUnitTest lintDebug assembleDebug` as the verification command. Remove `.env`, upload-key reset, deleting signing lines, and direct Gemini-key instructions.

- [ ] **Step 4: Run the complete verification suite**

```powershell
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

Expected: build succeeds from a clean checkout with JDK 17 and the documented Android/Firebase setup; all unit, Room, Compose, and screenshot tests pass.

- [ ] **Step 5: Scan the APK inputs for forbidden claims and secrets**

```powershell
rg -n "GEMINI_API_KEY|generativelanguage.googleapis.com|Drive Hygiene|Gmail Kontext|Scan starten|Greeting" app README.md
```

Expected: no matches. Inspect `app/build/outputs/apk/debug/app-debug.apk` with Android Studio APK Analyzer and confirm there is no Gemini credential string.

- [ ] **Step 6: Commit the release-ready V1**

```powershell
git add app/src/main app/src/test README.md
git commit -m "docs: lock Navigator V1 privacy and verification"
```

---

## Self-Review Results

- **Spec coverage:** Tasks 1–7 cover reproducible builds, secure AI access, structured extraction, deterministic scoring, bounded images, Room migration, capture retention, Today selection, completion/history, Areas replacement, navigation, privacy, tests, screenshots, and documentation.
- **Scope control:** Drive, Gmail, voice, sync, account auth, automatic deletion, and collaboration remain excluded. No task adds them.
- **Type consistency:** `ExtractedAction`, `ScoringContext`, `ScoreBreakdown`, `AnalysisResult`, `NavigatorAnalyzer`, `CaptureUiState`, and `TodaySelection<T>` are defined before their consumers and retain the same names and field types throughout.
- **Execution dependency:** Task 3 requires a Firebase project and `app/google-services.json`; the Android code can be developed with App Check debug registration, while release verification additionally requires Play Integrity registration.
