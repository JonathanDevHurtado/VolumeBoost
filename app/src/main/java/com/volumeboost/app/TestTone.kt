package com.volumeboost.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sonido de prueba en bucle (arpegio suave) para que compruebes el refuerzo sin depender de otra app.
 * Sube y baja el medidor mientras suena: debes oír la diferencia entre 100 % y 150 %.
 *
 * Si el modo global funciona, el efecto ya lo cubre todo. Si no, en modo por app el motor engancha
 * la sesión de este reproductor (ver [BoostEngine.trackSession]), así el test sirve en ambos modos.
 */
class TestTone {

    private var track: AudioTrack? = null

    val isPlaying: Boolean
        get() = track != null

    fun start(): Boolean {
        if (track != null) return true
        return try {
            val data = buildLoop()
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Sfx.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (t.write(data, 0, data.size) <= 0) {
                t.release()
                return false
            }
            t.setLoopPoints(0, data.size, -1) // un fotograma = una muestra (mono)
            t.play()
            track = t
            BoostEngine.trackSession(t.audioSessionId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar el sonido de prueba: ${e.message}")
            track = null
            false
        }
    }

    fun stop() {
        val t = track ?: return
        track = null
        BoostEngine.untrackSession(t.audioSessionId)
        try {
            t.stop()
        } catch (e: Exception) {
            // ya detenido
        }
        try {
            t.release()
        } catch (e: Exception) {
            // ya liberado
        }
    }

    /** Se llama cuando cambia el refuerzo, por si hay que volver a enganchar la sesión. */
    fun syncSession() {
        val t = track ?: return
        BoostEngine.trackSession(t.audioSessionId)
    }

    /** Cuatro notas (Do-Mi-Sol-Mi) de 450 ms con volumen constante: 1,8 s que se repiten. */
    private fun buildLoop(): ShortArray {
        val sr = Sfx.SAMPLE_RATE
        val noteFreqs = doubleArrayOf(523.25, 659.25, 783.99, 659.25)
        val n = 450 * sr / 1000
        val attack = (sr * 0.010).toInt()
        val release = (sr * 0.040).toInt()
        val out = ShortArray(n * noteFreqs.size)
        for (i in noteFreqs.indices) {
            val f = noteFreqs[i]
            for (k in 0 until n) {
                var env = 1.0
                if (k < attack) env = k / attack.toDouble()
                if (k > n - release) env = (n - k) / release.toDouble()
                val phase = 2.0 * PI * f * k / sr
                // Fundamental + un poco de octava: suena más "musical" y es buen estímulo de prueba.
                val s = (sin(phase) + 0.3 * sin(2.0 * phase)) * env * 0.38
                out[i * n + k] = (s * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    companion object {
        private const val TAG = "TestTone"
    }
}
