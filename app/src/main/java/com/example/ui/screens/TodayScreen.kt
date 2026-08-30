package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.ActionEntity
import com.example.ui.NavigatorViewModel
import androidx.compose.material3.CardDefaults

@Composable
fun TodayScreen(viewModel: NavigatorViewModel) {
    val pendingActions by viewModel.pendingActions.collectAsState()

    // 1 Main Mission (Highest totalScore)
    // 2 Optional small steps (High unblock, low effortMismatch, low risk)
    // Warnings: isHardDeadline = true
    
    val warnings = pendingActions.filter { it.isHardDeadline }
    val sortedOthers = pendingActions.filter { !it.isHardDeadline }.sortedByDescending { it.totalScore }
    
    val mainMission = sortedOthers.firstOrNull()
    val optionalSteps = sortedOthers.drop(1).take(2)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (warnings.isNotEmpty()) {
            item {
                Text("Warnings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            items(warnings) { action ->
                ActionCard(action = action, isWarning = true, onComplete = { viewModel.completeAction(action) })
            }
        }
        
        if (mainMission != null) {
            item {
                Text("Hauptmission", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                ActionCard(
                    action = mainMission, 
                    isMain = true,
                    onComplete = { viewModel.completeAction(mainMission) }
                )
            }
        } else if (warnings.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Missionen für heute. Alles erledigt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (optionalSteps.isNotEmpty()) {
            item {
                Text("Optionale Schritte (5-15 Min)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
            items(optionalSteps) { action ->
                ActionCard(action = action, onComplete = { viewModel.completeAction(action) })
            }
        }
    }
}

@Composable
fun ActionCard(action: ActionEntity, isMain: Boolean = false, isWarning: Boolean = false, onComplete: () -> Unit) {
    val cardColors = if (isWarning) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else if (isMain) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.title,
                        style = if (isMain) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        color = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Score: ${action.totalScore}") }
                        )
                        if (isWarning) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                IconButton(onClick = onComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle, 
                        contentDescription = "Erledigt",
                        tint = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
