package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.MenuDay
import com.ppkitchen.data.MenuTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MenuTemplatesScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val templates by db.menuTemplateDao().getAll().collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<MenuTemplate?>(null) }
    var templateToDelete by remember { mutableStateOf<MenuTemplate?>(null) }
    var templateToEdit by remember { mutableStateOf<MenuTemplate?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Создать меню")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (templates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Шаблонов меню пока нет.\nНажмите + чтобы создать.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates, key = { it.id }) { template ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedTemplate = template }
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        template.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Старт: ${formatDisplayDate(template.startDate)} · Цикл: ${template.cycleLength} дн.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // НОВАЯ КНОПКА РЕДАКТИРОВАНИЯ
                                IconButton(onClick = { templateToEdit = template }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        "Редактировать",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { templateToDelete = template }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        "Удалить",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateMenuTemplateDialog(
            onSave = { newTemplate ->
                scope.launch {
                    val templateId = db.menuTemplateDao().insert(newTemplate)
                    for (i in 1..newTemplate.cycleLength) {
                        db.menuDayDao().insert(MenuDay(templateId = templateId, dayNumber = i))
                    }
                }
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // ДИАЛОГ РЕДАКТИРОВАНИЯ ШАБЛОНА
    if (templateToEdit != null) {
        EditMenuTemplateDialog(
            template = templateToEdit!!,
            onSave = { updatedTemplate ->
                scope.launch {
                    db.menuTemplateDao().update(updatedTemplate)

                    // Обновляем дни, если изменилась длина цикла
                    val currentDays = db.menuDayDao().getDaysForTemplate(updatedTemplate.id).first()
                    val currentCount = currentDays.size

                    if (updatedTemplate.cycleLength > currentCount) {
                        // Добавляем недостающие дни
                        for (i in (currentCount + 1)..updatedTemplate.cycleLength) {
                            db.menuDayDao().insert(MenuDay(templateId = updatedTemplate.id, dayNumber = i))
                        }
                    } else if (updatedTemplate.cycleLength < currentCount) {
                        // Удаляем лишние дни и их блюда
                        currentDays.filter { it.dayNumber > updatedTemplate.cycleLength }.forEach { day ->
                            db.menuDayDishDao().deleteForDay(day.id)
                            db.menuDayDao().delete(day)
                        }
                    }
                }
                templateToEdit = null
            },
            onDismiss = { templateToEdit = null }
        )
    }

    if (selectedTemplate != null) {
        MenuTemplateEditScreen(
            template = selectedTemplate!!,
            onDismiss = { selectedTemplate = null }
        )
    }

    if (templateToDelete != null) {
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Удалить шаблон?") },
            text = { Text("Удалить «${templateToDelete!!.name}»? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = templateToDelete ?: return@TextButton
                    scope.launch {
                        val days = db.menuDayDao().getDaysForTemplate(target.id).first()
                        days.forEach { day ->
                            db.menuDayDishDao().deleteForDay(day.id)
                            db.menuDayDao().delete(day)
                        }
                        db.menuTemplateDao().delete(target)
                        templateToDelete = null
                    }
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMenuTemplateDialog(
    onSave: (MenuTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var name by remember { mutableStateOf("") }
    var cycleLengthText by remember { mutableStateOf("7") }
    var startDate by remember { mutableStateOf(sdf.format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый шаблон меню") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (напр. Меню на 45 дней)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cycleLengthText,
                    onValueChange = { cycleLengthText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Длина цикла (в днях)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Дата старта: $startDate", style = MaterialTheme.typography.bodySmall)
                }
                Text("💡 С этой даты начнется 1-й день меню.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val length = cycleLengthText.toIntOrNull() ?: 0
                    if (name.isNotBlank() && length > 0) {
                        onSave(MenuTemplate(name = name.trim(), cycleLength = length, startDate = startDate))
                    }
                }
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDate = startDate,
            onDateSelected = { startDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMenuTemplateDialog(
    template: MenuTemplate,
    onSave: (MenuTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(template.name) }
    var cycleLengthText by remember { mutableStateOf(template.cycleLength.toString()) }
    var startDate by remember { mutableStateOf(template.startDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать шаблон") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cycleLengthText,
                    onValueChange = { cycleLengthText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Длина цикла (в днях)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Дата старта: $startDate", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "💡 Внимание: изменение длины цикла может удалить старые дни и блюда в них.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val length = cycleLengthText.toIntOrNull() ?: 0
                    if (name.isNotBlank() && length > 0) {
                        onSave(template.copy(name = name.trim(), cycleLength = length, startDate = startDate))
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDate = startDate,
            onDateSelected = { startDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}