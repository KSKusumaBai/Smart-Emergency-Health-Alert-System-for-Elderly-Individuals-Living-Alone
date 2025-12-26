package com.healthguard.eldercare.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {
    
    companion object {
        const val BLUETOOTH_PERMISSION_REQUEST = 1001
        const val LOCATION_PERMISSION_REQUEST = 1002
        const val SMS_CALL_PERMISSION_REQUEST = 1003
        const val AUDIO_CAMERA_PERMISSION_REQUEST = 1004
        
        val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val COMMUNICATION_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
        )
        
        val MEDIA_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }
    
    fun hasBluetoothPermissions(): Boolean {
        return BLUETOOTH_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasCommunicationPermissions(): Boolean {
        return COMMUNICATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasMediaPermissions(): Boolean {
        return MEDIA_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun requestBluetoothPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            BLUETOOTH_PERMISSIONS,
            BLUETOOTH_PERMISSION_REQUEST
        )
    }
    
    fun requestLocationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            LOCATION_PERMISSIONS,
            LOCATION_PERMISSION_REQUEST
        )
    }
    
    fun requestCommunicationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            COMMUNICATION_PERMISSIONS,
            SMS_CALL_PERMISSION_REQUEST
        )
    }
    
    fun requestMediaPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            MEDIA_PERMISSIONS,
            AUDIO_CAMERA_PERMISSION_REQUEST
        )
    }
    
    fun hasAllRequiredPermissions(): Boolean {
        return hasBluetoothPermissions() && 
               hasLocationPermissions() && 
               hasCommunicationPermissions()
    }
    
    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        if (!hasBluetoothPermissions()) {
            missing.addAll(BLUETOOTH_PERMISSIONS.filter { 
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
            })
        }
        
        if (!hasLocationPermissions()) {
            missing.addAll(LOCATION_PERMISSIONS.filter { 
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
            })
        }
        
        if (!hasCommunicationPermissions()) {
            missing.addAll(COMMUNICATION_PERMISSIONS.filter { 
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
            })
        }
        
        return missing
    }
    
    fun requestAllMissingPermissions(activity: Activity) {
        val missing = getMissingPermissions()
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missing.toTypedArray(),
                1000
            )
        }
    }
}