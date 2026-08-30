package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn

@Composable
fun AreasScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Bereiche & Hygiene-Gate", style = MaterialTheme.typography.headlineMedium)
            Text("Daten-Inventar und Kontext-Management", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Drive Hygiene", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google Drive Hygiene-Gate", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analysiert Namenskonflikte, Duplikate und veraltete Versionen.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { /* TODO: Trigger Drive Scan */ }, modifier = Modifier.align(Alignment.End)) {
                        Text("Scan starten (OAuth benötigt)")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = "Gmail Index")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gmail Kontext-Index", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Kontoweite Suche und Indexierung für Entwürfe. Keine automatische Löschung in V1.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        item {
            Text("Projekte & Lebensbereiche", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        }
        
        val areas = listOf("Verwaltung", "EPA", "Akquise", "Bewerbung", "Garten", "Musik", "Forschung")
        items(areas.size) { index ->
            ListItem(
                headlineContent = { Text(areas[index]) },
                trailingContent = { Icon(Icons.Default.Check, contentDescription = "Aktiv", tint = MaterialTheme.colorScheme.primary) }
            )
            Divider()
        }
    }
}
