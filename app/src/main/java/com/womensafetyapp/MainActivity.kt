package com.womensafetyapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrator: Vibrator
    private lateinit var audioManager: AudioManager
    private lateinit var locationManager: LocationManager
    
    private var isAlarmPlaying = false
    private var isFlashlightOn = false
    private var currentLocation: Location? = null
    private var emergencyTimer: CountDownTimer? = null
    private var silentMode = false
    private lateinit var evidenceHelper: EvidenceCaptureHelper
    
    // UI Views
    private lateinit var btnPanic: Button
    private lateinit var btnAlarm: Button
    private lateinit var btnFlashlight: ImageView
    private lateinit var btnCallPolice: Button
    private lateinit var btnSendLocation: Button
    private lateinit var btnCheckIn: Button
    private lateinit var switchSilentMode: androidx.appcompat.widget.SwitchCompat
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvLocationStatus: TextView
    private lateinit var routeTracker: SafeRouteTracker
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
        private const val SHARED_PREFS_NAME = "WomenSafetyPrefs"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
        private const val KEY_POLICE_NUMBER = "police_number"
        private const val KEY_WOMEN_HELPLINE = "women_helpline"
        private const val DEFAULT_POLICE = "112"
        private const val DEFAULT_WOMEN_HELPLINE = "181"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OSMDroid configuration for Map
        org.osmdroid.config.Configuration.getInstance().load(
            applicationContext, 
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        setContentView(R.layout.activity_main)
        
        initializeComponents()
        checkPermissions()
        setupLocationUpdates()
    }
    
    private fun initializeComponents() {
        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        evidenceHelper = EvidenceCaptureHelper(this)
        
        // Load silent mode preference
        silentMode = sharedPreferences.getBoolean("silent_mode", false)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Find views
        btnPanic = findViewById(R.id.btnPanic)
        btnAlarm = findViewById(R.id.btnAlarm)
        btnFlashlight = findViewById(R.id.btnFlashlight)
        btnCallPolice = findViewById(R.id.btnCallPolice)
        btnSendLocation = findViewById(R.id.btnSendLocation)
        btnCheckIn = findViewById(R.id.btnCheckIn)
        switchSilentMode = findViewById(R.id.switchSilentMode)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        
        // Initialize route tracker
        routeTracker = SafeRouteTracker(this)
        
        // Set silent mode switch state
        switchSilentMode.isChecked = silentMode
        
        setupClickListeners()
                // Start Shake Detection Service
        try {
            val shakeServiceIntent = Intent(this, ShakeDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(shakeServiceIntent)
            } else {
                startService(shakeServiceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupClickListeners() {
        btnPanic.setOnClickListener {
            handlePanicButton()
        }
        
        btnAlarm.setOnClickListener {
            toggleAlarm()
        }
        
        btnFlashlight.setOnClickListener {
            toggleFlashlight()
        }
        
        btnCallPolice.setOnClickListener {
            callEmergencyNumber()
        }
        
        btnSendLocation.setOnClickListener {
            sendLocationToContacts()
        }
        
        btnCheckIn.setOnClickListener {
            sendCheckInConfirmation()
        }
        
        switchSilentMode.setOnCheckedChangeListener { _, isChecked ->
            silentMode = isChecked
            sharedPreferences.edit().putBoolean("silent_mode", isChecked).apply()
            Toast.makeText(this, 
                if (isChecked) "Silent mode enabled" else "Silent mode disabled", 
                Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.VIBRATE,
            Manifest.permission.CAMERA
        ).toMutableList()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGrantedPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun setupLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Request updates from both GPS and Network for better reliability
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000, 10f, locationListener
                    )
                }
                
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000, 10f, locationListener
                    )
                }
                
                // Try to get last known location from either provider
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                
                // Use the better/newer location
                currentLocation = if (lastGps != null && lastNetwork != null) {
                    if (lastGps.time > lastNetwork.time) lastGps else lastNetwork
                } else {
                    lastGps ?: lastNetwork
                }
                
                updateLocationStatus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            updateLocationStatus()
            
            // Update route tracker if tracking
            if (routeTracker.isCurrentlyTracking()) {
                routeTracker.checkLocation(location)
            }
        }
        
        override fun onProviderEnabled(provider: String) {
            updateLocationStatus()
        }
        
        override fun onProviderDisabled(provider: String) {
            tvLocationStatus.text = "Location: GPS Disabled"
        }
    }
    
    private fun updateLocationStatus() {
        currentLocation?.let { location ->
            tvLocationStatus.text = String.format("Location: %.4f, %.4f", 
                location.latitude, location.longitude)
        } ?: run {
            tvLocationStatus.text = "Location: Acquiring..."
        }
    }
    
    private fun checkEmergencyContacts() {
        val contacts = getEmergencyContacts()
        if (contacts.isEmpty()) {
            showSetupDialog()
        }
    }
    
    private fun showSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Setup Emergency Contacts")
            .setMessage("Please add at least one emergency contact to use the safety features.")
            .setPositiveButton("Setup Now") { _, _ ->
                startActivity(Intent(this, EmergencyContactsActivity::class.java))
            }
            .setNegativeButton("Later", null)
            .setCancelable(true)
            .show()
    }
    
    private fun handlePanicButton() {
        val locations = getEmergencyContacts()
        if (locations.isEmpty()) {
            Toast.makeText(this, "Please add emergency contacts first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
            return
        }
        
        showEmergencyAlertDialog()
    }
    
    private fun showEmergencyAlertDialog() {
        AlertDialog.Builder(this)
            .setTitle("EMERGENCY ALERT")
            .setMessage("This will send SOS messages to all your emergency contacts with your current location. Continue?")
            .setPositiveButton("SEND ALERT") { _, _ ->
                activateEmergencyMode()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun activateEmergencyMode() {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Sending emergency alerts..."
        
        // Step 1: Capture evidence photos
        try {
            evidenceHelper.captureEvidence()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Step 2: Send SMS to all contacts
        sendEmergencySMS()
        
        // Step 3: Play alarm (only if not in silent mode)
        if (!silentMode) {
            playPanicAlarm()
            turnOnFlashlight()
        } else {
            Toast.makeText(this, "Silent alert sent", Toast.LENGTH_SHORT).show()
        }
        
        // Step 4: Start repeating alerts
        startRepeatingAlerts()
        
        progressBar.visibility = View.GONE
        tvStatus.text = if (silentMode) "Silent alert sent!" else "Emergency alerts sent!"
        
        showEmergencyActiveDialog()
    }
    
    private fun sendEmergencySMS() {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val contacts = getEmergencyContacts()
        val locationText = getLocationText()
        
        val message = StringBuilder()
            .append("🚨 EMERGENCY ALERT 🚨\n")
            .append("I need immediate help!\n")
            .append(locationText)
            .append("\n\nTime: ").append(getCurrentTime())
            .toString()
        
        contacts.forEach { contact ->
            try {
                // Send location via SMS
                smsManager.sendTextMessage(contact.phone, null, message, null, null)
                
                // Send location coordinates for maps
                currentLocation?.let { location ->
                    val mapsLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    smsManager.sendTextMessage(contact.phone, null, mapsLink, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to send SMS to ${contact.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sendLocationToContacts() {
        val contacts = getEmergencyContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "Please add emergency contacts first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
            return
        }
        
        Toast.makeText(this, "Preparing to send location to ${contacts.size} contacts...", Toast.LENGTH_SHORT).show()
        
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Sending location..."
        
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        
        val locationText = if (currentLocation != null) {
            getLocationText()
        } else {
            "⚠️ HELP NEEDED!\nLocation not available yet. Please check my last known location or call me immediately!"
        }
        
        var successCount = 0
        var failCount = 0
        
        contacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.phone, null, locationText, null, null)
                successCount++
            } catch (e: Exception) {
                e.printStackTrace()
                failCount++
            }
        }
        
        progressBar.visibility = View.GONE
        
        if (successCount > 0) {
            tvStatus.text = "Location sent to $successCount contact(s)"
            Toast.makeText(this, "✓ Sent to $successCount contact(s)" + 
                if (failCount > 0) " ($failCount failed)" else "", Toast.LENGTH_LONG).show()
        } else {
            tvStatus.text = "Failed to send messages"
            Toast.makeText(this, "❌ Failed to send SMS. Check permissions!", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun getLocationText(): String {
        return currentLocation?.let { location ->
            String.format("My Location:\nLat: %.6f\nLong: %.6f\nMaps: https://maps.google.com/?q=%.6f,%.6f",
                location.latitude, location.longitude, location.latitude, location.longitude)
        } ?: "Location not available"
    }
    
    private fun playPanicAlarm() {
        try {
            // Set flag FIRST before starting thread
            isAlarmPlaying = true
            btnAlarm.text = "🔇 Stop Alarm"
            
            // Set maximum volume for alarm stream
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            
            // Use device's default alarm ringtone for maximum loudness
            val alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            
            // Start alarm in background thread
            Thread {
                try {
                    val ringtone = android.media.RingtoneManager.getRingtone(applicationContext, alarmUri)
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    
                    while (isAlarmPlaying) {
                        if (!ringtone.isPlaying) {
                            ringtone.play()
                        }
                        Thread.sleep(100) // Check every 100ms
                    }
                    
                    ringtone.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
            
            // Vibrate phone
            startContinuousVibration()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Alarm failed to start", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopPanicAlarm() {
        try {
            isAlarmPlaying = false // This stops the alarm thread loop
            vibrator.cancel()
            btnAlarm.text = "🚨 Activate Alarm"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun toggleAlarm() {
        if (isAlarmPlaying) {
            stopPanicAlarm()
        } else {
            playPanicAlarm()
        }
    }
    
    private fun turnOnFlashlight() {
        if (isFlashlightOn) return // Already on
        
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            
            cameraManager.setTorchMode(cameraId, true)
            isFlashlightOn = true
            btnFlashlight.setImageResource(R.drawable.ic_flashlight_on)
            
            // Flashlight stays on until stopped automatically or by user
            // No timeout handler
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Flashlight not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun turnOffFlashlight() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            
            cameraManager.setTorchMode(cameraId, false)
            isFlashlightOn = false
            btnFlashlight.setImageResource(R.drawable.ic_flashlight_off)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun toggleFlashlight() {
        if (isFlashlightOn) {
            turnOffFlashlight()
        } else {
            turnOnFlashlight()
        }
    }
    
    private fun startContinuousVibration() {
        // SOS Pattern? Or just aggressive pulsing
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200) // Vibrate 500ms, pause 200ms
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }
    
    private fun callEmergencyNumber() {
        val policeNumber = sharedPreferences.getString(KEY_POLICE_NUMBER, DEFAULT_POLICE) ?: DEFAULT_POLICE
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = android.net.Uri.parse("tel:$policeNumber")
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Call permission not granted", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startRepeatingAlerts() {
        emergencyTimer?.cancel()
        
        val intervalStr = sharedPreferences.getString("sms_interval", "1") ?: "1"
        val intervalMinutes = intervalStr.toLongOrNull() ?: 1L
        val intervalMillis = intervalMinutes * 60 * 1000
        
        emergencyTimer = object : CountDownTimer(600000, intervalMillis) { // 10 minutes total
            override fun onTick(millisUntilFinished: Long) {
                sendEmergencySMS()
            }
            
            override fun onFinish() {
                // Alerts stopped after 10 minutes
            }
        }.start()
    }
    
    private fun sendCheckInConfirmation() {
        val contacts = getEmergencyContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "Please add emergency contacts first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val locationText = if (currentLocation != null) {
            "\nLocation: https://maps.google.com/?q=${currentLocation!!.latitude},${currentLocation!!.longitude}"
        } else {
            ""
        }
        
        val message = "✓ I'm Safe Check-in\n\nYour contact has checked in safely at $timestamp.$locationText"
        
        var sent = 0
        contacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.phone, null, message, null, null)
                sent++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (sent > 0) {
            Toast.makeText(this, "✓ Check-in sent to $sent contact(s)", Toast.LENGTH_LONG).show()
            tvStatus.text = "Check-in sent successfully"
            
            // Schedule next check-in
            CheckInReceiver.scheduleNextCheckIn(this)
        } else {
            Toast.makeText(this, "Failed to send check-in", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showEmergencyActiveDialog() {
        AlertDialog.Builder(this)
            .setTitle("EMERGENCY MODE ACTIVE")
            .setMessage("Emergency alerts have been sent. The app will continue sending location updates every minute for 10 minutes.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Stop Alerts") { _, _ ->
                emergencyTimer?.cancel()
                tvStatus.text = "Emergency alerts stopped"
            }
            .show()
    }
    
    private fun getEmergencyContacts(): List<EmergencyContact> {
        val contactsJson = sharedPreferences.getString(KEY_EMERGENCY_CONTACTS, "[]")
        return try {
            val contacts = mutableListOf<EmergencyContact>()
            val jsonArray = org.json.JSONArray(contactsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                contacts.add(EmergencyContact(
                    name = jsonObj.getString("name"),
                    phone = jsonObj.getString("phone")
                ))
            }
            contacts
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_contacts -> {
                startActivity(Intent(this, EmergencyContactsActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_map -> {
                startActivity(Intent(this, MapActivity::class.java))
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupLocationUpdates()
            } else {
                // Permission denied. 
                // We use a less intrusive message or check if rationale is needed to avoid loops.
                // Toast.makeText(this, "Permissions are required for safety features", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        emergencyTimer?.cancel()
        vibrator.cancel()
    }
}

// Data class for emergency contact
data class EmergencyContact(
    val name: String,
    val phone: String
)