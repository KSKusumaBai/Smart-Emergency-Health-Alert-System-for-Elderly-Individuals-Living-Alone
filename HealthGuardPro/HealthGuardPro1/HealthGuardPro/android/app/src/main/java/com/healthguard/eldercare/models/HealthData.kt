package com.healthguard.eldercare.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class HealthData(
    val id: String = "",
    val userId: String = "",
    val heartRate: Int = 0,
    val bloodPressureSystolic: Int = 0,
    val bloodPressureDiastolic: Int = 0,
    val temperature: Float = 0.0f,
    val oxygenSaturation: Int = 0,
    val activityLevel: String = "rest", // rest, light, moderate, vigorous, sleep
    val isAbnormal: Boolean = false,
    val analysisResult: HealthAnalysisResult? = null,
    val location: Location? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val deviceType: String = "" // smartwatch, manual, etc.
) : Parcelable

@Parcelize
data class HealthAnalysisResult(
    val status: String = "normal", // normal, abnormal, critical
    val riskLevel: String = "low", // low, medium, high, critical
    val message: String = "",
    val recommendations: List<String> = emptyList(),
    val confidenceScore: Float = 0.0f
) : Parcelable

@Parcelize
data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable