package com.healthguard.eldercare.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import com.healthguard.eldercare.R

class LoadingDialog(private val context: Context) {
    
    private var dialog: Dialog? = null
    
    fun show(message: String = "Loading...") {
        dismiss()
        
        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(false)
            
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
            setContentView(view)
            
            val tvMessage = view.findViewById<TextView>(R.id.tv_loading_message)
            tvMessage.text = message
            
            show()
        }
    }
    
    fun hide() {
        dismiss()
    }
    
    private fun dismiss() {
        dialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        dialog = null
    }
}