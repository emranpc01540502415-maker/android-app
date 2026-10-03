package com.voicealert.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

class AlertService : Service() {
    private val fired = mutableSetOf<Int>()
    private var lastPlugged: Boolean? = null
    private var fullSpoken = false
    private var ringing = false
    private var answered = false
    private var cb: Any? = null
    private var legacy: Any? = null

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

            val lp = lastPlugged
            if (lp != null && lp != plugged) Voice.play(c, Events.get(if (plugged) "plug" else "unplug"), pct)
            lastPlugged = plugged

            if (plugged) {
                fired.clear()
            } else {
                fired.removeAll { pct > it }
                val steps = listOf(5, 10, 20)
                val t = steps.firstOrNull { pct <= it && it !in fired }
                if (t != null) {
                    fired.addAll(steps.filter { it >= t })
                    Voice.play(c, Events.get("b$t"), pct)
                }
            }

            if (pct >= 100 && plugged) {
                if (!fullSpoken) { fullSpoken = true; Voice.play(c, Events.get("full"), pct) }
            } else if (pct < 100) fullSpoken = false
        }
    }

    private val system = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SHUTDOWN -> Voice.play(c, Events.get("off"))
                Intent.ACTION_USER_PRESENT -> Voice.play(c, Events.get("unlock"))
            }
        }
    }

    private fun onCall(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> { ringing = true; answered = false; Voice.play(this, Events.get("in")) }
            TelephonyManager.CALL_STATE_OFFHOOK -> { answered = true; Voice.stop() }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (ringing && !answered) Voice.play(this, Events.get("miss"))
                ringing = false; answered = false
            }
        }
    }

    @RequiresApi(31)
    private inner class Cb : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = onCall(state)
    }

    override fun onCreate() {
        super.onCreate()
        val ch = "va_service"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ch, "ভয়েস অ্যালার্ট", NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, ch)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle("ভয়েস অ্যালার্ট চালু আছে")
            .setContentIntent(pi).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registerReceiver(system, IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN); addAction(Intent.ACTION_USER_PRESENT)
        })
        registerCalls()
    }

    @Suppress("DEPRECATION")
    private fun registerCalls() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val tm = getSystemService(TelephonyManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val c = Cb(); cb = c
                tm.registerTelephonyCallback(mainExecutor, c)
            } else {
                val l = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCall(state)
                }
                legacy = l
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("boot", false) == true) Voice.play(this, Events.get("on"))
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        try { unregisterReceiver(battery); unregisterReceiver(system) } catch (_: Exception) {}
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) (cb as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            else (legacy as? PhoneStateListener)?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
