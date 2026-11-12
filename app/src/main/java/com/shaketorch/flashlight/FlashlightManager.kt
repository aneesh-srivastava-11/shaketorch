package com.shaketorch.flashlight

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * FlashlightManager coordinates direct interaction with the Android Camera2 API
 * to control the device flashlight (torch mode).
 *
 * It maintains hardware-accurate synchronization via [CameraManager.TorchCallback].
 */
class FlashlightManager(private val context: Context) {

    companion object {
        private const val TAG = "FlashlightManager"

        @Volatile
        private var instance: FlashlightManager? = null

        fun getInstance(context: Context): FlashlightManager {
            return instance ?: synchronized(this) {
                instance ?: FlashlightManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutex = Mutex()

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _hasFlashHardware = MutableStateFlow(true)
    val hasFlashHardware: StateFlow<Boolean> = _hasFlashHardware.asStateFlow()

    private val _status = MutableStateFlow<FlashlightStatus>(FlashlightStatus.Ready)
    val status: StateFlow<FlashlightStatus> = _status.asStateFlow()

    private var targetCameraId: String? = null
    private var isTorchCallbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == targetCameraId) {
                Log.d(TAG, "Hardware torch mode changed for camera $cameraId: $enabled")
                _isFlashlightOn.value = enabled
                _status.value = FlashlightStatus.Ready
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == targetCameraId) {
                Log.w(TAG, "Hardware torch mode became unavailable for camera $cameraId")
                _isFlashlightOn.value = false
                _status.value = FlashlightStatus.Unavailable
            }
        }
    }

    init {
        detectFlashHardware()
        registerTorchCallback()
    }

    private fun detectFlashHardware() {
        val pm = context.packageManager
        val hasFlashFeature = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

        if (!hasFlashFeature || cameraManager == null) {
            _hasFlashHardware.value = false
            _status.value = FlashlightStatus.Unavailable
            Log.w(TAG, "Device does not report FEATURE_CAMERA_FLASH support.")
            return
        }

        try {
            var selectedId: String? = null
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                // Prefer rear camera with flash
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    selectedId = id
                    break
                } else if (hasFlash && selectedId == null) {
                    selectedId = id
                }
            }

            if (selectedId != null) {
                targetCameraId = selectedId
                _hasFlashHardware.value = true
                _status.value = FlashlightStatus.Ready
                Log.d(TAG, "Selected camera for torch: $targetCameraId")
            } else {
                _hasFlashHardware.value = false
                _status.value = FlashlightStatus.Unavailable
                Log.w(TAG, "No camera with flash unit found on this device.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed inspecting camera characteristics", e)
            _hasFlashHardware.value = false
            _status.value = FlashlightStatus.Error("Failed to initialize camera: ${e.localizedMessage}")
        }
    }

    private fun registerTorchCallback() {
        if (isTorchCallbackRegistered || cameraManager == null) return
        try {
            cameraManager.registerTorchCallback(torchCallback, mainHandler)
            isTorchCallbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering TorchCallback", e)
        }
    }

    /**
     * Toggles the device flashlight state.
     * If OFF -> turns ON.
     * If ON -> turns OFF.
     *
     * Thread-safe to eliminate race conditions between sensor events and UI clicks.
     *
     * @return Result containing the new state (true = ON, false = OFF) or an error.
     */
    suspend fun toggleFlashlight(): Result<Boolean> = mutex.withLock {
        val targetId = targetCameraId
        if (targetId == null || !_hasFlashHardware.value || cameraManager == null) {
            val error = "Flashlight hardware is unavailable on this device."
            _status.value = FlashlightStatus.Unavailable
            return Result.failure(IllegalStateException(error))
        }

        val nextState = !_isFlashlightOn.value
        return try {
            cameraManager.setTorchMode(targetId, nextState)
            _isFlashlightOn.value = nextState
            _status.value = FlashlightStatus.Ready
            Log.d(TAG, "Flashlight toggled to: $nextState")
            Result.success(nextState)
        } catch (e: CameraAccessException) {
            val message = when (e.reason) {
                CameraAccessException.CAMERA_IN_USE -> "Camera or flashlight is currently in use by another app."
                CameraAccessException.MAX_CAMERAS_IN_USE -> "Maximum camera resources are currently in use."
                CameraAccessException.CAMERA_DISABLED -> "Camera device has been disabled by device policy."
                CameraAccessException.CAMERA_DISCONNECTED -> "Camera was disconnected."
                else -> "Camera access error: ${e.message}"
            }
            Log.e(TAG, "CameraAccessException while setting torch mode", e)
            _status.value = FlashlightStatus.Error(message)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error setting torch mode", e)
            _status.value = FlashlightStatus.Error("Failed to control flashlight: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Explicitly sets the flashlight state to ON or OFF.
     */
    suspend fun setFlashlight(enabled: Boolean): Result<Boolean> = mutex.withLock {
        val targetId = targetCameraId
        if (targetId == null || !_hasFlashHardware.value || cameraManager == null) {
            return Result.failure(IllegalStateException("Flashlight hardware is unavailable."))
        }

        if (_isFlashlightOn.value == enabled) {
            return Result.success(enabled)
        }

        return try {
            cameraManager.setTorchMode(targetId, enabled)
            _isFlashlightOn.value = enabled
            _status.value = FlashlightStatus.Ready
            Result.success(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set flashlight to $enabled", e)
            _status.value = FlashlightStatus.Error("Could not set flashlight state: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    fun unregister() {
        if (isTorchCallbackRegistered && cameraManager != null) {
            try {
                cameraManager.unregisterTorchCallback(torchCallback)
                isTorchCallbackRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering TorchCallback", e)
            }
        }
    }
}

