package com.voicealert.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.util.Locale

object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var player: MediaPlayer? = null

    fun play(ctx: Context, ev: Ev, level: Int = -1, force: Boolean = false) {
        val app = ctx.applicationContext
        val pr = Prefs(app)
        if (!force && !pr.enabled(ev)) return
        stop()
        val audio = pr.audio(ev)
        if (audio != null) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    setDataSource(audio)
                    setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                    prepare()
                    start()
                }
                return
            } catch (_: Exception) { /* fall back to TTS */ }
        }
        speak(app, pr.text(ev).replace("{level}", bn(level)))
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        tts?.stop()
    }

    private fun speak(app: Context, text: String) {
        val t = tts
        if (t != null && ready) { say(app, t, text); return }
        pending = text
        if (t == null) {
            tts = TextToSpeech(app) { status ->
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    setup(engine)
                    ready = true
                    pending?.let { pending = null; say(app, engine, it) }
                }
            }
        }
    }

    private fun setup(t: TextToSpeech) {
        val loc = listOf(Locale("bn", "BD"), Locale("bn", "IN"), Locale("bn"))
            .firstOrNull { t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        if (loc != null) {
            t.language = loc
            val vs = t.voices?.filter { it.locale.language == "bn" }.orEmpty()
            (vs.firstOrNull { it.name.contains("female", true) } ?: vs.firstOrNull())?.let { t.voice = it }
        }
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
    }

    private fun say(app: Context, t: TextToSpeech, text: String) {
        val pr = Prefs(app)
        t.setPitch(pr.pitch / 100f)
        t.setSpeechRate(pr.rate / 100f)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "va")
    }

    private fun bn(n: Int): String =
        if (n < 0) "" else n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")
}
