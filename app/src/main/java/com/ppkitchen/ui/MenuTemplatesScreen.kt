package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
import com.ppkitchen.data.TestDataGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MenuTemplatesScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val templates by db.menuTemplateDao().getAll().collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<MenuTemplate?>(null) }
    var templateToDelete by remember { mutableStateOf<MenuTemplate?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Кнопка генератора (показываем только если шаблонов еще нет)
                if (templates.isEmpty()) {
                    ExtendedFloatingActionButton(
                        text = { Text(if (isGenerating) "Загрузка..." else "Тест: Меню на 30 дней") },
                        icon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = {
                            if (!isGenerating) {
                                isGenerating = true
                                scope.launch {
                                    TestDataGenerator.generateMonthMenu(ctx)
                                    isGenerating = false
                                }
                            }
                        }
                    )
                }

                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, "Создать пустое меню")
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (templates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Шаблонов меню пока нет.\nНажмите + чтобы создать пустой,\nили кнопку ниже для генерации теста.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                                    Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Длительность цикла: ${template.cycleLength} дней",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { templateToDelete = template }) {
                                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
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

@Composable
fun CreateMenuTemplateDialog(
    onSave: (MenuTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cycleLengthText by remember { mutableStateOf("7") }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val length = cycleLengthText.toIntOrNull() ?: 0
                    if (name.isNotBlank() && length > 0) {
                        onSave(MenuTemplate(name = name.trim(), cycleLength = length))
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
}