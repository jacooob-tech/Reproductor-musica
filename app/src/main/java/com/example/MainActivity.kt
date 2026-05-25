package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import com.example.data.MusicDatabase
import com.example.repository.MusicRepository
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.MusicViewModelFactory
import com.example.ui.screens.MusicPlayerApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val database = MusicDatabase.getDatabase(applicationContext)
    val repository = MusicRepository(database.musicDao())
    val factory = MusicViewModelFactory(application, repository)
    val viewModel = ViewModelProvider(this, factory)[MusicViewModel::class.java]

    setContent {
      val themeOption = viewModel.themeColorOption.collectAsState().value
      MyApplicationTheme(themeColorOption = themeOption) {
        MusicPlayerApp(viewModel = viewModel)
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}
