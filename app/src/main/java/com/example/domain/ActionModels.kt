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
