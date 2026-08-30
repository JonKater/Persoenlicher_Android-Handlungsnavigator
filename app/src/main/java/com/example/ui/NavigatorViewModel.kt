package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActionEntity
import com.example.data.ActionRepository
import com.example.ai.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.graphics.Bitmap

class NavigatorViewModel(
    private val repository: ActionRepository,
    private val geminiService: GeminiService = GeminiService()
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
                val action = geminiService.analyzeCapture(text, image)
                if (action != null) {
                    repository.insert(action)
                } else {
                    errorMessage.value = "Failed to parse action from input."
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
