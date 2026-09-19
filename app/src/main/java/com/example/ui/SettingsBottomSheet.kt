package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.Direction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    uiState: TimingUiState,
    onSensitivityChanged: (luminanceThreshold: Int, triggerRatio: Float) -> Unit,
    onStripConfigChanged: (width: Int, position: Float, dualStrip: Boolean, direction: Direction) -> Unit,
    onRunnerDetailsChanged: (runner: String, event: String) -> Unit,
    onSimulate: (Direction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var lumThresh by remember { mutableIntStateOf(uiState.luminanceThreshold) }
    var trigRatio by remember { mutableFloatStateOf(uiState.triggerThresholdRatio) }
    var stripWidth by remember { mutableIntStateOf(uiState.stripWidth) }
    var stripPos by remember { mutableFloatStateOf(uiState.stripPositionRatio) }
    var dualStrip by remember { mutableStateOf(uiState.dualStripEnabled) }
    var direction by remember { mutableStateOf(uiState.directionFilter) }
    var runnerName by remember { mutableStateOf(uiState.runnerName) }
    var eventTitle by remember { mutableStateOf(uiState.eventTitle) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111827)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .testTag("settings_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FINISH DETECTION SETTINGS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Runner & Event Info
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = runnerName,
                    onValueChange = {
                        runnerName = it
                        onRunnerDetailsChanged(it, eventTitle)
                    },
                    label = { Text("Runner / Lane") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).testTag("runner_name_input")
                )

                OutlinedTextField(
                    value = eventTitle,
                    onValueChange = {
                        eventTitle = it
                        onRunnerDetailsChanged(runnerName, it)
                    },
                    label = { Text("Event Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).testTag("event_title_input")
                )
            }

            // Dual Strip Directional Timing
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2937), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dual-Strip Directional Check",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Requires Strip A → Strip B sequence",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = dualStrip,
                        onCheckedChange = {
                            dualStrip = it
                            onStripConfigChanged(stripWidth, stripPos, it, direction)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFF59E0B)
                        ),
                        modifier = Modifier.testTag("dual_strip_switch")
                    )
                }

                if (dualStrip) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Allowed Direction:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = direction == Direction.LEFT_TO_RIGHT,
                            onClick = {
                                direction = Direction.LEFT_TO_RIGHT
                                onStripConfigChanged(stripWidth, stripPos, dualStrip, Direction.LEFT_TO_RIGHT)
                            },
                            label = { Text("Left → Right") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF59E0B),
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = direction == Direction.RIGHT_TO_LEFT,
                            onClick = {
                                direction = Direction.RIGHT_TO_LEFT
                                onStripConfigChanged(stripWidth, stripPos, dualStrip, Direction.RIGHT_TO_LEFT)
                            },
                            label = { Text("Right → Left") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF59E0B),
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = direction == Direction.ANY,
                            onClick = {
                                direction = Direction.ANY
                                onStripConfigChanged(stripWidth, stripPos, dualStrip, Direction.ANY)
                            },
                            label = { Text("Any Direction") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF59E0B),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }

            // Luminance Difference Threshold Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Luminance Pixel Threshold (absdiff):",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "$lumThresh / 255",
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = lumThresh.toFloat(),
                    onValueChange = {
                        lumThresh = it.toInt()
                        onSensitivityChanged(lumThresh, trigRatio)
                    },
                    valueRange = 10f..80f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF59E0B),
                        activeTrackColor = Color(0xFFF59E0B)
                    ),
                    modifier = Modifier.testTag("lum_thresh_slider")
                )
            }

            // Trigger Motion Ratio Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Motion Trigger Ratio (ROI Coverage):",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${(trigRatio * 100).toInt()}%",
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = trigRatio,
                    onValueChange = {
                        trigRatio = it
                        onSensitivityChanged(lumThresh, trigRatio)
                    },
                    valueRange = 0.05f..0.50f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF59E0B),
                        activeTrackColor = Color(0xFFF59E0B)
                    ),
                    modifier = Modifier.testTag("trigger_ratio_slider")
                )
            }

            // Finish Line Position Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Finish Line Screen Position:",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${(stripPos * 100).toInt()}%",
                        color = Color(0xFF06B6D4),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = stripPos,
                    onValueChange = {
                        stripPos = it
                        onStripConfigChanged(stripWidth, stripPos, dualStrip, direction)
                    },
                    valueRange = 0.20f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF06B6D4),
                        activeTrackColor = Color(0xFF06B6D4)
                    ),
                    modifier = Modifier.testTag("strip_pos_slider")
                )
            }

            // Testing / Simulation Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Algorithm Simulation & Verification",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Inject a simulated sprint runner to verify the frame differencing, thresholds, and photo-finish buffer without a live track.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSimulate(Direction.LEFT_TO_RIGHT)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("simulate_ltr_button")
                    ) {
                        Icon(imageVector = Icons.Default.DirectionsRun, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate L → R", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onSimulate(Direction.RIGHT_TO_LEFT)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("simulate_rtl_button")
                    ) {
                        Icon(imageVector = Icons.Default.DirectionsRun, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate R → L", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
