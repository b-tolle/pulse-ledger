package com.example.pulseledger.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Our Body-Battery-style "Charge" score (5–100).
 * Same input family Garmin/Firstbeat use: HRV (RMSSD) vs personal baseline,
 * stress proxy, sleep quality, and activity load. Sleep charges; stress and
 * exertion drain. Tuned to be simple, explainable, and personal-baseline-relative.
 */
object ChargeEngine {

    data class MinuteInput(
        val hrvRmssd: Double?,     // ms, when available (mostly overnight)
        val heartRate: Int?,       // bpm
        val restingHr: Int,        // personal baseline
        val asleep: Boolean,
        val sleepQuality01: Double, // 0..1 (score/100) during sleep, else 0
        val activeIntensity01: Double, // 0..1 from steps/exercise intensity
    )

    /** Returns the next charge value given the previous one and this minute's input. */
    fun step(prev: Double, m: MinuteInput, hrvBaseline: Double): Double {
        var delta = 0.0
        if (m.asleep) {
            // Full good night ≈ +45..55 over ~7.5h  → ~0.10-0.12/min
            delta += 0.06 + 0.06 * m.sleepQuality01
        } else {
            // Stress proxy: HR elevation over resting when not moving much
            val hrElev = ((m.heartRate ?: m.restingHr) - m.restingHr).coerceAtLeast(0)
            val stress01 = min(1.0, hrElev / 40.0) * (1.0 - m.activeIntensity01)
            delta -= 0.02 + 0.10 * stress01            // idle stress drain
            delta -= 0.25 * m.activeIntensity01        // exertion drain
            // Genuine calm (HRV above baseline while awake) can trickle-charge
            m.hrvRmssd?.let { if (it > hrvBaseline * 1.1) delta += 0.03 }
        }
        return min(100.0, max(5.0, prev + delta))
    }

    /** Human-readable contributors for the "what moved it" ledger. */
    data class Contributor(val label: String, val delta: Int)
}
