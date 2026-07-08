package com.example.pulseledger.ui

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer

/** Fade + rise entrance; call with an increasing index for a stagger. */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    return this
        .alpha(progress.value)
        .graphicsLayer { translationY = (1f - progress.value) * 28f }
}

private suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
