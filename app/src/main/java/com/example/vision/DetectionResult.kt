package com.example.vision

import android.graphics.Bitmap

enum class Direction {
    NONE,
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    ANY
}

data class DetectionResult(
    val timestampNs: Long,
    val wallClockMs: Long,
    val motionRatioStripA: Float,
    val motionRatioStripB: Float,
    val isTriggered: Boolean,
    val detectedDirection: Direction,
    val verticalMotionProfile: FloatArray,
    val photoFinishBitmap: Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DetectionResult

        if (timestampNs != other.timestampNs) return false
        if (wallClockMs != other.wallClockMs) return false
        if (motionRatioStripA != other.motionRatioStripA) return false
        if (motionRatioStripB != other.motionRatioStripB) return false
        if (isTriggered != other.isTriggered) return false
        if (detectedDirection != other.detectedDirection) return false
        if (!verticalMotionProfile.contentEquals(other.verticalMotionProfile)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestampNs.hashCode()
        result = 31 * result + wallClockMs.hashCode()
        result = 31 * result + motionRatioStripA.hashCode()
        result = 31 * result + motionRatioStripB.hashCode()
        result = 31 * result + isTriggered.hashCode()
        result = 31 * result + detectedDirection.hashCode()
        result = 31 * result + verticalMotionProfile.contentHashCode()
        return result
    }
}
