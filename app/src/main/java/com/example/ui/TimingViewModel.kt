package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TimingRepository
import com.example.data.TimingRunEntity
import com.example.vision.DetectionResult
import com.example.vision.Direction
import com.example.vision.FinishLineDetector
import com.example.vision.SimulationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TimerStatus {
    IDLE,
    ARMED,
    RUNNING,
    FINISHED
}

data class TimingUiState(
    val status: TimerStatus = TimerStatus.IDLE,
    val startTimeMs: Long = 0L,
    val elapsedTimeMs: Long = 0L,
    val formattedTime: String = "00:00.000",
    val finishCameraTimestampNs: Long = 0L,
    val finishWallClockMs: Long = 0L,
    val motionA: Float = 0f,
    val motionB: Float = 0f,
    val peakMotion: Float = 0f,
    val detectedDirection: Direction = Direction.NONE,
    val verticalProfile: FloatArray = FloatArray(16),
    val fps: Int = 30,
    val photoFinishBitmap: Bitmap? = null,
    // Settings
    val dualStripEnabled: Boolean = true,
    val directionFilter: Direction = Direction.LEFT_TO_RIGHT,
    val luminanceThreshold: Int = 30,
    val triggerThresholdRatio: Float = 0.18f,
    val stripPositionRatio: Float = 0.50f,
    val stripWidth: Int = 32,
    val runnerName: String = "Runner #1",
    val eventTitle: String = "100m Sprint",
    // Dialogs / Sheets
    val showPhotoFinishDialog: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showHistorySheet: Boolean = false,
    val isSimulating: Boolean = false,
    val hasCameraPermission: Boolean = false
)

class TimingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TimingRepository
    val detector = FinishLineDetector()
    private val simulator = SimulationEngine()

    private val _uiState = MutableStateFlow(TimingUiState())
    val uiState: StateFlow<TimingUiState> = _uiState.asStateFlow()

    val savedRuns: StateFlow<List<TimingRunEntity>>

    private var timerJob: Job? = null
    private var simulationJob: Job? = null
    private var frameCounter = 0
    private var lastFpsCalculationMs = System.currentTimeMillis()

    init {
        val db = AppDatabase.getInstance(application)
        repository = TimingRepository(db.timingDao())
        savedRuns = repository.allRuns.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        syncDetectorSettings()
    }

    private fun syncDetectorSettings() {
        val s = _uiState.value
        detector.luminanceThreshold = s.luminanceThreshold
        detector.triggerThresholdRatio = s.triggerThresholdRatio
        detector.stripWidth = s.stripWidth
        detector.stripPositionRatio = s.stripPositionRatio
        detector.dualStripEnabled = s.dualStripEnabled
        detector.directionFilter = s.directionFilter
    }

    fun setCameraPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
    }

    fun startTimer() {
        val now = System.currentTimeMillis()
        detector.resetSession()
        _uiState.value = _uiState.value.copy(
            status = TimerStatus.RUNNING,
            startTimeMs = now,
            elapsedTimeMs = 0L,
            formattedTime = "00:00.000",
            finishCameraTimestampNs = 0L,
            finishWallClockMs = 0L,
            peakMotion = 0f,
            photoFinishBitmap = null,
            showPhotoFinishDialog = false
        )

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.status == TimerStatus.RUNNING) {
                val elapsed = System.currentTimeMillis() - _uiState.value.startTimeMs
                _uiState.value = _uiState.value.copy(
                    elapsedTimeMs = elapsed,
                    formattedTime = formatTime(elapsed)
                )
                delay(16) // ~60fps UI refresh
            }
        }
    }

    fun armDetectorOnly() {
        detector.resetSession()
        _uiState.value = _uiState.value.copy(
            status = TimerStatus.ARMED,
            finishCameraTimestampNs = 0L,
            finishWallClockMs = 0L,
            peakMotion = 0f,
            photoFinishBitmap = null
        )
    }

    fun resetTimer() {
        timerJob?.cancel()
        simulationJob?.cancel()
        detector.resetSession()
        _uiState.value = _uiState.value.copy(
            status = TimerStatus.IDLE,
            startTimeMs = 0L,
            elapsedTimeMs = 0L,
            formattedTime = "00:00.000",
            motionA = 0f,
            motionB = 0f,
            peakMotion = 0f,
            photoFinishBitmap = null,
            showPhotoFinishDialog = false,
            isSimulating = false
        )
    }

    /**
     * Called by CameraX ImageAnalysis analyzer on every frame
     */
    fun onCameraFrameResult(result: DetectionResult) {
        frameCounter++
        val now = System.currentTimeMillis()
        var currentFps = _uiState.value.fps
        if (now - lastFpsCalculationMs >= 1000) {
            currentFps = frameCounter
            frameCounter = 0
            lastFpsCalculationMs = now
        }

        val currentPeak = maxOf(_uiState.value.peakMotion, result.motionRatioStripB)

        _uiState.value = _uiState.value.copy(
            motionA = result.motionRatioStripA,
            motionB = result.motionRatioStripB,
            peakMotion = currentPeak,
            detectedDirection = result.detectedDirection,
            verticalProfile = result.verticalMotionProfile,
            fps = currentFps,
            photoFinishBitmap = result.photoFinishBitmap ?: _uiState.value.photoFinishBitmap
        )

        if (result.isTriggered && (_uiState.value.status == TimerStatus.RUNNING || _uiState.value.status == TimerStatus.ARMED)) {
            triggerFinish(result.timestampNs, result.wallClockMs, result.photoFinishBitmap)
        }
    }

    private fun triggerFinish(timestampNs: Long, wallClockMs: Long, photoFinish: Bitmap?) {
        timerJob?.cancel()
        val elapsed = if (_uiState.value.startTimeMs > 0) {
            wallClockMs - _uiState.value.startTimeMs
        } else {
            0L
        }

        triggerHapticFeedback()

        _uiState.value = _uiState.value.copy(
            status = TimerStatus.FINISHED,
            finishCameraTimestampNs = timestampNs,
            finishWallClockMs = wallClockMs,
            elapsedTimeMs = elapsed,
            formattedTime = formatTime(elapsed),
            photoFinishBitmap = photoFinish ?: _uiState.value.photoFinishBitmap,
            showPhotoFinishDialog = true
        )
    }

    fun simulateRunner(direction: Direction = _uiState.value.directionFilter) {
        if (_uiState.value.status != TimerStatus.RUNNING) {
            startTimer()
        }

        _uiState.value = _uiState.value.copy(isSimulating = true)
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            simulator.runSimulation(
                direction = if (direction == Direction.ANY) Direction.LEFT_TO_RIGHT else direction,
                fps = 30
            ) { motionA, motionB, timestampNs, wallClockMs, isTrigger, photoFinish ->
                val currentPeak = maxOf(_uiState.value.peakMotion, motionB)
                _uiState.value = _uiState.value.copy(
                    motionA = motionA,
                    motionB = motionB,
                    peakMotion = currentPeak,
                    detectedDirection = direction
                )

                if (isTrigger && (_uiState.value.status == TimerStatus.RUNNING || _uiState.value.status == TimerStatus.ARMED)) {
                    triggerFinish(timestampNs, wallClockMs, photoFinish)
                }
            }
            _uiState.value = _uiState.value.copy(isSimulating = false)
        }
    }

    fun saveCurrentRun() {
        val state = _uiState.value
        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())
        val entity = TimingRunEntity(
            runnerName = state.runnerName.ifBlank { "Runner" },
            eventTitle = state.eventTitle.ifBlank { "Sprint" },
            startTimeMs = state.startTimeMs,
            finishCameraTimestampNs = state.finishCameraTimestampNs,
            finishWallClockMs = state.finishWallClockMs,
            elapsedTimeMs = state.elapsedTimeMs,
            direction = state.detectedDirection.name,
            peakMotionPercent = state.peakMotion * 100f,
            notes = "Camera Sensor: ${state.finishCameraTimestampNs} ns",
            recordedDate = dateFormat.format(Date(state.finishWallClockMs.takeIf { it > 0 } ?: System.currentTimeMillis()))
        )

        viewModelScope.launch {
            repository.insertRun(entity)
            _uiState.value = _uiState.value.copy(showPhotoFinishDialog = false)
        }
    }

    fun deleteRun(run: TimingRunEntity) {
        viewModelScope.launch {
            repository.deleteRun(run)
        }
    }

    fun clearAllRuns() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Config setters
    fun updateSensitivity(luminanceThreshold: Int, triggerRatio: Float) {
        _uiState.value = _uiState.value.copy(
            luminanceThreshold = luminanceThreshold,
            triggerThresholdRatio = triggerRatio
        )
        syncDetectorSettings()
    }

    fun updateStripConfig(width: Int, position: Float, dualStrip: Boolean, direction: Direction) {
        _uiState.value = _uiState.value.copy(
            stripWidth = width,
            stripPositionRatio = position,
            dualStripEnabled = dualStrip,
            directionFilter = direction
        )
        syncDetectorSettings()
    }

    fun updateRunnerDetails(runner: String, event: String) {
        _uiState.value = _uiState.value.copy(
            runnerName = runner,
            eventTitle = event
        )
    }

    fun setShowPhotoFinish(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPhotoFinishDialog = show)
    }

    fun setShowSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettingsSheet = show)
    }

    fun setShowHistory(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHistorySheet = show)
    }

    private fun triggerHapticFeedback() {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun formatTime(millis: Long): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val ms = millis % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", min, sec, ms)
    }
}
