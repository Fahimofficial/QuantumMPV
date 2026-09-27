package com.quantummpv.app.presentation.crash

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.quantummpv.app.ui.theme.MpvrxTheme

class DebugLogsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MpvrxTheme {
        DebugLogsScreen(onNavigateBack = ::finish)
      }
    }
  }
}