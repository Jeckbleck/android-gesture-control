package com.gesturecontrol.camera

import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

object MPImageConverter {

    fun fromImageProxy(imageProxy: ImageProxy): MPImage {
        val bitmap = imageProxy.toBitmap()
        return BitmapImageBuilder(bitmap).build()
    }
}
