package com.example.ai

import com.example.domain.EnergyLevel
import com.example.domain.ExtractedAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyzerResponseParserTest {
  private val parser = AnalyzerResponseParser()
  private val now = 1_800_000_000_000L
  private val allowedGoals = setOf("health", "finance")

  @Test
  fun `valid JSON produces the exact extracted action`() {
    val expected = ExtractedAction(
      title = "Call the dentist",
      description = "Book the earliest available appointment.",
      dueAtEpochMs = 1_800_086_400_000L,
      consequenceSeverity = 7,
      financialExposureCents = 12_345L,
      goalId = "health",
      unblocksCount = 2,
      estimatedMinutes = 15,
      requiredEnergy = EnergyLevel.MEDIUM,
      confidencePercent = 92,
      rationale = "The tooth pain may worsen without treatment.",
    )

    assertEquals(AnalysisResult.Success(expected), parser.parseAndValidate(validJson, now, allowedGoals))
  }

  @Test
  fun `invalid JSON returns invalid output`() {
    assertInvalid("{not-json")
  }

  @Test
  fun `missing required field returns invalid output`() {
    assertInvalid(validJson.replace("\n  \"title\": \"Call the dentist\",", ""))
  }

  @Test
  fun `unknown field returns invalid output`() {
    assertInvalid(validJson.replace("{", "{\n  \"unexpected\": true,"))
  }

  @Test
  fun `unknown energy returns invalid output`() {
    assertInvalid(validJson.replace("\"MEDIUM\"", "\"EXTREME\""))
  }

  @Test
  fun `unknown goal returns invalid output`() {
    assertInvalid(validJson.replace("\"health\"", "\"unknown\""))
  }

  @Test
  fun `out of range severity returns invalid output`() {
    assertInvalid(validJson.replace("\"consequenceSeverity\": 7", "\"consequenceSeverity\": 11"))
  }

  @Test
  fun `out of range financial exposure returns invalid output`() {
    assertInvalid(validJson.replace("\"financialExposureCents\": 12345", "\"financialExposureCents\": -1"))
  }

  @Test
  fun `out of range unblocks count returns invalid output`() {
    assertInvalid(validJson.replace("\"unblocksCount\": 2", "\"unblocksCount\": 4"))
  }

  @Test
  fun `out of range estimated minutes returns invalid output`() {
    assertInvalid(validJson.replace("\"estimatedMinutes\": 15", "\"estimatedMinutes\": 481"))
  }

  @Test
  fun `out of range confidence returns invalid output`() {
    assertInvalid(validJson.replace("\"confidencePercent\": 92", "\"confidencePercent\": 101"))
  }

  private fun assertInvalid(json: String) {
    assertEquals(
      AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT),
      parser.parseAndValidate(json, now, allowedGoals),
    )
  }

  private companion object {
    val validJson = """
      {
        "title": "Call the dentist",
        "description": "Book the earliest available appointment.",
        "dueAtEpochMs": 1800086400000,
        "consequenceSeverity": 7,
        "financialExposureCents": 12345,
        "goalId": "health",
        "unblocksCount": 2,
        "estimatedMinutes": 15,
        "requiredEnergy": "MEDIUM",
        "confidencePercent": 92,
        "rationale": "The tooth pain may worsen without treatment."
      }
    """.trimIndent()
  }
}
