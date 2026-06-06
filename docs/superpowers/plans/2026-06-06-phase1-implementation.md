# Android Gesture Control Phase 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working Android app that recognises hand gestures via Google MediaPipe and maps them to system-wide navigation and media actions using a hardcoded gesture-to-action table.

**Architecture:** A `GestureRecognitionService` (ForegroundService) owns the camera and MediaPipe pipeline, detects static and dynamic gestures, and emits typed events via a Kotlin `SharedFlow` singleton (`GestureEventBus`). A `GestureAccessibilityService` collects those events and dispatches Android system actions. A `TileService` provides the Quick Settings toggle. A `MainActivity` handles onboarding and camera-lens selection via DataStore.

**Tech Stack:** Kotlin 2.0, Android min SDK 26 / target SDK 34, CameraX 1.3.4, MediaPipe Tasks 0.10.14 (`GestureRecognizer`), Jetpack DataStore 1.1.1, Kotlin Coroutines 1.8.1, JUnit 4 + MockK 1.13.12 for unit tests.

---

## File Map

| Path | Purpose |
|---|---|
| `app/build.gradle.kts` | App module — SDK config, all dependencies |
| `build.gradle.kts` | Root build file — AGP + Kotlin versions |
| `settings.gradle.kts` | Module declaration |
| `gradle.properties` | JVM args, AndroidX flag |
| `app/src/main/AndroidManifest.xml` | Permissions, component declarations |
| `app/src/main/res/xml/accessibility_service_config.xml` | AccessibilityService metadata |
| `app/src/main/res/layout/activity_main.xml` | MainActivity layout |
| `app/src/main/res/values/strings.xml` | String resources |
| `app/src/main/java/com/gesturecontrol/MainActivity.kt` | Onboarding, camera selection, live status |
| `app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt` | ForegroundService — camera + MediaPipe + event emission |
| `app/src/main/java/com/gesturecontrol/services/GestureAccessibilityService.kt` | AccessibilityService — event collection + system action dispatch |
| `app/src/main/java/com/gesturecontrol/services/GestureControlTileService.kt` | TileService — Quick Settings toggle |
| `app/src/main/java/com/gesturecontrol/gesture/GestureEvent.kt` | `GestureEvent` sealed class + enums (`StaticGestureType`, `SwipeDirection`, `HandSide`) |
| `app/src/main/java/com/gesturecontrol/gesture/GestureEventBus.kt` | `SharedFlow` singleton |
| `app/src/main/java/com/gesturecontrol/gesture/detection/StaticGestureDetector.kt` | Maps MediaPipe category to `StaticGestureType`; enforces 0.8 s dwell timer |
| `app/src/main/java/com/gesturecontrol/gesture/detection/DynamicGestureTracker.kt` | 10-frame wrist ring buffer; classifies swipe direction |
| `app/src/main/java/com/gesturecontrol/gesture/detection/GestureDebouncer.kt` | Per-key 800 ms cooldown |
| `app/src/main/java/com/gesturecontrol/gesture/pipeline/MPImageConverter.kt` | `ImageProxy` → `MPImage` conversion |
| `app/src/main/java/com/gesturecontrol/gesture/mapping/GestureActionMapper.kt` | Hardcoded `GestureEvent` → `SystemAction` table |
| `app/src/main/java/com/gesturecontrol/gesture/mapping/SystemAction.kt` | `SystemAction` sealed class |
| `app/src/main/java/com/gesturecontrol/settings/CameraPreferenceRepository.kt` | DataStore wrapper for lens selection |
| `app/src/main/assets/gesture_recognizer.task` | MediaPipe model file (downloaded, not generated) |
| `app/src/test/java/com/gesturecontrol/gesture/GestureEventBusTest.kt` | Unit tests — emit/collect |
| `app/src/test/java/com/gesturecontrol/gesture/detection/StaticGestureDetectorTest.kt` | Unit tests — category mapping + dwell timer |
| `app/src/test/java/com/gesturecontrol/gesture/detection/DynamicGestureTrackerTest.kt` | Unit tests — ring buffer + swipe classification |
| `app/src/test/java/com/gesturecontrol/gesture/detection/GestureDebouncerTest.kt` | Unit tests — cooldown logic |
| `app/src/test/java/com/gesturecontrol/gesture/mapping/GestureActionMapperTest.kt` | Unit tests — every gesture/action pair |

---

## Phase 1 — Project Foundation

### Task 1: Android project scaffold

**Files:**
- Create: all Gradle files, `AndroidManifest.xml`, base resource files

> **Note:** The repo currently has a plain IntelliJ Java module. The cleanest approach is to use Android Studio's **New Project** wizard (Empty Activity, Kotlin DSL, package `com.gesturecontrol`, min SDK 26) which generates the correct Gradle setup automatically. After creation, replace `app/build.gradle.kts` with the content below to add all required dependencies in one step.

- [ ] **Step 1: Create the Android project via Android Studio**

  File → New → New Project → Empty Activity
  - Name: `Android Gesture Control`
  - Package: `com.gesturecontrol`
  - Language: Kotlin
  - Minimum SDK: API 26

  This generates `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `gradlew`, `AndroidManifest.xml`, `MainActivity.kt`, `activity_main.xml`, `strings.xml`.

- [ ] **Step 2: Replace `app/build.gradle.kts` with full dependency set**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gesturecontrol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gesturecontrol"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { viewBinding = true }
}

dependencies {
    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")

    // MediaPipe Tasks
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Jetpack DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Lifecycle service (LifecycleService base class)
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")

    // AppCompat + Material
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
```

