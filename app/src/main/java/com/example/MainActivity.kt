package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.CompanionViewModel
import com.example.ui.MythicScriptApp
import com.example.ui.theme.MythicScriptTheme

class MainActivity : ComponentActivity() {

    private val companionViewModel: CompanionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MythicScriptTheme {
                MythicScriptApp(viewModel = companionViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        companionViewModel.refreshShizukuStatus()
    }
}
