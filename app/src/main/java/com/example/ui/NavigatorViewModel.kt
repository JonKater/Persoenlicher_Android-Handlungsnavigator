package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActionEntity
import com.example.data.ActionRepository
import com.example.ai.AnalysisResult
import com.example.ai.CapturePayload
import com.example.ai.FirebaseNavigatorAnalyzer
import com.example.ai.NavigatorAnalyzer
import com.example.domain.ActionScorer
import com.example.domain.EnergyLevel
import com.example.domain.ScoringContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.graphics.Bitmap

class NavigatorViewModel(
    private val repository: ActionRepository,
    private val analyzer: NavigatorAnalyzer = FirebaseNavigatorAnalyzer()
) : ViewModel() {

    val pendingActions: StateFlow<List<ActionEntity>> = repository.pendingActions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val completedActions: StateFlow<List<ActionEntity>> = repository.completedActions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isProcessing = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    fun processInput(text: String, image: Bitmap? = null) {
        if (text.isBlank() && image == null) return
        viewModelScope.launch {
            isProcessing.value = true
            errorMessage.value = null
            try {
                val now = System.currentTimeMillis()
                val context = ScoringContext(now, 30, EnergyLevel.MEDIUM, emptySet())
                when (val result = analyzer.analyze(CapturePayload(text, image), context, emptyList())) {
                    is AnalysisResult.Success -> {
                        val score = ActionScorer.score(result.action, context)
                        repository.insert(ActionEntity(
                            title = result.action.title,
                            description = result.action.description,
                            source = if (image == null) "Text" else "Image",
                            urgencyRisk = score.urgencyRisk,
                            financial = score.financial,
                            goalFit = score.goalFit,
                            unblock = score.unblock,
                            contextFit = score.contextFit,
                            uncertainty = score.uncertainty,
                            effortMismatch = score.effortMismatch,
                            isHardDeadline = result.action.dueAtEpochMs != null,
                            deadlineMs = result.action.dueAtEpochMs ?: 0L,
                            timestamp = now,
                        ))
                    }
                    is AnalysisResult.Failure -> errorMessage.value =
                        "Analysis failed: ${result.error.name.lowercase()}"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun completeAction(action: ActionEntity) {
        viewModelScope.launch {
            repository.update(action.copy(isCompleted = true))
        }
    }
    
    fun dismissError() {
        errorMessage.value = null
    }
}