- [ ] **Step 3: Verify the project builds**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts build.gradle.kts settings.gradle.kts gradle.properties gradle/ app/src/
git commit -m "chore: scaffold Android project with all dependencies"
```

---

### Task 2: GestureEvent model

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/gesture/GestureEvent.kt`
- Create: `app/src/test/java/com/gesturecontrol/gesture/GestureEventTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/gesturecontrol/gesture/GestureEventTest.kt`:
```kotlin
package com.gesturecontrol.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureEventTest {

    @Test
    fun `Static event holds type and hand`() {
        val event = GestureEvent.Static(StaticGestureType.VICTORY, HandSide.RIGHT)
        assertEquals(StaticGestureType.VICTORY, event.type)
        assertEquals(HandSide.RIGHT, event.hand)
    }

    @Test
    fun `Dynamic event holds direction and hand`() {
        val event = GestureEvent.Dynamic(SwipeDirection.LEFT, HandSide.LEFT)
        assertEquals(SwipeDirection.LEFT, event.direction)
        assertEquals(HandSide.LEFT, event.hand)
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.GestureEventTest"
```
Expected: FAIL — `GestureEvent` not defined.

- [ ] **Step 3: Implement `GestureEvent.kt`**

```kotlin
package com.gesturecontrol.gesture

sealed class GestureEvent {
    data class Static(val type: StaticGestureType, val hand: HandSide) : GestureEvent()
    data class Dynamic(val direction: SwipeDirection, val hand: HandSide) : GestureEvent()
}

enum class StaticGestureType { OPEN_PALM, VICTORY, THUMB_UP, THUMB_DOWN, CLOSED_FIST }
enum class SwipeDirection    { LEFT, RIGHT, UP, DOWN }
enum class HandSide          { LEFT, RIGHT }
```

- [ ] **Step 4: Run to confirm it passes**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.GestureEventTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/gesture/GestureEvent.kt \
        app/src/test/java/com/gesturecontrol/gesture/GestureEventTest.kt
git commit -m "feat: add GestureEvent sealed class and gesture enums"
```

---

### Task 3: Accessibility service skeleton + XML config

**Files:**
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Create: `app/src/main/java/com/gesturecontrol/services/GestureAccessibilityService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Create `res/xml/accessibility_service_config.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
```

- [ ] **Step 2: Add string resource**

In `app/src/main/res/values/strings.xml` add inside `<resources>`:
```xml
<string name="accessibility_service_description">Controls your device with hand gestures using the camera.</string>
<string name="notification_channel_name">Gesture Control</string>
<string name="notification_title">Gesture Control Active</string>
<string name="notification_text">Tap to open settings</string>
```

- [ ] **Step 3: Create `GestureAccessibilityService.kt` skeleton**

```kotlin
package com.gesturecontrol.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
    }
}
```

- [ ] **Step 4: Declare in `AndroidManifest.xml`**

Inside `<application>`:
```xml
<service
    android:name=".services.GestureAccessibilityService"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

- [ ] **Step 5: Build to confirm no errors**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/xml/accessibility_service_config.xml \
        app/src/main/java/com/gesturecontrol/services/GestureAccessibilityService.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat: add GestureAccessibilityService skeleton and XML config"
```

---

### Task 4: MainActivity — accessibility prompt + camera selection stub

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Define layout `activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp"
    android:gravity="center_horizontal">

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/status_inactive"
        android:textSize="18sp"
        android:layout_marginBottom="16dp" />

    <Button
        android:id="@+id/btnEnableAccessibility"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/btn_enable_accessibility"
        android:layout_marginBottom="16dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/label_camera"
        android:layout_marginBottom="8dp" />

    <RadioGroup
        android:id="@+id/rgCamera"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <RadioButton
            android:id="@+id/rbFront"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/camera_front"
            android:checked="true" />

        <RadioButton
            android:id="@+id/rbBack"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/camera_back"
            android:layout_marginStart="16dp" />
    </RadioGroup>
</LinearLayout>
```

- [ ] **Step 2: Add strings**

```xml
<string name="status_inactive">Gesture Control: Inactive</string>
<string name="status_active">Gesture Control: Active</string>
<string name="btn_enable_accessibility">Enable Accessibility Service</string>
<string name="label_camera">Camera</string>
<string name="camera_front">Front</string>
<string name="camera_back">Back</string>
```

- [ ] **Step 3: Implement `MainActivity.kt`**

```kotlin
package com.gesturecontrol

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.gesturecontrol.databinding.ActivityMainBinding
import com.gesturecontrol.services.GestureAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isAccessibilityServiceEnabled()
        binding.tvStatus.setText(
            if (enabled) R.string.status_active else R.string.status_inactive
        )
        binding.btnEnableAccessibility.isEnabled = !enabled
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(
            "${packageName}/${GestureAccessibilityService::class.java.name}"
        )
    }
}
```

- [ ] **Step 4: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/MainActivity.kt \
        app/src/main/res/layout/activity_main.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat: add MainActivity with accessibility prompt and camera selection stub"
```

---

## Phase 2 — Camera Pipeline

### Task 5: GestureRecognitionService — ForegroundService skeleton

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permissions to `AndroidManifest.xml`**

Inside `<manifest>` (before `<application>`):
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />

<uses-feature android:name="android.hardware.camera" android:required="false" />
```

- [ ] **Step 2: Declare the service in `AndroidManifest.xml`**

Inside `<application>`:
```xml
<service
    android:name=".services.GestureRecognitionService"
    android:foregroundServiceType="camera"
    android:exported="false" />
```

- [ ] **Step 3: Create `GestureRecognitionService.kt`**

```kotlin
package com.gesturecontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.gesturecontrol.R

class GestureRecognitionService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "gesture_control_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}
```

- [ ] **Step 4: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add GestureRecognitionService ForegroundService skeleton"
```

---

### Task 6: CameraPreferenceRepository

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/settings/CameraPreferenceRepository.kt`

- [ ] **Step 1: Create the DataStore extension and repository**

```kotlin
package com.gesturecontrol.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class LensFacing { FRONT, BACK }

private val Context.cameraDataStore: DataStore<Preferences> by preferencesDataStore("camera_prefs")

class CameraPreferenceRepository(private val context: Context) {

    companion object {
        private val LENS_KEY = stringPreferencesKey("lens_facing")
    }

    suspend fun getLens(): LensFacing {
        val raw = context.cameraDataStore.data
            .map { it[LENS_KEY] ?: LensFacing.FRONT.name }
            .first()
        return LensFacing.valueOf(raw)
    }

    suspend fun setLens(lens: LensFacing) {
        context.cameraDataStore.edit { it[LENS_KEY] = lens.name }
    }
}
```

