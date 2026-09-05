package com.example.ai

import com.example.domain.GoalOption
import com.example.domain.ScoringContext
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import kotlinx.serialization.json.Json

class FirebaseNavigatorAnalyzer(
  json: Json = Json { ignoreUnknownKeys = false; explicitNulls = false },
) : NavigatorAnalyzer {
  private val parser = AnalyzerResponseParser(json)

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

  private val model by lazy {
    Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
      modelName = "gemini-3.6-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = schema
        temperature = 0.1f
      },
    )
  }

  override suspend fun analyze(
    payload: CapturePayload,
    context: ScoringContext,
    goals: List<GoalOption>,
  ): AnalysisResult = try {
    FirebaseApp.getInstance()
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
    parser.parseAndValidate(responseText, context.nowEpochMs, goals.map { it.id }.toSet())
  } catch (error: IllegalStateException) {
    val setupMissing = runCatching { FirebaseApp.getInstance() }.isFailure
    AnalysisResult.Failure(if (setupMissing) AnalysisError.SETUP_MISSING else AnalysisError.SERVICE)
  } catch (error: IOException) {
    AnalysisResult.Failure(AnalysisError.NETWORK)
  } catch (error: Exception) {
    AnalysisResult.Failure(AnalysisError.SERVICE)
  }

  internal fun parseAndValidate(text: String, now: Long, allowedGoals: Set<String>): AnalysisResult =
    parser.parseAndValidate(text, now, allowedGoals)
}
