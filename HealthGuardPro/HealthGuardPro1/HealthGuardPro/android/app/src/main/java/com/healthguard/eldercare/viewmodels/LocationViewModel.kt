package com.healthguard.eldercare.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.healthguard.eldercare.managers.LocationManager
import com.healthguard.eldercare.models.Location
import com.healthguard.eldercare.services.LocationService
import kotlinx.coroutines.launch

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    
    private val locationManager = LocationManager(application)
    
    // Expose location manager's live data
    val currentLocation: LiveData<Location?> = locationManager.currentLocation
    val homeLocation: LiveData<Location?> = locationManager.homeLocation
    val safeZones: LiveData<List<LocationService.SafeZone>> = locationManager.safeZones
    val isLocationSharingEnabled: LiveData<Boolean> = locationManager.isLocationSharingEnabled
    val geofenceAlerts: LiveData<List<LocationManager.GeofenceAlert>> = locationManager.geofenceAlerts
    
    private val _locationPattern = MutableLiveData<LocationManager.LocationPattern>()
    val locationPattern: LiveData<LocationManager.LocationPattern> = _locationPattern
    
    private val _walkingDistance = MutableLiveData<Float>()
    val walkingDistance: LiveData<Float> = _walkingDistance
    
    private val _emergencyLocationInfo = MutableLiveData<LocationManager.EmergencyLocationInfo>()
    val emergencyLocationInfo: LiveData<LocationManager.EmergencyLocationInfo> = _emergencyLocationInfo
    
    init {
        // Calculate daily patterns
        updateLocationPattern()
        updateWalkingDistance()
    }
    
    fun startLocationTracking() {
        locationManager.startLocationTracking()
    }
    
    fun stopLocationTracking() {
        locationManager.stopLocationTracking()
    }
    
    fun enableEmergencyMode() {
        locationManager.enableEmergencyMode()
    }
    
    fun disableEmergencyMode() {
        locationManager.disableEmergencyMode()
    }
    
    fun setCurrentLocationAsHome(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val currentLocation = locationManager.getCurrentLocationForEmergency()
                if (currentLocation != null) {
                    locationManager.setHomeLocation(currentLocation)
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                callback(false)
            }
        }
    }
    
    fun addSafeZoneAtCurrentLocation(name: String, radius: Float, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val currentLocation = currentLocation.value
                if (currentLocation != null) {
                    locationManager.addSafeZone(name, currentLocation.latitude, currentLocation.longitude, radius)
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                callback(false)
            }
        }
    }
    
    fun removeSafeZone(name: String) {
        locationManager.removeSafeZone(name)
    }
    
    fun setLocationSharingEnabled(enabled: Boolean) {
        locationManager.setLocationSharingEnabled(enabled)
    }
    
    fun getLocationHistoryForPeriod(hours: Int): List<Location> {
        return locationManager.getLocationHistory(hours)
    }
    
    fun getCurrentLocationForEmergency(callback: (Location?) -> Unit) {
        viewModelScope.launch {
            val location = locationManager.getCurrentLocationForEmergency()
            callback(location)
        }
    }
    
    fun updateLocationPattern() {
        viewModelScope.launch {
            val pattern = locationManager.getLocationPattern()
            _locationPattern.postValue(pattern)
        }
    }
    
    fun updateWalkingDistance() {
        viewModelScope.launch {
            val distance = locationManager.calculateWalkingDistance()
            _walkingDistance.postValue(distance)
        }
    }
    
    fun getEmergencyLocationInfo() {
        viewModelScope.launch {
            val info = locationManager.getEmergencyLocationInfo()
            _emergencyLocationInfo.postValue(info)
        }
    }
    
    fun isLocationServiceEnabled(): Boolean {
        // This would check if device location services are enabled
        return try {
            // Implementation would check LocationManager
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getLocationAccuracyStatus(): String {
        // This would return current location accuracy
        return "Good" // Placeholder
    }
    
    fun shareLocationWithCaretaker(caretakerEmail: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Implementation would share current location with specified caretaker
                // This would typically involve sending location data through Firebase
                val currentLocation = currentLocation.value
                if (currentLocation != null) {
                    // Share location logic here
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                callback(false)
            }
        }
    }
    
    fun generateLocationReport(): LocationReport {
        val current = currentLocation.value
        val home = homeLocation.value
        val zones = safeZones.value ?: emptyList()
        val pattern = _locationPattern.value ?: LocationManager.LocationPattern.INSUFFICIENT_DATA
        val walkingDist = _walkingDistance.value ?: 0f
        val history = getLocationHistoryForPeriod(24)
        
        return LocationReport(
            currentLocation = current,
            homeLocation = home,
            safeZones = zones,
            todayPattern = pattern,
            walkingDistance = walkingDist,
            locationHistory = history,
            isInSafeZone = current?.let { loc ->
                zones.any { zone ->
                    val distance = calculateDistance(
                        loc.latitude, loc.longitude,
                        zone.latitude, zone.longitude
                    )
                    distance <= zone.radiusMeters
                }
            } ?: false
        )
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    override fun onCleared() {
        super.onCleared()
        locationManager.cleanup()
    }
    
    data class LocationReport(
        val currentLocation: Location?,
        val homeLocation: Location?,
        val safeZones: List<LocationService.SafeZone>,
        val todayPattern: LocationManager.LocationPattern,
        val walkingDistance: Float,
        val locationHistory: List<Location>,
        val isInSafeZone: Boolean
    )
}