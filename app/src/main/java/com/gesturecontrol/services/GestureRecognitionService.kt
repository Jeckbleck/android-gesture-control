package com.gesturecontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gesturecontrol.R
import com.gesturecontrol.camera.CameraPreferenceRepository
import com.gesturecontrol.camera.LensFacing
import com.gesturecontrol.camera.MPImageConverter
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class GestureRecognitionService : LifecycleService() {

    companion object {
        private const val TAG = "GestureRecognitionSvc"
        private const val CHANNEL_ID = "gesture_control_channel"
        private const val NOTIFICATION_ID = 1
        private const val MODEL_ASSET = "gesture_recognizer.task"
    }

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private lateinit var cameraPrefs: CameraPreferenceRepository
    private var gestureRecognizer: GestureRecognizer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification())
        cameraPrefs = CameraPreferenceRepository(applicationContext)
        initMediaPipe()
        lifecycleScope.launch { startCamera() }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        gestureRecognizer?.close()
        analysisExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun initMediaPipe() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()

        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> onGestureResult(result) }
            .setErrorListener { error -> Log.e(TAG, "MediaPipe error", error) }
            .build()

        gestureRecognizer = GestureRecognizer.createFromOptions(this, options)
        Log.d(TAG, "MediaPipe GestureRecognizer initialized")
    }

    private fun onGestureResult(result: GestureRecognizerResult) {
        val gestures = result.gestures()
        if (gestures.isNotEmpty() && gestures[0].isNotEmpty()) {
            val top = gestures[0][0]
            Log.d(TAG, "Gesture: ${top.categoryName()} score=${top.score()}")
        }
    }

    private suspend fun startCamera() {
        val lensFacing = cameraPrefs.lensFacing.first()
        val selector = if (lensFacing == LensFacing.FRONT)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        onFrameReceived(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, imageAnalysis)
                Log.d(TAG, "Camera bound: $lensFacing")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFrameReceived(imageProxy: ImageProxy) {
        val recognizer = gestureRecognizer
        if (recognizer == null) {
            imageProxy.close()
            return
        }

        try {
            val mpImage = MPImageConverter.fromImageProxy(imageProxy)
            recognizer.recognizeAsync(mpImage, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
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
