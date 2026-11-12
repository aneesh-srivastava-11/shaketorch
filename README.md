# ShakeTorch 🔦 – Android Shake-to-Toggle Flashlight App

**ShakeTorch** is a modern, battery-efficient, and lightweight Android application built with Kotlin, Jetpack Compose, Material Design 3, and MVVM architecture. It utilizes the phone's built-in accelerometer to detect physical shaking gestures and seamlessly toggles the device flashlight ON and OFF.

---

## ⚡ The Core Concept

The entire application is designed around one intuitive interaction:

### **ONE SHAKE = ONE FLASHLIGHT STATE CHANGE**

| Current Flashlight State | User Action | Result State |
| :--- | :--- | :--- |
| **OFF** | 📱 Shake Phone | **ON** 💡 |
| **ON** | 📱 Shake Phone | **OFF** 🌑 |
| **OFF** | 📱 Shake Phone | **ON** 💡 |
| **ON** | 📱 Shake Phone | **OFF** 🌑 |

- The **exact same physical shaking gesture** alternates between ON and OFF.
- No separate gestures for ON and OFF.
- No requirement to shake twice.
- One intentional shake causes **exactly one state change**.
- Does **not** require pressing any on-screen button.
- Shaking continues to alternate state indefinitely.

---

## 🏗️ Architecture & Technology Stack

- **Language:** Kotlin 1.9.23
- **UI Framework:** Jetpack Compose + Material Design 3
- **Architecture:** MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Sensor Engine:** Android `SensorManager` (`Sensor.TYPE_ACCELEROMETER` with low-pass gravity subtraction)
- **Flashlight Control:** Android `CameraManager` (`setTorchMode`) with `CameraManager.TorchCallback` hardware synchronization
- **Persistence:** Jetpack DataStore Preferences
- **Asynchronous Flow:** Kotlin Coroutines (`StateFlow`, `SharedFlow`, `Mutex`)
- **Background Support:** Android Foreground Service with persistent status notification
- **Compatibility:** Android 8.0 (API 26) through Android 14+ (API 34)

---

## 📁 Project Directory Structure

```text
ShakeTorch/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/shaketorch/
│       │   │   ├── ShakeTorchApp.kt              # Application class & Notification channel setup
│       │   │   ├── MainActivity.kt               # Single Activity host with runtime permission handling
│       │   │   │
│       │   │   ├── data/
│       │   │   │   ├── SensitivityLevel.kt       # Low, Medium, High threshold definitions
│       │   │   │   ├── UserPreferences.kt        # Data holder for persisted user configuration
│       │   │   │   └── SettingsRepository.kt     # Jetpack DataStore Preferences implementation
│       │   │   │
│       │   │   ├── sensor/
│       │   │   │   ├── ShakeListener.kt          # Functional callback interface
│       │   │   │   └── ShakeDetector.kt          # Accelerometer processing, 3D magnitude, hysteresis
│       │   │   │
│       │   │   ├── flashlight/
│       │   │   │   ├── FlashlightState.kt        # Flashlight status & error representations
│       │   │   │   └── FlashlightManager.kt      # CameraManager torch control & hardware sync
│       │   │   │
│       │   │   ├── service/
│       │   │   │   └── ShakeDetectionService.kt  # Foreground Service for background monitoring
│       │   │   │
│       │   │   ├── util/
│       │   │   │   └── FeedbackManager.kt        # Haptic vibration & sound click coordinator
│       │   │   │
│       │   │   ├── viewmodel/
│       │   │   │   └── MainViewModel.kt          # MVVM coordinator for state, sensors & service
│       │   │   │
│       │   │   └── ui/
│       │   │       ├── MainScreen.kt             # Primary glowing flashlight UI & controls
│       │   │       ├── SettingsScreen.kt         # Sensitivity, cooldown, feedback, & background settings
│       │   │       ├── components/
│       │   │       │   ├── FlashlightIndicator.kt# Large glowing animated circular torch indicator
│       │   │       │   └── ShakeStatusBadge.kt   # Live shake detection & trigger pulse badge
│       │   │       └── theme/
│       │   │           ├── Color.kt              # Charcoal dark palette & Amber/Gold accents
│       │   │           ├── Theme.kt              # Material 3 Dark theme setup
│       │   │           └── Type.kt               # Typography specifications
│       │   │
│       │   └── res/
│       │       ├── drawable/                     # Vector drawables & notification icons
│       │       ├── mipmap-anydpi-v26/            # Adaptive launcher icons
│       │       ├── values/                       # strings.xml, colors.xml, themes.xml
│       │       └── xml/                          # Backup and data extraction rules
│       │
│       └── test/
│           └── java/com/shaketorch/
│               ├── sensor/
│               │   └── ShakeDetectorTest.kt      # Debounce, threshold & hysteresis tests
│               └── flashlight/
│                   └── FlashlightLogicTest.kt    # Sequential toggle & state sync tests
│
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── README.md
```

---

## 🔍 How Shake Detection Works

### 1. 3D Euclidean Linear Acceleration Calculation
To avoid falsely triggering from Earth's gravity (~9.8 m/s²), a low-pass filter isolates the gravitational component:

$$\text{gravity}_i = \alpha \cdot \text{gravity}_i + (1 - \alpha) \cdot \text{raw}_i \quad (\alpha = 0.8)$$

Subtracting gravity yields dynamic linear acceleration:

$$\text{linear}_x = \text{raw}_x - \text{gravity}_x$$
$$\text{linear}_y = \text{raw}_y - \text{gravity}_y$$
$$\text{linear}_z = \text{raw}_z - \text{gravity}_z$$

The dynamic acceleration magnitude is computed using the Euclidean norm:

$$\text{magnitude} = \sqrt{\text{linear}_x^2 + \text{linear}_y^2 + \text{linear}_z^2}$$

