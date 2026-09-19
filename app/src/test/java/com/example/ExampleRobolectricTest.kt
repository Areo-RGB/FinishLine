package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.vision.Direction
import com.example.vision.FinishLineDetector
import com.example.vision.SimulationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FinishLine", appName)
  }

  @Test
  fun `detector defaults are configured for dual-strip sprint timing`() {
    val detector = FinishLineDetector()
    assertEquals(30, detector.luminanceThreshold)
    assertEquals(0.18f, detector.triggerThresholdRatio, 0.01f)
    assertEquals(Direction.LEFT_TO_RIGHT, detector.directionFilter)
    assertTrue(detector.dualStripEnabled)
  }

  @Test
  fun `simulator generates realistic photo finish composite`() {
    val simulator = SimulationEngine()
    val bitmap = simulator.generateSimulatedPhotoFinish()
    assertNotNull(bitmap)
    assertEquals(260, bitmap.width)
    assertEquals(360, bitmap.height)
  }

  @Test
  fun `simulation engine triggers directional finish`() = runBlocking {
    val simulator = SimulationEngine()
    var triggerDetected = false
    var triggerTimestampNs = 0L

    simulator.runSimulation(
      direction = Direction.LEFT_TO_RIGHT,
      fps = 30
    ) { _, _, timestampNs, _, isTrigger, _ ->
      if (isTrigger) {
        triggerDetected = true
        triggerTimestampNs = timestampNs
      }
    }

    assertTrue(triggerDetected)
    assertTrue(triggerTimestampNs > 0L)
  }
}

