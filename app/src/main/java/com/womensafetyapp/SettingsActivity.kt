package com.womensafetyapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var etPoliceNumber: EditText
    private lateinit var etWomenHelpline: EditText
    private lateinit var etSmsInterval: EditText
    private lateinit var btnSaveSettings: Button
    private lateinit var btnResetDefaults: Button
    
    private lateinit var sharedPreferences: android.content.SharedPreferences

    companion object {
        const val SHARED_PREFS_NAME = "WomenSafetyPrefs"
        const val KEY_POLICE_NUMBER = "police_number"
        const val KEY_WOMEN_HELPLINE = "women_helpline"
        const val KEY_SMS_INTERVAL = "sms_interval"
        const val DEFAULT_POLICE = "112"
        const val DEFAULT_WOMEN_HELPLINE = "181"
        const val DEFAULT_SMS_INTERVAL = "1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        
        initViews()
        loadSettings()
        setupClickListeners()
    }

    private fun initViews() {
        etPoliceNumber = findViewById(R.id.etPoliceNumber)
        etWomenHelpline = findViewById(R.id.etWomenHelpline)
        etSmsInterval = findViewById(R.id.etSmsInterval)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)
    }

    private fun loadSettings() {
        etPoliceNumber.setText(sharedPreferences.getString(KEY_POLICE_NUMBER, DEFAULT_POLICE))
        etWomenHelpline.setText(sharedPreferences.getString(KEY_WOMEN_HELPLINE, DEFAULT_WOMEN_HELPLINE))
        etSmsInterval.setText(sharedPreferences.getString(KEY_SMS_INTERVAL, DEFAULT_SMS_INTERVAL))
    }

    private fun setupClickListeners() {
        btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        btnResetDefaults.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun saveSettings() {
        val policeNumber = etPoliceNumber.text.toString().trim()
        val womenHelpline = etWomenHelpline.text.toString().trim()
        val smsInterval = etSmsInterval.text.toString().trim()

        if (policeNumber.isEmpty() || womenHelpline.isEmpty() || smsInterval.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        sharedPreferences.edit().apply {
            putString(KEY_POLICE_NUMBER, policeNumber)
            putString(KEY_WOMEN_HELPLINE, womenHelpline)
            putString(KEY_SMS_INTERVAL, smsInterval)
            apply()
        }

        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resetToDefaults() {
        etPoliceNumber.setText(DEFAULT_POLICE)
        etWomenHelpline.setText(DEFAULT_WOMEN_HELPLINE)
        etSmsInterval.setText(DEFAULT_SMS_INTERVAL)
        
        saveSettings()
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}