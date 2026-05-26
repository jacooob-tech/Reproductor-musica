package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.MusicDatabase
import com.example.repository.MusicRepository
import com.example.ui.screens.MusicPlayerScreen
import com.example.ui.theme.MusicTheme
import com.example.ui.theme.ThemeColorOption
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.MusicViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dynamic Edge-to-Edge full bleeds support
        enableEdgeToEdge()

        // Init local Database & Repository
        val database = MusicDatabase.getDatabase(applicationContext)
        val repository = MusicRepository(database.musicDao())
        
        // Bind Core ViewModel
        val factory = MusicViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[MusicViewModel::class.java]

        setContent {
            var selectedTheme by remember { mutableStateOf(ThemeColorOption.DARK_SLATE) }

            MusicTheme(themeOption = selectedTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MusicPlayerScreen(
                        viewModel = viewModel,
                        activeTheme = selectedTheme,
                        onThemeChanged = { selectedTheme = it }
                    )
                }
            }
        }
    }
}
