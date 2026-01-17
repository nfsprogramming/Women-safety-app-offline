package com.womensafetyapp

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class to capture photos silently during emergency
 */
class EvidenceCaptureHelper(private val context: Context) {
    
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private val handler = Handler(Looper.getMainLooper())
    
    fun captureEvidence() {
        try {
            // Capture from back camera first
            captureSilentPhoto(android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
            
            // Capture from front camera after a delay
            handler.postDelayed({
                captureSilentPhoto(android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT)
            }, 3000)
            
        } catch (e: Exception) {
            Log.e("EvidenceCapture", "Failed to capture evidence", e)
        }
    }
    
    private fun captureSilentPhoto(facing: Int) {
        try {
            val cameraId = getCameraId(facing) ?: return
            
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    takePhoto(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, handler)
            
        } catch (e: Exception) {
            Log.e("EvidenceCapture", "Error in silent capture", e)
        }
    }

    private fun takePhoto(camera: CameraDevice) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(camera.id)
            val map = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSize = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.firstOrNull() ?: android.util.Size(640, 480)

            val imageReader = ImageReader.newInstance(outputSize.width, outputSize.height, android.graphics.ImageFormat.JPEG, 1)
            
            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                saveImage(bytes, camera.id)
                image.close()
                camera.close()
            }, handler)

            val callback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        session.capture(captureBuilder.build(), null, handler)
                    } catch (e: Exception) {
                        Log.e("EvidenceCapture", "Capture failed", e)
                        camera.close()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    camera.close()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val outputConfig = android.hardware.camera2.params.OutputConfiguration(imageReader.surface)
                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    listOf(outputConfig),
                    { command -> handler.post(command) },
                    callback
                )
                camera.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                camera.createCaptureSession(listOf(imageReader.surface), callback, handler)
            }

        } catch (e: Exception) {
            Log.e("EvidenceCapture", "Photo take failed", e)
            camera.close()
        }
    }

    private fun saveImage(bytes: ByteArray, cameraId: String) {
        try {
            val evidenceDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "EmergencyEvidence")
            if (!evidenceDir.exists()) evidenceDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cameraType = if (cameraId == getCameraId(android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT)) "front" else "back"
            val file = File(evidenceDir, "evidence_${cameraType}_$timestamp.jpg")

            FileOutputStream(file).use { it.write(bytes) }
            Log.d("EvidenceCapture", "Saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("EvidenceCapture", "Save failed", e)
        }
    }
    
    private fun getCameraId(facing: Int): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                cameraCharacteristicsGet(characteristics, android.hardware.camera2.CameraCharacteristics.LENS_FACING) == facing
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun <T> cameraCharacteristicsGet(characteristics: android.hardware.camera2.CameraCharacteristics, key: android.hardware.camera2.CameraCharacteristics.Key<T>): T? {
        return characteristics.get(key)
    }
}
