package com.abuhrov.openword.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A modifier that handles background dismissal taps while ignoring the bottom area 
 * where the app-switcher gesture (swipe-up) begins.
 */
fun Modifier.safeDismissClick(onDismiss: () -> Unit): Modifier = this.pointerInput(Unit) {
    detectTapGestures { offset ->
        // Ignore touches in the bottom 48dp to avoid accidental dismissal during iOS swipe-up
        val threshold = 48.dp.toPx()
        if (offset.y < size.height - threshold) {
            onDismiss()
        }
    }
}
