package com.healthguard.eldercare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.healthguard.eldercare.R
import com.healthguard.eldercare.databinding.ItemHealthDataBinding
import com.healthguard.eldercare.models.HealthData
import java.text.SimpleDateFormat
import java.util.*

class HealthDataAdapter(
    private val onItemClick: (HealthData) -> Unit
) : RecyclerView.Adapter<HealthDataAdapter.HealthDataViewHolder>() {
    
    private val healthDataList = mutableListOf<HealthData>()
    
    fun updateHealthData(newData: List<HealthData>) {
        healthDataList.clear()
        healthDataList.addAll(newData)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HealthDataViewHolder {
        val binding = ItemHealthDataBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HealthDataViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: HealthDataViewHolder, position: Int) {
        holder.bind(healthDataList[position])
    }
    
    override fun getItemCount(): Int = healthDataList.size
    
    inner class HealthDataViewHolder(
        private val binding: ItemHealthDataBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(healthData: HealthData) {
            binding.apply {
                // Time formatting (elderly-friendly)
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                
                tvTime.text = timeFormat.format(Date(healthData.timestamp))
                tvDate.text = dateFormat.format(Date(healthData.timestamp))
                
                // Health readings
                tvHeartRate.text = if (healthData.heartRate > 0) {
                    "${healthData.heartRate} BPM"
                } else {
                    "-- BPM"
                }
                
                tvBloodPressure.text = if (healthData.bloodPressureSystolic > 0 && healthData.bloodPressureDiastolic > 0) {
                    "${healthData.bloodPressureSystolic}/${healthData.bloodPressureDiastolic}"
                } else {
                    "-- / --"
                }
                
                tvTemperature.text = if (healthData.temperature > 0) {
                    String.format("%.1f°C", healthData.temperature)
                } else {
                    "--°C"
                }
                
                // Activity level
                tvActivityLevel.text = healthData.activityLevel.capitalize()
                
                // Status indicator
                val status = healthData.analysisResult?.status ?: "unknown"
                updateStatusIndicator(status)
                
                // Click listener
                root.setOnClickListener {
                    onItemClick(healthData)
                }
            }
        }
        
        private fun updateStatusIndicator(status: String) {
            val context = binding.root.context
            
            val (color, icon, text) = when (status) {
                "normal" -> Triple(
                    androidx.core.content.ContextCompat.getColor(context, R.color.success_color),
                    R.drawable.ic_check_circle,
                    "Normal"
                )
                "abnormal" -> Triple(
                    androidx.core.content.ContextCompat.getColor(context, R.color.warning_color),
                    R.drawable.ic_warning,
                    "Abnormal"
                )
                "critical" -> Triple(
                    androidx.core.content.ContextCompat.getColor(context, R.color.error_color),
                    R.drawable.ic_error,
                    "Critical"
                )
                else -> Triple(
                    androidx.core.content.ContextCompat.getColor(context, R.color.secondary_text),
                    R.drawable.ic_help,
                    "Unknown"
                )
            }
            
            binding.apply {
                ivStatusIcon.setImageResource(icon)
                ivStatusIcon.setColorFilter(color)
                tvStatus.text = text
                tvStatus.setTextColor(color)
            }
        }
    }
}