- [ ] **Step 2: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/settings/CameraPreferenceRepository.kt
git commit -m "feat: add CameraPreferenceRepository with DataStore"
```

---

### Task 7: CameraX integration in GestureRecognitionService

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt`

- [ ] **Step 1: Wire CameraX into the service**

Replace `GestureRecognitionService.kt` with:

```kotlin
package com.gesturecontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.gesturecontrol.R
import com.gesturecontrol.settings.CameraPreferenceRepository
import com.gesturecontrol.settings.LensFacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureRecognitionService : LifecycleService() {

    companion object {
        private const val TAG = "GestureRecognition"
        private const val CHANNEL_ID = "gesture_control_channel"
        private const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraPrefs: CameraPreferenceRepository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification())
        cameraPrefs = CameraPreferenceRepository(applicationContext)
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        cameraExecutor.shutdown()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun startCamera() {
        val lensFacing = runBlocking { cameraPrefs.getLens() }
        val cameraSelector = when (lensFacing) {
            LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            LensFacing.BACK  -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ::analyzeFrame)
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            Log.d(TAG, "Frame received: ${imageProxy.width}x${imageProxy.height}")
        } finally {
            imageProxy.close()
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}
```

- [ ] **Step 2: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Wire camera selection in MainActivity**

In `MainActivity.kt`, add after `binding = ActivityMainBinding.inflate(layoutInflater)`:

```kotlin
private val cameraPrefs by lazy { CameraPreferenceRepository(applicationContext) }

// Inside onCreate, after setContentView:
lifecycleScope.launch {
    val lens = cameraPrefs.getLens()
    if (lens == LensFacing.BACK) binding.rbBack.isChecked = true
}

binding.rgCamera.setOnCheckedChangeListener { _, checkedId ->
    val lens = if (checkedId == R.id.rbFront) LensFacing.FRONT else LensFacing.BACK
    lifecycleScope.launch { cameraPrefs.setLens(lens) }
}
```

Add import at top of `MainActivity.kt`:
```kotlin
import androidx.lifecycle.lifecycleScope
import com.gesturecontrol.settings.CameraPreferenceRepository
import com.gesturecontrol.settings.LensFacing
import kotlinx.coroutines.launch
```

- [ ] **Step 4: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt \
        app/src/main/java/com/gesturecontrol/MainActivity.kt
git commit -m "feat: integrate CameraX into GestureRecognitionService with selectable lens"
```

---

## Phase 3 — MediaPipe Integration

### Task 8: Download MediaPipe model + MPImageConverter

**Files:**
- Create: `app/src/main/assets/gesture_recognizer.task` (downloaded)
- Create: `app/src/main/java/com/gesturecontrol/gesture/pipeline/MPImageConverter.kt`

- [ ] **Step 1: Download the MediaPipe model**

  1. Go to: https://ai.google.dev/edge/mediapipe/solutions/vision/gesture_recognizer
  2. Download `gesture_recognizer.task`
  3. Create folder: `app/src/main/assets/`
  4. Place the file at: `app/src/main/assets/gesture_recognizer.task`

- [ ] **Step 2: Create `MPImageConverter.kt`**

```kotlin
package com.gesturecontrol.gesture.pipeline

import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

object MPImageConverter {

    fun convert(imageProxy: ImageProxy): Pair<MPImage, Int> {
        val bitmap = imageProxy.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()
        return Pair(mpImage, imageProxy.imageInfo.rotationDegrees)
    }
}
```

- [ ] **Step 3: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/gesture_recognizer.task \
        app/src/main/java/com/gesturecontrol/gesture/pipeline/MPImageConverter.kt
git commit -m "feat: add MediaPipe model asset and MPImageConverter"
```

---

### Task 9: Wire GestureRecognizer into GestureRecognitionService (log detections)

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt`

- [ ] **Step 1: Add MediaPipe initialisation and frame processing**

Add these imports to `GestureRecognitionService.kt`:
```kotlin
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerOptions
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.gesturecontrol.gesture.pipeline.MPImageConverter
```

Add field to `GestureRecognitionService`:
```kotlin
private lateinit var gestureRecognizer: GestureRecognizer
```

Add `initMediaPipe()` method:
```kotlin
private fun initMediaPipe() {
    val options = GestureRecognizerOptions.builder()
        .setBaseOptions(
            BaseOptions.builder()
                .setModelAssetPath("gesture_recognizer.task")
                .build()
        )
        .setRunningMode(RunningMode.LIVE_STREAM)
        .setResultListener(::onGestureResult)
        .setErrorListener { error -> Log.e(TAG, "MediaPipe error", error) }
        .build()
    gestureRecognizer = GestureRecognizer.createFromOptions(this, options)
}

private fun onGestureResult(result: GestureRecognizerResult, input: com.google.mediapipe.framework.image.MPImage) {
    if (result.gestures().isEmpty()) return
    val gesture = result.gestures()[0].firstOrNull() ?: return
    Log.d(TAG, "Detected: ${gesture.categoryName()} score=${gesture.score()}")
}
```

Replace `analyzeFrame` with:
```kotlin
private fun analyzeFrame(imageProxy: ImageProxy) {
    try {
        val (mpImage, rotation) = MPImageConverter.convert(imageProxy)
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotation)
            .build()
        gestureRecognizer.recognizeAsync(
            mpImage,
            imageOptions,
            imageProxy.imageInfo.timestamp / 1_000_000L
        )
    } finally {
        imageProxy.close()
    }
}
```

Call `initMediaPipe()` from `onStartCommand` before `startCamera()`.

- [ ] **Step 2: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Smoke-test on a device**

  Install and run the app. Enable the accessibility service. The service starts via the tile (Task 18) — for now, start it temporarily by calling `startForegroundService` from `MainActivity.onCreate` for smoke-testing:
  ```kotlin
  startForegroundService(Intent(this, GestureRecognitionService::class.java))
  ```
  Open Logcat and filter for `GestureRecognition`. Show your hand to the camera. Confirm gesture category names appear in logs (e.g. `Detected: Open_Palm score=0.92`). Remove the temporary startForegroundService call after confirming.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt
git commit -m "feat: integrate MediaPipe GestureRecognizer — log raw detections"
```

