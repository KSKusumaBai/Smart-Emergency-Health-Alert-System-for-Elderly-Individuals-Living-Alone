package com.healthguard.eldercare.managers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.healthguard.eldercare.models.HealthData
import com.healthguard.eldercare.services.BluetoothService
import android.bluetooth.BluetoothDevice

class BluetoothManager(private val context: Context) {
    
    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    
    private val _connectionState = MutableLiveData<BluetoothService.ConnectionState>()
    val connectionState: LiveData<BluetoothService.ConnectionState> = _connectionState
    
    private val _healthData = MutableLiveData<HealthData>()
    val healthData: LiveData<HealthData> = _healthData
    
    private val _discoveredDevices = MutableLiveData<List<BluetoothDevice>>()
    val discoveredDevices: LiveData<List<BluetoothDevice>> = _discoveredDevices
    
    private val _batteryLevel = MutableLiveData<Int>()
    val batteryLevel: LiveData<Int> = _batteryLevel
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            isBound = true
            
            // Observe service data
            bluetoothService?.let { bluetoothService ->
                bluetoothService.connectionState.observeForever { state ->
                    _connectionState.value = state
                }
                
                bluetoothService.healthData.observeForever { data ->
                    _healthData.value = data
                }
                
                bluetoothService.discoveredDevices.observeForever { devices ->
                    _discoveredDevices.value = devices
                }
                
                bluetoothService.batteryLevel.observeForever { level ->
                    _batteryLevel.value = level
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }
    
    fun bindService() {
        if (!isBound) {
            val intent = Intent(context, BluetoothService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
    
    fun startScan() {
        bluetoothService?.startScan()
    }
    
    fun stopScan() {
        bluetoothService?.stopScan()
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        bluetoothService?.connectToDevice(device)
    }
    
    fun disconnect() {
        bluetoothService?.disconnect()
    }
    
    fun isConnected(): Boolean {
        return bluetoothService?.connectionState?.value == BluetoothService.ConnectionState.CONNECTED
    }
    
    fun getCurrentHealthData(): HealthData? {
        return bluetoothService?.healthData?.value
    }
    
    fun getConnectionState(): BluetoothService.ConnectionState? {
        return bluetoothService?.connectionState?.value
    }
}