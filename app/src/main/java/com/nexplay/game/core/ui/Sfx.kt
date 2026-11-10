package com.nexplay.game.core.ui

import android.media.AudioManager
import android.media.ToneGenerator

/** Minimal sound effects using the system tone generator (no audio assets needed). */
object Sfx {
    private var gen: ToneGenerator? = null

    private fun tone(enabled: Boolean, type: Int, ms: Int) {
        if (!enabled) return
        try {
            if (gen == null) gen = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
            gen?.startTone(type, ms)
        } catch (_: Exception) {
        }
    }

    fun move(enabled: Boolean) = tone(enabled, ToneGenerator.TONE_PROP_BEEP, 40)
    fun win(enabled: Boolean) = tone(enabled, ToneGenerator.TONE_PROP_ACK, 250)
    fun lose(enabled: Boolean) = tone(enabled, ToneGenerator.TONE_PROP_NACK, 250)
}
