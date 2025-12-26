package com.healthguard.eldercare.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import com.healthguard.eldercare.models.HealthAnalysisResult
import com.healthguard.eldercare.models.HealthData
import com.healthguard.eldercare.utils.HealthThresholds
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class HealthAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "HealthAnalyzer"
        private const val MODEL_FILE = "health_anomaly_model.tflite"
        
        // Input features for the ML model
        private const val INPUT_SIZE = 8 // heart_rate, bp_sys, bp_dia, temp, age, gender, activity, time_of_day
        private const val OUTPUT_SIZE = 3 // normal, abnormal, critical
    }
    
    private var interpreter: Interpreter? = null
    private val thresholds = HealthThresholds()
    private val healthHistory = mutableListOf<HealthData>()
    
    init {
        initializeModel()
    }
    
    private fun initializeModel() {
        try {
            // Load the TensorFlow Lite model
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(true) // Use Neural Networks API if available
            }
            interpreter = Interpreter(modelBuffer, options)
            
            Log.d(TAG, "TensorFlow Lite model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load TensorFlow Lite model, using rule-based analysis", e)
            interpreter = null
        }
    }
    
    fun analyzeHealthData(
        healthData: HealthData,
        userAge: Int = 65,
        userGender: String = "unknown"
    ): HealthAnalysisResult {
        // Add to history for trend analysis
        healthHistory.add(healthData)
        if (healthHistory.size > 100) {
            healthHistory.removeAt(0)
        }
        
        return if (interpreter != null) {
            performMLAnalysis(healthData, userAge, userGender)
        } else {
            performRuleBasedAnalysis(healthData)
        }
    }
    
    private fun performMLAnalysis(
        healthData: HealthData,
        userAge: Int,
        userGender: String
    ): HealthAnalysisResult {
        return try {
            // Prepare input data
            val inputArray = prepareInputForML(healthData, userAge, userGender)
            val inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
                inputArray.forEach { putFloat(it) }
                rewind()
            }
            
            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(OUTPUT_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            
            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Parse results
            outputBuffer.rewind()
            val probabilities = FloatArray(OUTPUT_SIZE) { outputBuffer.float }
            
            interpretMLResults(probabilities, healthData)
        } catch (e: Exception) {
            Log.e(TAG, "ML analysis failed, falling back to rule-based", e)
            performRuleBasedAnalysis(healthData)
        }
    }
    
    private fun prepareInputForML(
        healthData: HealthData,
        userAge: Int,
        userGender: String
    ): FloatArray {
        val activityLevel = when (healthData.activityLevel) {
            "rest" -> 0.0f
            "light" -> 1.0f
            "moderate" -> 2.0f
            "vigorous" -> 3.0f
            "sleep" -> 4.0f
            else -> 0.0f
        }
        
        val gender = when (userGender.lowercase()) {
            "male" -> 0.0f
            "female" -> 1.0f
            else -> 0.5f
        }
        
        val timeOfDay = (System.currentTimeMillis() % (24 * 60 * 60 * 1000)) / (24 * 60 * 60 * 1000).toFloat()
        
        return floatArrayOf(
            normalizeHeartRate(healthData.heartRate.toFloat()),
            normalizeBloodPressure(healthData.bloodPressureSystolic.toFloat()),
            normalizeBloodPressure(healthData.bloodPressureDiastolic.toFloat()),
            normalizeTemperature(healthData.temperature),
            normalizeAge(userAge.toFloat()),
            gender,
            activityLevel,
            timeOfDay
        )
    }
    
    private fun normalizeHeartRate(heartRate: Float): Float {
        return (heartRate - 60f) / 100f // Normalize to roughly 0-1 range
    }
    
    private fun normalizeBloodPressure(bp: Float): Float {
        return (bp - 80f) / 60f // Normalize to roughly 0-1 range
    }
    
    private fun normalizeTemperature(temp: Float): Float {
        return (temp - 36f) / 6f // Normalize to roughly 0-1 range
    }
    
    private fun normalizeAge(age: Float): Float {
        return age / 100f // Normalize to 0-1 range
    }
    
    private fun interpretMLResults(
        probabilities: FloatArray,
        healthData: HealthData
    ): HealthAnalysisResult {
        val normalProb = probabilities[0]
        val abnormalProb = probabilities[1]
        val criticalProb = probabilities[2]
        
        val maxProb = probabilities.maxOrNull() ?: 0f
        val confidenceScore = maxProb
        
        val (status, riskLevel, message) = when {
            criticalProb > 0.7f -> Triple("critical", "critical", "Critical health reading detected. Immediate medical attention recommended.")
            abnormalProb > 0.6f -> Triple("abnormal", "high", "Abnormal health pattern detected. Please monitor closely.")
            abnormalProb > 0.4f -> Triple("abnormal", "medium", "Slightly elevated health readings detected.")
            else -> Triple("normal", "low", "Health readings are within normal range.")
        }
        
        val recommendations = generateRecommendations(status, healthData)
        
        return HealthAnalysisResult(
            status = status,
            riskLevel = riskLevel,
            message = message,
            recommendations = recommendations,
            confidenceScore = confidenceScore
        )
    }
    
    private fun performRuleBasedAnalysis(healthData: HealthData): HealthAnalysisResult {
        val activityThresholds = thresholds.getThresholdsForActivity(healthData.activityLevel)
        
        val heartRateStatus = analyzeHeartRate(healthData.heartRate, activityThresholds)
        val bloodPressureStatus = analyzeBloodPressure(
            healthData.bloodPressureSystolic,
            healthData.bloodPressureDiastolic,
            activityThresholds
        )
        val temperatureStatus = analyzeTemperature(healthData.temperature)
        
        // Trend analysis
        val trendAnalysis = analyzeTrends()
        
        // Combine results
        val overallStatus = determineOverallStatus(
            heartRateStatus,
            bloodPressureStatus,
            temperatureStatus,
            trendAnalysis
        )
        
        val riskLevel = determineRiskLevel(overallStatus, trendAnalysis)
        val message = generateMessage(overallStatus, heartRateStatus, bloodPressureStatus, temperatureStatus)
        val recommendations = generateRecommendations(overallStatus, healthData)
        
        return HealthAnalysisResult(
            status = overallStatus,
            riskLevel = riskLevel,
            message = message,
            recommendations = recommendations,
            confidenceScore = 0.85f
        )
    }
    
    private fun analyzeHeartRate(heartRate: Int, thresholds: HealthThresholds.ActivityThresholds): String {
        return when {
            heartRate < thresholds.heartRateMin -> "low"
            heartRate > thresholds.heartRateMax -> "high"
            heartRate in (thresholds.heartRateMin + 10)..(thresholds.heartRateMax - 10) -> "normal"
            else -> "borderline"
        }
    }
    
    private fun analyzeBloodPressure(
        systolic: Int,
        diastolic: Int,
        thresholds: HealthThresholds.ActivityThresholds
    ): String {
        return when {
            systolic < thresholds.bloodPressureMin || diastolic < 60 -> "low"
            systolic > thresholds.bloodPressureMax || diastolic > 100 -> "high"
            systolic > 140 || diastolic > 90 -> "elevated"
            else -> "normal"
        }
    }
    
    private fun analyzeTemperature(temperature: Float): String {
        return when {
            temperature < 36.0f -> "low"
            temperature > 37.5f -> "high"
            temperature > 38.0f -> "fever"
            else -> "normal"
        }
    }
    
    private fun analyzeTrends(): Map<String, String> {
        if (healthHistory.size < 5) {
            return mapOf("trend" to "insufficient_data")
        }
        
        val recent = healthHistory.takeLast(5)
        val heartRates = recent.map { it.heartRate }
        val systolicBP = recent.map { it.bloodPressureSystolic }
        
        val heartRateTrend = calculateTrend(heartRates.map { it.toFloat() })
        val bpTrend = calculateTrend(systolicBP.map { it.toFloat() })
        
        return mapOf(
            "heart_rate_trend" to heartRateTrend,
            "blood_pressure_trend" to bpTrend,
            "overall_trend" to if (heartRateTrend == "increasing" || bpTrend == "increasing") "concerning" else "stable"
        )
    }
    
    private fun calculateTrend(values: List<Float>): String {
        if (values.size < 3) return "stable"
        
        val slope = calculateSlope(values)
        val threshold = values.average() * 0.1 // 10% threshold
        
        return when {
            slope > threshold -> "increasing"
            slope < -threshold -> "decreasing"
            else -> "stable"
        }
    }
    
    private fun calculateSlope(values: List<Float>): Float {
        val n = values.size
        val x = (0 until n).map { it.toFloat() }
        val xMean = x.average().toFloat()
        val yMean = values.average().toFloat()
        
        val numerator = x.zip(values) { xi, yi -> (xi - xMean) * (yi - yMean) }.sum()
        val denominator = x.map { xi -> (xi - xMean) * (xi - xMean) }.sum()
        
        return if (denominator != 0f) numerator / denominator else 0f
    }
    
    private fun determineOverallStatus(
        heartRateStatus: String,
        bloodPressureStatus: String,
        temperatureStatus: String,
        trendAnalysis: Map<String, String>
    ): String {
        val criticalConditions = listOf("high", "low", "fever")
        val abnormalConditions = listOf("elevated", "borderline")
        
        val statuses = listOf(heartRateStatus, bloodPressureStatus, temperatureStatus)
        
        return when {
            statuses.any { it in criticalConditions } -> "critical"
            statuses.any { it in abnormalConditions } -> "abnormal"
            trendAnalysis["overall_trend"] == "concerning" -> "abnormal"
            else -> "normal"
        }
    }
    
    private fun determineRiskLevel(status: String, trendAnalysis: Map<String, String>): String {
        return when (status) {
            "critical" -> "critical"
            "abnormal" -> if (trendAnalysis["overall_trend"] == "concerning") "high" else "medium"
            else -> "low"
        }
    }
    
    private fun generateMessage(
        overallStatus: String,
        heartRateStatus: String,
        bloodPressureStatus: String,
        temperatureStatus: String
    ): String {
        return when (overallStatus) {
            "critical" -> {
                val issues = mutableListOf<String>()
                if (heartRateStatus in listOf("high", "low")) issues.add("heart rate")
                if (bloodPressureStatus in listOf("high", "low")) issues.add("blood pressure")
                if (temperatureStatus in listOf("high", "low", "fever")) issues.add("temperature")
                
                "Critical health alert: ${issues.joinToString(", ")} reading(s) are outside safe ranges. Seek immediate medical attention."
            }
            "abnormal" -> {
                "Health readings show some abnormal patterns. Please monitor closely and consider consulting with your healthcare provider."
            }
            else -> "All health readings are within normal ranges. Keep up the good work!"
        }
    }
    
    private fun generateRecommendations(status: String, healthData: HealthData): List<String> {
        val recommendations = mutableListOf<String>()
        
        when (status) {
            "critical" -> {
                recommendations.add("Contact emergency services immediately")
                recommendations.add("Do not engage in physical activity")
                recommendations.add("Notify emergency contacts")
            }
            "abnormal" -> {
                recommendations.add("Rest and avoid strenuous activities")
                recommendations.add("Monitor readings more frequently")
                recommendations.add("Contact your healthcare provider")
                
                if (healthData.heartRate > 120) {
                    recommendations.add("Practice deep breathing exercises")
                }
                if (healthData.temperature > 37.5f) {
                    recommendations.add("Stay hydrated and rest in a cool environment")
                }
            }
            else -> {
                recommendations.add("Continue regular monitoring")
                recommendations.add("Maintain healthy lifestyle habits")
                if (healthData.activityLevel == "rest") {
                    recommendations.add("Consider light physical activity if feeling well")
                }
            }
        }
        
        return recommendations
    }
    
    fun updateUserProfile(age: Int, gender: String, medicalConditions: List<String>) {
        // Update thresholds based on user profile
        thresholds.updateForUserProfile(age, gender, medicalConditions)
    }
    
    fun getHealthTrend(metric: String, days: Int = 7): List<Float> {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000)
        val recentData = healthHistory.filter { it.timestamp > cutoffTime }
        
        return when (metric) {
            "heart_rate" -> recentData.map { it.heartRate.toFloat() }
            "systolic_bp" -> recentData.map { it.bloodPressureSystolic.toFloat() }
            "diastolic_bp" -> recentData.map { it.bloodPressureDiastolic.toFloat() }
            "temperature" -> recentData.map { it.temperature }
            else -> emptyList()
        }
    }
    
    fun cleanup() {
        interpreter?.close()
        interpreter = null
    }
}