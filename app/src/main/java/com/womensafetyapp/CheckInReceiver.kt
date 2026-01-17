package com.womensafetyapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles periodic "I'm Safe" check-ins
 */
class CheckInReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CHECK_IN_REMINDER -> {
                // Send reminder notification
                sendCheckInReminder(context)
            }
            ACTION_MISSED_CHECK_IN -> {
                // Alert contacts that check-in was missed
                sendMissedCheckInAlert(context)
            }
            ACTION_CONFIRM_SAFE -> {
                Log.d("CheckInReceiver", "Confirm safe action received")
                val notificationHelper = NotificationHelper(context)
                notificationHelper.dismissNotification()
                
                // User confirmed they're safe
                sendSafeConfirmation(context)
                
                // Cancel any pending missed check-in alerts and schedule next
                cancelCheckIns(context)
                scheduleNextCheckIn(context)
            }
        }
    }
    
    private fun sendCheckInReminder(context: Context) {
        val notificationHelper = NotificationHelper(context)
        notificationHelper.showCheckInReminder()
    }
    
    private fun sendMissedCheckInAlert(context: Context) {
        val prefs = context.getSharedPreferences("WomenSafetyPrefs", Context.MODE_PRIVATE)
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
            
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val message = "⚠️ MISSED CHECK-IN ALERT\n\nYour contact has not checked in as scheduled at $timestamp. Please verify their safety."
            
            contacts.forEach { contact ->
                try {
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                } catch (e: Exception) {
                    Log.e("CheckInReceiver", "Failed to send alert to ${contact.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CheckInReceiver", "Error sending missed check-in alert", e)
        }
    }
    
    private fun sendSafeConfirmation(context: Context) {
        val prefs = context.getSharedPreferences("WomenSafetyPrefs", Context.MODE_PRIVATE)
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
            
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val message = "✓ I'm Safe Check-in\n\nYour contact has checked in safely at $timestamp."
            
            contacts.forEach { contact ->
                try {
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                } catch (e: Exception) {
                    Log.e("CheckInReceiver", "Failed to send confirmation to ${contact.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CheckInReceiver", "Error sending safe confirmation", e)
        }
    }
    
    companion object {
        const val ACTION_CHECK_IN_REMINDER = "com.womensafetyapp.CHECK_IN_REMINDER"
        const val ACTION_MISSED_CHECK_IN = "com.womensafetyapp.MISSED_CHECK_IN"
        const val ACTION_CONFIRM_SAFE = "com.womensafetyapp.CONFIRM_SAFE"
        
        fun scheduleNextCheckIn(context: Context) {
            val prefs = context.getSharedPreferences("WomenSafetyPrefs", Context.MODE_PRIVATE)
            val intervalMinutes = prefs.getInt("checkin_interval", 60) // Default 1 hour
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Schedule reminder
            val reminderIntent = Intent(context, CheckInReceiver::class.java).apply {
                action = ACTION_CHECK_IN_REMINDER
            }
            val reminderPendingIntent = PendingIntent.getBroadcast(
                context, 0, reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val reminderTime = System.currentTimeMillis() + (intervalMinutes.toLong() * 60 * 1000)
            
            val missedIntent = Intent(context, CheckInReceiver::class.java).apply {
                action = ACTION_MISSED_CHECK_IN
            }
            val missedPendingIntent = PendingIntent.getBroadcast(
                context, 1, missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val missedTime = reminderTime + (5 * 60 * 1000) // 5 minutes grace period
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, reminderPendingIntent)
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, missedTime, missedPendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, reminderPendingIntent)
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, missedTime, missedPendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, reminderPendingIntent)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, missedTime, missedPendingIntent)
            }
            Log.d("CheckInReceiver", "Next check-in scheduled in $intervalMinutes minutes")
        }
        
        fun cancelCheckIns(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val reminderIntent = Intent(context, CheckInReceiver::class.java)
            val reminderPendingIntent = PendingIntent.getBroadcast(
                context, 0, reminderIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            val missedIntent = Intent(context, CheckInReceiver::class.java)
            val missedPendingIntent = PendingIntent.getBroadcast(
                context, 1, missedIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            reminderPendingIntent?.let { alarmManager.cancel(it) }
            missedPendingIntent?.let { alarmManager.cancel(it) }
        }
    }
}

class NotificationHelper(private val context: Context) {
    private val channelId = "safety_check_in"
    private val notificationId = 1001

    fun showCheckInReminder() {
        createNotificationChannel()

        val confirmIntent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_CONFIRM_SAFE
        }
        val confirmPendingIntent = android.app.PendingIntent.getBroadcast(
            context, 2, confirmIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Safety Check-in Reminder")
            .setContentText("Please confirm that you are safe.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "I'm Safe", confirmPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(notificationId, notification)
    }

    fun dismissNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(notificationId)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Safety Check-in"
            val descriptionText = "Reminders for periodic safety check-ins"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
