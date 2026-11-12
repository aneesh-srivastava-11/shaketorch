package com.shaketorch.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shaketorch.data.SensitivityLevel
import com.shaketorch.data.SettingsRepository
import com.shaketorch.data.UserPreferences
import com.shaketorch.flashlight.FlashlightManager
import com.shaketorch.flashlight.FlashlightStatus
import com.shaketorch.sensor.ShakeDetector
import com.shaketorch.service.ShakeDetectionService
import com.shaketorch.util.FeedbackManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating shake detection, flashlight control, settings persistence,
 * and background service coordination.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val flashlightManager = FlashlightManager.getInstance(application)
    private val settingsRepository = SettingsRepository(application)
    private val feedbackManager = FeedbackManager(application)

    val isFlashlightOn: StateFlow<Boolean> = flashlightManager.isFlashlightOn
    val hasFlashHardware: StateFlow<Boolean> = flashlightManager.hasFlashHardware
    val flashlightStatus: StateFlow<FlashlightStatus> = flashlightManager.status

    val hasAccelerometer: Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    val userPreferences: StateFlow<UserPreferences> = settingsRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )

    private val _shakeDetectedPulse = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shakeDetectedPulse: SharedFlow<Unit> = _shakeDetectedPulse.asSharedFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    private var isForegroundActive = false

    private val foregroundShakeDetector = ShakeDetector {
        handleShakeDetected()
    }

    init {
        // Synchronize detector parameters whenever preferences update
        viewModelScope.launch {
            userPreferences.collectLatest { prefs ->
                foregroundShakeDetector.setSensitivity(prefs.sensitivity)
                foregroundShakeDetector.setCooldown(prefs.cooldownMs)

                // If background service preference changed externally
                if (prefs.isBackgroundServiceEnabled && !ShakeDetectionService.isServiceRunning) {
                    ShakeDetectionService.startService(getApplication())
                } else if (!prefs.isBackgroundServiceEnabled && ShakeDetectionService.isServiceRunning) {
                    ShakeDetectionService.stopService(getApplication())
                }

                // Refresh foreground listening state
                updateForegroundSensorListening()
            }
        }
    }

    fun onResumeForeground() {
        isForegroundActive = true
        updateForegroundSensorListening()
    }

    fun onPauseForeground() {
        isForegroundActive = false
        foregroundShakeDetector.stop(sensorManager)
    }

    private fun updateForegroundSensorListening() {
        val prefs = userPreferences.value
        // Only run foreground shake detector if shake is enabled AND background service is NOT already running
        // (to prevent double triggering when both are active)
        if (isForegroundActive && prefs.isShakeEnabled && !prefs.isBackgroundServiceEnabled) {
            foregroundShakeDetector.start(sensorManager)
        } else {
            foregroundShakeDetector.stop(sensorManager)
        }
    }

    private fun handleShakeDetected() {
        viewModelScope.launch {
            _shakeDetectedPulse.emit(Unit)
            val result = flashlightManager.toggleFlashlight()
            result.onSuccess {
                val prefs = userPreferences.value
                if (prefs.isVibrationEnabled) feedbackManager.vibrate()
                if (prefs.isSoundEnabled) feedbackManager.playSound()
            }.onFailure { error ->
                _userFeedbackMessage.value = error.localizedMessage ?: "Failed to toggle flashlight"
            }
        }
    }

    /**
     * Manual toggle button interaction.
     */
    fun toggleFlashlightManual() {
        viewModelScope.launch {
            val result = flashlightManager.toggleFlashlight()
            result.onSuccess {
                val prefs = userPreferences.value
                if (prefs.isVibrationEnabled) feedbackManager.vibrate()
                if (prefs.isSoundEnabled) feedbackManager.playSound()
            }.onFailure { error ->
                _userFeedbackMessage.value = error.localizedMessage ?: "Failed to toggle flashlight"
            }
        }
    }

    fun setShakeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShakeEnabled(enabled)
        }
    }

    fun setSensitivity(sensitivity: SensitivityLevel) {
        viewModelScope.launch {
            settingsRepository.setSensitivity(sensitivity)
        }
    }

    fun setCooldown(cooldownMs: Long) {
        viewModelScope.launch {
            settingsRepository.setCooldownMs(cooldownMs)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrationEnabled(enabled)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSoundEnabled(enabled)
        }
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundServiceEnabled(enabled)
            if (enabled) {
                ShakeDetectionService.startService(getApplication())
            } else {
                ShakeDetectionService.stopService(getApplication())
            }
        }
    }

    fun dismissUserFeedbackMessage() {
        _userFeedbackMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        foregroundShakeDetector.stop(sensorManager)
        feedbackManager.release()
    }
}

