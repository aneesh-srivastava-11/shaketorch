package com.shaketorch.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.shaketorch.data.SensitivityLevel
import kotlin.math.sqrt

/**
 * ShakeDetector monitors the device's accelerometer to detect intentional physical shaking.
 *
 * Key features:
 * - High-pass filtering to isolate dynamic linear acceleration from Earth's gravity.
 * - Invariant 3D Euclidean magnitude calculation: sqrt(x² + y² + z²).
 * - Cooldown debounce (default 1000ms) prevents continuous oscillating motions from multi-triggering.
 * - Hysteresis reset requirement: after triggering, acceleration must fall below a reset threshold
 *   before a subsequent shake can be accepted.
 * - Thread-safe state management.
 */
class ShakeDetector(
    private var listener: ShakeListener? = null
) : SensorEventListener {

    // Gravity tracking components for low-pass filter
    private val gravity = FloatArray(3) { 0f }
    private var isGravityInitialized = false

    // Detection tuning parameters
    @Volatile
    private var accelerationThreshold: Float = SensitivityLevel.MEDIUM.threshold

    @Volatile
    private var resetThreshold: Float = SensitivityLevel.MEDIUM.resetThreshold

    @Volatile
    private var cooldownMs: Long = 1000L

    // State tracking
    @Volatile
    private var lastShakeTimestampMs: Long = 0L

    @Volatile
    private var hasResetSinceLastShake: Boolean = true

    @Volatile
    var isListening: Boolean = false
        private set

    /**
     * Updates sensitivity level thresholds.
     */
    fun setSensitivity(level: SensitivityLevel) {
        synchronized(this) {
            accelerationThreshold = level.threshold
            resetThreshold = level.resetThreshold
        }
    }

    /**
     * Updates the debounce cooldown period in milliseconds.
     */
    fun setCooldown(cooldownMs: Long) {
        synchronized(this) {
            this.cooldownMs = cooldownMs
        }
    }

    /**
     * Updates the shake callback listener.
     */
    fun setShakeListener(listener: ShakeListener?) {
        this.listener = listener
    }

    /**
     * Registers this detector with the provided [SensorManager].
     * Uses [SensorManager.SENSOR_DELAY_GAME] for responsive, low-latency sampling.
     *
     * @return true if the accelerometer was successfully registered, false otherwise.
     */
    fun start(sensorManager: SensorManager): Boolean {
        if (isListening) return true

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false

        // Reset filter and state upon starting
        synchronized(this) {
            isGravityInitialized = false
            hasResetSinceLastShake = true
            lastShakeTimestampMs = 0L
        }

        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )
        isListening = registered
        return registered
    }

    /**
     * Unregisters the accelerometer listener.
     */
    fun stop(sensorManager: SensorManager) {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        // Low-pass filter to isolate gravity (alpha = 0.8)
        val alpha = 0.8f
        if (!isGravityInitialized) {
            gravity[0] = rawX
            gravity[1] = rawY
            gravity[2] = rawZ
            isGravityInitialized = true
        } else {
            gravity[0] = alpha * gravity[0] + (1 - alpha) * rawX
            gravity[1] = alpha * gravity[1] + (1 - alpha) * rawY
            gravity[2] = alpha * gravity[2] + (1 - alpha) * rawZ
        }

        // High-pass filter: subtract gravity to obtain dynamic linear acceleration
        val linearX = rawX - gravity[0]
        val linearY = rawY - gravity[1]
        val linearZ = rawZ - gravity[2]

        // 3D Euclidean magnitude: independent of phone orientation
        val dynamicMagnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        processAcceleration(dynamicMagnitude, System.currentTimeMillis())
    }

    /**
     * Evaluates acceleration magnitude against thresholds, cooldown, and hysteresis state.
     * Package-private for isolated unit testing.
     */
    internal fun processAcceleration(magnitude: Float, currentTimeMs: Long) {
        synchronized(this) {
            val timeSinceLastShake = currentTimeMs - lastShakeTimestampMs

            // Hysteresis reset: phone motion must settle below resetThreshold before re-arming
            if (!hasResetSinceLastShake && magnitude < resetThreshold) {
                // Also require minimum settle time of 250ms past last shake before re-arming
                if (timeSinceLastShake >= 250L) {
                    hasResetSinceLastShake = true
                }
            }

            // Check if current dynamic acceleration exceeds threshold
            if (magnitude >= accelerationThreshold) {
                if (hasResetSinceLastShake && timeSinceLastShake >= cooldownMs) {
                    // Valid shake triggered
                    hasResetSinceLastShake = false
                    lastShakeTimestampMs = currentTimeMs

                    // Fire callback
                    listener?.onShake()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for accelerometer
    }
}

