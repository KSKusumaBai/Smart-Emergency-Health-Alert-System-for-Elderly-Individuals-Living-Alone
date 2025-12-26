package com.healthguard.eldercare.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.healthguard.eldercare.R
import com.healthguard.eldercare.adapters.SafeZoneAdapter
import com.healthguard.eldercare.databinding.ActivityLocationSettingsBinding
import com.healthguard.eldercare.models.Location
import com.healthguard.eldercare.services.LocationService
import com.healthguard.eldercare.utils.PermissionManager
import com.healthguard.eldercare.viewmodels.LocationViewModel

class LocationSettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLocationSettingsBinding
    private lateinit var viewModel: LocationViewModel
    private lateinit var permissionManager: PermissionManager
    private lateinit var safeZoneAdapter: SafeZoneAdapter
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[LocationViewModel::class.java]
        permissionManager = PermissionManager(this)
        
        setupUI()
        setupRecyclerView()
        observeViewModel()
        checkPermissions()
    }
    
    private fun setupUI() {
        binding.apply {
            // Toolbar
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Location Settings"
            
            // Location sharing toggle
            switchLocationSharing.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setLocationSharingEnabled(isChecked)
            }
            
            // Set home location button
            btnSetHomeLocation.setOnClickListener {
                setCurrentLocationAsHome()
            }
            
            // Add safe zone button
            btnAddSafeZone.setOnClickListener {
                showAddSafeZoneDialog()
            }
            
            // Permission settings button
            btnLocationPermissions.setOnClickListener {
                openAppSettings()
            }
            
            // Location services button
            btnLocationServices.setOnClickListener {
                openLocationSettings()
            }
            
            // Back button
            toolbar.setNavigationOnClickListener {
                finish()
            }
        }
    }
    
    private fun setupRecyclerView() {
        safeZoneAdapter = SafeZoneAdapter { safeZone ->
            showRemoveSafeZoneDialog(safeZone)
        }
        
        binding.rvSafeZones.apply {
            layoutManager = LinearLayoutManager(this@LocationSettingsActivity)
            adapter = safeZoneAdapter
        }
    }
    
    private fun observeViewModel() {
        viewModel.currentLocation.observe(this) { location ->
            updateCurrentLocationUI(location)
        }
        
        viewModel.homeLocation.observe(this) { homeLocation ->
            updateHomeLocationUI(homeLocation)
        }
        
        viewModel.safeZones.observe(this) { safeZones ->
            safeZoneAdapter.updateSafeZones(safeZones)
            updateSafeZonesUI(safeZones.isEmpty())
        }
        
        viewModel.isLocationSharingEnabled.observe(this) { enabled ->
            binding.switchLocationSharing.isChecked = enabled
        }
        
        viewModel.locationPattern.observe(this) { pattern ->
            updateLocationPatternUI(pattern)
        }
        
        viewModel.walkingDistance.observe(this) { distance ->
            binding.tvWalkingDistance.text = String.format("%.1f meters today", distance)
        }
    }
    
    private fun checkPermissions() {
        if (!permissionManager.hasLocationPermissions()) {
            showLocationPermissionDialog()
        } else {
            viewModel.startLocationTracking()
        }
        
        updatePermissionStatus()
    }
    
    private fun updatePermissionStatus() {
        binding.apply {
            val hasLocationPermission = permissionManager.hasLocationPermissions()
            val isLocationServiceEnabled = viewModel.isLocationServiceEnabled()
            
            // Permission status
            tvLocationPermissionStatus.text = if (hasLocationPermission) {
                "✓ Location permission granted"
            } else {
                "✗ Location permission required"
            }
            
            tvLocationPermissionStatus.setTextColor(
                ContextCompat.getColor(
                    this@LocationSettingsActivity,
                    if (hasLocationPermission) R.color.success_color else R.color.error_color
                )
            )
            
            // Location service status
            tvLocationServiceStatus.text = if (isLocationServiceEnabled) {
                "✓ Location services enabled"
            } else {
                "✗ Location services disabled"
            }
            
            tvLocationServiceStatus.setTextColor(
                ContextCompat.getColor(
                    this@LocationSettingsActivity,
                    if (isLocationServiceEnabled) R.color.success_color else R.color.error_color
                )
            )
            
            // Enable/disable features based on permissions
            val locationAvailable = hasLocationPermission && isLocationServiceEnabled
            switchLocationSharing.isEnabled = locationAvailable
            btnSetHomeLocation.isEnabled = locationAvailable
            btnAddSafeZone.isEnabled = locationAvailable
        }
    }
    
    private fun showLocationPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Permission Required")
            .setMessage("HealthGuard needs location permission to provide emergency location services and track your safety.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestLocationPermissions()
            }
            .setNegativeButton("Not Now", null)
            .show()
    }
    
    private fun requestLocationPermissions() {
        permissionManager.requestLocationPermissions(this)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
                    viewModel.startLocationTracking()
                } else {
                    Toast.makeText(this, "Location permission is required for safety features", Toast.LENGTH_LONG).show()
                }
                updatePermissionStatus()
            }
        }
    }
    
    private fun updateCurrentLocationUI(location: Location?) {
        binding.apply {
            if (location != null) {
                tvCurrentLocation.text = location.address
                tvCurrentCoordinates.text = String.format(
                    "%.6f, %.6f",
                    location.latitude,
                    location.longitude
                )
                layoutCurrentLocation.visibility = View.VISIBLE
            } else {
                layoutCurrentLocation.visibility = View.GONE
            }
        }
    }
    
    private fun updateHomeLocationUI(homeLocation: Location?) {
        binding.apply {
            if (homeLocation != null) {
                tvHomeLocation.text = homeLocation.address
                btnSetHomeLocation.text = "Update Home Location"
            } else {
                tvHomeLocation.text = "No home location set"
                btnSetHomeLocation.text = "Set Home Location"
            }
        }
    }
    
    private fun updateSafeZonesUI(isEmpty: Boolean) {
        binding.apply {
            if (isEmpty) {
                rvSafeZones.visibility = View.GONE
                tvNoSafeZones.visibility = View.VISIBLE
            } else {
                rvSafeZones.visibility = View.VISIBLE
                tvNoSafeZones.visibility = View.GONE
            }
        }
    }
    
    private fun updateLocationPatternUI(pattern: com.healthguard.eldercare.managers.LocationManager.LocationPattern) {
        val patternText = when (pattern) {
            com.healthguard.eldercare.managers.LocationManager.LocationPattern.STAYED_HOME -> "Stayed at home"
            com.healthguard.eldercare.managers.LocationManager.LocationPattern.LOCAL_ACTIVITIES -> "Local activities"
            com.healthguard.eldercare.managers.LocationManager.LocationPattern.NORMAL_ACTIVITIES -> "Normal activities"
            com.healthguard.eldercare.managers.LocationManager.LocationPattern.LONG_DISTANCE_TRAVEL -> "Long distance travel"
            com.healthguard.eldercare.managers.LocationManager.LocationPattern.NO_HOME_SET -> "Home location not set"
            com.healthguard.eldercare.managers.LocationManager.LocationPattern.INSUFFICIENT_DATA -> "Insufficient data"
        }
        
        binding.tvLocationPattern.text = "Today's pattern: $patternText"
    }
    
    private fun setCurrentLocationAsHome() {
        viewModel.setCurrentLocationAsHome { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Home location set successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to set home location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showAddSafeZoneDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_safe_zone, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_safe_zone_name)
        val etRadius = dialogView.findViewById<TextInputEditText>(R.id.et_safe_zone_radius)
        
        // Pre-fill with current location name
        val currentLocation = viewModel.currentLocation.value
        if (currentLocation != null) {
            etName.setText(extractLocationName(currentLocation.address))
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Safe Zone")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val radiusText = etRadius.text.toString().trim()
                
                if (validateSafeZoneInput(name, radiusText)) {
                    val radius = radiusText.toFloat()
                    viewModel.addSafeZoneAtCurrentLocation(name, radius) { success ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, "Safe zone added", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to add safe zone", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun extractLocationName(address: String): String {
        // Extract a meaningful name from the address
        val parts = address.split(",")
        return if (parts.isNotEmpty()) {
            parts[0].trim()
        } else {
            "Safe Zone"
        }
    }
    
    private fun validateSafeZoneInput(name: String, radiusText: String): Boolean {
        when {
            name.isEmpty() -> {
                Toast.makeText(this, "Please enter a name for the safe zone", Toast.LENGTH_SHORT).show()
                return false
            }
            radiusText.isEmpty() -> {
                Toast.makeText(this, "Please enter a radius", Toast.LENGTH_SHORT).show()
                return false
            }
            else -> {
                try {
                    val radius = radiusText.toFloat()
                    if (radius <= 0 || radius > 10000) {
                        Toast.makeText(this, "Radius must be between 1 and 10000 meters", Toast.LENGTH_SHORT).show()
                        return false
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Please enter a valid radius", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
        }
        return true
    }
    
    private fun showRemoveSafeZoneDialog(safeZone: LocationService.SafeZone) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Safe Zone")
            .setMessage("Are you sure you want to remove '${safeZone.name}'?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeSafeZone(safeZone.name)
                Toast.makeText(this, "Safe zone removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}