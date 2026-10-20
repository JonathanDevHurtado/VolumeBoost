package com.volumeboost.app

import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Matemática pura (sin Android) para poder probarla con tests unitarios en la PC.
 *
 * Convención de la app:
 *   100 % = volumen normal (el máximo del teléfono, sin ganancia extra)
 *   150 % = amplitud x1.5  ->  20 * log10(1.5) = +3.52 dB
 */
object BoostMath {
    const val MIN_PERCENT = 100
    const val MAX_PERCENT = 150
    const val STEP = 5

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    /** LoudnessEnhancer trabaja en milibelios (1 dB = 100 mB). 100 % -> 0 mB, 150 % -> 352 mB. */
    fun gainMillibels(percent: Int): Int =
        (2000.0 * log10(clamp(percent) / 100.0)).roundToInt()

    /** Posición 0..1 dentro del rango 100..150 (para dibujar el medidor). */
    fun fraction(percent: Float): Float =
        ((percent - MIN_PERCENT) / (MAX_PERCENT - MIN_PERCENT).toFloat()).coerceIn(0f, 1f)

    /** Siguiente múltiplo de 5 hacia arriba (103 -> 105, 150 -> 150). */
    fun stepUp(percent: Int): Int = clamp((percent / STEP + 1) * STEP)

    /** Anterior múltiplo de 5 hacia abajo (103 -> 100, 105 -> 100, 100 -> 100). */
    fun stepDown(percent: Int): Int =
        clamp(if (percent % STEP == 0) percent - STEP else (percent / STEP) * STEP)

    /** Tono del sonidito de aviso: 600 Hz en 100 % hasta 1300 Hz en 150 % (más agudo = más volumen). */
    fun levelFrequency(percent: Int): Double =
        600.0 + 700.0 * fraction(clamp(percent).toFloat())
}
