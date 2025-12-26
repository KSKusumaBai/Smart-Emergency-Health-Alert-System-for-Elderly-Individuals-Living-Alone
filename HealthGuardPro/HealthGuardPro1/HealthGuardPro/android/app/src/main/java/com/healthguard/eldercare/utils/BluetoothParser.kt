package com.healthguard.eldercare.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

object BluetoothParser {
    
    /**
     * Parse heart rate data from Bluetooth characteristic
     * Based on Bluetooth SIG Heart Rate Measurement specification
     */
    fun parseHeartRate(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.get().toInt() and 0xFF
        
        return if (flags and 0x01 == 0) {
            // Heart rate format is UINT8
            buffer.get().toInt() and 0xFF
        } else {
            // Heart rate format is UINT16
            buffer.short.toInt() and 0xFFFF
        }
    }
    
    /**
     * Parse temperature data from Bluetooth characteristic
     * Based on Bluetooth SIG Health Thermometer specification
     */
    fun parseTemperature(data: ByteArray): Float {
        if (data.size < 5) return 0.0f
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.get().toInt() and 0xFF
        
        // Temperature measurement value (IEEE-11073 32-bit FLOAT)
        val tempValue = buffer.int
        
        // Extract mantissa and exponent
        val mantissa = tempValue and 0x00FFFFFF
        val exponent = (tempValue shr 24) and 0xFF
        
        // Convert to actual temperature value
        val temperature = mantissa * 10.0.pow(exponent.toByte().toDouble()).toFloat()
        
        // Check if temperature is in Fahrenheit (bit 0 of flags)
        return if (flags and 0x01 == 0) {
            // Celsius
            temperature
        } else {
            // Fahrenheit - convert to Celsius
            (temperature - 32) * 5 / 9
        }
    }
    
    /**
     * Parse blood pressure data from Bluetooth characteristic
     * Based on Bluetooth SIG Blood Pressure specification
     */
    fun parseBloodPressure(data: ByteArray): Pair<Int, Int> {
        if (data.size < 7) return Pair(0, 0)
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.get().toInt() and 0xFF
        
        // Blood pressure unit (bit 0: 0 = mmHg, 1 = kPa)
        val isKPa = (flags and 0x01) != 0
        
        // Systolic pressure (IEEE-11073 16-bit SFLOAT)
        val systolicRaw = buffer.short.toInt() and 0xFFFF
        val systolic = parseSFloat(systolicRaw)
        
        // Diastolic pressure (IEEE-11073 16-bit SFLOAT)
        val diastolicRaw = buffer.short.toInt() and 0xFFFF
        val diastolic = parseSFloat(diastolicRaw)
        
        // Convert kPa to mmHg if necessary
        return if (isKPa) {
            Pair((systolic * 7.50062).toInt(), (diastolic * 7.50062).toInt())
        } else {
            Pair(systolic.toInt(), diastolic.toInt())
        }
    }
    
    /**
     * Parse IEEE-11073 16-bit SFLOAT
     */
    private fun parseSFloat(value: Int): Float {
        val mantissa = value and 0x0FFF
        val exponent = (value shr 12) and 0x0F
        
        // Handle special values
        when (exponent) {
            0x0F -> return Float.NaN // NaN
            0x0E -> return Float.POSITIVE_INFINITY // +INFINITY
            0x0D -> return Float.NEGATIVE_INFINITY // -INFINITY
            0x0C -> return 0.0f // Reserved
        }
        
        // Convert to signed values
        val signedMantissa = if (mantissa >= 0x0800) mantissa - 0x1000 else mantissa
        val signedExponent = if (exponent >= 0x08) exponent - 0x10 else exponent
        
        return signedMantissa * 10.0.pow(signedExponent.toDouble()).toFloat()
    }
    
    /**
     * Parse battery level from Bluetooth characteristic
     */
    fun parseBatteryLevel(data: ByteArray): Int {
        return if (data.isNotEmpty()) {
            data[0].toInt() and 0xFF
        } else {
            0
        }
    }
    
    /**
     * Parse oxygen saturation (SpO2) from Bluetooth characteristic
     * Based on Bluetooth SIG Pulse Oximeter specification
     */
    fun parseOxygenSaturation(data: ByteArray): Int {
        if (data.size < 3) return 0
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.get().toInt() and 0xFF
        
        // SpO2 value (IEEE-11073 16-bit SFLOAT)
        val spo2Raw = buffer.short.toInt() and 0xFFFF
        val spo2 = parseSFloat(spo2Raw)
        
        return spo2.toInt()
    }
    
    /**
     * Parse step count from Bluetooth characteristic
     */
    fun parseStepCount(data: ByteArray): Int {
        if (data.size < 4) return 0
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.int
    }
    
    /**
     * Parse activity level from heart rate and movement data
     */
    fun determineActivityLevel(heartRate: Int, stepCount: Int = 0): String {
        return when {
            heartRate < 60 -> "sleep"
            heartRate in 60..80 -> "rest"
            heartRate in 81..100 -> "light"
            heartRate in 101..140 -> "moderate"
            heartRate > 140 -> "vigorous"
            else -> "unknown"
        }
    }
    
    /**
     * Validate health data ranges
     */
    fun validateHealthData(heartRate: Int?, bloodPressure: Pair<Int, Int>?, temperature: Float?): Boolean {
        heartRate?.let {
            if (it < 30 || it > 220) return false
        }
        
        bloodPressure?.let { (systolic, diastolic) ->
            if (systolic < 70 || systolic > 300 || diastolic < 40 || diastolic > 200) return false
        }
        
        temperature?.let {
            if (it < 30.0f || it > 45.0f) return false
        }
        
        return true
    }
}