---

## Phase 4 — Gesture Detection Layer

### Task 10: GestureEventBus

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/gesture/GestureEventBus.kt`
- Create: `app/src/test/java/com/gesturecontrol/gesture/GestureEventBusTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.gesturecontrol.gesture

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureEventBusTest {

    @Test
    fun `emitted event is received by collector`() = runTest {
        val received = mutableListOf<GestureEvent>()
        val job = launch { GestureEventBus.events.collect { received.add(it) } }

        GestureEventBus.emit(GestureEvent.Static(StaticGestureType.VICTORY, HandSide.RIGHT))
        delay(50)
        job.cancel()

        assertEquals(1, received.size)
        assertEquals(StaticGestureType.VICTORY, (received[0] as GestureEvent.Static).type)
    }

    @Test
    fun `collector does not receive events emitted before it subscribed`() = runTest {
        GestureEventBus.emit(GestureEvent.Static(StaticGestureType.THUMB_UP, HandSide.LEFT))
        delay(10)

        val received = mutableListOf<GestureEvent>()
        val job = launch { GestureEventBus.events.collect { received.add(it) } }
        delay(50)
        job.cancel()

        assertEquals(0, received.size)
    }
}
```

- [ ] **Step 2: Run to confirm fail**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.GestureEventBusTest"
```
Expected: FAIL — `GestureEventBus` not defined.

- [ ] **Step 3: Implement `GestureEventBus.kt`**

```kotlin
package com.gesturecontrol.gesture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GestureEventBus {
    private val _events = MutableSharedFlow<GestureEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GestureEvent> = _events.asSharedFlow()

    fun emit(event: GestureEvent) {
        _events.tryEmit(event)
    }
}
```

- [ ] **Step 4: Run to confirm pass**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.GestureEventBusTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/gesture/GestureEventBus.kt \
        app/src/test/java/com/gesturecontrol/gesture/GestureEventBusTest.kt
git commit -m "feat: add GestureEventBus SharedFlow singleton"
```

---

### Task 11: StaticGestureDetector

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/gesture/detection/StaticGestureDetector.kt`
- Create: `app/src/test/java/com/gesturecontrol/gesture/detection/StaticGestureDetectorTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.gesturecontrol.gesture.detection

import com.gesturecontrol.gesture.StaticGestureType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StaticGestureDetectorTest {

    private lateinit var detector: StaticGestureDetector

    @Before fun setUp() { detector = StaticGestureDetector(dwellThresholdMs = 800L) }

    @Test
    fun `Victory returns immediately without dwell`() {
        val result = detector.process("Victory", timestampMs = 1000L)
        assertEquals(StaticGestureType.VICTORY, result)
    }

    @Test
    fun `Thumb_Up returns immediately without dwell`() {
        assertEquals(StaticGestureType.THUMB_UP, detector.process("Thumb_Up", 1000L))
    }

    @Test
    fun `Thumb_Down returns immediately without dwell`() {
        assertEquals(StaticGestureType.THUMB_DOWN, detector.process("Thumb_Down", 1000L))
    }

    @Test
    fun `Open_Palm returns null before dwell threshold`() {
        assertNull(detector.process("Open_Palm", 1000L))
        assertNull(detector.process("Open_Palm", 1500L))
    }

    @Test
    fun `Open_Palm returns type after dwell threshold`() {
        assertNull(detector.process("Open_Palm", 1000L))
        assertEquals(StaticGestureType.OPEN_PALM, detector.process("Open_Palm", 1801L))
    }

    @Test
    fun `Closed_Fist returns type after dwell threshold`() {
        assertNull(detector.process("Closed_Fist", 1000L))
        assertEquals(StaticGestureType.CLOSED_FIST, detector.process("Closed_Fist", 1801L))
    }

    @Test
    fun `Dwell resets when gesture changes`() {
        assertNull(detector.process("Open_Palm", 1000L))
        assertNull(detector.process("Victory", 1400L))   // switches — resets
        assertNull(detector.process("Open_Palm", 1500L)) // new dwell starts at 1500
        assertNull(detector.process("Open_Palm", 2100L)) // 600ms — not yet
        assertEquals(StaticGestureType.OPEN_PALM, detector.process("Open_Palm", 2301L)) // 801ms
    }

    @Test
    fun `Unknown category returns null`() {
        assertNull(detector.process("None", 1000L))
        assertNull(detector.process("ILoveYou", 1000L))
    }

    @Test
    fun `Reset clears dwell state`() {
        assertNull(detector.process("Open_Palm", 1000L))
        detector.reset()
        assertNull(detector.process("Open_Palm", 1500L)) // timer starts fresh
    }
}
```

- [ ] **Step 2: Run to confirm fail**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.detection.StaticGestureDetectorTest"
```
Expected: FAIL

- [ ] **Step 3: Implement `StaticGestureDetector.kt`**

```kotlin
package com.gesturecontrol.gesture.detection

import com.gesturecontrol.gesture.StaticGestureType

class StaticGestureDetector(private val dwellThresholdMs: Long = 800L) {

    private var dwellType: StaticGestureType? = null
    private var dwellStartMs: Long = 0L

    fun process(categoryName: String, timestampMs: Long): StaticGestureType? {
        val type = mapCategory(categoryName) ?: run { reset(); return null }

        return if (requiresDwell(type)) {
            if (type == dwellType) {
                if (timestampMs - dwellStartMs >= dwellThresholdMs) type else null
            } else {
                dwellType = type
                dwellStartMs = timestampMs
                null
            }
        } else {
            dwellType = null
            type
        }
    }

