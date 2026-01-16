package com.womensafetyapp

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Background service that detects shake gestures to trigger emergency alerts
 * Works even when the app is in background
 */
class ShakeDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var vibrator: Vibrator
    
    private var shakeThreshold = 15f
    private var lastUpdate: Long = 0
    private var last_x = 0f
    private var last_y = 0f
    private var last_z = 0f
    private var shakeCount = 0
    
    // Constants
    companion object {
        private const val SHAKE_TIMEOUT = 1000 // 1 second
        private const val SHAKE_COUNT_THRESHOLD = 3 // Number of shakes needed
    }

    override fun onCreate() {
        super.onCreate()
        
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        
        // Register sensor listener
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Return sticky to keep service running
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            
            if ((currentTime - lastUpdate) > 100) {
                val diffTime = currentTime - lastUpdate
                lastUpdate = currentTime
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 10000
                
                if (speed > shakeThreshold) {
                    shakeCount++
                    
                    // Vibrate on shake detection
                    if (shakeCount == 1) {
                        vibrateAlert()
                    }
                    
                    if (shakeCount >= SHAKE_COUNT_THRESHOLD) {
                        // Shake detected multiple times - trigger emergency
                        triggerEmergency()
                        shakeCount = 0
                    }
                    
                    // Reset shake count after timeout
                    android.os.Handler().postDelayed({
                        if (shakeCount > 0) {
                            shakeCount--
                        }
                    }, SHAKE_TIMEOUT.toLong())
                }
                
                last_x = x
                last_y = y
                last_z = z
            }
        }
    }

    private fun vibrateAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun triggerEmergency() {
        // Send broadcast to trigger emergency mode
        val intent = Intent(this, EmergencyButtonReceiver::class.java).apply {
            action = "com.womensafetyapp.SHAKE_TRIGGER"
        }
        sendBroadcast(intent)
        
        // Show notification that emergency was triggered
        showEmergencyNotification()
    }

    private fun showEmergencyNotification() {
        // Could show a notification that shake gesture detected and emergency activated
        // For now, just log it
        android.util.Log.d("ShakeService", "Emergency triggered by shake gesture")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used but required by interface
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}