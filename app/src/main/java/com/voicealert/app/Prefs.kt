package com.voicealert.app

import android.content.Context
import java.io.File

data class Ev(val id: String, val title: String, val def: String, val on: Boolean = true)

object Events {
    val all = listOf(
        Ev("b20", "🔋 চার্জ ২০% এর নিচে", "ওহ! আপনার ফোনের চার্জ এখন {level} শতাংশ। দয়া করে চার্জারটা লাগিয়ে নিন, নাহলে আমি ঘুমিয়ে পড়ব।"),
        Ev("b10", "🪫 চার্জ ১০% এর নিচে", "সাবধান! চার্জ মাত্র {level} শতাংশ বাকি। প্লিজ, এক্ষুনি চার্জে লাগান।"),
        Ev("b5", "🚨 চার্জ ৫% এর নিচে", "জরুরি! মাত্র {level} শতাংশ চার্জ আছে। এখনই চার্জ না দিলে আমি বন্ধ হয়ে যাব।"),
        Ev("in", "📞 কল আসলে", "আপনার একটি কল আসছে। ফোনটা ধরুন না!"),
        Ev("miss", "📵 মিসড কল হলে", "আপনার একটি মিসড কল আছে। একবার দেখে নিন, কেউ হয়তো আপনাকে খুঁজছে।"),
        Ev("off", "⏻ ফোন বন্ধ হওয়ার সময়", "ফোন বন্ধ হচ্ছে। আবার দেখা হবে, নিজের খেয়াল রাখবেন।"),
        Ev("on", "✨ ফোন চালু হওয়ার সময়", "স্বাগতম! আপনার ফোন চালু হয়েছে। আপনার দিনটি সুন্দর হোক।"),
        Ev("plug", "🔌 চার্জার লাগালে", "চার্জার লাগানো হয়েছে, ধন্যবাদ।"),
        Ev("unplug", "🔕 চার্জার খুললে", "চার্জার খুলে ফেলা হয়েছে।"),
        Ev("full", "✅ ১০০% চার্জ হলে", "আপনার ফোন পুরোপুরি চার্জ হয়েছে। এবার চার্জারটা খুলে নিতে পারেন।"),
        Ev("unlock", "🔓 ফোন আনলক করলে", "স্বাগতম, আপনাকে আবার দেখে ভালো লাগছে।", on = false)
    )
    fun get(id: String) = all.first { it.id == id }
}

class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("va", Context.MODE_PRIVATE)

    fun enabled(e: Ev) = p.getBoolean("en_${e.id}", e.on)
    fun setEnabled(e: Ev, v: Boolean) = p.edit().putBoolean("en_${e.id}", v).apply()

    fun text(e: Ev): String = p.getString("tx_${e.id}", null) ?: e.def
    fun setText(e: Ev, v: String) = p.edit().putString("tx_${e.id}", v).apply()

    fun audio(e: Ev): String? = p.getString("au_${e.id}", null)?.takeIf { File(it).exists() }
    fun setAudio(e: Ev, path: String?) {
        val ed = p.edit()
        if (path == null) ed.remove("au_${e.id}") else ed.putString("au_${e.id}", path)
        ed.apply()
    }

    var pitch: Int
        get() = p.getInt("pitch", 115)
        set(v) = p.edit().putInt("pitch", v).apply()

    var rate: Int
        get() = p.getInt("rate", 95)
        set(v) = p.edit().putInt("rate", v).apply()

    var service: Boolean
        get() = p.getBoolean("svc", true)
        set(v) = p.edit().putBoolean("svc", v).apply()
}
