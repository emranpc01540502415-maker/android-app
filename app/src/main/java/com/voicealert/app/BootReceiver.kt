package com.voicealert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).service) return
        val i = Intent(context, AlertService::class.java).putExtra("boot", true)
        ContextCompat.startForegroundService(context, i)
    }
}
