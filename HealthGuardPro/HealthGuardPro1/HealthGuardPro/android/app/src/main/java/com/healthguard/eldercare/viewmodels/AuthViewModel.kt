package com.healthguard.eldercare.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.healthguard.eldercare.models.AuthResult
import com.healthguard.eldercare.models.User

class AuthViewModel : ViewModel() {
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _authResult = MutableLiveData<AuthResult?>()
    val authResult: LiveData<AuthResult?> = _authResult
    
    fun signIn(email: String, password: String) {
        _isLoading.value = true
        
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                
                if (task.isSuccessful) {
                    _authResult.value = AuthResult(isSuccess = true)
                } else {
                    _authResult.value = AuthResult(
                        isSuccess = false,
                        errorMessage = task.exception?.message ?: "Sign in failed"
                    )
                }
            }
    }
    
    fun signUp(email: String, password: String, name: String) {
        _isLoading.value = true
        
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Create user profile in Firestore
                    val user = auth.currentUser
                    user?.let { firebaseUser ->
                        val userProfile = User(
                            uid = firebaseUser.uid,
                            name = name,
                            email = email,
                            createdAt = System.currentTimeMillis()
                        )
                        
                        saveUserProfile(userProfile)
                    }
                } else {
                    _isLoading.value = false
                    _authResult.value = AuthResult(
                        isSuccess = false,
                        errorMessage = task.exception?.message ?: "Sign up failed"
                    )
                }
            }
    }
    
    private fun saveUserProfile(user: User) {
        firestore.collection("users")
            .document(user.uid)
            .set(user)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                
                if (task.isSuccessful) {
                    _authResult.value = AuthResult(isSuccess = true)
                } else {
                    _authResult.value = AuthResult(
                        isSuccess = false,
                        errorMessage = "Failed to create user profile"
                    )
                }
            }
    }
    
    fun signOut() {
        auth.signOut()
    }
    
    fun getCurrentUser() = auth.currentUser
}