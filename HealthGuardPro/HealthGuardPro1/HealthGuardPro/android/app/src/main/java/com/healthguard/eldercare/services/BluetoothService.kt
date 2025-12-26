package com.healthguard.eldercare.services

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.healthguard.eldercare.models.HealthData
import com.healthguard.eldercare.utils.BluetoothParser
import java.util.*

class BluetoothService : Service() {
    
    companion object {
        private const val TAG = "BluetoothService"
        
        // Standard Bluetooth Health Service UUIDs
        val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        val HEALTH_THERMOMETER_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805F9B34FB")
        val TEMPERATURE_MEASUREMENT_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805F9B34FB")
        val BLOOD_PRESSURE_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805F9B34FB")
        val BLOOD_PRESSURE_MEASUREMENT_UUID = UUID.fromString("00002A35-0000-1000-8000-00805F9B34FB")
        val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        val DEVICE_INFO_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    }
    
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentDevice: BluetoothDevice? = null
    private var isScanning = false
    private var isConnected = false
    
    // LiveData for UI updates
    val connectionState = MutableLiveData<ConnectionState>()
    val healthData = MutableLiveData<HealthData>()
    val discoveredDevices = MutableLiveData<List<BluetoothDevice>>()
    val batteryLevel = MutableLiveData<Int>()
    
    private val devices = mutableListOf<BluetoothDevice>()
    
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, FAILED
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        initializeBluetooth()
    }
    
    @SuppressLint("MissingPermission")
    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            connectionState.value = ConnectionState.FAILED
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }
        
        if (isScanning) {
            return
        }
        
        devices.clear()
        isScanning = true
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEALTH_THERMOMETER_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLOOD_PRESSURE_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setDeviceName("Health")
                .build(),
            ScanFilter.Builder()
                .setDeviceName("Fitbit")
                .build(),
            ScanFilter.Builder()
                .setDeviceName("Garmin")
                .build(),
            ScanFilter.Builder()
                .setDeviceName("Apple Watch")
                .build()
        )
        
        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        
        // Stop scanning after 10 seconds
        android.os.Handler(mainLooper).postDelayed({
            stopScan()
        }, 10000)
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        
        isScanning = false
        bluetoothLeScanner?.stopScan(scanCallback)
    }
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val device = result.device
            if (device != null && !devices.contains(device)) {
                devices.add(device)
                discoveredDevices.value = devices.toList()
                Log.d(TAG, "Found device: ${device.name ?: "Unknown"} - ${device.address}")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error: $errorCode")
            isScanning = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        currentDevice = device
        connectionState.value = ConnectionState.CONNECTING
        
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }
    
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        currentDevice = null
        isConnected = false
        connectionState.value = ConnectionState.DISCONNECTED
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    isConnected = true
                    connectionState.postValue(ConnectionState.CONNECTED)
                    
                    // Discover services
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    isConnected = false
                    connectionState.postValue(ConnectionState.DISCONNECTED)
                }
                else -> {
                    Log.d(TAG, "Connection state changed: $newState")
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                
                // Enable notifications for health services
                enableHeartRateNotifications(gatt)
                enableTemperatureNotifications(gatt)
                enableBloodPressureNotifications(gatt)
                enableBatteryNotifications(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            
            when (characteristic.uuid) {
                HEART_RATE_MEASUREMENT_UUID -> {
                    val heartRate = BluetoothParser.parseHeartRate(characteristic.value)
                    updateHealthData(heartRate = heartRate)
                }
                TEMPERATURE_MEASUREMENT_UUID -> {
                    val temperature = BluetoothParser.parseTemperature(characteristic.value)
                    updateHealthData(temperature = temperature)
                }
                BLOOD_PRESSURE_MEASUREMENT_UUID -> {
                    val (systolic, diastolic) = BluetoothParser.parseBloodPressure(characteristic.value)
                    updateHealthData(bloodPressureSystolic = systolic, bloodPressureDiastolic = diastolic)
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun enableHeartRateNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(HEART_RATE_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
        
        characteristic?.let {
            gatt.setCharacteristicNotification(it, true)
            
            val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
            descriptor?.let { desc ->
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun enableTemperatureNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(HEALTH_THERMOMETER_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(TEMPERATURE_MEASUREMENT_UUID)
        
        characteristic?.let {
            gatt.setCharacteristicNotification(it, true)
            
            val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
            descriptor?.let { desc ->
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun enableBloodPressureNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(BLOOD_PRESSURE_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BLOOD_PRESSURE_MEASUREMENT_UUID)
        
        characteristic?.let {
            gatt.setCharacteristicNotification(it, true)
            
            val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
            descriptor?.let { desc ->
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun enableBatteryNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(BATTERY_SERVICE_UUID)
        val batteryCharacteristic = service?.getCharacteristic(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB"))
        
        batteryCharacteristic?.let {
            gatt.readCharacteristic(it)
        }
    }
    
    private var currentHealthData = HealthData()
    
    private fun updateHealthData(
        heartRate: Int? = null,
        temperature: Float? = null,
        bloodPressureSystolic: Int? = null,
        bloodPressureDiastolic: Int? = null
    ) {
        currentHealthData = currentHealthData.copy(
            heartRate = heartRate ?: currentHealthData.heartRate,
            temperature = temperature ?: currentHealthData.temperature,
            bloodPressureSystolic = bloodPressureSystolic ?: currentHealthData.bloodPressureSystolic,
            bloodPressureDiastolic = bloodPressureDiastolic ?: currentHealthData.bloodPressureDiastolic,
            timestamp = System.currentTimeMillis(),
            deviceId = currentDevice?.address ?: "",
            deviceType = "smartwatch"
        )
        
        healthData.postValue(currentHealthData)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        stopScan()
    }
}