Because this scalar magnitude is invariant under 3D spatial rotation, the phone detects intentional shakes identically whether held in portrait, landscape, upside-down, or flat.

### 2. Anti-Repeat & False Trigger Prevention
Ordinary movements (walking, rotating the phone, placing it on a desk) typically generate magnitudes below $4.0\text{ m/s}^2$. Intentional shakes generate sharp peaks exceeding $10\text{--}18\text{ m/s}^2$.

To prevent continuous multi-toggling during a vigorous shake cycle:
- **Debounce Cooldown:** A configurable cooldown (default $1000\text{ ms}$) blocks any new trigger until the duration elapses.
- **Hysteresis Reset Condition:** After a shake is accepted, acceleration **must drop below the reset threshold** ($\approx 3.5\text{ m/s}^2$) for at least $250\text{ ms}$ before the detector re-arms. Continuous oscillating movements cannot re-trigger the flashlight without pausing.

### 3. Sensitivity Levels

| Level | Threshold | Reset Threshold | Best Used For |
| :--- | :--- | :--- | :--- |
| **Low** | $16.0\text{ m/s}^2$ | $4.0\text{ m/s}^2$ | Strong deliberate shakes; avoids accidental triggers while running or working out. |
| **Medium** | $12.0\text{ m/s}^2$ | $3.5\text{ m/s}^2$ | Balanced default suitable for everyday physical usage. |
| **High** | $8.5\text{ m/s}^2$ | $3.0\text{ m/s}^2$ | Light, effortless shakes; maximum responsiveness. |

---

## 🔦 Flashlight Hardware Synchronization

Android devices can experience external flashlight state changes (e.g. system battery saver disabling the torch, another app opening the camera, or quick settings toggle).

`FlashlightManager` registers a hardware callback via `CameraManager.TorchCallback`:
```kotlin
override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
    if (cameraId == targetCameraId) {
        _isFlashlightOn.value = enabled
    }
}
```
- If the flashlight is manually turned ON in the UI or Quick Settings, the internal state updates immediately.
- The **very next shake** will turn it OFF cleanly.
- Thread-safe Mutex locks prevent race conditions between rapid sensor events and button taps.

---

## 🔋 Background Operation & Battery Optimization

- **Foreground Service (`ShakeDetectionService`):** Provides an ongoing notification with live status and quick "Toggle" and "Stop" actions.
- **Sensor Rate:** Uses `SensorManager.SENSOR_DELAY_GAME` (~50 Hz), balancing instant gesture response with minimal CPU overhead.
- **Unregistering Listeners:**
  - When the app is in the background and Background Service is **disabled**, accelerometer listeners are completely unregistered.
  - Zero sensor polling when idle.
- **WakeLock Safety:** Uses a partial wake lock with a safety timeout to preserve battery while allowing screen-off detection where OEM policies permit.
- **Android 14 SpecialUse Declaration:** Complies with Android 14 (API 34) Foreground Service type restrictions (`FOREGROUND_SERVICE_SPECIAL_USE`).

---

## 🚀 How to Build and Run

### Prerequisites
1. **Android Studio Iguana / Jellyfish (or later)** or JDK 17+.
2. Android SDK installed with API 34 platforms and build-tools.
3. An Android device running Android 8.0 (API 26) or higher.

### Option A: Open in Android Studio
1. Launch Android Studio.
2. Select **Open** and choose the `hayenemoto` (or `ShakeTorch`) directory.
3. Allow Gradle to synchronize dependencies.
4. Connect an Android phone via USB (with USB Debugging enabled) or use an emulator with sensor controls.
5. Click **Run 'app'** (`Shift + F10`).

### Option B: Build Debug APK from Command Line
In the root directory of the project, execute:

**Windows (PowerShell or Command Prompt):**
```powershell
.\gradlew assembleDebug
```

**Linux / macOS:**
```bash
chmod +x gradlew
./gradlew assembleDebug
```

The resulting debug APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

To install directly to a connected device via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Testing Verification Scenarios

| Test Scenario | Action | Expected Result |
| :--- | :--- | :--- |
| **1. Shake when OFF** | Hold phone, perform one firm shake | Flashlight turns **ON**, indicator glows yellow, haptic pulse fires. |
| **2. Shake when ON** | Perform one firm shake | Flashlight turns **OFF**, indicator becomes muted dark, haptic pulse fires. |
| **3. Continuous shaking** | Vigorously shake phone back and forth 5 times | Flashlight toggles **once** on the first shake; anti-repeat hysteresis suppresses repeat triggers during continuous motion. |
| **4. Cooldown pause** | Shake, wait 1.2 seconds, shake again | Flashlight toggles cleanly on both occasions (cooldown satisfied). |
| **5. Manual Turn ON** | Tap "Manual Turn ON" button on UI | Flashlight turns **ON**; UI status immediately updates to "Flashlight is ON". |
| **6. Shake after Manual ON** | Shake phone after manual turn ON | Flashlight turns **OFF** (synchronized state). |
| **7. Disable shake detection** | In Settings, switch off "Enable Shake Detection" | Shaking phone produces no effect; status badge displays "Shake Disabled". |
| **8. Adjust sensitivity** | Switch sensitivity to "High" or "Low" | Threshold immediately updates without requiring app restart. |
| **9. App restart** | Change settings, force-close app, reopen | Jetpack DataStore preserves all configurations. |
| **10. Missing Hardware** | Run on emulator/device lacking flash | Informative card displayed: *"This device does not have camera flash hardware."* No crash occurs. |
| **11. Background Service** | Enable background detection in Settings, turn screen off or switch apps | Persistent notification appears; shaking phone toggles the physical flashlight. |

---

## 📄 License
This project is open-source under the MIT License.

