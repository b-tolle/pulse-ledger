package com.example.pulseledger.ui

import androidx.compose.ui.graphics.Color

object PL {
    val Bg = Color(0xFF0A0F1C)
    val Card = Color(0xFF111A2C)
    val CardUp = Color(0xFF18243A)
    val Line = Color(0xFF1E2A42)
    val Txt = Color(0xFFEAF0F9)
    val Soft = Color(0xFF9FB2CD)
    val Dim = Color(0xFF8093B0)
    val Sys = Color(0xFFFF5D73)
    val Dia = Color(0xFF5B9BFF)
    val Sleep = Color(0xFF9D8CFF)
    val Charge = Color(0xFF3EE58A)
    val Drain = Color(0xFFF5A623)
    val Gold = Color(0xFFF0C36D)
}

/** ACC/AHA-graded color: normal green → elevated dim/bright yellow →
 *  stage-1 yellow→orange (deeper = hotter) → stage-2 red → crisis-red. */
fun bpSeverityColor(sys: Int, dia: Int): androidx.compose.ui.graphics.Color {
    fun frac(v: Int, lo: Int, hi: Int) = ((v - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    return when {
        sys >= 140 || dia >= 90 -> {
            val t = maxOf(frac(sys, 140, 180), frac(dia, 90, 110))
            androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color(0xFFFF5D73),
                 androidx.compose.ui.graphics.Color(0xFFFF1F3D), t)
        }
        sys >= 130 || dia >= 80 -> {
            val t = maxOf(frac(sys, 130, 140), frac(dia, 80, 90))
            androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color(0xFFF0C36D),
                 androidx.compose.ui.graphics.Color(0xFFFF8A5D), t)
        }
        sys >= 120 -> androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color(0xFF9A8B5A),
                           androidx.compose.ui.graphics.Color(0xFFF5C542), frac(sys, 120, 130))
        else -> androidx.compose.ui.graphics.Color(0xFF3EE58A)
    }
}
