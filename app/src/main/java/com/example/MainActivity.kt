package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.ActionRepository
import com.example.ui.NavigatorApp
import com.example.ui.NavigatorViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val db = Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java, "navigator-database"
    ).build()
    
    val repository = ActionRepository(db.actionDao())

    setContent {
      val viewModel: NavigatorViewModel = androidx.lifecycle.viewmodel.compose.viewModel { NavigatorViewModel(repository) }
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavigatorApp(viewModel)
        }
      }
    }
  }
}
