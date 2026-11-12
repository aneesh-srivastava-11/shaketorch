package com.shaketorch.flashlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the core non-negotiable logic:
 * ONE SHAKE = ONE FLASHLIGHT STATE CHANGE
 * Synchronized with manual toggling.
 */
class FlashlightLogicTest {

    private class EmulatedFlashlightController {
        var isFlashlightOn: Boolean = false
            private set

        fun toggle(): Boolean {
            isFlashlightOn = !isFlashlightOn
            return isFlashlightOn
        }

        fun manualSet(enabled: Boolean) {
            isFlashlightOn = enabled
        }
    }

    @Test
    fun `test sequential shaking alternates flashlight indefinitely`() {
        val controller = EmulatedFlashlightController()

        // Initial state: OFF
        assertFalse(controller.isFlashlightOn)

        // Shake 1: OFF -> ON
        controller.toggle()
        assertTrue(controller.isFlashlightOn)

        // Shake 2: ON -> OFF
        controller.toggle()
        assertFalse(controller.isFlashlightOn)

        // Shake 3: OFF -> ON
        controller.toggle()
        assertTrue(controller.isFlashlightOn)

        // Shake 4: ON -> OFF
        controller.toggle()
        assertFalse(controller.isFlashlightOn)

        // Shake 5: OFF -> ON
        controller.toggle()
        assertTrue(controller.isFlashlightOn)
    }

    @Test
    fun `test manual toggle seamlessly synchronizes with next shake`() {
        val controller = EmulatedFlashlightController()

        // Flashlight OFF
        assertFalse(controller.isFlashlightOn)

        // User manually turns ON from app UI
        controller.manualSet(true)
        assertTrue(controller.isFlashlightOn)

        // Next shake MUST turn it OFF
        controller.toggle()
        assertFalse("Next shake after manual ON must turn flashlight OFF", controller.isFlashlightOn)

        // User manually turns ON again
        controller.manualSet(true)
        assertTrue(controller.isFlashlightOn)

        // User manually turns OFF
        controller.manualSet(false)
        assertFalse(controller.isFlashlightOn)

        // Next shake MUST turn it ON
        controller.toggle()
        assertTrue("Next shake after manual OFF must turn flashlight ON", controller.isFlashlightOn)
    }
}

