package com.healthguard.eldercare.adapters

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.healthguard.eldercare.databinding.ItemBluetoothDeviceBinding

class BluetoothDeviceAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {
    
    private val devices = mutableListOf<BluetoothDevice>()
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBluetoothDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }
    
    override fun getItemCount(): Int = devices.size
    
    inner class DeviceViewHolder(
        private val binding: ItemBluetoothDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        @SuppressLint("MissingPermission")
        fun bind(device: BluetoothDevice) {
            binding.apply {
                tvDeviceName.text = device.name ?: "Unknown Device"
                tvDeviceAddress.text = device.address
                
                // Set device type icon based on name
                val deviceName = device.name?.lowercase() ?: ""
                when {
                    deviceName.contains("fitbit") -> {
                        ivDeviceIcon.setImageResource(android.R.drawable.ic_input_add) // Replace with actual icon
                    }
                    deviceName.contains("garmin") -> {
                        ivDeviceIcon.setImageResource(android.R.drawable.ic_input_add) // Replace with actual icon
                    }
                    deviceName.contains("apple") || deviceName.contains("watch") -> {
                        ivDeviceIcon.setImageResource(android.R.drawable.ic_input_add) // Replace with actual icon
                    }
                    else -> {
                        ivDeviceIcon.setImageResource(android.R.drawable.ic_input_add) // Default icon
                    }
                }
                
                // Set click listener
                root.setOnClickListener {
                    onDeviceClick(device)
                }
            }
        }
    }
}