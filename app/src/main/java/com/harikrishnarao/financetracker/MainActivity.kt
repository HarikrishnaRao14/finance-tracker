package com.harikrishnarao.financetracker

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.MainScaffold
import com.harikrishnarao.financetracker.ui.RootViewModel
import com.harikrishnarao.financetracker.ui.onboarding.OnboardingScreen
import com.harikrishnarao.financetracker.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so system bar icons are always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val root: RootViewModel = viewModel(factory = AppViewModelFactory)
            val settings by root.settings.collectAsStateWithLifecycle()
            val s = settings
            if (s != null) {
                FinanceTheme(theme = s.theme) {
                    if (s.onboardingDone) MainScaffold() else OnboardingScreen()
                }
            }
        }
    }
}
