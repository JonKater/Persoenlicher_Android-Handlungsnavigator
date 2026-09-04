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
