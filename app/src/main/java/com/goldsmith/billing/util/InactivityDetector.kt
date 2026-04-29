package com.goldsmith.billing.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * Modifier extension to detect user interaction and reset an inactivity timer.
 * Used in MainActivity via onUserInteraction(), but can also be applied per-screen.
 */
@Composable
fun rememberInactivityState(
    timeoutSeconds: Int = 30,
    onTimeout: () -> Unit
): MutableState<Long> {
    val lastActivity = remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastActivity.value) {
        delay(timeoutSeconds * 1000L)
        val now = System.currentTimeMillis()
        if (now - lastActivity.value >= timeoutSeconds * 1000L) {
            onTimeout()
        }
    }

    return lastActivity
}

fun Modifier.resetInactivityOnTouch(lastActivity: MutableState<Long>): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(onPress = { lastActivity.value = System.currentTimeMillis() })
    }
