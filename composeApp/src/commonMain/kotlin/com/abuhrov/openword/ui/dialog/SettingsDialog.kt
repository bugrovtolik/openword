package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.NavigationViewMode
import com.abuhrov.openword.model.SearchStrictness
import com.abuhrov.openword.ui.util.safeDismissClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentFontSizeScale: Float,
    currentAutoTranslate: Boolean,
    currentNavViewMode: NavigationViewMode,
    currentSearchStrictness: SearchStrictness,
    onFontSizeChange: (Float) -> Unit,
    onAutoTranslateChange: (Boolean) -> Unit,
    onNavViewModeChange: (NavigationViewMode) -> Unit,
    onSearchStrictnessChange: (SearchStrictness) -> Unit,
    onReloadAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .safeDismissClick { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Налаштування", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити")
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Розмір шрифту: ${(currentFontSizeScale * 100).toInt()}%")
                Slider(
                    value = currentFontSizeScale,
                    onValueChange = onFontSizeChange,
                    valueRange = 0.8f..1.5f,
                    steps = 6
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = currentAutoTranslate,
                        onCheckedChange = onAutoTranslateChange
                    )
                    Text("Автопереклад коментарів")
                }
                Spacer(Modifier.height(16.dp))

                Text("Вигляд навігації", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = currentNavViewMode == NavigationViewMode.LIST,
                        onClick = { onNavViewModeChange(NavigationViewMode.LIST) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Список")
                    }
                    SegmentedButton(
                        selected = currentNavViewMode == NavigationViewMode.GRID,
                        onClick = { onNavViewModeChange(NavigationViewMode.GRID) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Сітка")
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text("Точність пошуку", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = currentSearchStrictness == SearchStrictness.LOOSE,
                        onClick = { onSearchStrictnessChange(SearchStrictness.LOOSE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Вільний")
                    }
                    SegmentedButton(
                        selected = currentSearchStrictness == SearchStrictness.STRICT,
                        onClick = { onSearchStrictnessChange(SearchStrictness.STRICT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Точний")
                    }
                }
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onReloadAllData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Перезавантажити всі дані")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Очищує кеш і завантажує бази даних наново.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