    fun reset() {
        dwellType = null
        dwellStartMs = 0L
    }

    private fun mapCategory(name: String): StaticGestureType? = when (name) {
        "Open_Palm"    -> StaticGestureType.OPEN_PALM
        "Victory"      -> StaticGestureType.VICTORY
        "Thumb_Up"     -> StaticGestureType.THUMB_UP
        "Thumb_Down"   -> StaticGestureType.THUMB_DOWN
        "Closed_Fist"  -> StaticGestureType.CLOSED_FIST
        else           -> null
    }

    private fun requiresDwell(type: StaticGestureType): Boolean =
        type == StaticGestureType.OPEN_PALM || type == StaticGestureType.CLOSED_FIST
}
```

- [ ] **Step 4: Run to confirm pass**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.detection.StaticGestureDetectorTest"
```
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/gesture/detection/StaticGestureDetector.kt \
        app/src/test/java/com/gesturecontrol/gesture/detection/StaticGestureDetectorTest.kt
git commit -m "feat: add StaticGestureDetector with dwell timer"
```

---

### Task 12: DynamicGestureTracker

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/gesture/detection/DynamicGestureTracker.kt`
- Create: `app/src/test/java/com/gesturecontrol/gesture/detection/DynamicGestureTrackerTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.gesturecontrol.gesture.detection

import com.gesturecontrol.gesture.SwipeDirection
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DynamicGestureTrackerTest {

    // Small buffer and low threshold for testability
    private lateinit var tracker: DynamicGestureTracker

    @Before fun setUp() {
        tracker = DynamicGestureTracker(bufferSize = 5, displacementThreshold = 0.15f, purityThreshold = 0.7f)
    }

    @Test
    fun `returns null while buffer is filling`() {
        repeat(4) { assertNull(tracker.process(0.5f, 0.5f, it.toLong())) }
    }

    @Test
    fun `no swipe when displacement is below threshold`() {
        // Move only 0.05 to the left — below 0.15 threshold
        repeat(5) { i -> tracker.process(0.5f - i * 0.01f, 0.5f, i.toLong()) }
        val result = tracker.process(0.45f, 0.5f, 5L)
        assertNull(result)
    }

    @Test
    fun `detects LEFT swipe`() {
        // Hand moves 0.3 to the left over 5 frames
        val result = feedSwipe(dx = -0.3f, dy = 0.0f)
        assertEquals(SwipeDirection.LEFT, result)
    }

    @Test
    fun `detects RIGHT swipe`() {
        assertEquals(SwipeDirection.RIGHT, feedSwipe(dx = 0.3f, dy = 0.0f))
    }

    @Test
    fun `detects UP swipe`() {
        // dy negative = hand moves upward in image
        assertEquals(SwipeDirection.UP, feedSwipe(dx = 0.0f, dy = -0.3f))
    }

    @Test
    fun `detects DOWN swipe`() {
        assertEquals(SwipeDirection.DOWN, feedSwipe(dx = 0.0f, dy = 0.3f))
    }

    @Test
    fun `diagonal motion below purity threshold returns null`() {
        // Equal dx and dy — purity = 0.5, below 0.7
        val result = feedSwipe(dx = 0.25f, dy = 0.25f)
        assertNull(result)
    }

    @Test
    fun `clear resets the buffer`() {
        feedSwipe(dx = -0.3f, dy = 0.0f)
        tracker.clear()
        // After clear, buffer is empty — no swipe yet
        repeat(4) { assertNull(tracker.process(0.5f, 0.5f, it.toLong())) }
    }

    private fun feedSwipe(dx: Float, dy: Float): SwipeDirection? {
        val n = 5
        var result: SwipeDirection? = null
        for (i in 0 until n) {
            val x = 0.5f + dx * i / (n - 1)
            val y = 0.5f + dy * i / (n - 1)
            result = tracker.process(x, y, i.toLong())
        }
        return result
    }
}
```

- [ ] **Step 2: Run to confirm fail**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.detection.DynamicGestureTrackerTest"
```
Expected: FAIL

- [ ] **Step 3: Implement `DynamicGestureTracker.kt`**

```kotlin
package com.gesturecontrol.gesture.detection

import com.gesturecontrol.gesture.SwipeDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class DynamicGestureTracker(
    private val bufferSize: Int = 10,
    private val displacementThreshold: Float = 0.15f,
    private val purityThreshold: Float = 0.7f
) {

    private data class Position(val x: Float, val y: Float)

    private val buffer = ArrayDeque<Position>()

    fun process(x: Float, y: Float, timestampMs: Long): SwipeDirection? {
        buffer.addLast(Position(x, y))
        if (buffer.size > bufferSize) buffer.removeFirst()
        if (buffer.size < bufferSize) return null

        val dx = buffer.last().x - buffer.first().x
        val dy = buffer.last().y - buffer.first().y
        val magnitude = sqrt(dx * dx + dy * dy)

        if (magnitude < displacementThreshold) return null

        val absDx = abs(dx)
        val absDy = abs(dy)
        val purity = max(absDx, absDy) / (absDx + absDy)

        if (purity < purityThreshold) return null

        return when {
            absDx >= absDy && dx < 0 -> SwipeDirection.LEFT
            absDx >= absDy && dx > 0 -> SwipeDirection.RIGHT
            absDy > absDx  && dy < 0 -> SwipeDirection.UP
            else                      -> SwipeDirection.DOWN
        }
    }

    fun clear() { buffer.clear() }
}
```

- [ ] **Step 4: Run to confirm pass**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.detection.DynamicGestureTrackerTest"
```
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/gesture/detection/DynamicGestureTracker.kt \
        app/src/test/java/com/gesturecontrol/gesture/detection/DynamicGestureTrackerTest.kt
