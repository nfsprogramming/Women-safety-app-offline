package com.womensafetyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast receiver to handle emergency triggers from various sources
 * - Panic button press
 * - Shake gesture detection
 * - Widget clicks
 * - Hardware button shortcuts (if configured)
 */
class EmergencyButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.womensafetyapp.EMERGENCY_TRIGGER" -> {
                // Main panic button activation
                activateEmergencyMode(context)
            }
            "com.womensafetyapp.SHAKE_TRIGGER" -> {
                // Shake gesture detected
                handleShakeTrigger(context)
            }
        }
    }

    private fun activateEmergencyMode(context: Context) {
        // Start MainActivity and trigger emergency mode
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ACTIVATE_EMERGENCY", true)
        }
        context.startActivity(intent)
    }

    private fun handleShakeTrigger(context: Context) {
        // Show confirmation dialog before activating emergency mode
        // This prevents false positives from shake detection
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CONFIRM_SHAKE_EMERGENCY", true)
        }
        context.startActivity(intent)
    }
}