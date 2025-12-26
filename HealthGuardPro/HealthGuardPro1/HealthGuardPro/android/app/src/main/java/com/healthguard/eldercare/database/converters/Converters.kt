package com.healthguard.eldercare.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthguard.eldercare.models.EmergencyContact
import com.healthguard.eldercare.models.HealthAnalysisResult
import com.healthguard.eldercare.models.Location

class Converters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromEmergencyContactList(contacts: List<EmergencyContact>?): String? {
        return gson.toJson(contacts)
    }
    
    @TypeConverter
    fun toEmergencyContactList(contactsString: String?): List<EmergencyContact>? {
        if (contactsString == null) return null
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        return gson.fromJson(contactsString, type)
    }
    
    @TypeConverter
    fun fromHealthAnalysisResult(result: HealthAnalysisResult?): String? {
        return gson.toJson(result)
    }
    
    @TypeConverter
    fun toHealthAnalysisResult(resultString: String?): HealthAnalysisResult? {
        if (resultString == null) return null
        return gson.fromJson(resultString, HealthAnalysisResult::class.java)
    }
    
    @TypeConverter
    fun fromLocation(location: Location?): String? {
        return gson.toJson(location)
    }
    
    @TypeConverter
    fun toLocation(locationString: String?): Location? {
        if (locationString == null) return null
        return gson.fromJson(locationString, Location::class.java)
    }
    
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return gson.toJson(list)
    }
    
    @TypeConverter
    fun toStringList(listString: String?): List<String>? {
        if (listString == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(listString, type)
    }
}