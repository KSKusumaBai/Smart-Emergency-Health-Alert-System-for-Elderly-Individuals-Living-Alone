package com.healthguard.eldercare.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import com.healthguard.eldercare.models.Location
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*

class LocationService(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationService"
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 seconds
        private const val FASTEST_LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
        private const val EMERGENCY_LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds during emergency
    }
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null
    private var isTracking = false
    private var isEmergencyMode = false
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // LiveData for location updates
    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation
    
    private val _locationAccuracy = MutableLiveData<Float>()
    val locationAccuracy: LiveData<Float> = _locationAccuracy
    
    private val _isLocationEnabled = MutableLiveData<Boolean>()
    val isLocationEnabled: LiveData<Boolean> = _isLocationEnabled
    
    init {
        checkLocationSettings()
    }
    
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return
        }
        
        if (isTracking) {
            Log.d(TAG, "Location tracking already active")
            return
        }
        
        createLocationRequest()
        createLocationCallback()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest!!,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "Location tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when starting location updates", e)
        }
    }
    
    fun stopLocationUpdates() {
        if (!isTracking) return
        
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        isTracking = false
        isEmergencyMode = false
        Log.d(TAG, "Location tracking stopped")
    }
    
    fun enableEmergencyMode() {
        Log.d(TAG, "Emergency mode enabled - increasing location frequency")
        isEmergencyMode = true
        
        if (isTracking) {
            stopLocationUpdates()
            startLocationUpdates()
        }
    }
    
    fun disableEmergencyMode() {
        Log.d(TAG, "Emergency mode disabled - returning to normal frequency")
        isEmergencyMode = false
        
        if (isTracking) {
            stopLocationUpdates()
            startLocationUpdates()
        }
    }
    
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationOnce(): Location? {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return null
        }
        
        return try {
            val locationResult = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )
            
            val androidLocation = locationResult.await()
            convertToCustomLocation(androidLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location", e)
            null
        }
    }
    
    private fun createLocationRequest() {
        val interval = if (isEmergencyMode) {
            EMERGENCY_LOCATION_UPDATE_INTERVAL
        } else {
            LOCATION_UPDATE_INTERVAL
        }
        
        val fastestInterval = if (isEmergencyMode) {
            EMERGENCY_LOCATION_UPDATE_INTERVAL / 2
        } else {
            FASTEST_LOCATION_UPDATE_INTERVAL
        }
        
        locationRequest = LocationRequest.Builder(interval)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(fastestInterval)
            .setMaxUpdateDelayMillis(interval * 2)
            .build()
    }
    
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                
                locationResult.locations.forEach { androidLocation ->
                    serviceScope.launch {
                        val location = convertToCustomLocation(androidLocation)
                        _currentLocation.postValue(location)
                        _locationAccuracy.postValue(androidLocation.accuracy)
                        
                        Log.d(TAG, "Location updated: ${location?.latitude}, ${location?.longitude}")
                    }
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                _isLocationEnabled.postValue(availability.isLocationAvailable)
                
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Location is not available")
                }
            }
        }
    }
    
    private suspend fun convertToCustomLocation(androidLocation: android.location.Location): Location? {
        return withContext(Dispatchers.IO) {
            try {
                val address = getAddressFromLocation(androidLocation.latitude, androidLocation.longitude)
                
                Location(
                    latitude = androidLocation.latitude,
                    longitude = androidLocation.longitude,
                    address = address,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error converting location", e)
                
                // Return location without address if geocoding fails
                Location(
                    latitude = androidLocation.latitude,
                    longitude = androidLocation.longitude,
                    address = "Address unavailable",
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
    
    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            if (!Geocoder.isPresent()) {
                return "Geocoder not available"
            }
            
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                buildString {
                    address.getAddressLine(0)?.let { append(it) }
                    if (isEmpty()) {
                        address.subThoroughfare?.let { append(it).append(" ") }
                        address.thoroughfare?.let { append(it).append(", ") }
                        address.locality?.let { append(it).append(", ") }
                        address.adminArea?.let { append(it).append(", ") }
                        address.countryName?.let { append(it) }
                    }
                }
            } else {
                "Address not found"
            }
        } catch (e: IOException) {
            Log.e(TAG, "Geocoder service not available", e)
            "Address lookup failed"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting address", e)
            "Address error"
        }
    }
    
    fun getLocationForEmergency(callback: (Location?) -> Unit) {
        serviceScope.launch {
            try {
                // Try to get the most recent location first
                val recentLocation = _currentLocation.value
                
                if (recentLocation != null && isLocationRecent(recentLocation.timestamp)) {
                    callback(recentLocation)
                    return@launch
                }
                
                // If no recent location, try to get current location
                val currentLocation = getCurrentLocationOnce()
                callback(currentLocation)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting emergency location", e)
                callback(null)
            }
        }
    }
    
    private fun isLocationRecent(timestamp: Long): Boolean {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        return timestamp > fiveMinutesAgo
    }
    
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    private fun checkLocationSettings() {
        _isLocationEnabled.value = isLocationServiceEnabled()
    }
    
    fun getLocationAccuracyStatus(): String {
        val accuracy = _locationAccuracy.value ?: return "Unknown"
        
        return when {
            accuracy < 10f -> "Excellent (${accuracy.toInt()}m)"
            accuracy < 50f -> "Good (${accuracy.toInt()}m)"
            accuracy < 100f -> "Fair (${accuracy.toInt()}m)"
            else -> "Poor (${accuracy.toInt()}m)"
        }
    }
    
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    fun isWithinSafeZone(
        currentLocation: Location,
        safeZones: List<SafeZone>
    ): Boolean {
        return safeZones.any { safeZone ->
            val distance = calculateDistance(
                currentLocation.latitude, currentLocation.longitude,
                safeZone.latitude, safeZone.longitude
            )
            distance <= safeZone.radiusMeters
        }
    }
    
    fun cleanup() {
        stopLocationUpdates()
        serviceScope.cancel()
    }
    
    data class SafeZone(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float
    )
}