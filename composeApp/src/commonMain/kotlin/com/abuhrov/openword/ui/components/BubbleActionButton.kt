package com.abuhrov.openword.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun BubbleActionButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
        )
    }
}
