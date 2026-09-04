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
