package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {
  private val now = 1_800_000_000_000L

  private fun action(
    title: String = "Call",
    description: String = "Make the call",
    dueAt: Long? = null,
    consequence: Int = 0,
    cents: Long = 0,
    unblocks: Int = 0,
    minutes: Int = 15,
    confidence: Int = 100,
    rationale: String = "Deadline and impact",
  ) = ExtractedAction(
    title, description, dueAt, consequence, cents, "income", unblocks, minutes,
    EnergyLevel.MEDIUM, confidence, rationale,
  )

  @Test fun invalidValuesListTheirFieldsInValidationOrder() {
    val invalidAction = action(
      title = "   ",
      dueAt = now,
      cents = -1,
      unblocks = 4,
      minutes = 0,
      confidence = 101,
    )

    val result = ActionValidator.validate(invalidAction, nowEpochMs = now)

    assertEquals(
      listOf("title", "dueAtEpochMs", "financialExposureCents", "unblocksCount", "estimatedMinutes", "confidencePercent"),
      (result as ValidationResult.Invalid).fields,
    )
  }

  @Test fun titleWithEightyOneCharactersIsInvalid() {
    val result = ActionValidator.validate(action(title = "x".repeat(81)), now)

    assertEquals(listOf("title"), (result as ValidationResult.Invalid).fields)
  }

  @Test fun pastDeadlineIsInvalid() {
    val result = ActionValidator.validate(action(dueAt = now - 1), now)

    assertEquals(listOf("dueAtEpochMs"), (result as ValidationResult.Invalid).fields)
  }

  @Test fun boundaryValuesAreValid() {
    val result = ActionValidator.validate(
      action(
        title = "x",
        description = "d".repeat(500),
        dueAt = now + 1,
        consequence = 10,
        cents = 10_000_000L,
        unblocks = 3,
        minutes = 480,
        confidence = 100,
        rationale = "r".repeat(500),
      ),
      now,
    )

    assertTrue(result is ValidationResult.Valid)
  }
}
