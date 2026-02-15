package com.abuhrov.openword.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NavigationGridItem(number: Long, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(50.dp).fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
