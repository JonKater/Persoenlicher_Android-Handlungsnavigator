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
