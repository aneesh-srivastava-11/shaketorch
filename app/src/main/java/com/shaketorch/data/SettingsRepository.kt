package com.shaketorch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shake_torch_settings")

/**
 * Repository for storing and retrieving user configuration via Jetpack DataStore.
 */
class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val SHAKE_ENABLED = booleanPreferencesKey("shake_enabled")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val BACKGROUND_SERVICE_ENABLED = booleanPreferencesKey("background_service_enabled")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val shakeEnabled = preferences[PreferencesKeys.SHAKE_ENABLED] ?: true
            val sensitivityStr = preferences[PreferencesKeys.SENSITIVITY] ?: SensitivityLevel.MEDIUM.name
            val sensitivity = SensitivityLevel.fromName(sensitivityStr)
            val cooldownMs = preferences[PreferencesKeys.COOLDOWN_MS] ?: 1000L
            val vibrationEnabled = preferences[PreferencesKeys.VIBRATION_ENABLED] ?: true
            val soundEnabled = preferences[PreferencesKeys.SOUND_ENABLED] ?: false
            val backgroundEnabled = preferences[PreferencesKeys.BACKGROUND_SERVICE_ENABLED] ?: false

            UserPreferences(
                isShakeEnabled = shakeEnabled,
                sensitivity = sensitivity,
                cooldownMs = cooldownMs,
                isVibrationEnabled = vibrationEnabled,
                isSoundEnabled = soundEnabled,
                isBackgroundServiceEnabled = backgroundEnabled
            )
        }

    suspend fun setShakeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHAKE_ENABLED] = enabled
        }
    }

    suspend fun setSensitivity(sensitivity: SensitivityLevel) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SENSITIVITY] = sensitivity.name
        }
    }

    suspend fun setCooldownMs(cooldownMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COOLDOWN_MS] = cooldownMs
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_SERVICE_ENABLED] = enabled
        }
    }
}

