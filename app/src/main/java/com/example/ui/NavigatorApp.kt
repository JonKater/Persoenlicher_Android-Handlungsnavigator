package com.example.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.AreasScreen
import com.example.ui.screens.HistoryScreen

@Composable
fun NavigatorApp(viewModel: NavigatorViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Heute") },
                    label = { Text("Heute") },
                    selected = currentRoute == "today",
                    onClick = {
                        if (currentRoute != "today") navController.navigate("today")
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Eingang") },
                    label = { Text("Eingang") },
                    selected = currentRoute == "capture",
                    onClick = {
                        if (currentRoute != "capture") navController.navigate("capture")
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Bereiche") },
                    label = { Text("Bereiche") },
                    selected = currentRoute == "areas",
                    onClick = {
                        if (currentRoute != "areas") navController.navigate("areas")
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "Verlauf") },
                    label = { Text("Verlauf") },
                    selected = currentRoute == "history",
                    onClick = {
                        if (currentRoute != "history") navController.navigate("history")
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("today") { TodayScreen(viewModel) }
            composable("capture") { CaptureScreen(viewModel) }
            composable("areas") { AreasScreen() }
            composable("history") { HistoryScreen(viewModel) }
        }
    }
}
