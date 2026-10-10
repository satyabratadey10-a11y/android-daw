package com.android.daw

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.daw.diagnostics.CrashLogger
import com.android.daw.ui.studio.StudioScreen
import com.android.daw.ui.theme.DawTheme
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioViewModel

/**
 * MainActivity
 *
 * Primary entry point activity for Android Digital Audio Workstation.
 * Features:
 * - Edge-to-edge Jetpack Compose architecture (enableEdgeToEdge).
 * - High-frequency telemetry decoupling from Compose recomposition phases.
 * - Runtime microphone permission handling for low-latency live audio recording.
 * - Diagnostic Live Log viewer integration.
 */
class MainActivity : ComponentActivity() {

    private val requestRecordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            CrashLogger.i("MainActivity", "RECORD_AUDIO permission result: $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.i("MainActivity", "MainActivity onCreate started")

        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            CrashLogger.w("MainActivity", "enableEdgeToEdge fallback: ${t.message}")
        }

        // Check and prompt for RECORD_AUDIO permission if needed
        try {
            checkRecordAudioPermission()
        } catch (t: Throwable) {
            CrashLogger.w("MainActivity", "checkRecordAudioPermission fallback: ${t.message}")
        }

        setContent {
            DawTheme {
                val viewModel: StudioViewModel = viewModel()
                val uiState = viewModel.uiState.collectAsStateWithLifecycle()

                // Draw-phase lambda providers decoupling 60/120 FPS high-frequency telemetry
                // from the Composition phase (Rule 8 & High-Performance Telemetry Isolation)
                StudioScreen(
                    state = uiState.value,
                    playheadProvider = { viewModel.playheadFrame.value },
                    telemetryProvider = { viewModel.telemetryLevels.value },
                    masterLevelProvider = { uiState.value.masterLevels },
                    onAction = { action ->
                        if (action == StudioAction.OpenLiveLog) {
                            CrashLogger.i("MainActivity", "Launching Live Logcat & Diagnostics")
                            CrashActivity.startLiveLog(this@MainActivity)
                        } else {
                            viewModel.onAction(action)
                        }
                    }
                )
            }
        }
        CrashLogger.i("MainActivity", "MainActivity setContent initialized successfully")
    }

    private fun checkRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
