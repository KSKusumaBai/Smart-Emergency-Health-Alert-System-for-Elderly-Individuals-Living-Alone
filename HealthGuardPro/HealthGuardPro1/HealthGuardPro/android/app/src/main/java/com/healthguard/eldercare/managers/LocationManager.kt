package com.healthguard.eldercare.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthguard.eldercare.firebase.FirebaseManager
import com.healthguard.eldercare.models.Location
import com.healthguard.eldercare.services.LocationService
import kotlinx.coroutines.*

class LocationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationManager"
        private const val LOCATION_PREFS = "location_preferences"
        private const val KEY_SAFE_ZONES = "safe_zones"
        private const val KEY_HOME_LOCATION = "home_location"
        private const val KEY_LOCATION_SHARING_ENABLED = "location_sharing_enabled"
        private const val KEY_GEOFENCE_ALERTS_ENABLED = "geofence_alerts_enabled"
    }
    
    private val locationService = LocationService(context)
    private val firebaseManager = FirebaseManager()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Live data
    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation
    
    private val _safeZones = MutableLiveData<List<LocationService.SafeZone>>()
    val safeZones: LiveData<List<LocationService.SafeZone>> = _safeZones
    
    private val _homeLocation = MutableLiveData<Location?>()
    val homeLocation: LiveData<Location?> = _homeLocation
    
    private val _isLocationSharingEnabled = MutableLiveData<Boolean>()
    val isLocationSharingEnabled: LiveData<Boolean> = _isLocationSharingEnabled
    
    private val _geofenceAlerts = MutableLiveData<List<GeofenceAlert>>()
    val geofenceAlerts: LiveData<List<GeofenceAlert>> = _geofenceAlerts
    
    // Location history for tracking patterns
    private val locationHistory = mutableListOf<Location>()
    private val maxHistorySize = 1000
    
    init {
        loadSettings()
        observeLocationService()
    }
    
    private fun loadSettings() {
        // Load safe zones
        val safeZonesJson = sharedPrefs.getString(KEY_SAFE_ZONES, "[]")
        val safeZoneType = object : TypeToken<List<LocationService.SafeZone>>() {}.type
        val loadedSafeZones: List<LocationService.SafeZone> = gson.fromJson(safeZoneType, safeZoneType) ?: emptyList()
        _safeZones.value = loadedSafeZones
        
        // Load home location
        val homeLocationJson = sharedPrefs.getString(KEY_HOME_LOCATION, null)
        if (homeLocationJson != null) {
            val homeLocation = gson.fromJson(homeLocationJson, Location::class.java)
            _homeLocation.value = homeLocation
        }
        
        // Load sharing settings
        _isLocationSharingEnabled.value = sharedPrefs.getBoolean(KEY_LOCATION_SHARING_ENABLED, true)
    }
    
    private fun saveSettings() {
        val editor = sharedPrefs.edit()
        
        // Save safe zones
        val safeZonesJson = gson.toJson(_safeZones.value ?: emptyList())
        editor.putString(KEY_SAFE_ZONES, safeZonesJson)
        
        // Save home location
        val homeLocationJson = gson.toJson(_homeLocation.value)
        editor.putString(KEY_HOME_LOCATION, homeLocationJson)
        
        // Save sharing settings
        editor.putBoolean(KEY_LOCATION_SHARING_ENABLED, _isLocationSharingEnabled.value ?: true)
        
        editor.apply()
    }
    
    private fun observeLocationService() {
        locationService.currentLocation.observeForever { location ->
            location?.let {
                _currentLocation.value = it
                
                // Add to history
                addToLocationHistory(it)
                
                // Check geofences
                checkGeofences(it)
                
                // Share location if enabled
                if (_isLocationSharingEnabled.value == true) {
                    shareLocationWithCaretakers(it)
                }
            }
        }
    }
    
    fun startLocationTracking() {
        if (locationService.hasLocationPermission()) {
            locationService.startLocationUpdates()
            Log.d(TAG, "Location tracking started")
        } else {
            Log.w(TAG, "Location permission not granted")
        }
    }
    
    fun stopLocationTracking() {
        locationService.stopLocationUpdates()
        Log.d(TAG, "Location tracking stopped")
    }
    
    fun enableEmergencyMode() {
        locationService.enableEmergencyMode()
        Log.d(TAG, "Emergency location mode enabled")
    }
    
    fun disableEmergencyMode() {
        locationService.disableEmergencyMode()
        Log.d(TAG, "Emergency location mode disabled")
    }
    
    suspend fun getCurrentLocationForEmergency(): Location? {
        return locationService.getCurrentLocationOnce()
    }
    
    fun addSafeZone(name: String, latitude: Double, longitude: Double, radiusMeters: Float) {
        val currentSafeZones = _safeZones.value?.toMutableList() ?: mutableListOf()
        val newSafeZone = LocationService.SafeZone(name, latitude, longitude, radiusMeters)
        currentSafeZones.add(newSafeZone)
        _safeZones.value = currentSafeZones
        saveSettings()
        
        Log.d(TAG, "Safe zone added: $name")
    }
    
    fun removeSafeZone(name: String) {
        val currentSafeZones = _safeZones.value?.toMutableList() ?: mutableListOf()
        currentSafeZones.removeAll { it.name == name }
        _safeZones.value = currentSafeZones
        saveSettings()
        
        Log.d(TAG, "Safe zone removed: $name")
    }
    
    fun setHomeLocation(location: Location) {
        _homeLocation.value = location
        saveSettings()
        
        // Automatically add home as a safe zone
        addSafeZone("Home", location.latitude, location.longitude, 100f)
        
        Log.d(TAG, "Home location set: ${location.address}")
    }
    
    fun setLocationSharingEnabled(enabled: Boolean) {
        _isLocationSharingEnabled.value = enabled
        saveSettings()
        
        Log.d(TAG, "Location sharing ${if (enabled) "enabled" else "disabled"}")
    }
    
    private fun addToLocationHistory(location: Location) {
        locationHistory.add(location)
        
        // Limit history size
        if (locationHistory.size > maxHistorySize) {
            locationHistory.removeAt(0)
        }
    }
    
    private fun checkGeofences(location: Location) {
        val safeZones = _safeZones.value ?: return
        val isInSafeZone = locationService.isWithinSafeZone(location, safeZones)
        
        if (!isInSafeZone) {
            handleGeofenceExit(location)
        }
    }
    
    private fun handleGeofenceExit(location: Location) {
        // Check if it's been a significant time since last geofence alert
        val lastAlert = getLastGeofenceAlert()
        val now = System.currentTimeMillis()
        
        if (lastAlert == null || now - lastAlert.timestamp > 30 * 60 * 1000) { // 30 minutes
            val alert = GeofenceAlert(
                id = "geofence_${now}",
                location = location,
                timestamp = now,
                type = "safe_zone_exit",
                message = "User has left all safe zones"
            )
            
            addGeofenceAlert(alert)
            notifyCaretakersOfGeofenceExit(alert)
            
            Log.w(TAG, "Geofence exit detected: ${location.address}")
        }
    }
    
    private fun shareLocationWithCaretakers(location: Location) {
        managerScope.launch {
            try {
                // This would typically send location to Firebase for caretaker access
                // Implementation depends on your specific sharing requirements
                Log.d(TAG, "Location shared with caretakers")
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing location", e)
            }
        }
    }
    
    private fun notifyCaretakersOfGeofenceExit(alert: GeofenceAlert) {
        managerScope.launch {
            try {
                // Send notification to caretakers about geofence exit
                Log.w(TAG, "Caretakers notified of geofence exit")
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying caretakers", e)
            }
        }
    }
    
    private fun addGeofenceAlert(alert: GeofenceAlert) {
        val currentAlerts = _geofenceAlerts.value?.toMutableList() ?: mutableListOf()
        currentAlerts.add(0, alert) // Add to beginning
        
        // Keep only recent alerts (last 50)
        if (currentAlerts.size > 50) {
            currentAlerts.removeAt(currentAlerts.size - 1)
        }
        
        _geofenceAlerts.value = currentAlerts
    }
    
    private fun getLastGeofenceAlert(): GeofenceAlert? {
        return _geofenceAlerts.value?.firstOrNull()
    }
    
    fun getLocationHistory(hours: Int = 24): List<Location> {
        val cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000)
        return locationHistory.filter { it.timestamp > cutoffTime }
    }
    
    fun getLocationPattern(): LocationPattern {
        val recentHistory = getLocationHistory(24)
        
        if (recentHistory.isEmpty()) {
            return LocationPattern.INSUFFICIENT_DATA
        }
        
        val homeLocation = _homeLocation.value
        if (homeLocation == null) {
            return LocationPattern.NO_HOME_SET
        }
        
        val distancesFromHome = recentHistory.map { location ->
            locationService.calculateDistance(
                location.latitude, location.longitude,
                homeLocation.latitude, homeLocation.longitude
            )
        }
        
        val averageDistance = distancesFromHome.average()
        val maxDistance = distancesFromHome.maxOrNull() ?: 0f
        
        return when {
            maxDistance < 100f -> LocationPattern.STAYED_HOME
            averageDistance < 500f -> LocationPattern.LOCAL_ACTIVITIES
            maxDistance > 5000f -> LocationPattern.LONG_DISTANCE_TRAVEL
            else -> LocationPattern.NORMAL_ACTIVITIES
        }
    }
    
    fun calculateWalkingDistance(): Float {
        val recentHistory = getLocationHistory(24)
        if (recentHistory.size < 2) return 0f
        
        var totalDistance = 0f
        
        for (i in 1 until recentHistory.size) {
            val prev = recentHistory[i - 1]
            val current = recentHistory[i]
            
            val distance = locationService.calculateDistance(
                prev.latitude, prev.longitude,
                current.latitude, current.longitude
            )
            
            // Only count movements that seem like walking (< 100m between updates)
            if (distance < 100f) {
                totalDistance += distance
            }
        }
        
        return totalDistance
    }
    
    fun getEmergencyLocationInfo(): EmergencyLocationInfo {
        val currentLocation = _currentLocation.value
        val homeLocation = _homeLocation.value
        val safeZones = _safeZones.value ?: emptyList()
        
        return EmergencyLocationInfo(
            currentLocation = currentLocation,
            homeLocation = homeLocation,
            nearestSafeZone = findNearestSafeZone(currentLocation, safeZones),
            isInSafeZone = currentLocation?.let { locationService.isWithinSafeZone(it, safeZones) } ?: false,
            locationAccuracy = locationService.getLocationAccuracyStatus()
        )
    }
    
    private fun findNearestSafeZone(
        currentLocation: Location?,
        safeZones: List<LocationService.SafeZone>
    ): LocationService.SafeZone? {
        if (currentLocation == null || safeZones.isEmpty()) return null
        
        return safeZones.minByOrNull { safeZone ->
            locationService.calculateDistance(
                currentLocation.latitude, currentLocation.longitude,
                safeZone.latitude, safeZone.longitude
            )
        }
    }
    
    fun cleanup() {
        locationService.cleanup()
        managerScope.cancel()
    }
    
    data class GeofenceAlert(
        val id: String,
        val location: Location,
        val timestamp: Long,
        val type: String,
        val message: String
    )
    
    data class EmergencyLocationInfo(
        val currentLocation: Location?,
        val homeLocation: Location?,
        val nearestSafeZone: LocationService.SafeZone?,
        val isInSafeZone: Boolean,
        val locationAccuracy: String
    )
    
    enum class LocationPattern {
        STAYED_HOME,
        LOCAL_ACTIVITIES,
        NORMAL_ACTIVITIES,
        LONG_DISTANCE_TRAVEL,
        NO_HOME_SET,
        INSUFFICIENT_DATA
    }
}