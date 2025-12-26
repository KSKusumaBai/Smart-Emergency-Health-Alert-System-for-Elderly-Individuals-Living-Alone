package com.healthguard.eldercare.ui.activities

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.healthguard.eldercare.R
import com.healthguard.eldercare.adapters.BluetoothDeviceAdapter
import com.healthguard.eldercare.databinding.ActivityBluetoothConnectionBinding
import com.healthguard.eldercare.managers.BluetoothManager
import com.healthguard.eldercare.services.BluetoothService
import com.healthguard.eldercare.utils.PermissionManager
import com.healthguard.eldercare.viewmodels.BluetoothViewModel

class BluetoothConnectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBluetoothConnectionBinding
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothViewModel: BluetoothViewModel
    private lateinit var permissionManager: PermissionManager
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    
    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize components
        bluetoothManager = BluetoothManager(this)
        bluetoothViewModel = ViewModelProvider(this)[BluetoothViewModel::class.java]
        permissionManager = PermissionManager(this)
        
        // Setup UI
        setupUI()
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Observe ViewModel
        observeViewModel()
        
        // Bind Bluetooth service
        bluetoothManager.bindService()
        
        // Check permissions
        checkBluetoothPermissions()
    }
    
    private fun setupUI() {
        binding.apply {
            // Toolbar
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = getString(R.string.bluetooth_title)
            
            // Scan button
            btnScan.setOnClickListener {
                if (permissionManager.hasBluetoothPermissions()) {
                    startScan()
                } else {
                    requestBluetoothPermissions()
                }
            }
            
            // Back button
            toolbar.setNavigationOnClickListener {
                finish()
            }
        }
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter { device ->
            connectToDevice(device)
        }
        
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@BluetoothConnectionActivity)
            adapter = deviceAdapter
        }
    }
    
    private fun observeViewModel() {
        // Connection state
        bluetoothManager.connectionState.observe(this) { state ->
            updateConnectionUI(state)
        }
        
        // Discovered devices
        bluetoothManager.discoveredDevices.observe(this) { devices ->
            deviceAdapter.updateDevices(devices)
            binding.tvNoDevices.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        }
        
        // Health data
        bluetoothManager.healthData.observe(this) { healthData ->
            updateHealthDataUI(healthData)
        }
        
        // Battery level
        bluetoothManager.batteryLevel.observe(this) { level ->
            binding.tvBatteryLevel.text = getString(R.string.battery_level, level)
        }
    }
    
    private fun checkBluetoothPermissions() {
        if (!permissionManager.hasBluetoothPermissions()) {
            requestBluetoothPermissions()
        }
    }
    
    private fun requestBluetoothPermissions() {
        permissionManager.requestBluetoothPermissions(this)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
    
    private fun startScan() {
        binding.apply {
            btnScan.isEnabled = false
            btnScan.text = getString(R.string.bluetooth_scan_in_progress)
            progressBar.visibility = View.VISIBLE
            tvScanStatus.text = getString(R.string.bluetooth_scanning)
            tvScanStatus.visibility = View.VISIBLE
        }
        
        bluetoothManager.startScan()
        
        // Re-enable scan button after 10 seconds
        binding.btnScan.postDelayed({
            binding.apply {
                btnScan.isEnabled = true
                btnScan.text = getString(R.string.bluetooth_scan)
                progressBar.visibility = View.GONE
                tvScanStatus.visibility = View.GONE
            }
        }, 10000)
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothManager.connectToDevice(device)
        Toast.makeText(this, "Connecting to ${device.name ?: "Unknown Device"}", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateConnectionUI(state: BluetoothService.ConnectionState) {
        binding.apply {
            when (state) {
                BluetoothService.ConnectionState.CONNECTING -> {
                    tvConnectionStatus.text = getString(R.string.bluetooth_connecting)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@BluetoothConnectionActivity, R.color.warning_color))
                    progressConnection.visibility = View.VISIBLE
                }
                BluetoothService.ConnectionState.CONNECTED -> {
                    tvConnectionStatus.text = getString(R.string.bluetooth_connected)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@BluetoothConnectionActivity, R.color.success_color))
                    progressConnection.visibility = View.GONE
                    layoutHealthData.visibility = View.VISIBLE
                    btnDisconnect.visibility = View.VISIBLE
                    
                    btnDisconnect.setOnClickListener {
                        bluetoothManager.disconnect()
                    }
                }
                BluetoothService.ConnectionState.DISCONNECTED -> {
                    tvConnectionStatus.text = getString(R.string.bluetooth_disconnected)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@BluetoothConnectionActivity, R.color.secondary_text))
                    progressConnection.visibility = View.GONE
                    layoutHealthData.visibility = View.GONE
                    btnDisconnect.visibility = View.GONE
                }
                BluetoothService.ConnectionState.FAILED -> {
                    tvConnectionStatus.text = getString(R.string.bluetooth_connection_failed)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@BluetoothConnectionActivity, R.color.error_color))
                    progressConnection.visibility = View.GONE
                    Toast.makeText(this@BluetoothConnectionActivity, "Connection failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateHealthDataUI(healthData: com.healthguard.eldercare.models.HealthData) {
        binding.apply {
            tvHeartRate.text = if (healthData.heartRate > 0) {
                "${healthData.heartRate} BPM"
            } else {
                "-- BPM"
            }
            
            tvBloodPressure.text = if (healthData.bloodPressureSystolic > 0 && healthData.bloodPressureDiastolic > 0) {
                "${healthData.bloodPressureSystolic}/${healthData.bloodPressureDiastolic} mmHg"
            } else {
                "-- / -- mmHg"
            }
            
            tvTemperature.text = if (healthData.temperature > 0) {
                String.format("%.1f°C", healthData.temperature)
            } else {
                "--°C"
            }
            
            tvLastUpdate.text = "Last updated: ${android.text.format.DateFormat.getTimeFormat(this@BluetoothConnectionActivity).format(healthData.timestamp)}"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.unbindService()
    }
}