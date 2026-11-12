package com.shaketorch.flashlight

/**
 * Represents the current status and health of the device flashlight.
 */
sealed class FlashlightStatus {
    object Ready : FlashlightStatus()
    object Unavailable : FlashlightStatus()
    data class Error(val message: String) : FlashlightStatus()
}

