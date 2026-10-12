package com.snapcal.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.snapcal.app.ui.CaptureScreen
import com.snapcal.app.ui.HistoryScreen
import com.snapcal.app.ui.HomeScreen
import com.snapcal.app.ui.MainViewModel
import com.snapcal.app.ui.MealDetailScreen
import com.snapcal.app.ui.OnboardingScreen
import com.snapcal.app.ui.Screen
import com.snapcal.app.ui.SettingsScreen
import com.snapcal.app.ui.ScaleScreen
import com.snapcal.app.ui.SnapCalTheme
import com.snapcal.app.ui.WeightScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            SnapCalTheme {
                val screen by vm.screen.collectAsState()
                BackHandler(enabled = screen != Screen.Home && vm.canGoBack()) { vm.back() }
                when (val s = screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Capture -> CaptureScreen(vm)
                    is Screen.MealDetail -> MealDetailScreen(vm, s.id)
                    Screen.History -> HistoryScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.Onboarding -> OnboardingScreen(vm)
                    Screen.Weight -> WeightScreen(vm)
                    Screen.Scale -> ScaleScreen(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshEnergy()
        if (vm.settings.scale != null) vm.startScaleScan(background = true)
    }

    override fun onPause() {
        super.onPause()
        vm.stopScaleScan()
    }

    /** Launcher long-press shortcut "拍一顿" jumps straight into the camera. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_SNAP && vm.settings.onboarded) vm.startCapture()
    }

    companion object {
        const val ACTION_SNAP = "com.snapcal.app.SNAP"
    }
}
