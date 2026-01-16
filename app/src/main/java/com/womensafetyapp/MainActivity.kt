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
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrator: Vibrator
    private lateinit var audioManager: AudioManager
    private lateinit var locationManager: LocationManager
    
    private var isAlarmPlaying = false
    private var currentLocation: Location? = null
    private var emergencyTimer: CountDownTimer? = null
    
    // UI Views
    private lateinit var btnPanic: Button
    private lateinit var btnAlarm: Button
    private lateinit var btnFlashlight: ImageView
    private lateinit var btnCallPolice: Button
    private lateinit var btnSendLocation: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvLocationStatus: TextView
    
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
        setContentView(R.layout.activity_main)
        
        initializeComponents()
        checkPermissions()
        setupLocationUpdates()
        checkEmergencyContacts()
    }
    
    private fun initializeComponents() {
        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Find views
        btnPanic = findViewById(R.id.btnPanic)
        btnAlarm = findViewById(R.id.btnAlarm)
        btnFlashlight = findViewById(R.id.btnFlashlight)
        btnCallPolice = findViewById(R.id.btnCallPolice)
        btnSendLocation = findViewById(R.id.btnSendLocation)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        
        setupClickListeners()
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
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.VIBRATE,
            Manifest.permission.CAMERA
        )
        
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
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000, // 5 seconds
                10f, // 10 meters
                locationListener
            )
            
            // Get last known location
            currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            updateLocationStatus()
        }
    }
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            updateLocationStatus()
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
            .setCancelable(false)
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
        
        // Step 1: Send SMS to all contacts
        sendEmergencySMS()
        
        // Step 2: Play alarm
        playPanicAlarm()
        
        // Step 3: Turn on flashlight
        turnOnFlashlight()
        
        // Step 4: Start repeating alerts
        startRepeatingAlerts()
        
        progressBar.visibility = View.GONE
        tvStatus.text = "Emergency alerts sent!"
        
        showEmergencyActiveDialog()
    }
    
    private fun sendEmergencySMS() {
        val smsManager = SmsManager.getDefault()
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
            Toast.makeText(this, "Please add emergency contacts first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentLocation == null) {
            Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Sending location..."
        
        val smsManager = SmsManager.getDefault()
        val locationText = getLocationText()
        
        contacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.phone, null, locationText, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        progressBar.visibility = View.GONE
        tvStatus.text = "Location sent to contacts"
        Toast.makeText(this, "Location sent to ${contacts.size} contacts", Toast.LENGTH_SHORT).show()
    }
    
    private fun getLocationText(): String {
        return currentLocation?.let { location ->
            String.format("My Location:\nLat: %.6f\nLong: %.6f\nMaps: https://maps.google.com/?q=%.6f,%.6f",
                location.latitude, location.longitude, location.latitude, location.longitude)
        } ?: "Location not available"
    }
    
    private fun playPanicAlarm() {
        try {
            val alarmTone = resources.openRawResourceFd(R.raw.emergency_alarm)
            val mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(alarmTone.fileDescriptor, alarmTone.startOffset, alarmTone.length)
                isLooping = true
                prepare()
            }
            
            // Set maximum volume
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            
            mediaPlayer.start()
            
            // Vibrate phone
            vibrateFor30Seconds()
            
            // Stop after 30 seconds
            android.os.Handler().postDelayed({
                try {
                    mediaPlayer.stop()
                    mediaPlayer.release()
                    isAlarmPlaying = false
                    btnAlarm.text = "🚨 Activate Alarm"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 30000)
            
            isAlarmPlaying = true
            btnAlarm.text = "🔇 Stop Alarm"
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to default alarm
            val tone = android.media.ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 30000)
        }
    }
    
    private fun toggleAlarm() {
        if (isAlarmPlaying) {
            // Stop the alarm (this is a simplified version)
            isAlarmPlaying = false
            btnAlarm.text = "🚨 Activate Alarm"
            vibrator.cancel()
        } else {
            playPanicAlarm()
        }
    }
    
    private fun turnOnFlashlight() {
        try {
            // Flashlight implementation will be handled by camera manager
            // This is a simplified version
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            
            cameraManager.setTorchMode(cameraId, true)
            
            // Turn off after 30 seconds
            android.os.Handler().postDelayed({
                try {
                    cameraManager.setTorchMode(cameraId, false)
                    btnFlashlight.setImageResource(R.drawable.ic_flashlight_off)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 30000)
            
            btnFlashlight.setImageResource(R.drawable.ic_flashlight_on)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Flashlight not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleFlashlight() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            
            // Toggle logic would go here
            turnOnFlashlight()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Flashlight not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun vibrateFor30Seconds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 100, 200, 100, 200),
                    10 // Repeat 10 times
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30000)
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
        
        emergencyTimer = object : CountDownTimer(600000, 60000) { // 10 minutes, every 1 minute
            override fun onTick(millisUntilFinished: Long) {
                sendEmergencySMS()
            }
            
            override fun onFinish() {
                // Alerts stopped after 10 minutes
            }
        }.start()
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
                Toast.makeText(this, "Some features may not work without permissions", Toast.LENGTH_LONG).show()
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