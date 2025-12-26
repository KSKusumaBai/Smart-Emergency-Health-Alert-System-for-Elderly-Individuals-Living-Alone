package com.healthguard.eldercare.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.healthguard.eldercare.R
import com.healthguard.eldercare.adapters.EmergencyContactAdapter
import com.healthguard.eldercare.databinding.ActivityEmergencyContactsBinding
import com.healthguard.eldercare.models.EmergencyContact
import com.healthguard.eldercare.viewmodels.EmergencyContactViewModel
import java.util.*

class EmergencyContactsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEmergencyContactsBinding
    private lateinit var viewModel: EmergencyContactViewModel
    private lateinit var contactAdapter: EmergencyContactAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[EmergencyContactViewModel::class.java]
        
        setupUI()
        setupRecyclerView()
        observeViewModel()
        
        // Load existing contacts
        viewModel.loadEmergencyContacts()
        
        // Check if opened from emergency alert
        handleEmergencyIntent()
    }
    
    private fun setupUI() {
        binding.apply {
            // Toolbar
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = getString(R.string.emergency_contacts)
            
            // Add contact button
            fabAddContact.setOnClickListener {
                showAddContactDialog()
            }
            
            // SOS button (always visible)
            btnSos.setOnClickListener {
                triggerManualSOS()
            }
            
            // Back button
            toolbar.setNavigationOnClickListener {
                finish()
            }
        }
    }
    
    private fun setupRecyclerView() {
        contactAdapter = EmergencyContactAdapter(
            onEditContact = { contact -> showEditContactDialog(contact) },
            onDeleteContact = { contact -> confirmDeleteContact(contact) },
            onCallContact = { contact -> callContact(contact) }
        )
        
        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(this@EmergencyContactsActivity)
            adapter = contactAdapter
        }
        
        // Add swipe to delete functionality
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                contactAdapter.moveContact(fromPosition, toPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val contact = contactAdapter.getContactAt(position)
                confirmDeleteContact(contact)
            }
        })
        
        itemTouchHelper.attachToRecyclerView(binding.rvContacts)
    }
    
    private fun observeViewModel() {
        viewModel.emergencyContacts.observe(this) { contacts ->
            contactAdapter.updateContacts(contacts)
            updateEmptyState(contacts.isEmpty())
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        viewModel.operationResult.observe(this) { result ->
            result?.let {
                Toast.makeText(this, if (it) "Operation successful" else "Operation failed", Toast.LENGTH_SHORT).show()
                viewModel.clearOperationResult()
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.apply {
            if (isEmpty) {
                rvContacts.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
            } else {
                rvContacts.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE
            }
        }
    }
    
    private fun showAddContactDialog() {
        showContactDialog(null)
    }
    
    private fun showEditContactDialog(contact: EmergencyContact) {
        showContactDialog(contact)
    }
    
    private fun showContactDialog(existingContact: EmergencyContact?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emergency_contact, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_contact_name)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.et_contact_phone)
        val etRelationship = dialogView.findViewById<TextInputEditText>(R.id.et_contact_relationship)
        
        // Pre-fill if editing
        existingContact?.let { contact ->
            etName.setText(contact.name)
            etPhone.setText(contact.phoneNumber)
            etRelationship.setText(contact.relationship)
        }
        
        val title = if (existingContact == null) "Add Emergency Contact" else "Edit Emergency Contact"
        
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val relationship = etRelationship.text.toString().trim()
                
                if (validateContactInput(name, phone, relationship)) {
                    val contact = EmergencyContact(
                        id = existingContact?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        phoneNumber = phone,
                        relationship = relationship,
                        priority = existingContact?.priority ?: (contactAdapter.itemCount + 1),
                        isActive = true
                    )
                    
                    if (existingContact == null) {
                        viewModel.addEmergencyContact(contact)
                    } else {
                        viewModel.updateEmergencyContact(contact)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun validateContactInput(name: String, phone: String, relationship: String): Boolean {
        when {
            name.isEmpty() -> {
                Toast.makeText(this, "Please enter contact name", Toast.LENGTH_SHORT).show()
                return false
            }
            phone.isEmpty() -> {
                Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
                return false
            }
            relationship.isEmpty() -> {
                Toast.makeText(this, "Please enter relationship", Toast.LENGTH_SHORT).show()
                return false
            }
            !isValidPhoneNumber(phone) -> {
                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }
    
    private fun isValidPhoneNumber(phone: String): Boolean {
        val phonePattern = "^[+]?[0-9]{10,15}$".toRegex()
        return phonePattern.matches(phone.replace("\\s".toRegex(), ""))
    }
    
    private fun confirmDeleteContact(contact: EmergencyContact) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.removeEmergencyContact(contact.id)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Refresh the list to restore the swiped item
                contactAdapter.notifyDataSetChanged()
            }
            .setOnCancelListener {
                // Refresh the list to restore the swiped item
                contactAdapter.notifyDataSetChanged()
            }
            .show()
    }
    
    private fun callContact(contact: EmergencyContact) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:${contact.phoneNumber}")
            }
            startActivity(callIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to make call", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun triggerManualSOS() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Emergency SOS")
            .setMessage("This will trigger an emergency alert and contact your emergency contacts. Are you sure?")
            .setPositiveButton("YES - EMERGENCY") { _, _ ->
                viewModel.triggerManualSOS()
                showSOSTriggeredDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showSOSTriggeredDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("🚨 Emergency Alert Triggered")
            .setMessage("Emergency contacts will be notified in 60 seconds unless you cancel this alert.")
            .setPositiveButton("I'M OK - Cancel Alert") { _, _ ->
                viewModel.cancelEmergencyAlert()
            }
            .setNegativeButton("Continue Emergency", null)
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Auto-dismiss after 60 seconds if user doesn't respond
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 60000)
    }
    
    private fun handleEmergencyIntent() {
        if (intent.getBooleanExtra("emergency_alert", false)) {
            val alertType = intent.getStringExtra("alert_type") ?: "unknown"
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Emergency Alert Active")
                .setMessage("An emergency alert has been triggered: ${alertType.replace("_", " ").capitalize()}")
                .setPositiveButton("I'm OK") { _, _ ->
                    viewModel.respondToEmergency("user_ok")
                }
                .setNegativeButton("False Alarm") { _, _ ->
                    viewModel.respondToEmergency("false_alarm")
                }
                .setCancelable(false)
                .show()
        }
    }
    
    override fun onBackPressed() {
        // Check if there are any unsaved changes
        if (contactAdapter.hasUnsavedChanges()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Do you want to save them?")
                .setPositiveButton("Save") { _, _ ->
                    viewModel.saveAllContacts()
                    super.onBackPressed()
                }
                .setNegativeButton("Discard") { _, _ ->
                    super.onBackPressed()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}