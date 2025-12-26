package com.healthguard.eldercare.services

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.util.Log
import com.healthguard.eldercare.R
import com.healthguard.eldercare.firebase.FirebaseManager
import com.healthguard.eldercare.models.EmergencyAlert
import com.healthguard.eldercare.models.EmergencyContact
import com.healthguard.eldercare.repositories.EmergencyRepository
import kotlinx.coroutines.*

class EmergencyAlertService : Service() {
    
    companion object {
        private const val TAG = "EmergencyAlertService"
        
        // Actions
        const val ACTION_TRIGGER_SOS = "TRIGGER_SOS"
        const val ACTION_TRIGGER_HEALTH_EMERGENCY = "TRIGGER_HEALTH_EMERGENCY"
        const val ACTION_TRIGGER_FALL_DETECTED = "TRIGGER_FALL_DETECTED"
        const val ACTION_CANCEL_EMERGENCY = "CANCEL_EMERGENCY"
        const val ACTION_USER_RESPONSE = "USER_RESPONSE"
        
        // Response timeout
        private const val USER_RESPONSE_TIMEOUT = 60000L // 1 minute
        private const val CONTACT_RESPONSE_TIMEOUT = 60000L // 1 minute per contact
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var emergencyRepository: EmergencyRepository
    private lateinit var firebaseManager: FirebaseManager
    
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var activeEmergencyJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        
        emergencyRepository = EmergencyRepository(this)
        firebaseManager = FirebaseManager()
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        
        Log.d(TAG, "Emergency Alert Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_SOS -> {
                val alertData = intent.getStringExtra("alert_data")
                triggerManualSOS(alertData)
            }
            ACTION_TRIGGER_HEALTH_EMERGENCY -> {
                val healthData = intent.getStringExtra("health_data")
                val location = intent.getStringExtra("location")
                triggerHealthEmergency(healthData, location)
            }
            ACTION_TRIGGER_FALL_DETECTED -> {
                val accelerometerData = intent.getStringExtra("accelerometer_data")
                val location = intent.getStringExtra("location")
                triggerFallDetectedEmergency(accelerometerData, location)
            }
            ACTION_CANCEL_EMERGENCY -> {
                val alertId = intent.getStringExtra("alert_id")
                cancelEmergency(alertId)
            }
            ACTION_USER_RESPONSE -> {
                val alertId = intent.getStringExtra("alert_id")
                val responseType = intent.getStringExtra("response_type")
                handleUserResponse(alertId, responseType)
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun triggerManualSOS(alertData: String?) {
        Log.w(TAG, "Manual SOS triggered")
        
        val emergencyAlert = EmergencyAlert(
            id = generateAlertId(),
            type = "manual_sos",
            triggeredAt = System.currentTimeMillis(),
            status = "active"
        )
        
        startEmergencyProtocol(emergencyAlert)
    }
    
    private fun triggerHealthEmergency(healthData: String?, location: String?) {
        Log.w(TAG, "Health emergency triggered")
        
        val emergencyAlert = EmergencyAlert(
            id = generateAlertId(),
            type = "health_emergency",
            triggeredAt = System.currentTimeMillis(),
            status = "active"
        )
        
        startEmergencyProtocol(emergencyAlert)
    }
    
    private fun triggerFallDetectedEmergency(accelerometerData: String?, location: String?) {
        Log.w(TAG, "Fall detected emergency triggered")
        
        val emergencyAlert = EmergencyAlert(
            id = generateAlertId(),
            type = "fall_detected",
            triggeredAt = System.currentTimeMillis(),
            status = "active"
        )
        
        startEmergencyProtocol(emergencyAlert)
    }
    
    private fun startEmergencyProtocol(alert: EmergencyAlert) {
        // Cancel any existing emergency protocol
        activeEmergencyJob?.cancel()
        
        activeEmergencyJob = serviceScope.launch {
            try {
                // Step 1: Store the emergency alert
                emergencyRepository.triggerEmergencyAlert(alert)
                
                // Step 2: Alert the user and wait for response
                alertUser(alert)
                
                // Step 3: Wait for user response
                val userResponded = waitForUserResponse(alert.id)
                
                if (!userResponded) {
                    // Step 4: Start contacting emergency contacts
                    contactEmergencyContacts(alert)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in emergency protocol", e)
            }
        }
    }
    
    private fun alertUser(alert: EmergencyAlert) {
        // Play alarm sound
        playAlarmSound()
        
        // Vibrate device
        startVibration()
        
        // Show emergency notification
        showEmergencyNotification(alert)
        
        Log.d(TAG, "User alerted for emergency: ${alert.id}")
    }
    
    private suspend fun waitForUserResponse(alertId: String): Boolean {
        Log.d(TAG, "Waiting for user response for alert: $alertId")
        
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < USER_RESPONSE_TIMEOUT) {
            val activeAlerts = emergencyRepository.getActiveEmergencyAlerts()
            val alert = activeAlerts.find { it.id == alertId }
            
            if (alert == null || alert.responseReceived || alert.status != "active") {
                Log.d(TAG, "User response received for alert: $alertId")
                stopAlarmAndVibration()
                return true
            }
            
            delay(1000) // Check every second
        }
        
        Log.d(TAG, "User response timeout for alert: $alertId")
        stopAlarmAndVibration()
        return false
    }
    
    private suspend fun contactEmergencyContacts(alert: EmergencyAlert) {
        Log.d(TAG, "Starting emergency contact protocol for alert: ${alert.id}")
        
        val emergencyContacts = emergencyRepository.getEmergencyContacts()
        
        if (emergencyContacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured")
            return
        }
        
        val sortedContacts = emergencyContacts.sortedBy { it.priority }
        
        for ((index, contact) in sortedContacts.withIndex()) {
            Log.d(TAG, "Contacting emergency contact ${index + 1}: ${contact.name}")
            
            // Send SMS and make call
            val contacted = contactPerson(contact, alert)
            
            if (contacted) {
                // Mark contact as contacted
                emergencyRepository.markContactAsContacted(contact.id)
                
                // Wait for response
                val responseReceived = waitForContactResponse(alert.id, contact)
                
                if (responseReceived) {
                    Log.d(TAG, "Response received from contact: ${contact.name}")
                    break
                }
                
                Log.d(TAG, "No response from contact: ${contact.name}, trying next contact")
            }
        }
        
        // If no response from any contact, consider alerting authorities
        val finalCheck = emergencyRepository.getActiveEmergencyAlerts()
        val finalAlert = finalCheck.find { it.id == alert.id }
        
        if (finalAlert != null && !finalAlert.responseReceived) {
            Log.w(TAG, "No response from any emergency contact. Consider alerting authorities.")
            // In a real implementation, this would contact emergency services
        }
    }
    
    private suspend fun contactPerson(contact: EmergencyContact, alert: EmergencyAlert): Boolean {
        return try {
            // Send SMS
            sendEmergencySMS(contact, alert)
            
            // Make phone call (if permission granted)
            makeEmergencyCall(contact, alert)
            
            Log.d(TAG, "Successfully contacted: ${contact.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to contact: ${contact.name}", e)
            false
        }
    }
    
    private fun sendEmergencySMS(contact: EmergencyContact, alert: EmergencyAlert) {
        try {
            val message = buildEmergencyMessage(contact, alert)
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)
            }
            
            Log.d(TAG, "Emergency SMS sent to: ${contact.phoneNumber}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to: ${contact.phoneNumber}", e)
        }
    }
    
