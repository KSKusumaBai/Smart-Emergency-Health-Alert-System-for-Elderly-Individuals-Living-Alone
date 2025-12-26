package com.healthguard.eldercare.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.healthguard.eldercare.R
import com.healthguard.eldercare.databinding.ActivityMainBinding
import com.healthguard.eldercare.utils.PermissionManager

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var permissionManager: PermissionManager
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Initialize permission manager
        permissionManager = PermissionManager(this)
        
        // Setup UI
        setupUI()
        
        // Check permissions
        checkAndRequestPermissions()
        
        // Check if user is already logged in
        checkUserAuthentication()
    }
    
    private fun setupUI() {
        binding.apply {
            // App logo and welcome message
            tvWelcomeTitle.text = getString(R.string.welcome_title)
            tvWelcomeSubtitle.text = getString(R.string.welcome_subtitle)
            
            // Login button
            btnLogin.setOnClickListener {
                startActivity(Intent(this@MainActivity, AuthActivity::class.java).apply {
                    putExtra("mode", "login")
                })
            }
            
            // Register button
            btnRegister.setOnClickListener {
                startActivity(Intent(this@MainActivity, AuthActivity::class.java).apply {
                    putExtra("mode", "register")
                })
            }
            
            // Emergency button - always accessible
            btnEmergency.setOnClickListener {
                handleEmergencyAccess()
            }
            
            // Doctor portal button
            btnDoctorPortal.setOnClickListener {
                startActivity(Intent(this@MainActivity, DoctorPortalActivity::class.java))
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val requiredPermissions = listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                
                if (deniedPermissions.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "Some permissions are required for the app to function properly",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun checkUserAuthentication() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in, go to dashboard
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }
    
    private fun handleEmergencyAccess() {
        // Emergency access without login - call emergency services
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("emergency_mode", true)
            putExtra("skip_auth", true)
        }
        startActivity(intent)
    }
    
    override fun onStart() {
        super.onStart()
        // Check if user is signed in and update UI accordingly
        checkUserAuthentication()
    }
}