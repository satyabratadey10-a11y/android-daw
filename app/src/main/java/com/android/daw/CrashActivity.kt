package com.android.daw

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.daw.diagnostics.CrashLogger
import com.android.daw.ui.components.DawIconButton
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer

/**
 * CrashActivity
 *
 * Dedicated isolated crash recovery and live diagnostics screen.
 * Displays:
 * - Intercepted crash stack traces or live system logcat.
 * - 1-Click "Copy Entire Log" button to system clipboard.
 * - 1-Click "Save log.txt to Downloads" button targeting public external storage.
 * - "Restart App" button to cleanly revive the DAW studio.
 *
 * Strictly adheres to Compose-only architecture (0 XML, Palette-only colors, Layer.* zIndex).
 */
class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CRASH_REPORT = "extra_crash_report"
        const val EXTRA_IS_CRASH = "extra_is_crash"

        fun startLiveLog(context: Context) {
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(EXTRA_IS_CRASH, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {}

        val initialReport = intent.getStringExtra(EXTRA_CRASH_REPORT)
            ?: CrashLogger.lastCrashReport
            ?: CrashLogger.buildFullDiagnosticReport(this, "Live diagnostics inspection session.")
        val isCrash = intent.getBooleanExtra(EXTRA_IS_CRASH, true)

        setContent {
            DawTheme {
                CrashScreenContent(
                    initialReport = initialReport,
                    isCrash = isCrash,
                    onCopyToClipboard = { text ->
                        val success = CrashLogger.copyToClipboard(this, text)
                        Toast.makeText(
                            this,
                            if (success) "Entire log copied to clipboard!" else "Failed to copy to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onSaveToDownloads = { text ->
                        val result = CrashLogger.saveLogToDownloads(this, text)
                        result.onSuccess { path ->
                            Toast.makeText(
                                this,
                                "Successfully saved log.txt to: $path",
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(
                                this,
                                "Failed to save to Downloads: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onRefreshLogs = {
                        CrashLogger.buildFullDiagnosticReport(this, if (isCrash) "Crash Log Refresh" else null)
                    },
                    onRestartApp = {
                        val restartIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restartIntent)
                        finish()
                    },
                    onClose = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun CrashScreenContent(
    initialReport: String,
    isCrash: Boolean,
    onCopyToClipboard: (String) -> Unit,
    onSaveToDownloads: (String) -> Unit,
    onRefreshLogs: () -> String,
    onRestartApp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentReport by remember { mutableStateOf(initialReport) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Daw.colors.n0Workspace)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .zIndex(Layer.Workspace)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Daw.space.sm)
                .zIndex(Layer.Content)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Daw.colors.n2Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = Daw.space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCrash) Daw.colors.coral.base else Daw.colors.mint.base)
                    )
                    Spacer(modifier = Modifier.width(Daw.space.sm))
                    Text(
                        text = if (isCrash) "CRASH LOG CATCHER" else "LIVE LOGCAT & DIAGNOSTICS",
                        color = Daw.colors.inkOnDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
                ) {
                    // Refresh Button
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Daw.colors.n3Raised)
                            .clickable { currentReport = onRefreshLogs() }
                            .padding(horizontal = Daw.space.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Refresh Logcat",
                            color = Daw.colors.inkOnDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Close / Back button
                    DawIconButton(
                        icon = DawIcons.Close,
                        contentDescription = "Close Diagnostics",
                        onClick = onClose,
                        tint = Daw.colors.inkMuted,
                        size = 32.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(Daw.space.xs))

            // Informative Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isCrash) Daw.colors.coral.faint else Daw.colors.mint.faint,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = Daw.space.md, vertical = Daw.space.xs)
            ) {
                Text(
                    text = if (isCrash) {
                        "The application encountered an unexpected runtime exception. The crash details, device telemetry, and logcat have been captured below."
                    } else {
                        "Live system and DAW telemetry logs. Tap 'Copy Entire Log' to clipboard or 'Save log.txt to Downloads' below."
                    },
                    color = Daw.colors.inkOnDark,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(Daw.space.xs))

            // Monospace Scrollable Log Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Daw.colors.n1Grid, RoundedCornerShape(6.dp))
                    .border(1.dp, Daw.colors.n3Raised, RoundedCornerShape(6.dp))
                    .padding(Daw.space.sm)
            ) {
                SelectionContainer {
                    Text(
                        text = currentReport,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Daw.colors.inkOnDark,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Daw.space.xs))

            // Action Tray
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Daw.colors.n2Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = Daw.space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
            ) {
                // Button 1: Copy to Clipboard
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Daw.colors.sky.base)
                        .clickable { onCopyToClipboard(currentReport) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DawIconButton(
                            icon = DawIcons.Copy,
                            contentDescription = "Copy Log",
                            onClick = { onCopyToClipboard(currentReport) },
                            tint = Daw.colors.inkOnLight,
                            size = 20.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy Entire Log",
                            color = Daw.colors.inkOnLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 2: Save to Downloads
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Daw.colors.mint.base)
                        .clickable { onSaveToDownloads(currentReport) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DawIconButton(
                            icon = DawIcons.Save,
                            contentDescription = "Save to Downloads",
                            onClick = { onSaveToDownloads(currentReport) },
                            tint = Daw.colors.inkOnLight,
                            size = 20.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save log.txt to Downloads",
                            color = Daw.colors.inkOnLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 3: Restart Studio
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Daw.colors.n3Raised)
                        .clickable { onRestartApp() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Restart App",
                        color = Daw.colors.inkOnDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
