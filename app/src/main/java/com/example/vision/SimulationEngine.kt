package com.example.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import kotlin.math.sin

class SimulationEngine {

    /**
     * Generates a simulated runner motion crossing the screen over ~40-60 frames
     */
    suspend fun runSimulation(
        direction: Direction,
        fps: Int = 30,
        onFrame: (motionA: Float, motionB: Float, timestampNs: Long, wallClockMs: Long, isTriggerFrame: Boolean, photoFinish: Bitmap) -> Unit
    ) {
        val totalFrames = 60
        val frameDurationMs = 1000L / fps
        val startNs = System.nanoTime()
        val startMs = System.currentTimeMillis()

        // Create a realistic runner silhouette photo-finish composite
        val photoFinish = generateSimulatedPhotoFinish()

        for (frame in 0 until totalFrames) {
            val progress = frame.toFloat() / totalFrames // 0.0 to 1.0
            val currentNs = startNs + frame * (1_000_000_000L / fps)
            val currentMs = startMs + frame * frameDurationMs

            // Runner positions: Strip A is at ~0.4, Strip B is at ~0.5
            val (motionA, motionB, isTrigger) = when (direction) {
                Direction.LEFT_TO_RIGHT -> {
                    // Runner arrives at Strip A around frame 20-30, Strip B around frame 32-42
                    val mA = gaussianPulse(progress, 0.40f, 0.08f) * 0.45f
                    val mB = gaussianPulse(progress, 0.55f, 0.08f) * 0.50f
                    val trig = frame == 35
                    Triple(mA, mB, trig)
                }
                Direction.RIGHT_TO_LEFT -> {
                    // Runner arrives at Strip B first, then Strip A
                    val mB = gaussianPulse(progress, 0.40f, 0.08f) * 0.50f
                    val mA = gaussianPulse(progress, 0.55f, 0.08f) * 0.45f
                    val trig = frame == 35
                    Triple(mA, mB, trig)
                }
                else -> {
                    val mB = gaussianPulse(progress, 0.50f, 0.10f) * 0.48f
                    val trig = frame == 32
                    Triple(0f, mB, trig)
                }
            }

            onFrame(motionA, motionB, currentNs, currentMs, isTrigger, photoFinish)
            delay(frameDurationMs)
        }
    }

    private fun gaussianPulse(x: Float, center: Float, sigma: Float): Float {
        val dist = (x - center) / sigma
        return kotlin.math.exp(-(dist * dist) / 2.0).toFloat().coerceIn(0f, 1f)
    }

    fun generateSimulatedPhotoFinish(): Bitmap {
        val width = 260
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: Track lane texture with scanline streaks
        val bgPaint = Paint().apply { color = Color.rgb(24, 30, 42) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Track lane stripes horizontally
        val lanePaint = Paint().apply {
            color = Color.rgb(180, 50, 40)
            strokeWidth = 3f
        }
        canvas.drawLine(0f, height * 0.85f, width.toFloat(), height * 0.85f, lanePaint)

        // Draw runner silhouette passing across the finish line slit
        val runnerPaint = Paint().apply {
            color = Color.rgb(245, 158, 11) // Gold runner kit
            isAntiAlias = true
        }
        val headPaint = Paint().apply {
            color = Color.rgb(255, 230, 180)
            isAntiAlias = true
        }

        val centerX = width * 0.55f
        // Head
        canvas.drawCircle(centerX - 10f, height * 0.28f, 18f, headPaint)
        // Torso leaning forward through the finish line
        canvas.drawRoundRect(centerX - 35f, height * 0.35f, centerX + 15f, height * 0.65f, 12f, 12f, runnerPaint)
        // Leading Arm
        runnerPaint.color = Color.rgb(217, 119, 6)
        canvas.drawLine(centerX - 10f, height * 0.40f, centerX + 35f, height * 0.52f, runnerPaint.apply { strokeWidth = 10f })
        // Trailing Leg
        canvas.drawLine(centerX - 25f, height * 0.65f, centerX - 55f, height * 0.85f, runnerPaint.apply { strokeWidth = 12f })
        // Leading Leg
        canvas.drawLine(centerX, height * 0.65f, centerX + 20f, height * 0.84f, runnerPaint.apply { strokeWidth = 12f })

        // Vertical Laser Finish Line Reference (Red)
        val linePaint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
        }
        canvas.drawLine(width * 0.60f, 0f, width * 0.60f, height.toFloat(), linePaint)

        return bitmap
    }
}
