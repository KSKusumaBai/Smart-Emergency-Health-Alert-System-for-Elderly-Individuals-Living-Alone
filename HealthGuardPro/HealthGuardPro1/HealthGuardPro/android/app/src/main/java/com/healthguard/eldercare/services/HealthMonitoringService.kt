package com.healthguard.eldercare.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.healthguard.eldercare.R
import com.healthguard.eldercare.managers.BluetoothManager
import com.healthguard.eldercare.ml.FallDetectionAnalyzer
import com.healthguard.eldercare.ml.HealthAnalyzer
import com.healthguard.eldercare.models.*
import com.healthguard.eldercare.repositories.EmergencyRepository
import com.healthguard.eldercare.repositories.HealthDataRepository
import com.healthguard.eldercare.services.LocationService
import com.healthguard.eldercare.ui.activities.DashboardActivity
import kotlinx.coroutines.*

class HealthMonitoringService : Service() {
    
    companion object {
        private const val TAG = "HealthMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "health_monitoring_channel"
        
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_EMERGENCY_RESPONSE = "EMERGENCY_RESPONSE"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Components
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var healthAnalyzer: HealthAnalyzer
    private lateinit var fallDetectionAnalyzer: FallDetectionAnalyzer
    private lateinit var healthDataRepository: HealthDataRepository
    private lateinit var emergencyRepository: EmergencyRepository
    private lateinit var locationService: LocationService
    
    // State
    private var isMonitoring = false
    private var currentHealthData: HealthData? = null
    private var currentLocation: Location? = null
    
    // Live data for UI updates
    private val _monitoringState = MutableLiveData<Boolean>()
    val monitoringState: LiveData<Boolean> = _monitoringState
    
    private val _currentHealth = MutableLiveData<HealthData>()
    val currentHealth: LiveData<HealthData> = _currentHealth
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Health Monitoring Service created")
        
        initializeComponents()
        createNotificationChannel()
    }
    
