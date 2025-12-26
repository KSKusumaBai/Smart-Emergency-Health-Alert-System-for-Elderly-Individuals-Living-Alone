package com.healthguard.eldercare.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.healthguard.eldercare.R
import com.healthguard.eldercare.ui.activities.DashboardActivity
import com.healthguard.eldercare.ui.activities.EmergencyContactsActivity

class FirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCMService"
        
        // Notification channels
        private const val HEALTH_CHANNEL_ID = "health_notifications"
        private const val EMERGENCY_CHANNEL_ID = "emergency_notifications"
        private const val GENERAL_CHANNEL_ID = "general_notifications"
        
        // Notification types
        private const val TYPE_HEALTH_ALERT = "health_alert"
        private const val TYPE_EMERGENCY_ALERT = "emergency_alert"
        private const val TYPE_CARETAKER_RESPONSE = "caretaker_response"
        private const val TYPE_GENERAL = "general"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Send token to server
        sendTokenToServer(token)
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        
        // Handle data payload
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
        
        // Handle notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Message notification: ${notification.title} - ${notification.body}")
            showNotification(
                title = notification.title ?: "",
                body = notification.body ?: "",
                type = remoteMessage.data["type"] ?: TYPE_GENERAL
            )
        }
    }
    
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: TYPE_GENERAL
        val title = data["title"] ?: ""
        val body = data["body"] ?: ""
        
        when (type) {
            TYPE_HEALTH_ALERT -> handleHealthAlert(data)
            TYPE_EMERGENCY_ALERT -> handleEmergencyAlert(data)
            TYPE_CARETAKER_RESPONSE -> handleCaretakerResponse(data)
            else -> showNotification(title, body, type)
        }
    }
    
    private fun handleHealthAlert(data: Map<String, String>) {
        val title = data["title"] ?: "Health Alert"
        val body = data["body"] ?: "Abnormal health reading detected"
        val severity = data["severity"] ?: "normal"
        
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_health_alert", true)
            putExtra("alert_data", data.toString())
        }
        
        showNotification(
            title = title,
            body = body,
            type = TYPE_HEALTH_ALERT,
            intent = intent,
            priority = if (severity == "critical") NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH
        )
    }
    
    private fun handleEmergencyAlert(data: Map<String, String>) {
        val title = data["title"] ?: "Emergency Alert"
        val body = data["body"] ?: "Emergency assistance required"
        val alertType = data["alert_type"] ?: "unknown"
        
        val intent = Intent(this, EmergencyContactsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("emergency_alert", true)
            putExtra("alert_type", alertType)
        }
        
        showNotification(
            title = title,
            body = body,
            type = TYPE_EMERGENCY_ALERT,
            intent = intent,
            priority = NotificationCompat.PRIORITY_MAX,
            ongoing = true
        )
    }
    
    private fun handleCaretakerResponse(data: Map<String, String>) {
        val title = data["title"] ?: "Caretaker Response"
        val body = data["body"] ?: "Your caretaker has responded"
        val responseType = data["response_type"] ?: "acknowledged"
        
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("caretaker_response", true)
            putExtra("response_type", responseType)
        }
        
        showNotification(
            title = title,
            body = body,
            type = TYPE_CARETAKER_RESPONSE,
            intent = intent
        )
    }
    
    private fun showNotification(
        title: String,
        body: String,
        type: String,
        intent: Intent? = null,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        ongoing: Boolean = false
    ) {
        val channelId = when (type) {
            TYPE_HEALTH_ALERT -> HEALTH_CHANNEL_ID
            TYPE_EMERGENCY_ALERT -> EMERGENCY_CHANNEL_ID
            else -> GENERAL_CHANNEL_ID
        }
        
        val defaultIntent = intent ?: Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            defaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_health_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
        
        // Add action buttons for emergency alerts
        if (type == TYPE_EMERGENCY_ALERT) {
            val cancelIntent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("cancel_emergency", true)
            }
            val cancelPendingIntent = PendingIntent.getActivity(
                this,
                1001,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val respondIntent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("respond_emergency", true)
            }
            val respondPendingIntent = PendingIntent.getActivity(
                this,
                1002,
                respondIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            notificationBuilder
                .addAction(R.drawable.ic_cancel, "Cancel", cancelPendingIntent)
                .addAction(R.drawable.ic_check, "I'm OK", respondPendingIntent)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Health notifications channel
            val healthChannel = NotificationChannel(
                HEALTH_CHANNEL_ID,
                "Health Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for health alerts and abnormal readings"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Emergency notifications channel
            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "Emergency Notifications",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Critical emergency alerts and SOS notifications"
                enableVibration(true)
                setShowBadge(true)
                setBypassDnd(true)
            }
            
            // General notifications channel
            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications and updates"
                enableVibration(false)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannels(
                listOf(healthChannel, emergencyChannel, generalChannel)
            )
        }
    }
    
    private fun sendTokenToServer(token: String) {
        // TODO: Send the token to your app server
        // This would typically involve making an API call to register the token
        // with the user's account for targeted notifications
        
        Log.d(TAG, "Token sent to server: $token")
        
        // Store token locally for now
        val sharedPrefs = getSharedPreferences("firebase_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("fcm_token", token)
            .apply()
    }
}