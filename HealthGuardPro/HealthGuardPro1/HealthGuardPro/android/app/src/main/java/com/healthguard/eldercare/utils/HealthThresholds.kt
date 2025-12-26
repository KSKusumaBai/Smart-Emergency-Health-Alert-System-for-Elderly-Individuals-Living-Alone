package com.healthguard.eldercare.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HealthThresholds {
    
    companion object {
        private const val PREFS_NAME = "health_thresholds"
        private const val KEY_CUSTOM_THRESHOLDS = "custom_thresholds"
        private const val KEY_USER_AGE = "user_age"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_MEDICAL_CONDITIONS = "medical_conditions"
    }
    
    data class ActivityThresholds(
        val heartRateMin: Int,
        val heartRateMax: Int,
        val bloodPressureMin: Int,
        val bloodPressureMax: Int,
        val temperatureMin: Float = 36.0f,
        val temperatureMax: Float = 37.5f
    )
    
    private val defaultThresholds = mapOf(
        "rest" to ActivityThresholds(
            heartRateMin = 60,
            heartRateMax = 100,
            bloodPressureMin = 90,
            bloodPressureMax = 140
        ),
        "light" to ActivityThresholds(
            heartRateMin = 80,
            heartRateMax = 120,
            bloodPressureMin = 95,
            bloodPressureMax = 150
        ),
        "moderate" to ActivityThresholds(
            heartRateMin = 100,
            heartRateMax = 140,
            bloodPressureMin = 100,
            bloodPressureMax = 160
        ),
        "vigorous" to ActivityThresholds(
            heartRateMin = 120,
            heartRateMax = 170,
            bloodPressureMin = 110,
            bloodPressureMax = 180
        ),
        "sleep" to ActivityThresholds(
            heartRateMin = 50,
            heartRateMax = 80,
            bloodPressureMin = 85,
            bloodPressureMax = 130
        )
    )
    
    private var customThresholds: MutableMap<String, ActivityThresholds> = mutableMapOf()
    private var userAge: Int = 65
    private var userGender: String = "unknown"
    private var medicalConditions: List<String> = emptyList()
    
    fun loadFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load user profile
        userAge = prefs.getInt(KEY_USER_AGE, 65)
        userGender = prefs.getString(KEY_USER_GENDER, "unknown") ?: "unknown"
        
        val conditionsJson = prefs.getString(KEY_MEDICAL_CONDITIONS, "[]")
        val conditionsType = object : TypeToken<List<String>>() {}.type
        medicalConditions = Gson().fromJson(conditionsJson, conditionsType) ?: emptyList()
        
        // Load custom thresholds
        val thresholdsJson = prefs.getString(KEY_CUSTOM_THRESHOLDS, "{}")
        val thresholdsType = object : TypeToken<Map<String, ActivityThresholds>>() {}.type
        customThresholds = Gson().fromJson(thresholdsJson, thresholdsType)?.toMutableMap() ?: mutableMapOf()
        
        // Apply age and gender adjustments
        applyProfileAdjustments()
    }
    
    fun saveToPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        editor.putInt(KEY_USER_AGE, userAge)
        editor.putString(KEY_USER_GENDER, userGender)
        editor.putString(KEY_MEDICAL_CONDITIONS, Gson().toJson(medicalConditions))
        editor.putString(KEY_CUSTOM_THRESHOLDS, Gson().toJson(customThresholds))
        
        editor.apply()
    }
    
    fun getThresholdsForActivity(activityLevel: String): ActivityThresholds {
        return customThresholds[activityLevel] ?: defaultThresholds[activityLevel] ?: defaultThresholds["rest"]!!
    }
    
    fun setCustomThresholds(activityLevel: String, thresholds: ActivityThresholds) {
        customThresholds[activityLevel] = thresholds
    }
    
    fun resetToDefaults() {
        customThresholds.clear()
        applyProfileAdjustments()
    }
    
    fun updateForUserProfile(age: Int, gender: String, conditions: List<String>) {
        userAge = age
        userGender = gender
        medicalConditions = conditions
        applyProfileAdjustments()
    }
    
    private fun applyProfileAdjustments() {
        // Age-based adjustments
        val ageMultiplier = when {
            userAge < 30 -> 1.0f
            userAge < 50 -> 0.95f
            userAge < 70 -> 0.9f
            userAge < 80 -> 0.85f
            else -> 0.8f
        }
        
        // Gender-based adjustments
        val genderAdjustment = when (userGender.lowercase()) {
            "female" -> 0.95f // Generally lower heart rate ranges for women
            "male" -> 1.0f
            else -> 0.975f
        }
        
        // Medical condition adjustments
        val conditionAdjustment = when {
            "hypertension" in medicalConditions -> 0.9f
            "heart_disease" in medicalConditions -> 0.85f
            "diabetes" in medicalConditions -> 0.95f
            else -> 1.0f
        }
        
        val totalMultiplier = ageMultiplier * genderAdjustment * conditionAdjustment
        
        // Apply adjustments to default thresholds
        defaultThresholds.forEach { (activity, threshold) ->
            if (activity !in customThresholds) {
                customThresholds[activity] = ActivityThresholds(
                    heartRateMin = (threshold.heartRateMin * totalMultiplier).toInt(),
                    heartRateMax = (threshold.heartRateMax * totalMultiplier).toInt(),
                    bloodPressureMin = threshold.bloodPressureMin,
                    bloodPressureMax = if ("hypertension" in medicalConditions) {
                        (threshold.bloodPressureMax * 0.9f).toInt()
                    } else {
                        threshold.bloodPressureMax
                    }
                )
            }
        }
    }
    
    fun getAllThresholds(): Map<String, ActivityThresholds> {
        return customThresholds.ifEmpty { defaultThresholds }
    }
    
    fun getAdjustedHeartRateRange(baseMin: Int, baseMax: Int, activityLevel: String): Pair<Int, Int> {
        val activityMultiplier = when (activityLevel) {
            "sleep" -> 0.7f
            "rest" -> 1.0f
            "light" -> 1.2f
            "moderate" -> 1.5f
            "vigorous" -> 2.0f
            else -> 1.0f
        }
        
        val adjustedMin = (baseMin * activityMultiplier).toInt()
        val adjustedMax = (baseMax * activityMultiplier).toInt()
        
        return Pair(adjustedMin, adjustedMax)
    }
    
    fun isHeartRateNormal(heartRate: Int, activityLevel: String): Boolean {
        val threshold = getThresholdsForActivity(activityLevel)
        return heartRate in threshold.heartRateMin..threshold.heartRateMax
    }
    
    fun isBloodPressureNormal(systolic: Int, diastolic: Int, activityLevel: String): Boolean {
        val threshold = getThresholdsForActivity(activityLevel)
        return systolic >= threshold.bloodPressureMin && 
               systolic <= threshold.bloodPressureMax &&
               diastolic >= 60 && diastolic <= 100
    }
    
    fun isTemperatureNormal(temperature: Float): Boolean {
        return temperature >= 36.0f && temperature <= 37.5f
    }
    
    fun getHealthRiskLevel(
        heartRate: Int,
        systolic: Int,
        diastolic: Int,
        temperature: Float,
        activityLevel: String
    ): String {
        val threshold = getThresholdsForActivity(activityLevel)
        
        val heartRateRisk = when {
            heartRate < threshold.heartRateMin - 20 || heartRate > threshold.heartRateMax + 30 -> "critical"
            heartRate < threshold.heartRateMin - 10 || heartRate > threshold.heartRateMax + 20 -> "high"
            heartRate < threshold.heartRateMin || heartRate > threshold.heartRateMax -> "medium"
            else -> "low"
        }
        
        val bpRisk = when {
            systolic > 180 || diastolic > 110 -> "critical"
            systolic > 160 || diastolic > 100 -> "high"
            systolic > threshold.bloodPressureMax || diastolic > 90 -> "medium"
            else -> "low"
        }
        
        val tempRisk = when {
            temperature > 40.0f || temperature < 35.0f -> "critical"
            temperature > 38.5f || temperature < 35.5f -> "high"
            temperature > 37.5f || temperature < 36.0f -> "medium"
            else -> "low"
        }
        
        val risks = listOf(heartRateRisk, bpRisk, tempRisk)
        
        return when {
            "critical" in risks -> "critical"
            "high" in risks -> "high"
            "medium" in risks -> "medium"
            else -> "low"
        }
    }
    
    fun getRecommendationsForActivity(activityLevel: String): List<String> {
        return when (activityLevel) {
            "sleep" -> listOf(
                "Ensure 7-9 hours of quality sleep",
                "Keep bedroom cool and dark",
                "Avoid screens before bedtime"
            )
            "rest" -> listOf(
                "Practice relaxation techniques",
                "Stay hydrated",
                "Monitor stress levels"
            )
            "light" -> listOf(
                "Maintain steady breathing",
                "Listen to your body",
                "Stay hydrated"
            )
            "moderate" -> listOf(
                "Monitor heart rate regularly",
                "Take breaks if needed",
                "Ensure proper warm-up"
            )
            "vigorous" -> listOf(
                "Closely monitor vital signs",
                "Stop if experiencing chest pain",
                "Cool down gradually"
            )
            else -> listOf("Follow general health guidelines")
        }
    }
}