git commit -m "feat: add DynamicGestureTracker with ring buffer and swipe classification"
```

---

### Task 13: GestureDebouncer

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/gesture/detection/GestureDebouncer.kt`
- Create: `app/src/test/java/com/gesturecontrol/gesture/detection/GestureDebouncerTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.gesturecontrol.gesture.detection

import com.gesturecontrol.gesture.StaticGestureType
import com.gesturecontrol.gesture.SwipeDirection
import org.junit.Assert.*
import org.junit.Test

class GestureDebouncerTest {

    @Test
    fun `first emission is always allowed`() {
        val debouncer = GestureDebouncer(cooldownMs = 800L) { 1000L }
        assertTrue(debouncer.tryEmit(StaticGestureType.VICTORY))
    }

    @Test
    fun `second emission within cooldown is suppressed`() {
        var time = 1000L
        val debouncer = GestureDebouncer(cooldownMs = 800L) { time }
        assertTrue(debouncer.tryEmit(StaticGestureType.VICTORY))
        time = 1500L
        assertFalse(debouncer.tryEmit(StaticGestureType.VICTORY))
    }

    @Test
    fun `emission is allowed after cooldown expires`() {
        var time = 1000L
        val debouncer = GestureDebouncer(cooldownMs = 800L) { time }
        assertTrue(debouncer.tryEmit(StaticGestureType.VICTORY))
        time = 1801L
        assertTrue(debouncer.tryEmit(StaticGestureType.VICTORY))
    }

    @Test
    fun `different keys have independent cooldowns`() {
        val debouncer = GestureDebouncer(cooldownMs = 800L) { 1000L }
        assertTrue(debouncer.tryEmit(StaticGestureType.VICTORY))
        assertTrue(debouncer.tryEmit(SwipeDirection.LEFT))
    }

    @Test
    fun `same key after exact cooldown boundary is allowed`() {
        var time = 1000L
        val debouncer = GestureDebouncer(cooldownMs = 800L) { time }
        assertTrue(debouncer.tryEmit(SwipeDirection.LEFT))
        time = 1800L
        assertTrue(debouncer.tryEmit(SwipeDirection.LEFT))
    }
}
```

- [ ] **Step 2: Run to confirm fail**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.detection.GestureDebouncerTest"
```
Expected: FAIL

- [ ] **Step 3: Implement `GestureDebouncer.kt`**

```kotlin
package com.gesturecontrol.gesture.detection

class GestureDebouncer(
    private val cooldownMs: Long = 800L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val lastEmitted = mutableMapOf<Any, Long>()

    fun tryEmit(key: Any): Boolean {
        val now = clock()
        val last = lastEmitted[key] ?: 0L
        return if (now - last >= cooldownMs) {
            lastEmitted[key] = now
            true
        } else {
            false
        }
    }
}
```

- [ ] **Step 4: Run to confirm pass**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.detection.GestureDebouncerTest"
```
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/gesture/detection/GestureDebouncer.kt \
        app/src/test/java/com/gesturecontrol/gesture/detection/GestureDebouncerTest.kt
git commit -m "feat: add GestureDebouncer with injectable clock for testability"
```

---

### Task 14: Wire detection pipeline into GestureRecognitionService

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt`

- [ ] **Step 1: Replace `onGestureResult` with full detection pipeline**

Add fields to `GestureRecognitionService`:
```kotlin
private val staticDetector = StaticGestureDetector()
private val dynamicTracker = DynamicGestureTracker()
private val debouncer = GestureDebouncer()
```

Add imports:
```kotlin
import com.gesturecontrol.gesture.GestureEvent
import com.gesturecontrol.gesture.GestureEventBus
import com.gesturecontrol.gesture.HandSide
import com.gesturecontrol.gesture.detection.DynamicGestureTracker
import com.gesturecontrol.gesture.detection.GestureDebouncer
import com.gesturecontrol.gesture.detection.StaticGestureDetector
```

Replace `onGestureResult` with:
```kotlin
private fun onGestureResult(
    result: com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult,
    input: com.google.mediapipe.framework.image.MPImage
) {
    val timestampMs = System.currentTimeMillis()

    if (result.gestures().isEmpty()) {
        staticDetector.reset()
        dynamicTracker.clear()
        return
    }

    val gestures = result.gestures()[0]
    val landmarks = result.handLandmarks()[0]
    val handedness = result.handedness()[0].firstOrNull()
    val hand = if (handedness?.categoryName() == "Left") HandSide.LEFT else HandSide.RIGHT
    val wrist = landmarks[0]

    // Static gesture
    val staticType = gestures.firstOrNull()?.let {
        staticDetector.process(it.categoryName(), timestampMs)
    }
    if (staticType != null && debouncer.tryEmit(staticType)) {
        GestureEventBus.emit(GestureEvent.Static(staticType, hand))
    }

    // Dynamic gesture
    val swipe = dynamicTracker.process(wrist.x(), wrist.y(), timestampMs)
    if (swipe != null && debouncer.tryEmit(swipe)) {
        GestureEventBus.emit(GestureEvent.Dynamic(swipe, hand))
    }
}
```

