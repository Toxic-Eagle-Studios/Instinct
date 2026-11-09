package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.AppNavigation
import com.example.ui.InstinctViewModel
import com.example.ui.theme.InstinctTheme
import com.example.ui.theme.JetBlack
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
  private val viewModel: InstinctViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // App Check MUST be configured with launch intent before any UI or Firebase operations
    configureAppCheck(this, intent)

    enableEdgeToEdge()

    setContent {
      InstinctTheme(darkTheme = true) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = JetBlack
        ) {
          AppNavigation(viewModel = viewModel)
        }
      }
    }
  }
}
