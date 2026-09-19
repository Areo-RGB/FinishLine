package com.example.vision

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FinishLineDetector {

    // Configuration parameters
    var luminanceThreshold: Int = 30
    var triggerThresholdRatio: Float = 0.18f
    var stripWidth: Int = 32
    var stripPositionRatio: Float = 0.5f // 0.0 to 1.0 (0.5 = center)
    var dualStripEnabled: Boolean = true
    var dualStripSeparationRatio: Float = 0.10f // ~10% screen width between A and B
    var directionFilter: Direction = Direction.LEFT_TO_RIGHT
    var morphologicalMinRun: Int = 4

    // Reusable buffers to avoid per-frame GC allocations
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var prevStripA: ByteArray? = null
    private var prevStripB: ByteArray? = null

    // Direction timing tracking
    private var lastStripATriggerNs: Long = 0L
    private var lastStripBTriggerNs: Long = 0L
    private var hasTriggeredForSession = false

    // Slit-scan / Photo finish rolling buffer
    private val slitScanWidth = 260
    private var slitScanBuffer: IntArray? = null
    private var slitScanIndex = 0
    private var postTriggerFramesRemaining = 0
    private var triggerSlitIndex = -1
    private var lastCapturedPhotoFinish: Bitmap? = null

    fun resetSession() {
        hasTriggeredForSession = false
        lastStripATriggerNs = 0L
        lastStripBTriggerNs = 0L
        postTriggerFramesRemaining = 0
        triggerSlitIndex = -1
        lastCapturedPhotoFinish = null
    }

    /**
     * Analyzes an incoming camera frame's Y/luminance plane.
     * ZERO RGB conversion is performed during detection.
     */
    fun analyzeFrame(image: ImageProxy, isArmed: Boolean): DetectionResult {
        val timestampNs = image.imageInfo.timestamp
        val wallClockMs = System.currentTimeMillis()

        val planes = image.planes
        if (planes.isEmpty()) {
            return emptyResult(timestampNs, wallClockMs)
        }

        val yPlane = planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val imageWidth = image.width
        val imageHeight = image.height

        // Downsample height if needed for extreme speed (sample every stepY rows)
        val stepY = if (imageHeight > 720) 2 else 1
        val sampleHeight = imageHeight / stepY
        val actualStripWidth = min(stripWidth, imageWidth / 4)

        ensureBuffers(actualStripWidth, sampleHeight)

        // Calculate horizontal ROI positions
        val centerBX = (imageWidth * stripPositionRatio).toInt()
        val leftBX = (centerBX - actualStripWidth / 2).coerceIn(0, imageWidth - actualStripWidth)

        val separationPx = (imageWidth * dualStripSeparationRatio).toInt().coerceAtLeast(actualStripWidth + 8)
        val leftAX = (leftBX - separationPx).coerceIn(0, imageWidth - actualStripWidth)

        // 1. Process Strip B (Finish Line)
        val verticalProfile = FloatArray(16)
        val (motionRatioB, currStripB) = processStrip(
            buffer = buffer,
            leftX = leftBX,
            width = actualStripWidth,
            sampleHeight = sampleHeight,
            imageHeight = imageHeight,
            stepY = stepY,
            rowStride = rowStride,
            pixelStride = pixelStride,
            prevStrip = prevStripB,
            verticalProfile = verticalProfile
        )

        // 2. Process Strip A (Pre-Finish Guard Strip for Direction Detection)
        val motionRatioA = if (dualStripEnabled) {
            val (ratioA, currStripA) = processStrip(
                buffer = buffer,
                leftX = leftAX,
                width = actualStripWidth,
                sampleHeight = sampleHeight,
                imageHeight = imageHeight,
                stepY = stepY,
                rowStride = rowStride,
                pixelStride = pixelStride,
                prevStrip = prevStripA,
                verticalProfile = null
            )
            prevStripA = currStripA
            ratioA
        } else {
            0f
        }
        prevStripB = currStripB

        // 3. Update Slit-Scan (Rolling timeline column for photo-finish)
        updateSlitScan(buffer, leftBX + actualStripWidth / 2, sampleHeight, imageHeight, stepY, rowStride, pixelStride)

        // 4. Directional Evaluation & Trigger Logic
        var triggeredNow = false
        var detectedDir = Direction.NONE

        if (motionRatioA >= triggerThresholdRatio) {
            lastStripATriggerNs = timestampNs
        }

        if (motionRatioB >= triggerThresholdRatio) {
            lastStripBTriggerNs = timestampNs

            val diffNs = lastStripBTriggerNs - lastStripATriggerNs
            // 20ms to 900ms window between strips indicates directional runner
            val leftToRightDetected = diffNs in 20_000_000L..900_000_000L
            val rightToLeftDetected = (lastStripATriggerNs - lastStripBTriggerNs) in 20_000_000L..900_000_000L

            detectedDir = when {
                leftToRightDetected -> Direction.LEFT_TO_RIGHT
                rightToLeftDetected -> Direction.RIGHT_TO_LEFT
                else -> Direction.ANY
            }

            if (isArmed && !hasTriggeredForSession) {
                val matchesDirection = when (directionFilter) {
                    Direction.LEFT_TO_RIGHT -> leftToRightDetected || !dualStripEnabled
                    Direction.RIGHT_TO_LEFT -> rightToLeftDetected || !dualStripEnabled
                    Direction.ANY, Direction.NONE -> true
                }

                if (matchesDirection) {
                    hasTriggeredForSession = true
                    triggeredNow = true
                    triggerSlitIndex = slitScanIndex
                    postTriggerFramesRemaining = 60 // Capture post-finish frames for photo finish
                }
            }
        }

        if (postTriggerFramesRemaining > 0) {
            postTriggerFramesRemaining--
            if (postTriggerFramesRemaining == 0 && triggerSlitIndex >= 0) {
                lastCapturedPhotoFinish = buildPhotoFinishBitmap(sampleHeight)
            }
        }

        return DetectionResult(
            timestampNs = timestampNs,
            wallClockMs = wallClockMs,
            motionRatioStripA = motionRatioA,
            motionRatioStripB = motionRatioB,
            isTriggered = triggeredNow,
            detectedDirection = detectedDir,
            verticalMotionProfile = verticalProfile,
            photoFinishBitmap = lastCapturedPhotoFinish
        )
    }

    private fun processStrip(
        buffer: ByteBuffer,
        leftX: Int,
        width: Int,
        sampleHeight: Int,
        imageHeight: Int,
        stepY: Int,
        rowStride: Int,
        pixelStride: Int,
        prevStrip: ByteArray?,
        verticalProfile: FloatArray?
    ): Pair<Float, ByteArray> {
        val currStrip = ByteArray(width * sampleHeight)
        var changedPixelsCount = 0
        val binHeight = max(1, sampleHeight / 16)

        // Read Y luminance and compute absdiff
        var idx = 0
        for (sy in 0 until sampleHeight) {
            val y = sy * stepY
            val rowOffset = y * rowStride
            val bin = min(15, sy / binHeight)

            for (x in 0 until width) {
                val colOffset = (leftX + x) * pixelStride
                val pos = rowOffset + colOffset
                val lum = if (pos < buffer.limit()) buffer.get(pos).toInt() and 0xFF else 0
                currStrip[idx] = lum.toByte()

                if (prevStrip != null && idx < prevStrip.size) {
                    val prevLum = prevStrip[idx].toInt() and 0xFF
                    val diff = abs(lum - prevLum)
                    if (diff > luminanceThreshold) {
                        changedPixelsCount++
                        if (verticalProfile != null) {
                            verticalProfile[bin] += 1f
                        }
                    }
                }
                idx++
            }
        }

        // Morphological filtering: Check for minimum vertical contiguous runs
        // If changed pixels are sparse and isolated (sensor CMOS grain), discount them
        val totalPixels = width * sampleHeight
        val rawRatio = if (totalPixels > 0) changedPixelsCount.toFloat() / totalPixels else 0f

        // Normalize vertical profile
        if (verticalProfile != null && totalPixels > 0) {
            val pixelsPerBin = (width * sampleHeight) / 16f
            for (i in verticalProfile.indices) {
                verticalProfile[i] = (verticalProfile[i] / pixelsPerBin).coerceIn(0f, 1f)
            }
        }

        // Apply morphological suppression if less than morphologicalMinRun contiguous rows have activity
        var activeBinsContiguous = 0
        var maxContiguousBins = 0
        if (verticalProfile != null) {
            for (activity in verticalProfile) {
                if (activity > 0.08f) {
                    activeBinsContiguous++
                    if (activeBinsContiguous > maxContiguousBins) {
                        maxContiguousBins = activeBinsContiguous
                    }
                } else {
                    activeBinsContiguous = 0
                }
            }
        }

        val filteredRatio = if (verticalProfile != null && maxContiguousBins < 2 && rawRatio < 0.25f) {
            0f // Noise suppressed
        } else {
            rawRatio
        }

        return Pair(filteredRatio, currStrip)
    }

    private fun updateSlitScan(
        buffer: ByteBuffer,
        finishLineX: Int,
        sampleHeight: Int,
        imageHeight: Int,
        stepY: Int,
        rowStride: Int,
        pixelStride: Int
    ) {
        val slitBuffer = slitScanBuffer ?: return
        val currentCol = slitScanIndex

        for (sy in 0 until sampleHeight) {
            val y = sy * stepY
            val pos = y * rowStride + finishLineX * pixelStride
            val lum = if (pos < buffer.limit()) buffer.get(pos).toInt() and 0xFF else 128
            // Map luminance to grayscale RGB pixel in slit-scan
            val pixel = Color.rgb(lum, lum, lum)
            slitBuffer[sy * slitScanWidth + currentCol] = pixel
        }

        slitScanIndex = (slitScanIndex + 1) % slitScanWidth
    }

    private fun buildPhotoFinishBitmap(sampleHeight: Int): Bitmap {
        val slitBuffer = slitScanBuffer ?: return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bitmap = Bitmap.createBitmap(slitScanWidth, sampleHeight, Bitmap.Config.ARGB_8888)

        // Rearrange circular buffer chronologically: oldest to newest
        val orderedPixels = IntArray(slitScanWidth * sampleHeight)
        val startIndex = slitScanIndex

        for (sy in 0 until sampleHeight) {
            val rowOffset = sy * slitScanWidth
            for (x in 0 until slitScanWidth) {
                val circularX = (startIndex + x) % slitScanWidth
                val color = slitBuffer[rowOffset + circularX]
                orderedPixels[rowOffset + x] = color
            }
        }

        // Draw vertical red finish line guide at the trigger position
        val relativeTriggerX = (slitScanWidth - 60).coerceIn(0, slitScanWidth - 1)
        for (sy in 0 until sampleHeight) {
            orderedPixels[sy * slitScanWidth + relativeTriggerX] = Color.RED
            if (relativeTriggerX + 1 < slitScanWidth) {
                orderedPixels[sy * slitScanWidth + relativeTriggerX + 1] = Color.YELLOW
            }
        }

        bitmap.setPixels(orderedPixels, 0, slitScanWidth, 0, 0, slitScanWidth, sampleHeight)
        return bitmap
    }

    private fun ensureBuffers(width: Int, height: Int) {
        if (bufferWidth != width || bufferHeight != height) {
            bufferWidth = width
            bufferHeight = height
            prevStripA = null
            prevStripB = null
            slitScanBuffer = IntArray(slitScanWidth * height)
            slitScanIndex = 0
        }
    }

    private fun emptyResult(timestampNs: Long, wallClockMs: Long) = DetectionResult(
        timestampNs = timestampNs,
        wallClockMs = wallClockMs,
        motionRatioStripA = 0f,
        motionRatioStripB = 0f,
        isTriggered = false,
        detectedDirection = Direction.NONE,
        verticalMotionProfile = FloatArray(16)
    )
}