    private fun initializeComponents() {
        bluetoothManager = BluetoothManager(this)
        healthAnalyzer = HealthAnalyzer(this)
        healthDataRepository = HealthDataRepository(this)
        emergencyRepository = EmergencyRepository(this)
        locationService = LocationService(this)
        
        fallDetectionAnalyzer = FallDetectionAnalyzer(this) { accelerometerData ->
            handleFallDetection(accelerometerData)
        }
        
        // Observe Bluetooth health data
        bluetoothManager.healthData.observeForever { healthData ->
            handleNewHealthData(healthData)
        }
        
        // Observe location updates
        locationService.currentLocation.observeForever { location ->
            currentLocation = location
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startHealthMonitoring()
            ACTION_STOP_MONITORING -> stopHealthMonitoring()
            ACTION_EMERGENCY_RESPONSE -> handleEmergencyResponse(intent)
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startHealthMonitoring() {
        if (isMonitoring) return
        
        Log.d(TAG, "Starting health monitoring")
        isMonitoring = true
        _monitoringState.value = true
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createMonitoringNotification())
        
        // Bind Bluetooth service
        bluetoothManager.bindService()
        
        // Start fall detection
        fallDetectionAnalyzer.startMonitoring()
        
        // Start location tracking
        locationService.startLocationUpdates()
        
        // Start periodic health analysis
        startPeriodicAnalysis()
    }
    
    private fun stopHealthMonitoring() {
        if (!isMonitoring) return
        
        Log.d(TAG, "Stopping health monitoring")
        isMonitoring = false
        _monitoringState.value = false
        
        // Stop components
        bluetoothManager.unbindService()
        fallDetectionAnalyzer.stopMonitoring()
        locationService.stopLocationUpdates()
        
        // Stop periodic analysis
        serviceScope.coroutineContext.cancelChildren()
        
        // Stop foreground service
        stopForeground(true)
        stopSelf()
    }
    
    private fun handleNewHealthData(healthData: HealthData) {
        currentHealthData = healthData
        _currentHealth.value = healthData
        
        serviceScope.launch {
            try {
                // Analyze health data
                val analysisResult = healthAnalyzer.analyzeHealthData(healthData)
                val analyzedHealthData = healthData.copy(
                    analysisResult = analysisResult,
                    location = currentLocation,
                    isAbnormal = analysisResult.status != "normal"
                )
                
                // Store health data
                healthDataRepository.storeHealthData(analyzedHealthData)
                
                // Check for emergency conditions
                if (analysisResult.status == "critical") {
                    handleHealthEmergency(analyzedHealthData)
                }
                
                // Update notification
                updateMonitoringNotification(analyzedHealthData)
                
                Log.d(TAG, "Health data processed: ${analysisResult.status}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing health data", e)
            }
        }
    }
    
    private fun handleFallDetection(accelerometerData: AccelerometerData) {
        Log.w(TAG, "Fall detected!")
        
        serviceScope.launch {
            try {
                val emergencyAlert = EmergencyAlert(
                    type = "fall_detected",
                    healthData = currentHealthData,
                    location = currentLocation,
                    accelerometerData = accelerometerData,
                    triggeredAt = System.currentTimeMillis()
                )
                
                triggerEmergencyAlert(emergencyAlert)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling fall detection", e)
            }
        }
    }
    
    private fun handleHealthEmergency(healthData: HealthData) {
        Log.w(TAG, "Critical health condition detected!")
        
        serviceScope.launch {
            try {
                val emergencyAlert = EmergencyAlert(
                    type = "health_emergency",
                    healthData = healthData,
                    location = currentLocation,
                    triggeredAt = System.currentTimeMillis()
                )
                
                triggerEmergencyAlert(emergencyAlert)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling health emergency", e)
            }
        }
    }
    
    private suspend fun triggerEmergencyAlert(alert: EmergencyAlert) {
        // Store emergency alert
        val alertId = emergencyRepository.triggerEmergencyAlert(alert)
        
        if (alertId != null) {
            // Show emergency notification
            showEmergencyNotification(alert)
            
            // Start emergency contact protocol
            startEmergencyContactProtocol(alertId)
            
            Log.d(TAG, "Emergency alert triggered: $alertId")
        }
    }
    
    private suspend fun startEmergencyContactProtocol(alertId: String) {
        val emergencyContacts = emergencyRepository.getEmergencyContacts()
        
        if (emergencyContacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured")
            return
        }
        
        // Wait for user response (60 seconds)
        delay(60000)
        
        // Check if user has responded
        val activeAlerts = emergencyRepository.getActiveEmergencyAlerts()
        val alert = activeAlerts.find { it.id == alertId }
        
        if (alert != null && !alert.responseReceived) {
            // Start contacting emergency contacts
            contactEmergencyContacts(emergencyContacts, alertId)
        }
    }
    
    private suspend fun contactEmergencyContacts(contacts: List<EmergencyContact>, alertId: String) {
        for (contact in contacts.sortedBy { it.priority }) {
            try {
                Log.d(TAG, "Contacting: ${contact.name} at ${contact.phoneNumber}")
                
                // Mark contact as contacted
                emergencyRepository.markContactAsContacted(contact.id)
                
                // Send SMS and make call (implementation depends on your SMS/call service)
                sendEmergencySMS(contact, alertId)
                
                // Wait for response (1 minute per contact)
                delay(60000)
                
                // Check if response received
                val updatedAlerts = emergencyRepository.getActiveEmergencyAlerts()
                val currentAlert = updatedAlerts.find { it.id == alertId }
                
                if (currentAlert?.responseReceived == true) {
                    Log.d(TAG, "Emergency response received, stopping contact protocol")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error contacting ${contact.name}", e)
            }
        }
    }
    
    private suspend fun sendEmergencySMS(contact: EmergencyContact, alertId: String) {
        // TODO: Implement SMS sending using Twilio or similar service
        // For now, just log the action
        Log.d(TAG, "Sending emergency SMS to ${contact.phoneNumber}")
    }
    
    private fun handleEmergencyResponse(intent: Intent) {
        val alertId = intent.getStringExtra("alert_id")
        val responseType = intent.getStringExtra("response_type")
        
        if (alertId != null && responseType != null) {
            serviceScope.launch {
                try {
                    val activeAlerts = emergencyRepository.getActiveEmergencyAlerts()
                    val alert = activeAlerts.find { it.id == alertId }
                    
                    if (alert != null) {
                        val updatedAlert = alert.copy(
                            responseReceived = true,
                            responseTime = System.currentTimeMillis() - alert.triggeredAt,
                            status = if (responseType == "false_alarm") "cancelled" else "resolved"
                        )
                        
                        emergencyRepository.updateEmergencyAlert(updatedAlert)
                        Log.d(TAG, "Emergency response handled: $responseType")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling emergency response", e)
                }
            }
        }
    }
    
    private fun startPeriodicAnalysis() {
        serviceScope.launch {
            while (isMonitoring) {
                try {
                    // Sync local data to Firebase
                    healthDataRepository.syncLocalDataToFirebase()
                    
                    // Analyze trends and patterns
                    analyzeHealthTrends()
                    
                    delay(300000) // 5 minutes
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic analysis", e)
                    delay(60000) // Retry in 1 minute
                }
            }
        }
    }
    
    private suspend fun analyzeHealthTrends() {
        try {
            val recentData = healthDataRepository.getHealthDataHistory(
                startTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000), // Last 24 hours
                limit = 100
            )
            
            if (recentData.size > 10) {
                // Analyze trends and generate insights
                Log.d(TAG, "Analyzed ${recentData.size} health records")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing health trends", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Health Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous health monitoring notifications"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createMonitoringNotification(): Notification {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HealthGuard Monitoring")
            .setContentText("Monitoring your health continuously")
            .setSmallIcon(R.drawable.ic_health_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
    
    private fun updateMonitoringNotification(healthData: HealthData) {
        val status = healthData.analysisResult?.status ?: "unknown"
        val statusText = when (status) {
            "normal" -> "All readings normal"
            "abnormal" -> "Abnormal readings detected"
            "critical" -> "Critical condition - seeking help"
            else -> "Monitoring active"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HealthGuard Monitoring")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_health_logo)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showEmergencyNotification(alert: EmergencyAlert) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("emergency_alert", true)
            putExtra("alert_id", alert.id)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "emergency_notifications")
            .setContentTitle("Emergency Alert")
            .setContentText("${alert.type.replace("_", " ").capitalize()} detected - Tap to respond")
            .setSmallIcon(R.drawable.ic_emergency)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(R.drawable.ic_check, "I'm OK", pendingIntent)
            .addAction(R.drawable.ic_cancel, "False Alarm", pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2001, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopHealthMonitoring()
        healthAnalyzer.cleanup()
        serviceScope.cancel()
        Log.d(TAG, "Health Monitoring Service destroyed")
    }
}