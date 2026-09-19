package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.vision.Direction

@Composable
fun TimingScreen(
    viewModel: TimingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedRuns by viewModel.savedRuns.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setCameraPermissionGranted(isGranted)
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setCameraPermissionGranted(isGranted)
    }

    Scaffold(
        containerColor = Color(0xFF0A0E17),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Header Bar
            TopBar(
                runnerName = uiState.runnerName,
                eventTitle = uiState.eventTitle,
                savedCount = savedRuns.size,
                onOpenHistory = { viewModel.setShowHistory(true) },
                onOpenSettings = { viewModel.setShowSettings(true) },
                onQuickSimulate = { viewModel.simulateRunner() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. High-Precision Digital Chronometer Display
            ChronometerDisplay(
                formattedTime = uiState.formattedTime,
                status = uiState.status,
                fps = uiState.fps,
                peakMotion = uiState.peakMotion
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Camera Viewport with Finish-Line Vision Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF030712))
                    .border(1.5.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .testTag("camera_viewport_container")
            ) {
                if (uiState.hasCameraPermission) {
                    CameraPreviewView(
                        detector = viewModel.detector,
                        isArmed = uiState.status == TimerStatus.RUNNING || uiState.status == TimerStatus.ARMED,
                        onResult = { result ->
                            viewModel.onCameraFrameResult(result)
                        }
                    )
                } else {
                    // Fallback when camera permission is not yet granted
                    CameraPermissionPrompt(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onSimulate = { viewModel.simulateRunner() }
                    )
                }

                // Overlay laser finish lines & live motion HUD
                FinishLineOverlay(
                    stripPositionRatio = uiState.stripPositionRatio,
                    stripWidthPx = uiState.stripWidth,
                    dualStripEnabled = uiState.dualStripEnabled,
                    motionA = uiState.motionA,
                    motionB = uiState.motionB,
                    thresholdRatio = uiState.triggerThresholdRatio,
                    detectedDirection = uiState.detectedDirection,
                    isTriggered = uiState.status == TimerStatus.FINISHED
                )

                // Viewport HUD telemetry badges (top-left and top-right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vision Mode badge
                    Box(
                        modifier = Modifier
                            .background(Color(0xCC0F172A), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x66F59E0B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.dualStripEnabled) "DUAL-STRIP [A → B]" else "SINGLE STRIP",
                            color = Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // FPS & Algorithm badge
                    Box(
                        modifier = Modifier
                            .background(Color(0xCC0F172A), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x6606B6D4), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Y-LUM DIFF • ${uiState.fps} FPS",
                            color = Color(0xFF22D3EE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Live Motion Intensity Bar
            MotionIntensityBar(
                motionA = uiState.motionA,
                motionB = uiState.motionB,
                threshold = uiState.triggerThresholdRatio,
                dualStrip = uiState.dualStripEnabled
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Sprint Timing Controls (Bottom Bar)
            TimingControls(
                status = uiState.status,
                isSimulating = uiState.isSimulating,
                onStart = { viewModel.startTimer() },
                onArmOnly = { viewModel.armDetectorOnly() },
                onReset = { viewModel.resetTimer() },
                onViewPhotoFinish = { viewModel.setShowPhotoFinish(true) },
                onSimulate = { viewModel.simulateRunner() }
            )
        }

        // Dialogs and Bottom Sheets
        if (uiState.showPhotoFinishDialog) {
            PhotoFinishDialog(
                formattedTime = uiState.formattedTime,
                timestampNs = uiState.finishCameraTimestampNs,
                direction = uiState.detectedDirection,
                peakMotion = uiState.peakMotion,
                photoFinishBitmap = uiState.photoFinishBitmap,
                runnerName = uiState.runnerName,
                eventTitle = uiState.eventTitle,
                onSave = { viewModel.saveCurrentRun() },
                onDismiss = { viewModel.setShowPhotoFinish(false) }
            )
        }

        if (uiState.showSettingsSheet) {
            SettingsBottomSheet(
                uiState = uiState,
                onSensitivityChanged = { lum, trig ->
                    viewModel.updateSensitivity(lum, trig)
                },
                onStripConfigChanged = { width, pos, dual, dir ->
                    viewModel.updateStripConfig(width, pos, dual, dir)
                },
                onRunnerDetailsChanged = { runner, event ->
                    viewModel.updateRunnerDetails(runner, event)
                },
                onRepoSlugChanged = { slug ->
                    viewModel.updateRepoSlug(slug)
                },
                onCheckForUpdates = {
                    viewModel.checkForUpdates()
                },
                onSimulate = { dir ->
                    viewModel.simulateRunner(dir)
                },
                onDismiss = { viewModel.setShowSettings(false) }
            )
        }

        if (uiState.showUpdateDialog) {
            UpdateDialog(
                updateInfo = uiState.updateInfo,
                onDismiss = { viewModel.setShowUpdateDialog(false) }
            )
        }

        if (uiState.showHistorySheet) {
            HistoryBottomSheet(
                runs = savedRuns,
                onDeleteRun = { viewModel.deleteRun(it) },
                onClearAll = { viewModel.clearAllRuns() },
                onDismiss = { viewModel.setShowHistory(false) }
            )
        }
    }
}

@Composable
private fun TopBar(
    runnerName: String,
    eventTitle: String,
    savedCount: Int,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuickSimulate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFF59E0B), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FINISHLINE",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }
            Text(
                text = "$eventTitle • $runnerName",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onQuickSimulate, modifier = Modifier.testTag("quick_simulate_button")) {
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = "Simulate Runner",
                    tint = Color(0xFFF59E0B)
                )
            }

            IconButton(onClick = onOpenHistory, modifier = Modifier.testTag("open_history_button")) {
                BadgedBox(
                    badge = {
                        if (savedCount > 0) {
                            Badge(containerColor = Color(0xFFF59E0B), contentColor = Color.Black) {
                                Text("$savedCount")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Run History",
                        tint = Color.White
                    )
                }
            }

            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings_button")) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ChronometerDisplay(
    formattedTime: String,
    status: TimerStatus,
    fps: Int,
    peakMotion: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chronometer_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Pill
            val (statusText, statusBg, statusColor) = when (status) {
                TimerStatus.IDLE -> Triple("IDLE - READY", Color(0xFF1F2937), Color.LightGray)
                TimerStatus.ARMED -> Triple("BEAM ARMED", Color(0xFF042F2E), Color(0xFF2DD4BF))
                TimerStatus.RUNNING -> Triple("SPRINT RUNNING", Color(0xFF451A03), Color(0xFFF59E0B))
                TimerStatus.FINISHED -> Triple("FINISH TRIGGERED!", Color(0xFF450A0A), Color(0xFFEF4444))
            }

            Box(
                modifier = Modifier
                    .background(statusBg, RoundedCornerShape(20.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Digital Numbers
            Text(
                text = formattedTime,
                color = when (status) {
                    TimerStatus.FINISHED -> Color(0xFFEF4444)
                    TimerStatus.RUNNING -> Color(0xFFF59E0B)
                    else -> Color.White
                },
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun MotionIntensityBar(
    motionA: Float,
    motionB: Float,
    threshold: Float,
    dualStrip: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111827), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (dualStrip) "STRIP A: ${(motionA * 100).toInt()}% • FINISH B: ${(motionB * 100).toInt()}%" else "FINISH MOTION: ${(motionB * 100).toInt()}%",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "TRIGGER THRESHOLD: ${(threshold * 100).toInt()}%",
                color = Color(0xFFF59E0B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { motionB.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (motionB >= threshold) Color(0xFFEF4444) else Color(0xFFF59E0B),
            trackColor = Color(0xFF1F2937)
        )
    }
}

@Composable
private fun TimingControls(
    status: TimerStatus,
    isSimulating: Boolean,
    onStart: () -> Unit,
    onArmOnly: () -> Unit,
    onReset: () -> Unit,
    onViewPhotoFinish: () -> Unit,
    onSimulate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            TimerStatus.IDLE -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .testTag("start_sprint_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("START SPRINT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = onArmOnly,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("arm_detector_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFF06B6D4))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ARM BEAM", color = Color(0xFF06B6D4), fontSize = 13.sp)
                }
            }

            TimerStatus.RUNNING, TimerStatus.ARMED -> {
                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("reset_sprint_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESET", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            TimerStatus.FINISHED -> {
                Button(
                    onClick = onViewPhotoFinish,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp)
                        .testTag("view_photo_finish_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PHOTO FINISH", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("new_sprint_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NEXT", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    onSimulate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Camera Access Required",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "FinishLine requires camera access to process raw luminance frames at high FPS.",
                color = Color.LightGray,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                modifier = Modifier.testTag("request_camera_button")
            ) {
                Text("Enable Camera", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSimulate, modifier = Modifier.testTag("test_simulation_prompt_button")) {
                Text("Or Test With Runner Simulator", color = Color(0xFF06B6D4))
            }
        }
    }
}
