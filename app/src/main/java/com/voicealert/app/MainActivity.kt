package com.voicealert.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private var pickFor: Ev? = null
    private val labels = mutableMapOf<String, TextView>()

    private val BG = Color.parseColor("#0B1220")
    private val CARD = Color.parseColor("#141E33")
    private val MUTED = Color.parseColor("#9AA7C0")

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val ev = pickFor
        if (uri == null || ev == null) return@registerForActivityResult
        try {
            val f = File(filesDir, "v_${ev.id}")
            contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            prefs.setAudio(ev, f.absolutePath)
            refreshLabel(ev)
        } catch (e: Exception) {
            Toast.makeText(this, "ফাইলটি পড়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startSvc() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun bg(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun btn(text: String, onClick: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text; textSize = 12f; isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun refreshLabel(ev: Ev) {
        labels[ev.id]?.text =
            if (prefs.audio(ev) != null) "🎵 আপনার বাছাই করা ভয়েস ব্যবহার হচ্ছে"
            else "🗣 ডিফল্ট সুন্দরী কণ্ঠ (লেখা থেকে)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#FF5C93"))
            ).apply { cornerRadius = dp(24).toFloat() }
        }
        header.addView(tv("🎙 ভয়েস অ্যালার্ট", 24f, bold = true))
        header.addView(tv("আপনার ফোনের প্রতিটি মুহূর্তে আকর্ষণীয় কণ্ঠের রিমাইন্ডার", 13f, Color.parseColor("#F3EEFF")))
        val master = MaterialSwitch(this).apply {
            text = "ভয়েস সেবা চালু"; setTextColor(Color.WHITE); isChecked = prefs.service
            setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, on ->
                prefs.service = on
                if (on) ensure() else { stopService(Intent(this@MainActivity, AlertService::class.java)); Voice.stop() }
            }
        }
        header.addView(master)
        root.addView(header, lp(0))

        // Voice tuning
        val tune = card()
        tune.addView(tv("🎚 কণ্ঠ সেটিংস", 16f, bold = true))
        tune.addView(tv("সুর (পিচ) — বেশি হলে মিহি কণ্ঠ", 12f, MUTED))
        tune.addView(seek(50, 200, prefs.pitch) { prefs.pitch = it })
        tune.addView(tv("গতি — কম হলে ধীরে বলবে", 12f, MUTED))
        tune.addView(seek(50, 150, prefs.rate) { prefs.rate = it })
        tune.addView(tv("বাংলা কণ্ঠ না পেলে: সেটিংস → ভাষা → টেক্সট-টু-স্পিচ → Google → বাংলা ভয়েস ইনস্টল করুন।", 11f, MUTED))
        tune.addView(btn("ব্যাকগ্রাউন্ডে সীমাবদ্ধতা বন্ধ করুন (প্রস্তাবিত)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(tune, lp(12))

        // Event cards
        Events.all.forEach { ev -> root.addView(eventCard(ev), lp(12)) }

        if (prefs.service) ensure()
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(CARD, 18)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun seek(min: Int, max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max - min; progress = value - min
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) onChange(p + min) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun eventCard(ev: Ev): LinearLayout {
        val c = card()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(tv(ev.title, 16f, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(MaterialSwitch(this).apply {
            isChecked = prefs.enabled(ev)
            setOnCheckedChangeListener { _, on -> prefs.setEnabled(ev, on) }
        })
        c.addView(top)

        val edit = EditText(this).apply {
            setText(prefs.text(ev)); setTextColor(Color.WHITE); textSize = 14f
            minLines = 2; gravity = Gravity.TOP
            background = bg(BG, 12); setPadding(dp(12), dp(10), dp(12), dp(10))
            doAfterTextChanged { prefs.setText(ev, it.toString()) }
        }
        c.addView(edit, lp(8))

        val lab = tv("", 12f, MUTED); labels[ev.id] = lab; refreshLabel(ev)
        c.addView(lab, lp(6))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(b: MaterialButton) = row.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        add(btn("🎵 ভয়েস বাছুন") { pickFor = ev; picker.launch(arrayOf("audio/*")) })
        add(btn("▶ শুনুন") { Voice.play(this, ev, 17, force = true) })
        add(btn("↺ রিসেট") {
            prefs.setText(ev, ev.def); prefs.setAudio(ev, null)
            edit.setText(ev.def); refreshLabel(ev)
        })
        c.addView(row, lp(6))
        return c
    }

    private fun ensure() {
        val need = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val miss = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (miss.isEmpty()) startSvc() else perms.launch(miss.toTypedArray())
    }

    private fun startSvc() {
        if (prefs.service) ContextCompat.startForegroundService(this, Intent(this, AlertService::class.java))
    }
}
