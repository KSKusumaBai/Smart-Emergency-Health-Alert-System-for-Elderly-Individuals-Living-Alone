package com.healthguard.eldercare.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.healthguard.eldercare.managers.BluetoothManager
import com.healthguard.eldercare.managers.LocationManager
import com.healthguard.eldercare.models.EmergencyAlert
import com.healthguard.eldercare.models.HealthData
import com.healthguard.eldercare.repositories.EmergencyRepository
import com.healthguard.eldercare.repositories.HealthDataRepository
import com.healthguard.eldercare.services.BluetoothService
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    
    private val healthDataRepository = HealthDataRepository(application)
    private val emergencyRepository = EmergencyRepository(application)
    private val bluetoothManager = BluetoothManager(application)
    private val locationManager = LocationManager(application)
    private val auth = FirebaseAuth.getInstance()
    
    // Live data for UI
    private val _currentHealthData = MutableLiveData<HealthData?>()
    val currentHealthData: LiveData<HealthData?> = _currentHealthData
    
    private val _healthDataHistory = MutableLiveData<List<HealthData>>()
    val healthDataHistory: LiveData<List<HealthData>> = _healthDataHistory
    
    private val _bluetoothConnectionStatus = MutableLiveData<Boolean>()
    val bluetoothConnectionStatus: LiveData<Boolean> = _bluetoothConnectionStatus
    
    private val _activeEmergencyAlerts = MutableLiveData<List<EmergencyAlert>>()
    val activeEmergencyAlerts: LiveData<List<EmergencyAlert>> = _activeEmergencyAlerts
    
    private val _locationInfo = MutableLiveData<LocationManager.EmergencyLocationInfo?>()
    val locationInfo: LiveData<LocationManager.EmergencyLocationInfo?> = _locationInfo
    
    private val _isMonitoring = MutableLiveData<Boolean>()
    val isMonitoring: LiveData<Boolean> = _isMonitoring
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    init {
        loadDashboardData()
        observeDataSources()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                // Load recent health data
                refreshHealthData()
                
                // Load active emergency alerts
                loadActiveEmergencyAlerts()
                
                // Get location info
                loadLocationInfo()
                
                // Check monitoring status
                _isMonitoring.value = true // This would check the actual monitoring service
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load dashboard data: ${e.message}"
            }
        }
    }
    
    private fun observeDataSources() {
        // Observe health data repository
        healthDataRepository.latestHealthData.observeForever { healthData ->
            _currentHealthData.value = healthData
        }
        
        // Observe Bluetooth connection
        bluetoothManager.connectionState.observeForever { connectionState ->
            _bluetoothConnectionStatus.value = connectionState == BluetoothService.ConnectionState.CONNECTED
        }
        
        // Observe emergency alerts
        emergencyRepository.activeEmergencyAlerts.observeForever { alerts ->
            _activeEmergencyAlerts.value = alerts
        }
        
        // Observe location data
        locationManager.currentLocation.observeForever { _ ->
            loadLocationInfo()
        }
    }
    
    fun refreshHealthData() {
        viewModelScope.launch {
            try {
                val history = healthDataRepository.getHealthDataHistory(limit = 10)
                _healthDataHistory.value = history
                
                if (history.isNotEmpty()) {
                    _currentHealthData.value = history.first()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh health data: ${e.message}"
            }
        }
    }
    
    fun refreshDashboard() {
        loadDashboardData()
    }
    
    private suspend fun loadActiveEmergencyAlerts() {
        try {
            val alerts = emergencyRepository.getActiveEmergencyAlerts()
            _activeEmergencyAlerts.value = alerts
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load emergency alerts: ${e.message}"
        }
    }
    
    private fun loadLocationInfo() {
        viewModelScope.launch {
            try {
                val info = locationManager.getEmergencyLocationInfo()
                _locationInfo.value = info
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load location info: ${e.message}"
            }
        }
    }
    
    fun triggerEmergencyAlert() {
        viewModelScope.launch {
            try {
                locationManager.enableEmergencyMode()
                
                val currentLocation = locationManager.getCurrentLocationForEmergency()
                val currentHealthData = _currentHealthData.value
                
                val emergencyAlert = EmergencyAlert(
                    id = "manual_${System.currentTimeMillis()}",
                    type = "manual_sos",
                    healthData = currentHealthData,
                    location = currentLocation,
                    triggeredAt = System.currentTimeMillis(),
                    status = "active"
                )
                
                val alertId = emergencyRepository.triggerEmergencyAlert(emergencyAlert)
                if (alertId != null) {
                    loadActiveEmergencyAlerts()
                } else {
                    _errorMessage.value = "Failed to trigger emergency alert"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error triggering emergency alert: ${e.message}"
            }
        }
    }
    
    fun cancelEmergencyAlert() {
        viewModelScope.launch {
            try {
                val activeAlerts = _activeEmergencyAlerts.value ?: return@launch
                
                activeAlerts.forEach { alert ->
                    val cancelledAlert = alert.copy(
                        status = "cancelled",
                        resolvedAt = System.currentTimeMillis(),
                        responseReceived = true
                    )
                    emergencyRepository.updateEmergencyAlert(cancelledAlert)
                }
                
                locationManager.disableEmergencyMode()
                loadActiveEmergencyAlerts()
                
            } catch (e: Exception) {
                _errorMessage.value = "Error cancelling emergency alert: ${e.message}"
            }
        }
    }
    
    fun respondToEmergency(alertId: String?, responseType: String) {
        if (alertId == null) return
        
        viewModelScope.launch {
            try {
                val activeAlerts = _activeEmergencyAlerts.value ?: return@launch
                val alert = activeAlerts.find { it.id == alertId }
                
                if (alert != null) {
                    val status = when (responseType) {
                        "false_alarm" -> "cancelled"
                        "user_ok" -> "resolved"
                        "need_help" -> "escalated"
                        else -> "resolved"
                    }
                    
                    val updatedAlert = alert.copy(
                        status = status,
                        resolvedAt = System.currentTimeMillis(),
                        responseReceived = true,
                        responseTime = System.currentTimeMillis() - alert.triggeredAt
                    )
                    
                    emergencyRepository.updateEmergencyAlert(updatedAlert)
                    
                    if (responseType != "need_help") {
                        locationManager.disableEmergencyMode()
                    }
                    
                    loadActiveEmergencyAlerts()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error responding to emergency: ${e.message}"
            }
        }
    }
    
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    fun getDashboardSummary(): DashboardSummary {
        val currentHealth = _currentHealthData.value
        val activeAlerts = _activeEmergencyAlerts.value?.size ?: 0
        val isConnected = _bluetoothConnectionStatus.value ?: false
        val isMonitoringActive = _isMonitoring.value ?: false
        val locationInfo = _locationInfo.value
        
        return DashboardSummary(
            currentHealthStatus = currentHealth?.analysisResult?.status ?: "unknown",
            lastHealthUpdate = currentHealth?.timestamp ?: 0L,
            activeEmergencyAlerts = activeAlerts,
            isDeviceConnected = isConnected,
            isMonitoringActive = isMonitoringActive,
            isInSafeZone = locationInfo?.isInSafeZone ?: false,
            currentLocation = locationInfo?.currentLocation?.address ?: "Unknown"
        )
    }
    
    fun getHealthTrend(days: Int = 7): HealthTrend {
        val history = _healthDataHistory.value ?: return HealthTrend()
        
        if (history.size < 2) {
            return HealthTrend()
        }
        
        val recent = history.take(days * 24) // Assuming hourly data
        val heartRates = recent.mapNotNull { if (it.heartRate > 0) it.heartRate else null }
        val systolicBP = recent.mapNotNull { if (it.bloodPressureSystolic > 0) it.bloodPressureSystolic else null }
        val temperatures = recent.mapNotNull { if (it.temperature > 0) it.temperature else null }
        
        return HealthTrend(
            averageHeartRate = heartRates.average().toFloat(),
            averageSystolicBP = systolicBP.average().toFloat(),
            averageTemperature = temperatures.average().toFloat(),
            abnormalReadingsCount = recent.count { it.isAbnormal },
            totalReadingsCount = recent.size
        )
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up observers if needed
    }
    
    data class DashboardSummary(
        val currentHealthStatus: String,
        val lastHealthUpdate: Long,
        val activeEmergencyAlerts: Int,
        val isDeviceConnected: Boolean,
        val isMonitoringActive: Boolean,
        val isInSafeZone: Boolean,
        val currentLocation: String
    )
    
    data class HealthTrend(
        val averageHeartRate: Float = 0f,
        val averageSystolicBP: Float = 0f,
        val averageTemperature: Float = 0f,
        val abnormalReadingsCount: Int = 0,
        val totalReadingsCount: Int = 0
    )
}