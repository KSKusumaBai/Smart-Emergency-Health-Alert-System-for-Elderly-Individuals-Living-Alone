package com.healthguard.eldercare.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.healthguard.eldercare.R
import com.healthguard.eldercare.adapters.HealthDataAdapter
import com.healthguard.eldercare.databinding.ActivityDashboardBinding
import com.healthguard.eldercare.models.HealthData
import com.healthguard.eldercare.services.HealthMonitoringService
import com.healthguard.eldercare.viewmodels.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var viewModel: DashboardViewModel
    private lateinit var healthDataAdapter: HealthDataAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        
        setupUI()
        setupRecyclerView()
        observeViewModel()
        
        // Start health monitoring service
        startHealthMonitoringService()
        
        // Handle emergency intents
        handleEmergencyIntent()
    }
    
    private fun setupUI() {
        binding.apply {
            // Welcome message with current time
            updateWelcomeMessage()
            
            // Large, elderly-friendly buttons
            btnEmergencySos.setOnClickListener {
                showEmergencyConfirmation()
            }
            
            btnBluetoothConnect.setOnClickListener {
                startActivity(Intent(this@DashboardActivity, BluetoothConnectionActivity::class.java))
            }
            
            btnEmergencyContacts.setOnClickListener {
                startActivity(Intent(this@DashboardActivity, EmergencyContactsActivity::class.java))
            }
            
            btnLocationSettings.setOnClickListener {
                startActivity(Intent(this@DashboardActivity, LocationSettingsActivity::class.java))
            }
            
            btnSettings.setOnClickListener {
                startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java))
            }
            
            // Refresh button
            btnRefresh.setOnClickListener {
                viewModel.refreshHealthData()
            }
            
            // Health status card click
            cardHealthStatus.setOnClickListener {
                showHealthDetailsDialog()
            }
        }
    }
    
    private fun setupRecyclerView() {
        healthDataAdapter = HealthDataAdapter { healthData ->
            showHealthDataDetails(healthData)
        }
        
        binding.rvHealthHistory.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = healthDataAdapter
            
            // Limit to 5 items for elderly-friendly UI
            isNestedScrollingEnabled = false
        }
    }
    
    private fun observeViewModel() {
        // Current health data
        viewModel.currentHealthData.observe(this) { healthData ->
            updateHealthStatusUI(healthData)
        }
        
        // Health data history
        viewModel.healthDataHistory.observe(this) { history ->
            healthDataAdapter.updateHealthData(history.take(5)) // Show only 5 recent items
            updateHistoryVisibility(history.isEmpty())
        }
        
        // Connection status
        viewModel.bluetoothConnectionStatus.observe(this) { isConnected ->
            updateConnectionStatusUI(isConnected)
        }
        
        // Emergency alerts
        viewModel.activeEmergencyAlerts.observe(this) { alerts ->
            updateEmergencyAlertsUI(alerts.isNotEmpty())
        }
        
        // Location status
        viewModel.locationInfo.observe(this) { locationInfo ->
            updateLocationStatusUI(locationInfo)
        }
        
        // Health monitoring status
        viewModel.isMonitoring.observe(this) { isMonitoring ->
            updateMonitoringStatusUI(isMonitoring)
        }
        
        // Error messages
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showErrorDialog(it)
                viewModel.clearError()
            }
        }
    }
    
    private fun updateWelcomeMessage() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        
        val currentUser = viewModel.getCurrentUser()
        val userName = currentUser?.displayName?.split(" ")?.firstOrNull() ?: "User"
        
        binding.tvWelcomeMessage.text = "$greeting, $userName!"
        
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        binding.tvCurrentDate.text = dateFormat.format(Date())
    }
    
    private fun updateHealthStatusUI(healthData: HealthData?) {
        binding.apply {
            if (healthData != null) {
                // Heart rate
                tvHeartRateValue.text = if (healthData.heartRate > 0) {
                    "${healthData.heartRate} BPM"
                } else {
                    "-- BPM"
                }
                
                // Blood pressure
                if (healthData.bloodPressureSystolic > 0 && healthData.bloodPressureDiastolic > 0) {
                    tvBloodPressureValue.text = "${healthData.bloodPressureSystolic}/${healthData.bloodPressureDiastolic}"
                    tvBloodPressureUnit.text = "mmHg"
                } else {
                    tvBloodPressureValue.text = "-- / --"
                    tvBloodPressureUnit.text = "mmHg"
                }
                
                // Temperature
                tvTemperatureValue.text = if (healthData.temperature > 0) {
                    String.format("%.1f", healthData.temperature)
                } else {
                    "--"
                }
                
                // Overall status
                val status = healthData.analysisResult?.status ?: "unknown"
                updateHealthStatusIndicator(status)
                
                // Last updated
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                tvLastUpdated.text = "Last updated: ${timeFormat.format(Date(healthData.timestamp))}"
                
                cardHealthStatus.visibility = View.VISIBLE
            } else {
                cardHealthStatus.visibility = View.GONE
            }
        }
    }
    
    private fun updateHealthStatusIndicator(status: String) {
        binding.apply {
            val (color, text, icon) = when (status) {
                "normal" -> Triple(
                    R.color.success_color,
                    "All readings normal",
                    R.drawable.ic_check_circle
                )
                "abnormal" -> Triple(
                    R.color.warning_color,
                    "Some readings elevated",
                    R.drawable.ic_warning
                )
                "critical" -> Triple(
                    R.color.error_color,
                    "Critical - Seek help",
                    R.drawable.ic_error
                )
                else -> Triple(
                    R.color.secondary_text,
                    "Monitoring...",
                    R.drawable.ic_refresh
                )
            }
            
            tvHealthStatus.text = text
            tvHealthStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, color))
            ivHealthStatusIcon.setImageResource(icon)
            ivHealthStatusIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, color))
        }
    }
    
    private fun updateConnectionStatusUI(isConnected: Boolean) {
        binding.apply {
            if (isConnected) {
                tvConnectionStatus.text = "✓ Device Connected"
                tvConnectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.success_color))
                btnBluetoothConnect.text = "Device Settings"
            } else {
                tvConnectionStatus.text = "⚠ No Device Connected"
                tvConnectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.warning_color))
                btnBluetoothConnect.text = "Connect Device"
            }
        }
    }
    
    private fun updateEmergencyAlertsUI(hasActiveAlerts: Boolean) {
        binding.apply {
            if (hasActiveAlerts) {
                layoutEmergencyAlert.visibility = View.VISIBLE
                btnEmergencySos.text = "VIEW ACTIVE ALERT"
                btnEmergencySos.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.error_color))
            } else {
                layoutEmergencyAlert.visibility = View.GONE
                btnEmergencySos.text = "🚨 EMERGENCY SOS"
                btnEmergencySos.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.emergency_color))
            }
        }
    }
    
    private fun updateLocationStatusUI(locationInfo: Any?) {
        binding.apply {
            // This would display current location status
            // Implementation depends on the location info structure
        }
    }
    
    private fun updateMonitoringStatusUI(isMonitoring: Boolean) {
        binding.apply {
            if (isMonitoring) {
                tvMonitoringStatus.text = "🟢 Health monitoring active"
                tvMonitoringStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.success_color))
            } else {
                tvMonitoringStatus.text = "🔴 Health monitoring paused"
                tvMonitoringStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.error_color))
            }
        }
    }
    
    private fun updateHistoryVisibility(isEmpty: Boolean) {
        binding.apply {
            if (isEmpty) {
                rvHealthHistory.visibility = View.GONE
                tvNoHistory.visibility = View.VISIBLE
            } else {
                rvHealthHistory.visibility = View.VISIBLE
                tvNoHistory.visibility = View.GONE
            }
        }
    }
    
    private fun showEmergencyConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🚨 EMERGENCY ALERT")
            .setMessage("This will immediately contact your emergency contacts. Are you in an emergency?")
            .setPositiveButton("YES - EMERGENCY") { _, _ ->
                viewModel.triggerEmergencyAlert()
                showEmergencyTriggeredDialog()
            }
            .setNegativeButton("CANCEL") { _, _ ->
                // Do nothing
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showEmergencyTriggeredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🚨 Emergency Alert Sent")
            .setMessage("Your emergency contacts will be notified. Emergency services may be contacted if no response is received.")
            .setPositiveButton("I'M OK - CANCEL ALERT") { _, _ ->
                viewModel.cancelEmergencyAlert()
            }
            .setNegativeButton("CONTINUE") { _, _ ->
                // Alert continues
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showHealthDetailsDialog() {
        val healthData = viewModel.currentHealthData.value
        if (healthData != null) {
            val analysisResult = healthData.analysisResult
            val message = analysisResult?.message ?: "No analysis available"
            val recommendations = analysisResult?.recommendations?.joinToString("\n• ") ?: "No recommendations"
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Health Analysis")
                .setMessage("$message\n\nRecommendations:\n• $recommendations")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun showHealthDataDetails(healthData: HealthData) {
        val intent = Intent(this, HealthDataDetailActivity::class.java)
        intent.putExtra("health_data", healthData)
        startActivity(intent)
    }
    
    private fun showErrorDialog(error: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(error)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun startHealthMonitoringService() {
        val intent = Intent(this, HealthMonitoringService::class.java)
        intent.action = HealthMonitoringService.ACTION_START_MONITORING
        startService(intent)
    }
    
    private fun handleEmergencyIntent() {
        if (intent.getBooleanExtra("emergency_alert", false)) {
            val alertId = intent.getStringExtra("alert_id")
            showEmergencyResponseDialog(alertId)
        }
        
        if (intent.getBooleanExtra("caretaker_response", false)) {
            val responseType = intent.getStringExtra("response_type")
            Toast.makeText(this, "Caretaker response: $responseType", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showEmergencyResponseDialog(alertId: String?) {
        MaterialAlertDialogBuilder(this)
            .setTitle("🚨 Emergency Alert Active")
            .setMessage("Please confirm your status. If you don't respond, emergency contacts will be notified.")
            .setPositiveButton("I'M OK") { _, _ ->
                viewModel.respondToEmergency(alertId, "user_ok")
            }
            .setNegativeButton("FALSE ALARM") { _, _ ->
                viewModel.respondToEmergency(alertId, "false_alarm")
            }
            .setNeutralButton("NEED HELP") { _, _ ->
                viewModel.respondToEmergency(alertId, "need_help")
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        updateWelcomeMessage()
        viewModel.refreshDashboard()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Don't stop the monitoring service when activity is destroyed
        // It should continue running in background
    }
}