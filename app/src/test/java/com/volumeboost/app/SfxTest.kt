package com.volumeboost.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la síntesis de audio. No reproducen nada: verifican las muestras PCM.
 * Así se detectan regresiones en el generador de tonos sin depender de un teléfono.
 */
class SfxTest {

    @Test
    fun synth_length_matches_requested_duration() {
        val samples = Sfx.synth(0.5, 440.0 to 100, 880.0 to 50)
        val expected = (100 * Sfx.SAMPLE_RATE / 1000) + (50 * Sfx.SAMPLE_RATE / 1000)
        assertEquals(expected, samples.size)
    }

    @Test
    fun synth_is_silent_with_no_parts() {
        assertEquals(0, Sfx.synth(0.5).size)
    }

    @Test
    fun synth_never_clips() {
        val samples = Sfx.synth(4.0, 440.0 to 120)
        assertTrue(samples.isNotEmpty())
        assertTrue(samples.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
    }

    @Test
    fun synth_starts_and_ends_near_zero_to_avoid_clicks() {
        val samples = Sfx.synth(0.5, 440.0 to 100)
        assertTrue(kotlin.math.abs(samples.first().toInt()) < 1000)
        assertTrue(kotlin.math.abs(samples.last().toInt()) < 1000)
    }

    @Test
    fun level_frequency_is_within_audible_range() {
        assertTrue(BoostMath.levelFrequency(100) in 500.0..1500.0)
        assertTrue(BoostMath.levelFrequency(150) in 500.0..1500.0)
    }
}
