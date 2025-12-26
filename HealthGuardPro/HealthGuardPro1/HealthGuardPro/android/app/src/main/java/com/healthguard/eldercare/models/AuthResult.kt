package com.healthguard.eldercare.models

data class AuthResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val user: User? = null
)