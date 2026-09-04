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
  suspend fun analyze(
    payload: CapturePayload,
    context: ScoringContext,
    goals: List<GoalOption>,
  ): AnalysisResult
}
