package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.abuhrov.openword.util.parseCommentaryText

@Composable
fun MarkerNotePopup(
    text: String,
    offset: IntOffset,
    onDismiss: () -> Unit,
    onReferenceClick: (String) -> Unit = {}
) {
    Popup(
        offset = offset,
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(8.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Примітка",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                val styledNote = remember(text) { parseCommentaryText(text.replace("<br>", "\n").replace("<BR>", "\n")) }
                ClickableText(
                    text = styledNote,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    onClick = { pos ->
                        styledNote.getStringAnnotations(tag = "REFERENCE", start = pos, end = pos)
                            .firstOrNull()?.let { annotation ->
                                onReferenceClick(annotation.item)
                            }
                    }
                )
            }
        }
    }
}
