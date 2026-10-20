package com.volumeboost.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sonidos de aviso generados por código (sin archivos de audio).
 * Todo el tono depende del nivel: más agudo = más volumen.
 *
 *  - tick():  clic cortito cada 5 % mientras mantienes presionado
 *  - level(): "bip" al soltar o al pulsar un botón (te dice hasta dónde llegó)
 *  - max():   campanita de tres notas al llegar al 150 %
 *  - min():   dos notas descendentes al volver a 100 %
 *
 * Se reproducen por el flujo multimedia, así que también pasan por el refuerzo: por eso la
 * amplitud es moderada (el refuerzo máximo la multiplica por 1.5 sin llegar a saturar).
 * Solo se usa desde el hilo principal.
 */
class Sfx {

    var enabled: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private val live = ArrayList<AudioTrack>()

    fun tick(percent: Int) {
        play(synth(0.22, BoostMath.levelFrequency(percent) to 40))
    }

    fun level(percent: Int) {
        play(synth(0.40, BoostMath.levelFrequency(percent) to 170))
    }

    fun max() {
        play(synth(0.40, 988.0 to 110, 1318.5 to 110, 1975.5 to 380))
    }

    fun min() {
        play(synth(0.35, 660.0 to 90, 440.0 to 230))
    }

    /** Libera todo lo pendiente. Llamar en onDestroy. */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        for (t in live.toList()) free(t)
    }

    private fun play(samples: ShortArray) {
        if (!enabled || samples.isEmpty() || live.size >= MAX_LIVE) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (track.write(samples, 0, samples.size) <= 0) {
                track.release()
                return
            }
            track.play()
            live.add(track)
            val durationMs = samples.size * 1000L / SAMPLE_RATE
            handler.postDelayed({ free(track) }, durationMs + 150L)
        } catch (e: Exception) {
            // Si el audio no está disponible simplemente no suena: no es crítico.
            Log.w(TAG, "No se pudo reproducir el aviso: ${e.message}")
        }
    }

    private fun free(track: AudioTrack) {
        live.remove(track)
        try {
            track.stop()
        } catch (e: Exception) {
            // ya estaba detenido
        }
        try {
            track.release()
        } catch (e: Exception) {
            // ya estaba liberado
        }
    }

    companion object {
        private const val TAG = "Sfx"
        const val SAMPLE_RATE = 44100
        private const val MAX_LIVE = 8

        /**
         * Notas seguidas (frecuencia Hz, duración ms). Cada nota empieza y termina en cero
         * (ataque de 4 ms, caída natural y fundido final de 12 ms) para que no haya "clics".
         */
        fun synth(gain: Double, vararg parts: Pair<Double, Int>): ShortArray {
            val lengths = IntArray(parts.size) { parts[it].second * SAMPLE_RATE / 1000 }
            val out = ShortArray(lengths.sum())
            val attack = (SAMPLE_RATE * 0.004).toInt().coerceAtLeast(1)
            val fade = (SAMPLE_RATE * 0.012).toInt().coerceAtLeast(1)
            var offset = 0
            for (i in parts.indices) {
                val n = lengths[i]
                val freq = parts[i].first
                for (k in 0 until n) {
                    var env = exp(-2.5 * k / n)
                    if (k < attack) env *= k / attack.toDouble()
                    if (k > n - fade) env *= (n - k) / fade.toDouble()
                    val s = sin(2.0 * PI * freq * k / SAMPLE_RATE) * env * gain
                    out[offset + k] = (s * Short.MAX_VALUE).toInt().toShort()
                }
                offset += n
            }
            return out
        }
    }
}
