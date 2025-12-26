package com.healthguard.eldercare.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val createdAt: Long = 0L,
    val lastLoginAt: Long = 0L,
    val profileImageUrl: String = ""
) : Parcelable