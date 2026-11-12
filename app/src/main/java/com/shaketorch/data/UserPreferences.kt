package com.shaketorch.data

/**
 * Immutable data holder representing user preferences for ShakeTorch.
 */
data class UserPreferences(
    val isShakeEnabled: Boolean = true,
    val sensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    val cooldownMs: Long = 1000L,
    val isVibrationEnabled: Boolean = true,
    val isSoundEnabled: Boolean = false,
    val isBackgroundServiceEnabled: Boolean = false
)

