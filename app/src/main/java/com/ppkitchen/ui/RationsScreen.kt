package com.ppkitchen.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.Ration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RationsScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val rations by db.rationDao().getAll().collectAsState(initial = emptyList())
    var showEditDialog by remember { mutableStateOf(false) }
    var editingRation by remember { mutableStateOf<Ration?>(null) }
    var rationToDelete by remember { mutableStateOf<Ration?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditDialog = true }) {
                Icon(Icons.Default.Add, "Создать рацион")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (rations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Рационов пока нет.\nНажмите + чтобы создать.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rations, key = { it.id }) { ration ->
                        Card(
                            Modifier.fillMaxWidth().clickable { editingRation = ration }
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ration.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (ration.calories > 0) {
                                        Text("Тариф: ${ration.calories} ккал", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("Включает: ${ration.categories}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { rationToDelete = ration }) {
                                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog || editingRation != null) {
        RationEditDialog(
            ration = editingRation,
            onSave = { newRation ->
                // ИСПРАВЛЕНИЕ: Проверяем ID ДО запуска корутины и закрытия окна
                val isUpdate = newRation.id > 0

                scope.launch {
                    try {
                        if (isUpdate) {
                            db.rationDao().update(newRation)
                        } else {
                            db.rationDao().insert(newRation)
                        }
                    } catch (e: Exception) {
                        Log.e("RationSave", "Ошибка сохранения", e)
                        Toast.makeText(ctx, "Ошибка при сохранении: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                showEditDialog = false
                editingRation = null
            },
            onDismiss = {
                showEditDialog = false
                editingRation = null
            }
        )
    }

    if (rationToDelete != null) {
        AlertDialog(
            onDismissRequest = { rationToDelete = null },
            title = { Text("Удалить рацион?") },
            text = { Text("Удалить «${rationToDelete!!.name}»?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            db.rationDao().delete(rationToDelete!!)
                        } catch (e: Exception) {
                            Log.e("RationDelete", "Ошибка удаления", e)
                        }
                    }
                    rationToDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { rationToDelete = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun RationEditDialog(
    ration: Ration?,
    onSave: (Ration) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val allCategories by db.dishCategoryDao().getAll().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf(ration?.name ?: "") }

    // Если калорийность больше 0, показываем её, иначе пусто
    var caloriesText by remember { mutableStateOf(if ((ration?.calories ?: 0) > 0) ration!!.calories.toString() else "") }

    val initialSelected = remember(ration) {
        ration?.categories?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    var selectedCategories by remember { mutableStateOf(initialSelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ration == null) "Новый рацион" else "Редактировать рацион") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (напр. Стандарт 1800)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Калорийность (для справки)") },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text("Приемы пищи (категории блюд):", style = MaterialTheme.typography.titleSmall)

                if (allCategories.isEmpty()) {
                    Text("Сначала создайте категории блюд (Завтрак, Обед и т.д.)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    allCategories.forEach { cat ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedCategories = if (selectedCategories.contains(cat.name)) {
                                    selectedCategories - cat.name
                                } else {
                                    selectedCategories + cat.name
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedCategories.contains(cat.name),
                                onCheckedChange = {
                                    selectedCategories = if (it) selectedCategories + cat.name else selectedCategories - cat.name
                                }
                            )
                            Text(cat.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // УМНАЯ ЛОГИКА: Если поле калорий пустое, пытаемся вытащить цифру из названия
                    val extractedCal = name.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val kcal = caloriesText.toIntOrNull() ?: extractedCal

                    if (name.isNotBlank() && selectedCategories.isNotEmpty()) {
                        onSave(
                            Ration(
                                id = ration?.id ?: 0,
                                name = name.trim(),
                                calories = kcal,
                                categories = selectedCategories.joinToString(",")
                            )
                        )
                    }
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}