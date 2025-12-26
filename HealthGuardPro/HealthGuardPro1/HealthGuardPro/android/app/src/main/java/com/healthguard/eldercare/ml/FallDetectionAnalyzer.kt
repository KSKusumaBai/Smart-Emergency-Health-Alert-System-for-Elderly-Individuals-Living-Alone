package com.healthguard.eldercare.ml

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.healthguard.eldercare.models.AccelerometerData
import com.healthguard.eldercare.models.EmergencyAlert
import kotlin.math.sqrt

class FallDetectionAnalyzer(
    private val context: Context,
    private val onFallDetected: (AccelerometerData) -> Unit
) : SensorEventListener {
    
    companion object {
        private const val TAG = "FallDetectionAnalyzer"
        
        // Fall detection thresholds
        private const val FALL_THRESHOLD = 15.0f // m/s²
        private const val IMPACT_THRESHOLD = 25.0f // m/s²
        private const val STILLNESS_THRESHOLD = 2.0f // m/s²
        private const val STILLNESS_DURATION = 3000L // 3 seconds
        
        // Moving average window size
        private const val WINDOW_SIZE = 20
        
        // Orientation change threshold
        private const val ORIENTATION_CHANGE_THRESHOLD = 0.8f
    }
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    private val accelerometerData = mutableListOf<AccelerometerData>()
    private val magnitudeHistory = mutableListOf<Float>()
    
    private var isMonitoring = false
    private var lastFallDetectionTime = 0L
    private var potentialFallStartTime = 0L
    private var isInPotentialFall = false
    
    // Baseline values for user calibration
    private var baselineAcceleration = 9.8f
    private var userActivityBaseline = mutableMapOf<String, Float>()
    
    fun startMonitoring() {
        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available")
            return
        }
        
        if (!isMonitoring) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
            gyroscope?.let { 
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            }
            isMonitoring = true
            Log.d(TAG, "Fall detection monitoring started")
        }
    }
    
    fun stopMonitoring() {
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
            isMonitoring = false
            Log.d(TAG, "Fall detection monitoring stopped")
        }
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometerData(event)
            Sensor.TYPE_GYROSCOPE -> processGyroscopeData(event)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }
    
    private fun processAccelerometerData(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val timestamp = System.currentTimeMillis()
        
        // Calculate magnitude (total acceleration)
        val magnitude = sqrt(x * x + y * y + z * z)
        
        val accelerometerReading = AccelerometerData(x, y, z, magnitude, timestamp)
        
        // Add to history
        accelerometerData.add(accelerometerReading)
        magnitudeHistory.add(magnitude)
        
        // Maintain sliding window
        if (accelerometerData.size > WINDOW_SIZE) {
            accelerometerData.removeAt(0)
            magnitudeHistory.removeAt(0)
        }
        
        // Analyze for fall patterns
        if (accelerometerData.size >= WINDOW_SIZE) {
            analyzeFallPattern(accelerometerReading)
        }
    }
    
    private fun processGyroscopeData(event: SensorEvent) {
        // Process gyroscope data for orientation changes
        val rotationX = event.values[0]
        val rotationY = event.values[1]
        val rotationZ = event.values[2]
        
        val rotationMagnitude = sqrt(rotationX * rotationX + rotationY * rotationY + rotationZ * rotationZ)
        
        // Detect significant orientation change (potential fall indicator)
        if (rotationMagnitude > ORIENTATION_CHANGE_THRESHOLD && isInPotentialFall) {
            Log.d(TAG, "Significant orientation change detected during potential fall")
        }
    }
    
    private fun analyzeFallPattern(currentReading: AccelerometerData) {
        val currentTime = System.currentTimeMillis()
        
        // Prevent multiple fall detections within 30 seconds
        if (currentTime - lastFallDetectionTime < 30000) {
            return
        }
        
        // Phase 1: Free fall detection (low acceleration)
        if (detectFreeFall(currentReading)) {
            if (!isInPotentialFall) {
                isInPotentialFall = true
                potentialFallStartTime = currentTime
                Log.d(TAG, "Potential fall detected - free fall phase")
            }
        }
        
        // Phase 2: Impact detection (high acceleration)
        if (isInPotentialFall && detectImpact(currentReading)) {
            Log.d(TAG, "Impact detected during potential fall")
            
            // Phase 3: Stillness detection (low movement after impact)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (detectStillness()) {
                    confirmFallDetection(currentReading)
                } else {
                    resetFallDetection()
                }
            }, STILLNESS_DURATION)
        }
        
        // Reset if potential fall phase has lasted too long without impact
        if (isInPotentialFall && currentTime - potentialFallStartTime > 5000) {
            resetFallDetection()
        }
    }
    
    private fun detectFreeFall(reading: AccelerometerData): Boolean {
        // Free fall: sudden drop in acceleration magnitude
        val recentMagnitudes = magnitudeHistory.takeLast(5)
        val average = recentMagnitudes.average().toFloat()
        
        return reading.magnitude < FALL_THRESHOLD && average > baselineAcceleration * 0.8f
    }
    
    private fun detectImpact(reading: AccelerometerData): Boolean {
        // Impact: sudden spike in acceleration
        return reading.magnitude > IMPACT_THRESHOLD
    }
    
    private fun detectStillness(): Boolean {
        // Check if recent readings show minimal movement
        val recentMagnitudes = magnitudeHistory.takeLast(10)
        if (recentMagnitudes.size < 10) return false
        
        val variance = calculateVariance(recentMagnitudes)
        return variance < STILLNESS_THRESHOLD
    }
    
    private fun calculateVariance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        val squaredDifferences = values.map { (it - mean) * (it - mean) }
        return squaredDifferences.average().toFloat()
    }
    
    private fun confirmFallDetection(accelerometerData: AccelerometerData) {
        Log.w(TAG, "FALL DETECTED!")
        
        lastFallDetectionTime = System.currentTimeMillis()
        resetFallDetection()
        
        // Trigger fall detection callback
        onFallDetected(accelerometerData)
    }
    
    private fun resetFallDetection() {
        isInPotentialFall = false
        potentialFallStartTime = 0L
    }
    
    fun calibrateForUser(activityLevel: String, duration: Long = 30000L) {
        Log.d(TAG, "Starting calibration for activity: $activityLevel")
        
        val calibrationData = mutableListOf<Float>()
        val startTime = System.currentTimeMillis()
        
        val calibrationRunnable = object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() - startTime < duration) {
                    if (magnitudeHistory.isNotEmpty()) {
                        calibrationData.add(magnitudeHistory.last())
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 100)
                } else {
                    // Calculate baseline for this activity
                    if (calibrationData.isNotEmpty()) {
                        val baseline = calibrationData.average().toFloat()
                        userActivityBaseline[activityLevel] = baseline
                        Log.d(TAG, "Calibration complete for $activityLevel: baseline = $baseline")
                    }
                }
            }
        }
        
        android.os.Handler(android.os.Looper.getMainLooper()).post(calibrationRunnable)
    }
    
    fun adjustSensitivityForUser(age: Int, medicalConditions: List<String>) {
        // Adjust thresholds based on user profile
        var sensitivityMultiplier = 1.0f
        
        // Age-based adjustments
        when {
            age > 80 -> sensitivityMultiplier *= 0.8f // More sensitive for elderly
            age > 70 -> sensitivityMultiplier *= 0.9f
            age < 50 -> sensitivityMultiplier *= 1.2f // Less sensitive for younger adults
        }
        
        // Medical condition adjustments
        if ("balance_issues" in medicalConditions) {
            sensitivityMultiplier *= 0.7f // More sensitive
        }
        if ("mobility_issues" in medicalConditions) {
            sensitivityMultiplier *= 0.8f
        }
        if ("osteoporosis" in medicalConditions) {
            sensitivityMultiplier *= 0.7f // Fall risk is higher
        }
        
        // Update baseline acceleration
        baselineAcceleration = 9.8f * sensitivityMultiplier
        
        Log.d(TAG, "Fall detection sensitivity adjusted: multiplier = $sensitivityMultiplier")
    }
    
    fun getFallRiskScore(): Float {
        // Calculate fall risk based on recent movement patterns
        if (magnitudeHistory.size < WINDOW_SIZE) return 0.0f
        
        val recentVariance = calculateVariance(magnitudeHistory.takeLast(WINDOW_SIZE))
        val baselineVariance = 2.0f // Normal movement variance
        
        return when {
            recentVariance > baselineVariance * 3 -> 0.8f // High risk - erratic movement
            recentVariance > baselineVariance * 2 -> 0.5f // Medium risk
            recentVariance < baselineVariance * 0.5 -> 0.3f // Low movement - potential weakness
            else -> 0.1f // Normal movement
        }
    }
    
    fun getMovementPattern(): String {
        if (magnitudeHistory.size < 10) return "insufficient_data"
        
        val recent = magnitudeHistory.takeLast(10)
        val variance = calculateVariance(recent)
        val average = recent.average().toFloat()
        
        return when {
            variance > 5.0f -> "erratic"
            variance < 0.5f && average < 5.0f -> "still"
            average > 15.0f -> "active"
            else -> "normal"
        }
    }
}