- [ ] **Step 2: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt
git commit -m "feat: wire static and dynamic gesture detection into GestureRecognitionService"
```

---

## Phase 5 — Action Dispatch

### Task 15: SystemAction + GestureActionMapper

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/gesture/mapping/SystemAction.kt`
- Create: `app/src/main/java/com/gesturecontrol/gesture/mapping/GestureActionMapper.kt`
- Create: `app/src/test/java/com/gesturecontrol/gesture/mapping/GestureActionMapperTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.gesturecontrol.gesture.mapping

import com.gesturecontrol.gesture.*
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureActionMapperTest {

    @Test fun `Open_Palm maps to Home`() =
        assertEquals(SystemAction.Home, GestureActionMapper.map(GestureEvent.Static(StaticGestureType.OPEN_PALM, HandSide.RIGHT)))

    @Test fun `Victory maps to Recents`() =
        assertEquals(SystemAction.Recents, GestureActionMapper.map(GestureEvent.Static(StaticGestureType.VICTORY, HandSide.RIGHT)))

    @Test fun `Thumb_Up maps to VolumeUp`() =
        assertEquals(SystemAction.VolumeUp, GestureActionMapper.map(GestureEvent.Static(StaticGestureType.THUMB_UP, HandSide.RIGHT)))

    @Test fun `Thumb_Down maps to VolumeDown`() =
        assertEquals(SystemAction.VolumeDown, GestureActionMapper.map(GestureEvent.Static(StaticGestureType.THUMB_DOWN, HandSide.RIGHT)))

    @Test fun `Closed_Fist maps to Notifications`() =
        assertEquals(SystemAction.Notifications, GestureActionMapper.map(GestureEvent.Static(StaticGestureType.CLOSED_FIST, HandSide.RIGHT)))

    @Test fun `Swipe LEFT maps to Back`() =
        assertEquals(SystemAction.Back, GestureActionMapper.map(GestureEvent.Dynamic(SwipeDirection.LEFT, HandSide.RIGHT)))

    @Test fun `Swipe RIGHT maps to QuickSettings`() =
        assertEquals(SystemAction.QuickSettings, GestureActionMapper.map(GestureEvent.Dynamic(SwipeDirection.RIGHT, HandSide.RIGHT)))

    @Test fun `Swipe UP maps to ScrollUp`() =
        assertEquals(SystemAction.ScrollUp, GestureActionMapper.map(GestureEvent.Dynamic(SwipeDirection.UP, HandSide.RIGHT)))

    @Test fun `Swipe DOWN maps to ScrollDown`() =
        assertEquals(SystemAction.ScrollDown, GestureActionMapper.map(GestureEvent.Dynamic(SwipeDirection.DOWN, HandSide.RIGHT)))
}
```

- [ ] **Step 2: Run to confirm fail**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.mapping.GestureActionMapperTest"
```
Expected: FAIL

- [ ] **Step 3: Implement `SystemAction.kt`**

```kotlin
package com.gesturecontrol.gesture.mapping

sealed class SystemAction {
    object Home          : SystemAction()
    object Back          : SystemAction()
    object Recents       : SystemAction()
    object Notifications : SystemAction()
    object QuickSettings : SystemAction()
    object VolumeUp      : SystemAction()
    object VolumeDown    : SystemAction()
    object ScrollUp      : SystemAction()
    object ScrollDown    : SystemAction()
}
```

- [ ] **Step 4: Implement `GestureActionMapper.kt`**

```kotlin
package com.gesturecontrol.gesture.mapping

import com.gesturecontrol.gesture.*

object GestureActionMapper {
    fun map(event: GestureEvent): SystemAction = when (event) {
        is GestureEvent.Static -> when (event.type) {
            StaticGestureType.OPEN_PALM   -> SystemAction.Home
            StaticGestureType.VICTORY     -> SystemAction.Recents
            StaticGestureType.THUMB_UP    -> SystemAction.VolumeUp
            StaticGestureType.THUMB_DOWN  -> SystemAction.VolumeDown
            StaticGestureType.CLOSED_FIST -> SystemAction.Notifications
        }
        is GestureEvent.Dynamic -> when (event.direction) {
            SwipeDirection.LEFT  -> SystemAction.Back
            SwipeDirection.RIGHT -> SystemAction.QuickSettings
            SwipeDirection.UP    -> SystemAction.ScrollUp
            SwipeDirection.DOWN  -> SystemAction.ScrollDown
        }
    }
}
```

- [ ] **Step 5: Run to confirm pass**

```
.\gradlew testDebugUnitTest --tests "com.gesturecontrol.gesture.mapping.GestureActionMapperTest"
```
Expected: PASS (9 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/gesture/mapping/ \
        app/src/test/java/com/gesturecontrol/gesture/mapping/GestureActionMapperTest.kt
git commit -m "feat: add SystemAction sealed class and GestureActionMapper"
```

---

