package com.healthguard.eldercare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.healthguard.eldercare.databinding.ItemSafeZoneBinding
import com.healthguard.eldercare.services.LocationService

class SafeZoneAdapter(
    private val onRemoveSafeZone: (LocationService.SafeZone) -> Unit
) : RecyclerView.Adapter<SafeZoneAdapter.SafeZoneViewHolder>() {
    
    private val safeZones = mutableListOf<LocationService.SafeZone>()
    
    fun updateSafeZones(newSafeZones: List<LocationService.SafeZone>) {
        safeZones.clear()
        safeZones.addAll(newSafeZones)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SafeZoneViewHolder {
        val binding = ItemSafeZoneBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SafeZoneViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SafeZoneViewHolder, position: Int) {
        holder.bind(safeZones[position])
    }
    
    override fun getItemCount(): Int = safeZones.size
    
    inner class SafeZoneViewHolder(
        private val binding: ItemSafeZoneBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(safeZone: LocationService.SafeZone) {
            binding.apply {
                tvSafeZoneName.text = safeZone.name
                tvSafeZoneCoordinates.text = String.format(
                    "%.6f, %.6f",
                    safeZone.latitude,
                    safeZone.longitude
                )
                tvSafeZoneRadius.text = "${safeZone.radiusMeters.toInt()}m radius"
                
                // Set icon based on safe zone type
                val iconRes = when {
                    safeZone.name.lowercase().contains("home") -> android.R.drawable.ic_menu_mylocation
                    safeZone.name.lowercase().contains("hospital") -> android.R.drawable.ic_menu_help
                    safeZone.name.lowercase().contains("pharmacy") -> android.R.drawable.ic_menu_help
                    else -> android.R.drawable.ic_menu_mapmode
                }
                ivSafeZoneIcon.setImageResource(iconRes)
                
                btnRemoveSafeZone.setOnClickListener {
                    onRemoveSafeZone(safeZone)
                }
            }
        }
    }
}