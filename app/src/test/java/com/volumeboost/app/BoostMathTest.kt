package com.volumeboost.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostMathTest {

    @Test
    fun gain_at_100_is_zero() {
        assertEquals(0, BoostMath.gainMillibels(100))
    }

    @Test
    fun gain_at_150_is_about_3_5_dB() {
        // 20 * log10(1.5) = 3.5218 dB = 352 mB
        assertEquals(352, BoostMath.gainMillibels(150))
    }

    @Test
    fun gain_at_125() {
        assertEquals(194, BoostMath.gainMillibels(125))
    }

    @Test
    fun gain_is_clamped_outside_range() {
        assertEquals(0, BoostMath.gainMillibels(50))
        assertEquals(352, BoostMath.gainMillibels(400))
    }

    @Test
    fun gain_never_decreases_as_percent_grows() {
        var previous = -1
        for (p in 100..150) {
            val g = BoostMath.gainMillibels(p)
            assertTrue("p=$p", g >= previous)
            previous = g
        }
    }

    @Test
    fun step_up_snaps_to_multiples_of_five() {
        assertEquals(105, BoostMath.stepUp(100))
        assertEquals(105, BoostMath.stepUp(103))
        assertEquals(110, BoostMath.stepUp(105))
        assertEquals(150, BoostMath.stepUp(148))
        assertEquals(150, BoostMath.stepUp(150))
    }

    @Test
    fun step_down_snaps_to_multiples_of_five() {
        assertEquals(100, BoostMath.stepDown(100))
        assertEquals(100, BoostMath.stepDown(103))
        assertEquals(100, BoostMath.stepDown(105))
        assertEquals(145, BoostMath.stepDown(150))
        assertEquals(145, BoostMath.stepDown(149))
    }

    @Test
    fun fraction_covers_zero_to_one() {
        assertEquals(0f, BoostMath.fraction(100f), 0.0001f)
        assertEquals(0.5f, BoostMath.fraction(125f), 0.0001f)
        assertEquals(1f, BoostMath.fraction(150f), 0.0001f)
        assertEquals(0f, BoostMath.fraction(80f), 0.0001f)
        assertEquals(1f, BoostMath.fraction(999f), 0.0001f)
    }

    @Test
    fun clamp_limits_both_ends() {
        assertEquals(100, BoostMath.clamp(Int.MIN_VALUE))
        assertEquals(100, BoostMath.clamp(99))
        assertEquals(150, BoostMath.clamp(151))
        assertEquals(150, BoostMath.clamp(Int.MAX_VALUE))
    }

    @Test
    fun step_helpers_are_idempotent_at_bounds() {
        assertEquals(100, BoostMath.stepDown(100))
        assertEquals(150, BoostMath.stepUp(150))
        assertEquals(100, BoostMath.stepUp(99))
        assertEquals(150, BoostMath.stepDown(151))
    }

    @Test
    fun gain_is_monotonic_for_every_percent() {
        val gains = (100..150).map(BoostMath::gainMillibels)
        assertEquals(gains.sorted(), gains)
        assertTrue(gains.first() == 0)
        assertTrue(gains.last() == 352)
    }

    @Test
    fun beep_frequency_rises_with_level() {
        assertEquals(600.0, BoostMath.levelFrequency(100), 0.001)
        assertEquals(1300.0, BoostMath.levelFrequency(150), 0.001)
        assertTrue(BoostMath.levelFrequency(130) > BoostMath.levelFrequency(120))
    }
}