    private fun makeEmergencyCall(contact: EmergencyContact, alert: EmergencyAlert) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:${contact.phoneNumber}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            startActivity(callIntent)
            Log.d(TAG, "Emergency call initiated to: ${contact.phoneNumber}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call to: ${contact.phoneNumber}", e)
        }
    }
    
    private fun buildEmergencyMessage(contact: EmergencyContact, alert: EmergencyAlert): String {
        val user = firebaseManager.getCurrentUser()
        val userName = user?.displayName ?: "HealthGuard User"
        
        val alertType = when (alert.type) {
            "manual_sos" -> "Manual SOS Alert"
            "health_emergency" -> "Health Emergency"
            "fall_detected" -> "Fall Detected"
            else -> "Emergency Alert"
        }
        
        return """
            🚨 EMERGENCY ALERT 🚨
            
            $alertType from $userName
            
            Time: ${formatTimestamp(alert.triggeredAt)}
            
            This is an automated message from HealthGuard app. Please check on $userName immediately.
            
            If this is a false alarm, the user can cancel this alert in the app.
            
            Location: ${alert.location?.address ?: "Location not available"}
            
            Response required urgently.
        """.trimIndent()
    }
    
    private suspend fun waitForContactResponse(alertId: String, contact: EmergencyContact): Boolean {
        Log.d(TAG, "Waiting for response from contact: ${contact.name}")
        
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < CONTACT_RESPONSE_TIMEOUT) {
            val activeAlerts = emergencyRepository.getActiveEmergencyAlerts()
            val alert = activeAlerts.find { it.id == alertId }
            
            if (alert == null || alert.responseReceived || alert.status != "active") {
                return true
            }
            
            delay(5000) // Check every 5 seconds
        }
        
        return false
    }
    
    private fun cancelEmergency(alertId: String?) {
        if (alertId == null) return
        
        Log.d(TAG, "Cancelling emergency: $alertId")
        
        serviceScope.launch {
            try {
                val activeAlerts = emergencyRepository.getActiveEmergencyAlerts()
                val alert = activeAlerts.find { it.id == alertId }
                
                if (alert != null) {
                    val cancelledAlert = alert.copy(
                        status = "cancelled",
                        resolvedAt = System.currentTimeMillis(),
                        responseReceived = true
                    )
                    
                    emergencyRepository.updateEmergencyAlert(cancelledAlert)
                }
                
                // Cancel active emergency job
                activeEmergencyJob?.cancel()
                
                // Stop alarm and vibration
                stopAlarmAndVibration()
                
                Log.d(TAG, "Emergency cancelled: $alertId")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling emergency", e)
            }
        }
    }
    
    private fun handleUserResponse(alertId: String?, responseType: String?) {
        if (alertId == null || responseType == null) return
        
        Log.d(TAG, "User response received: $responseType for alert: $alertId")
        
        serviceScope.launch {
            try {
                val activeAlerts = emergencyRepository.getActiveEmergencyAlerts()
                val alert = activeAlerts.find { it.id == alertId }
                
                if (alert != null) {
                    val respondedAlert = alert.copy(
                        status = if (responseType == "false_alarm") "cancelled" else "resolved",
                        resolvedAt = System.currentTimeMillis(),
                        responseReceived = true,
                        responseTime = System.currentTimeMillis() - alert.triggeredAt
                    )
                    
                    emergencyRepository.updateEmergencyAlert(respondedAlert)
                }
                
                // Cancel active emergency job
                activeEmergencyJob?.cancel()
                
                // Stop alarm and vibration
                stopAlarmAndVibration()
                
                Log.d(TAG, "User response processed: $responseType")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling user response", e)
            }
        }
    }
    
    private fun playAlarmSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.emergency_alarm)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
            
            Log.d(TAG, "Emergency alarm sound started")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound", e)
        }
    }
    
    private fun startVibration() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), 0)
            }
            
            Log.d(TAG, "Emergency vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }
    
    private fun stopAlarmAndVibration() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            vibrator?.cancel()
            
            Log.d(TAG, "Alarm and vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm and vibration", e)
        }
    }
    
    private fun showEmergencyNotification(alert: EmergencyAlert) {
        // This would typically create and show a high-priority notification
        // Implementation depends on your notification manager
        Log.d(TAG, "Emergency notification shown for alert: ${alert.id}")
    }
    
    private fun generateAlertId(): String {
        return "alert_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        activeEmergencyJob?.cancel()
        serviceScope.cancel()
        stopAlarmAndVibration()
        
        Log.d(TAG, "Emergency Alert Service destroyed")
    }
}