### Task 16: GestureAccessibilityService — action dispatch

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/services/GestureAccessibilityService.kt`

- [ ] **Step 1: Implement full action dispatch**

Replace `GestureAccessibilityService.kt` with:

```kotlin
package com.gesturecontrol.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.media.AudioManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.gesturecontrol.gesture.GestureEventBus
import com.gesturecontrol.gesture.mapping.GestureActionMapper
import com.gesturecontrol.gesture.mapping.SystemAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GestureAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var audioManager: AudioManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(AudioManager::class.java)
        serviceScope.launch {
            GestureEventBus.events.collect { event ->
                executeAction(GestureActionMapper.map(event))
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun executeAction(action: SystemAction) {
        when (action) {
            SystemAction.Home          -> performGlobalAction(GLOBAL_ACTION_HOME)
            SystemAction.Back          -> performGlobalAction(GLOBAL_ACTION_BACK)
            SystemAction.Recents       -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            SystemAction.Notifications -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            SystemAction.QuickSettings -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            SystemAction.VolumeUp      -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            SystemAction.VolumeDown    -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            SystemAction.ScrollUp      -> dispatchScroll(up = true)
            SystemAction.ScrollDown    -> dispatchScroll(up = false)
        }
    }

    private fun dispatchScroll(up: Boolean) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()

        val startY = if (up) h * 0.7f else h * 0.3f
        val endY   = if (up) h * 0.3f else h * 0.7f

        val path = Path().apply {
            moveTo(w * 0.5f, startY)
            lineTo(w * 0.5f, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 300L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
```

- [ ] **Step 2: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: End-to-end smoke test (manual)**

  1. Install on device: `.\gradlew installDebug`
  2. Open app → tap "Enable Accessibility Service" → enable `Gesture Control` in system settings
  3. Temporarily add to `MainActivity.onCreate`: `startForegroundService(Intent(this, GestureRecognitionService::class.java))`
  4. Reinstall and open the app
  5. Show your hand to the camera, try: Victory (should open Recents), Swipe Left (should navigate Back), Thumb Up (should raise volume), Open Palm held (should go Home)
  6. Remove the temporary startForegroundService call before the next commit

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/services/GestureAccessibilityService.kt
git commit -m "feat: implement GestureAccessibilityService action dispatch — app end-to-end functional"
```

---

## Phase 6 — Quick Settings Tile

### Task 17: GestureControlTileService

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/services/GestureControlTileService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add tile label string**

```xml
<string name="tile_label">Gesture Control</string>
```

- [ ] **Step 2: Declare tile in `AndroidManifest.xml`**

Inside `<application>`:
```xml
<service
    android:name=".services.GestureControlTileService"
    android:exported="true"
    android:label="@string/tile_label"
    android:icon="@android:drawable/ic_menu_camera"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

- [ ] **Step 3: Implement `GestureControlTileService.kt`**

```kotlin
package com.gesturecontrol.services

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class GestureControlTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isServiceRunning()) stopRecognition() else startRecognition()
        updateTile()
    }

    private fun startRecognition() {
        startForegroundService(Intent(this, GestureRecognitionService::class.java))
    }

    private fun stopRecognition() {
        stopService(Intent(this, GestureRecognitionService::class.java))
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(android.app.ActivityManager::class.java)
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == GestureRecognitionService::class.java.name
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (isServiceRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
```

- [ ] **Step 4: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Manual test**

  1. Install on device
  2. Pull down notification shade → swipe down again to expand quick settings
  3. Find the "Gesture Control" tile (may need to edit tiles to add it)
  4. Tap tile → service starts → foreground notification appears → tile turns active (coloured)
  5. Tap tile again → service stops → notification disappears → tile turns inactive

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/services/GestureControlTileService.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat: add GestureControlTileService Quick Settings toggle"
```

---

## Phase 7 — Polish & Hardening

### Task 18: Camera permission handling

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/MainActivity.kt`

- [ ] **Step 1: Add runtime camera permission request to `MainActivity.kt`**

Add constant and launcher field:
```kotlin
private val cameraPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (!granted) {
        Toast.makeText(this, "Camera permission required for gesture control", Toast.LENGTH_LONG).show()
    }
}
```

In `onCreate`, after `setContentView`:
```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
}
```

Add imports:
```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
```

- [ ] **Step 2: Guard `GestureRecognitionService.startCamera` against missing permission**

At the start of `startCamera()`:
```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    stopSelf()
    return
}
```

- [ ] **Step 3: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/MainActivity.kt \
        app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt
git commit -m "feat: add camera permission handling in MainActivity and GestureRecognitionService"
```

---

### Task 19: Frame rate throttling when no hand is detected

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt`

**Rationale:** When MediaPipe finds no hand, we track consecutive empty frames and pause analysis after a threshold, resuming on the next non-empty result. This cuts idle CPU/battery use significantly.

- [ ] **Step 1: Add idle throttling to `GestureRecognitionService`**

Add fields:
```kotlin
private var emptyFrameCount = 0
private val IDLE_FRAME_THRESHOLD = 30   // ~2 s at 15 fps before throttling
private var isThrottled = false
```

Replace `analyzeFrame` with:
```kotlin
private fun analyzeFrame(imageProxy: ImageProxy) {
    if (isThrottled) {
        imageProxy.close()
        return
    }
    try {
        val (mpImage, rotation) = MPImageConverter.convert(imageProxy)
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotation)
            .build()
        gestureRecognizer.recognizeAsync(
            mpImage,
            imageOptions,
            imageProxy.imageInfo.timestamp / 1_000_000L
        )
    } finally {
        imageProxy.close()
    }
}
```

Update `onGestureResult` — at the top, before the `isEmpty` check:
```kotlin
if (result.gestures().isEmpty()) {
    emptyFrameCount++
    if (emptyFrameCount >= IDLE_FRAME_THRESHOLD) isThrottled = true
    staticDetector.reset()
    dynamicTracker.clear()
    return
}
// Hand detected — resume full processing
emptyFrameCount = 0
isThrottled = false
```

- [ ] **Step 2: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt
git commit -m "perf: throttle frame analysis after 2s with no hand detected"
```

---

### Task 20: Live status in MainActivity

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/MainActivity.kt`

- [ ] **Step 1: Broadcast service state changes from GestureRecognitionService**

Add to `GestureRecognitionService.kt`:
```kotlin
companion object {
    const val ACTION_STATE_CHANGED = "com.gesturecontrol.GESTURE_SERVICE_STATE"
    const val EXTRA_RUNNING = "running"
}
```

In `onStartCommand` after `startForeground(...)`:
```kotlin
sendBroadcast(Intent(ACTION_STATE_CHANGED).putExtra(EXTRA_RUNNING, true))
```

In `onDestroy` before `stopForeground(...)`:
```kotlin
sendBroadcast(Intent(ACTION_STATE_CHANGED).putExtra(EXTRA_RUNNING, false))
```

- [ ] **Step 2: Receive broadcasts in `MainActivity`**

Add field:
```kotlin
private val serviceStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val running = intent?.getBooleanExtra(GestureRecognitionService.EXTRA_RUNNING, false) ?: false
        binding.tvStatus.setText(
            if (running) R.string.status_active else R.string.status_inactive
        )
    }
}
```

In `onResume`:
```kotlin
registerReceiver(
    serviceStateReceiver,
    IntentFilter(GestureRecognitionService.ACTION_STATE_CHANGED),
    RECEIVER_NOT_EXPORTED
)
```

In `onPause`:
```kotlin
unregisterReceiver(serviceStateReceiver)
```

Add imports:
```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
```

- [ ] **Step 3: Build**

```
.\gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run all unit tests to confirm no regressions**

```
.\gradlew testDebugUnitTest
```
Expected: PASS (all tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gesturecontrol/MainActivity.kt \
        app/src/main/java/com/gesturecontrol/services/GestureRecognitionService.kt
git commit -m "feat: broadcast service state and show live status in MainActivity"
```

---

## All unit tests — final verification

- [ ] **Run full test suite**

```
.\gradlew testDebugUnitTest
```
Expected: All tests pass across:
- `GestureEventTest`
- `GestureEventBusTest`
- `StaticGestureDetectorTest` (9 tests)
- `DynamicGestureTrackerTest` (8 tests)
- `GestureDebouncerTest` (5 tests)
- `GestureActionMapperTest` (9 tests)
