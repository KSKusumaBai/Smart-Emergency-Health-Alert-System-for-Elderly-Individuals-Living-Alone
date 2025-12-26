package com.healthguard.eldercare.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.healthguard.eldercare.R
import com.healthguard.eldercare.databinding.ActivityAuthBinding
import com.healthguard.eldercare.utils.LoadingDialog
import com.healthguard.eldercare.viewmodels.AuthViewModel

class AuthActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var authViewModel: AuthViewModel
    private lateinit var loadingDialog: LoadingDialog
    private var isLoginMode = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Initialize ViewModel
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        
        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)
        
        // Get mode from intent
        isLoginMode = intent.getStringExtra("mode") == "login"
        
        // Setup UI
        setupUI()
        
        // Observe ViewModel
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.apply {
            // Set mode-specific UI
            if (isLoginMode) {
                setupLoginMode()
            } else {
                setupRegisterMode()
            }
            
            // Auth button click
            btnAuth.setOnClickListener {
                validateAndProceed()
            }
            
            // Switch mode
            tvSwitchMode.setOnClickListener {
                switchMode()
            }
            
            // Back button
            btnBack.setOnClickListener {
                finish()
            }
            
            // Forgot password (only in login mode)
            tvForgotPassword.setOnClickListener {
                if (isLoginMode) {
                    handleForgotPassword()
                }
            }
        }
    }
    
    private fun setupLoginMode() {
        binding.apply {
            tvTitle.text = getString(R.string.login_title)
            tvSubtitle.text = getString(R.string.login_subtitle)
            btnAuth.text = getString(R.string.login_button)
            tvSwitchMode.text = getString(R.string.switch_to_register)
            
            // Hide name field in login mode
            tilName.visibility = View.GONE
            tvForgotPassword.visibility = View.VISIBLE
        }
    }
    
    private fun setupRegisterMode() {
        binding.apply {
            tvTitle.text = getString(R.string.register_title)
            tvSubtitle.text = getString(R.string.register_subtitle)
            btnAuth.text = getString(R.string.register_button)
            tvSwitchMode.text = getString(R.string.switch_to_login)
            
            // Show name field in register mode
            tilName.visibility = View.VISIBLE
            tvForgotPassword.visibility = View.GONE
        }
    }
    
    private fun switchMode() {
        isLoginMode = !isLoginMode
        if (isLoginMode) {
            setupLoginMode()
        } else {
            setupRegisterMode()
        }
        
        // Clear fields
        binding.apply {
            etName.text?.clear()
            etEmail.text?.clear()
            etPassword.text?.clear()
        }
    }
    
    private fun validateAndProceed() {
        binding.apply {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val name = if (!isLoginMode) etName.text.toString().trim() else ""
            
            // Validation
            if (!isLoginMode && name.isEmpty()) {
                tilName.error = getString(R.string.error_name_required)
                return
            }
            
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = getString(R.string.error_invalid_email)
                return
            }
            
            if (password.isEmpty() || password.length < 6) {
                tilPassword.error = getString(R.string.error_weak_password)
                return
            }
            
            // Clear errors
            tilName.error = null
            tilEmail.error = null
            tilPassword.error = null
            
            // Proceed with authentication
            if (isLoginMode) {
                authViewModel.signIn(email, password)
            } else {
                authViewModel.signUp(email, password, name)
            }
        }
    }
    
    private fun observeViewModel() {
        authViewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                loadingDialog.show("Authenticating...")
            } else {
                loadingDialog.hide()
            }
        }
        
        authViewModel.authResult.observe(this) { result ->
            result?.let {
                if (it.isSuccess) {
                    // Authentication successful
                    Toast.makeText(this, "Welcome to HealthGuard!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                } else {
                    // Authentication failed
                    Toast.makeText(this, it.errorMessage ?: "Authentication failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun handleForgotPassword() {
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }
        
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to send reset email", Toast.LENGTH_SHORT).show()
                }
            }
    }
}