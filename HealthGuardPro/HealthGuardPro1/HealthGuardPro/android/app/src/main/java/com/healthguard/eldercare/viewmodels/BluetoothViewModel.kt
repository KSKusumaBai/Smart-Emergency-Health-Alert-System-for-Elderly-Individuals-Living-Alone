package com.healthguard.eldercare.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.healthguard.eldercare.managers.BluetoothManager
import com.healthguard.eldercare.models.HealthData
import com.healthguard.eldercare.services.BluetoothService

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bluetoothManager = BluetoothManager(application)
    
    private val _isScanning = MutableLiveData<Boolean>()
    val isScanning: LiveData<Boolean> = _isScanning
    
    private val _scanResults = MutableLiveData<List<BluetoothDevice>>()
    val scanResults: LiveData<List<BluetoothDevice>> = _scanResults
    
    private val _connectionState = MutableLiveData<BluetoothService.ConnectionState>()
    val connectionState: LiveData<BluetoothService.ConnectionState> = _connectionState
    
    private val _currentHealthData = MutableLiveData<HealthData>()
    val currentHealthData: LiveData<HealthData> = _currentHealthData
    
    private val _batteryLevel = MutableLiveData<Int>()
    val batteryLevel: LiveData<Int> = _batteryLevel
    
    init {
        bluetoothManager.bindService()
        
        // Observe manager data
        bluetoothManager.discoveredDevices.observeForever { devices ->
            _scanResults.value = devices
        }
        
        bluetoothManager.connectionState.observeForever { state ->
            _connectionState.value = state
        }
        
        bluetoothManager.healthData.observeForever { data ->
            _currentHealthData.value = data
        }
        
        bluetoothManager.batteryLevel.observeForever { level ->
            _batteryLevel.value = level
        }
    }
    
    fun startScan() {
        _isScanning.value = true
        bluetoothManager.startScan()
        
        // Stop scanning after timeout
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopScan()
        }, 10000)
    }
    
    fun stopScan() {
        _isScanning.value = false
        bluetoothManager.stopScan()
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        bluetoothManager.connectToDevice(device)
    }
    
    fun disconnect() {
        bluetoothManager.disconnect()
    }
    
    fun isConnected(): Boolean {
        return bluetoothManager.isConnected()
    }
    
    override fun onCleared() {
        super.onCleared()
        bluetoothManager.unbindService()
    }
}