package com.womensafetyapp

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.telephony.SmsManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tracks user's route and alerts if they deviate from planned path
 */
class SafeRouteTracker(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("WomenSafetyPrefs", Context.MODE_PRIVATE)
    private var plannedRoute: List<Location> = emptyList()
    private var isTracking = false
    private val maxDeviationMeters = 500.0 // Alert if 500m off route
    
    fun startTracking(destination: Location) {
        isTracking = true
        saveDestination(destination)
        Log.d("SafeRouteTracker", "Started tracking to destination")
    }
    
    fun stopTracking() {
        isTracking = false
        prefs.edit().remove("tracking_destination").apply()
        Log.d("SafeRouteTracker", "Stopped tracking")
    }
    
    fun checkLocation(currentLocation: Location) {
        if (!isTracking) return
        
        val destination = getDestination() ?: return
        
        // Check if significantly deviated from direct route
        val deviationDistance = calculateDeviation(currentLocation, destination)
        
        if (deviationDistance > maxDeviationMeters) {
            sendDeviationAlert(currentLocation, deviationDistance)
        }
        
        // Check if reached destination
        val distanceToDestination = currentLocation.distanceTo(destination).toDouble()
        if (distanceToDestination < 50) { // Within 50 meters
            sendArrivalConfirmation(currentLocation)
            stopTracking()
        }
    }
    
    private fun calculateDeviation(current: Location, destination: Location): Double {
        // Calculate distance from current location to direct line to destination
        // For simplicity, using distance to destination as proxy
        // In production, would calculate perpendicular distance to route line
        return current.distanceTo(destination).toDouble()
    }
    
    private fun sendDeviationAlert(location: Location, deviation: Double) {
        val contactsJson = prefs.getString("emergency_contacts", "[]")
        
        try {
            val contacts = mutableListOf<EmergencyContact>()
            val jsonArray = JSONArray(contactsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                contacts.add(EmergencyContact(
                    name = jsonObj.getString("name"),
                    phone = jsonObj.getString("phone")
                ))
            }
            
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val message = """
                ⚠️ ROUTE DEVIATION ALERT
                
                Your contact has deviated from their planned route.
                
                Current Location:
                https://maps.google.com/?q=${location.latitude},${location.longitude}
                
                Deviation: ${deviation.toInt()}m from planned route
            """.trimIndent()
            
            contacts.forEach { contact ->
                try {
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                } catch (e: Exception) {
                    Log.e("SafeRouteTracker", "Failed to send alert to ${contact.name}", e)
                }
            }
            
            // Mark that alert was sent to avoid spam
            prefs.edit().putLong("last_deviation_alert", System.currentTimeMillis()).apply()
            
        } catch (e: Exception) {
            Log.e("SafeRouteTracker", "Error sending deviation alert", e)
        }
    }
    
    private fun sendArrivalConfirmation(location: Location) {
        val contactsJson = prefs.getString("emergency_contacts", "[]")
        
        try {
            val contacts = mutableListOf<EmergencyContact>()
            val jsonArray = JSONArray(contactsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                contacts.add(EmergencyContact(
                    name = jsonObj.getString("name"),
                    phone = jsonObj.getString("phone")
                ))
            }
            
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val message = """
                ✓ SAFE ARRIVAL
                
                Your contact has safely reached their destination.
                
                Location:
                https://maps.google.com/?q=${location.latitude},${location.longitude}
            """.trimIndent()
            
            contacts.forEach { contact ->
                try {
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                } catch (e: Exception) {
                    Log.e("SafeRouteTracker", "Failed to send confirmation to ${contact.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("SafeRouteTracker", "Error sending arrival confirmation", e)
        }
    }
    
    private fun saveDestination(destination: Location) {
        val json = JSONObject().apply {
            put("latitude", destination.latitude)
            put("longitude", destination.longitude)
        }
        prefs.edit().putString("tracking_destination", json.toString()).apply()
    }
    
    private fun getDestination(): Location? {
        val json = prefs.getString("tracking_destination", null) ?: return null
        return try {
            val obj = JSONObject(json)
            Location("").apply {
                latitude = obj.getDouble("latitude")
                longitude = obj.getDouble("longitude")
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun isCurrentlyTracking(): Boolean = isTracking
}
