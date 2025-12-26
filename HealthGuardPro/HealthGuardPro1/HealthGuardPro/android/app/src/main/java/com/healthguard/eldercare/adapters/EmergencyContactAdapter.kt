package com.healthguard.eldercare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.healthguard.eldercare.databinding.ItemEmergencyContactBinding
import com.healthguard.eldercare.models.EmergencyContact
import java.util.*

class EmergencyContactAdapter(
    private val onEditContact: (EmergencyContact) -> Unit,
    private val onDeleteContact: (EmergencyContact) -> Unit,
    private val onCallContact: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.ContactViewHolder>() {
    
    private val contacts = mutableListOf<EmergencyContact>()
    private var hasChanges = false
    
    fun updateContacts(newContacts: List<EmergencyContact>) {
        contacts.clear()
        contacts.addAll(newContacts.sortedBy { it.priority })
        notifyDataSetChanged()
        hasChanges = false
    }
    
    fun moveContact(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contacts, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contacts, i, i - 1)
            }
        }
        
        // Update priorities
        contacts.forEachIndexed { index, contact ->
            contacts[index] = contact.copy(priority = index + 1)
        }
        
        notifyItemMoved(fromPosition, toPosition)
        hasChanges = true
    }
    
    fun getContactAt(position: Int): EmergencyContact = contacts[position]
    
    fun hasUnsavedChanges(): Boolean = hasChanges
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemEmergencyContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }
    
    override fun getItemCount(): Int = contacts.size
    
    inner class ContactViewHolder(
        private val binding: ItemEmergencyContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(contact: EmergencyContact) {
            binding.apply {
                // Basic info
                tvContactName.text = contact.name
                tvContactPhone.text = contact.phoneNumber
                tvContactRelationship.text = contact.relationship
                tvPriority.text = "Priority ${contact.priority}"
                
                // Set relationship icon
                val iconRes = when (contact.relationship.lowercase()) {
                    "family", "spouse", "partner" -> android.R.drawable.ic_menu_call
                    "doctor", "physician" -> android.R.drawable.ic_menu_call
                    "caretaker", "nurse" -> android.R.drawable.ic_menu_call
                    "neighbor", "friend" -> android.R.drawable.ic_menu_call
                    else -> android.R.drawable.ic_menu_call
                }
                ivContactIcon.setImageResource(iconRes)
                
                // Last contacted info
                if (contact.lastContactedAt > 0) {
                    val lastContacted = formatTimestamp(contact.lastContactedAt)
                    tvLastContacted.text = "Last contacted: $lastContacted"
                    tvLastContacted.visibility = android.view.View.VISIBLE
                } else {
                    tvLastContacted.visibility = android.view.View.GONE
                }
                
                // Response status
                if (contact.responseReceived) {
                    tvResponseStatus.text = "✓ Responded"
                    tvResponseStatus.setTextColor(
                        androidx.core.content.ContextCompat.getColor(root.context, android.R.color.holo_green_dark)
                    )
                    tvResponseStatus.visibility = android.view.View.VISIBLE
                } else if (contact.lastContactedAt > 0) {
                    tvResponseStatus.text = "⏳ Awaiting response"
                    tvResponseStatus.setTextColor(
                        androidx.core.content.ContextCompat.getColor(root.context, android.R.color.holo_orange_dark)
                    )
                    tvResponseStatus.visibility = android.view.View.VISIBLE
                } else {
                    tvResponseStatus.visibility = android.view.View.GONE
                }
                
                // Click listeners
                btnCall.setOnClickListener {
                    onCallContact(contact)
                }
                
                btnEdit.setOnClickListener {
                    onEditContact(contact)
                }
                
                btnDelete.setOnClickListener {
                    onDeleteContact(contact)
                }
                
                // Long click for reordering
                root.setOnLongClickListener {
                    // Visual feedback for drag start
                    root.alpha = 0.7f
                    true
                }
                
                // Reset alpha when not dragging
                root.alpha = 1.0f
            }
        }
        
        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000} minutes ago"
                diff < 86400000 -> "${diff / 3600000} hours ago"
                diff < 604800000 -> "${diff / 86400000} days ago"
                else -> {
                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(timestamp))
                }
            }
        }
    }
}