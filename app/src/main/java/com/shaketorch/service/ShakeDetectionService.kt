package com.shaketorch.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shaketorch.MainActivity
import com.shaketorch.R
import com.shaketorch.ShakeTorchApp
import com.shaketorch.data.SettingsRepository
import com.shaketorch.data.UserPreferences
import com.shaketorch.flashlight.FlashlightManager
import com.shaketorch.sensor.ShakeDetector
import com.shaketorch.util.FeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service for continuous shake detection and flashlight toggling
 * when the application is running in the background or device screen is dimmed.
 */
class ShakeDetectionService : Service() {

    companion object {
        private const val TAG = "ShakeDetectionService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.shaketorch.action.START_SERVICE"
        const val ACTION_STOP = "com.shaketorch.action.STOP_SERVICE"
        const val ACTION_TOGGLE = "com.shaketorch.action.TOGGLE_TORCH"

        @Volatile
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, ShakeDetectionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ShakeDetectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var sensorManager: SensorManager
    private lateinit var flashlightManager: FlashlightManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var feedbackManager: FeedbackManager
    private lateinit var shakeDetector: ShakeDetector

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentPreferences = UserPreferences()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating ShakeDetectionService")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        flashlightManager = FlashlightManager.getInstance(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        feedbackManager = FeedbackManager(applicationContext)

        acquireWakeLock()
        initShakeDetector()
        observePreferencesAndTorchState()

        isServiceRunning = true
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ShakeTorch::ForegroundShakeWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes rolling safety acquisition
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    private fun initShakeDetector() {
        shakeDetector = ShakeDetector {
            // Check if shake detection is enabled in preferences
            if (!currentPreferences.isShakeEnabled) return@ShakeDetector

            Log.d(TAG, "Shake detected in background service! Toggling flashlight...")
            serviceScope.launch {
                val result = flashlightManager.toggleFlashlight()
                if (result.isSuccess) {
                    if (currentPreferences.isVibrationEnabled) {
                        feedbackManager.vibrate()
                    }
                    if (currentPreferences.isSoundEnabled) {
                        feedbackManager.playSound()
                    }
                }
            }
        }

        shakeDetector.start(sensorManager)
    }

    private fun observePreferencesAndTorchState() {
        // Collect user preferences
        serviceScope.launch {
            settingsRepository.userPreferencesFlow.collectLatest { prefs ->
                currentPreferences = prefs
                shakeDetector.setSensitivity(prefs.sensitivity)
                shakeDetector.setCooldown(prefs.cooldownMs)
            }
        }

        // Collect flashlight status to update notification text
        serviceScope.launch {
            flashlightManager.isFlashlightOn.collectLatest { isOn ->
                updateNotification(isOn)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Received ACTION_STOP")
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                Log.d(TAG, "Received ACTION_TOGGLE from notification")
                serviceScope.launch {
                    val result = flashlightManager.toggleFlashlight()
                    if (result.isSuccess) {
                        if (currentPreferences.isVibrationEnabled) feedbackManager.vibrate()
                        if (currentPreferences.isSoundEnabled) feedbackManager.playSound()
                    }
                }
            }
            else -> {
                startForegroundWithNotification()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification(flashlightManager.isFlashlightOn.value)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service with notification", e)
        }
    }

    private fun updateNotification(isFlashlightOn: Boolean) {
        if (!isServiceRunning) return
        val notification = buildNotification(isFlashlightOn)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(isFlashlightOn: Boolean): Notification {
        // Intent to open MainActivity on click
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, ShakeDetectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Toggle action
        val toggleIntent = Intent(this, ShakeDetectionService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isFlashlightOn) "Flashlight is ON" else "Flashlight is OFF"

        return NotificationCompat.Builder(this, ShakeTorchApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_flashlight)
            .setContentTitle("ShakeTorch Active")
            .setContentText("Monitoring movement • $statusText")
            .setSubText("Shake phone to toggle")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_flashlight, "Toggle", togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun stopForegroundService() {
        isServiceRunning = false
        shakeDetector.stop(sensorManager)
        releaseWakeLock()

        serviceScope.launch {
            settingsRepository.setBackgroundServiceEnabled(false)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroying ShakeDetectionService")
        isServiceRunning = false
        shakeDetector.stop(sensorManager)
        releaseWakeLock()
        feedbackManager.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

