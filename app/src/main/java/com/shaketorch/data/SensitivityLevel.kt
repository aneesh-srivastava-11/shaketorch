package com.shaketorch.data

/**
 * Defines the sensitivity levels for shake gesture detection.
 *
 * @property label User-visible title.
 * @property description User-visible explanation.
 * @property threshold Acceleration magnitude threshold in m/s² above gravity.
 * @property resetThreshold Acceleration magnitude below which the phone must return
 *                          to re-arm the shake detector (hysteresis).
 */
enum class SensitivityLevel(
    val label: String,
    val description: String,
    val threshold: Float,
    val resetThreshold: Float
) {
    LOW(
        label = "Low",
        description = "Requires a vigorous shake. Best for preventing accidental triggers.",
        threshold = 16.0f,
        resetThreshold = 4.0f
    ),
    MEDIUM(
        label = "Medium",
        description = "Balanced detection suitable for daily natural usage.",
        threshold = 12.0f,
        resetThreshold = 3.5f
    ),
    HIGH(
        label = "High",
        description = "Detects light, effortless shakes. Highly responsive.",
        threshold = 8.5f,
        resetThreshold = 3.0f
    );

    companion object {
        fun fromName(name: String?): SensitivityLevel {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
        }
    }
}

