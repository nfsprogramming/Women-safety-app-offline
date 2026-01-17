package com.womensafetyapp

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Background service that detects shake gestures to trigger emergency alerts
 * Works even when the app is in background
 */
class ShakeDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var vibrator: Vibrator
    
    private var shakeThreshold = 800f
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
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        
        // Register sensor listener
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "ShakeServiceChannel",
                "Shake Detection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "ShakeServiceChannel")
            .setContentTitle("Woman Safety App Active")
            .setContentText("Shake detection is running in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use valid vector icon
            .setContentIntent(pendingIntent)
            .build()
            
        try {
            if (Build.VERSION.SDK_INT >= 34) {
               startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
               startForeground(1, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
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
                    
                    // Vibrate only on full trigger, not every shake
                    // Pre-warning vibration removed to fix "constant vibration" issue
                    
                    if (shakeCount >= SHAKE_COUNT_THRESHOLD) {
                        // Shake detected multiple times - trigger emergency
                        triggerEmergency()
                        shakeCount = 0
                    }
                    
                    // Reset shake count after timeout
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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