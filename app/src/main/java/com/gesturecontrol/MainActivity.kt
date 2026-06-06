package com.gesturecontrol

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gesturecontrol.camera.CameraPreferenceRepository
import com.gesturecontrol.camera.LensFacing
import com.gesturecontrol.databinding.ActivityMainBinding
import com.gesturecontrol.services.GestureAccessibilityService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraPrefs: CameraPreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraPrefs = CameraPreferenceRepository(applicationContext)

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        lifecycleScope.launch {
            val current = cameraPrefs.lensFacing.first()
            binding.rgCamera.check(
                if (current == LensFacing.FRONT) R.id.rbFront else R.id.rbBack
            )
        }

        binding.rgCamera.setOnCheckedChangeListener { _, checkedId ->
            val facing = if (checkedId == R.id.rbFront) LensFacing.FRONT else LensFacing.BACK
            lifecycleScope.launch { cameraPrefs.setLens(facing) }
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
