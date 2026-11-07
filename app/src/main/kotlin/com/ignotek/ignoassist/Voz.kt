package com.ignotek.ignoassist

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Agente "Igno habla" (E10) — Text To Speech del propio teléfono. Modulo AISLADO y OPT-IN:
 * solo se usa cuando prefs.ignoHabla == true (apagado por defecto). Todo envuelto en try/catch:
 * si el TTS falla o no existe en el equipo, no afecta en nada a la escucha.
 */
object Voz {
    private var tts: TextToSpeech? = null
    private var listo = false
    private var pendiente: String? = null

    fun init(ctx: Context) {
        if (tts != null) return
        try {
            tts = TextToSpeech(ctx.applicationContext) { status ->
                listo = (status == TextToSpeech.SUCCESS)
                if (listo) {
                    try {
                        val r = tts?.setLanguage(Locale("es", "US")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale("es"))
                        }
                    } catch (e: Exception) { /* idioma por defecto */ }
                    val p = pendiente; pendiente = null
                    if (p != null) hablar(p)
                }
            }
        } catch (e: Exception) { tts = null }
    }

    fun hablar(texto: String) {
        if (texto.isBlank()) return
        try {
            val t = tts
            if (t == null || !listo) { pendiente = texto; return }
            t.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "igno")
        } catch (e: Exception) { /* nunca romper por el TTS */ }
    }

    fun apagar() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        tts = null; listo = false; pendiente = null
    }
}
