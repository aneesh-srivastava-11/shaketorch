package com.shaketorch.sensor

import com.shaketorch.data.SensitivityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ShakeDetector's thresholding, debounce cooldown,
 * hysteresis re-arming, and anti-repeat protections.
 */
class ShakeDetectorTest {

    private var shakeCount = 0
    private lateinit var detector: ShakeDetector

    @Before
    fun setUp() {
        shakeCount = 0
        detector = ShakeDetector {
            shakeCount++
        }
    }

    @Test
    fun `test single intentional shake triggers exactly one event`() {
        detector.setSensitivity(SensitivityLevel.MEDIUM) // threshold = 12.0f, resetThreshold = 3.5f
        detector.setCooldown(1000L)

        // Mild motion (e.g. walking or tilting)
        detector.processAcceleration(magnitude = 4.0f, currentTimeMs = 1000L)
        assertEquals(0, shakeCount)

        // Sharp physical shake above threshold
        detector.processAcceleration(magnitude = 15.0f, currentTimeMs = 1050L)
        assertEquals(1, shakeCount)
    }

    @Test
    fun `test continuous shake oscillations do not multi-trigger without reset`() {
        detector.setSensitivity(SensitivityLevel.MEDIUM)
        detector.setCooldown(1000L)

        // First shake spike
        detector.processAcceleration(magnitude = 14.0f, currentTimeMs = 1000L)
        assertEquals(1, shakeCount)

        // Continuous high acceleration during the same vigorous shake cycle (even past 1s)
        detector.processAcceleration(magnitude = 13.5f, currentTimeMs = 1200L)
        detector.processAcceleration(magnitude = 14.5f, currentTimeMs = 1500L)
        detector.processAcceleration(magnitude = 15.0f, currentTimeMs = 2100L)
        // Must NOT trigger again because motion never dropped below resetThreshold!
        assertEquals(1, shakeCount)

        // Motion finally settles below resetThreshold (3.5f)
        detector.processAcceleration(magnitude = 2.0f, currentTimeMs = 2200L)

        // Now, subsequent shake after settling triggers 2nd event
        detector.processAcceleration(magnitude = 14.0f, currentTimeMs = 2300L)
        assertEquals(2, shakeCount)
    }

    @Test
    fun `test cooldown prevents rapid successive toggles`() {
        detector.setSensitivity(SensitivityLevel.MEDIUM)
        detector.setCooldown(1000L)

        // Shake 1 at t = 1000ms
        detector.processAcceleration(magnitude = 14.0f, currentTimeMs = 1000L)
        assertEquals(1, shakeCount)

        // Motion settles quickly at t = 1300ms
        detector.processAcceleration(magnitude = 1.0f, currentTimeMs = 1300L)

        // Shake attempt at t = 1500ms (only 500ms after shake 1, cooldown is 1000ms)
        detector.processAcceleration(magnitude = 15.0f, currentTimeMs = 1500L)
        // Must be rejected by cooldown!
        assertEquals(1, shakeCount)

        // Shake attempt after cooldown expires at t = 2100ms
        detector.processAcceleration(magnitude = 15.0f, currentTimeMs = 2100L)
        assertEquals(2, shakeCount)
    }

    @Test
    fun `test sensitivity levels adjust acceleration thresholds`() {
        val testDetector = ShakeDetector { shakeCount++ }
        testDetector.setCooldown(500L)

        // LOW sensitivity: threshold = 16.0f
        testDetector.setSensitivity(SensitivityLevel.LOW)
        testDetector.processAcceleration(magnitude = 13.0f, currentTimeMs = 1000L)
        assertEquals(0, shakeCount) // 13 is below 16.0
        testDetector.processAcceleration(magnitude = 17.0f, currentTimeMs = 1050L)
        assertEquals(1, shakeCount) // 17 is above 16.0

        // Reset
        testDetector.processAcceleration(magnitude = 1.0f, currentTimeMs = 1600L)

        // HIGH sensitivity: threshold = 8.5f
        testDetector.setSensitivity(SensitivityLevel.HIGH)
        testDetector.processAcceleration(magnitude = 9.5f, currentTimeMs = 2200L)
        assertEquals(2, shakeCount) // 9.5 is above 8.5
    }

    @Test
    fun `test alternating toggle behavior sequence`() {
        var torchState = false
        val alternatingDetector = ShakeDetector {
            // Emulate torch toggle
            torchState = !torchState
        }
        alternatingDetector.setCooldown(500L)

        assertFalse(torchState)

        // Shake 1 -> Turns ON
        alternatingDetector.processAcceleration(14.0f, 1000L)
        assertTrue("Torch should be ON after 1st shake", torchState)

        // Settle
        alternatingDetector.processAcceleration(2.0f, 1600L)

        // Shake 2 -> Turns OFF
        alternatingDetector.processAcceleration(14.0f, 1700L)
        assertFalse("Torch should be OFF after 2nd shake", torchState)

        // Settle
        alternatingDetector.processAcceleration(2.0f, 2300L)

        // Shake 3 -> Turns ON
        alternatingDetector.processAcceleration(14.0f, 2400L)
        assertTrue("Torch should be ON after 3rd shake", torchState)

        // Settle
        alternatingDetector.processAcceleration(2.0f, 3000L)

        // Shake 4 -> Turns OFF
        alternatingDetector.processAcceleration(14.0f, 3100L)
        assertFalse("Torch should be OFF after 4th shake", torchState)
    }
}

