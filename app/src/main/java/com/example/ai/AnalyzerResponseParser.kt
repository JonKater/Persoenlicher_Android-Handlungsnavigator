package com.example.ai

import com.example.domain.ActionValidator
import com.example.domain.EnergyLevel
import com.example.domain.ExtractedAction
import com.example.domain.ValidationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

internal class AnalyzerResponseParser(
  private val json: Json = Json { ignoreUnknownKeys = false; explicitNulls = false },
) {
  fun parseAndValidate(text: String, now: Long, allowedGoals: Set<String>): AnalysisResult {
    val dto = runCatching { json.decodeFromString<ExtractedActionDto>(text) }.getOrNull()
      ?: return AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    if (dto.goalId != null && dto.goalId !in allowedGoals) {
      return AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    }
    val energy = runCatching { EnergyLevel.valueOf(dto.requiredEnergy) }.getOrNull()
      ?: return AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    val candidate = ExtractedAction(
      dto.title,
      dto.description,
      dto.dueAtEpochMs,
      dto.consequenceSeverity,
      dto.financialExposureCents,
      dto.goalId,
      dto.unblocksCount,
      dto.estimatedMinutes,
      energy,
      dto.confidencePercent,
      dto.rationale,
    )
    return when (val validation = ActionValidator.validate(candidate, now)) {
      is ValidationResult.Valid -> AnalysisResult.Success(validation.action)
      is ValidationResult.Invalid -> AnalysisResult.Failure(AnalysisError.INVALID_OUTPUT)
    }
  }
}
