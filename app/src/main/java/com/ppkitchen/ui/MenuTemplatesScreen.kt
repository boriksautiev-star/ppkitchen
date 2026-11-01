package com.ppkitchen.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppkitchen.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // --- 1. СКАЧИВАНИЕ ПУСТОГО ШАБЛОНА ---
    val downloadTemplateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelHelper.createMenuTemplate(out)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Шаблон Excel сохранен", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- 2. ЭКСПОРТ КОНКРЕТНОГО МЕНЮ ---
    var templateToExport by remember { mutableStateOf<MenuTemplate?>(null) }
    val exportMenuLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null && templateToExport != null) {
            val currentTemplate = templateToExport!!
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelHelper.exportMenu(out, currentTemplate, db)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Меню экспортировано", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun exportMenuToExcel(template: MenuTemplate) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val safeName = template.name.replace(Regex("[^a-zA-Z0-9а-яА-Я ]"), "").replace(" ", "_")
        val fileName = "Меню_${safeName}_${sdf.format(Date())}.xlsx"
        templateToExport = template
        exportMenuLauncher.launch(fileName)
    }

    // --- 3. ИМПОРТ В КОНКРЕТНОЕ МЕНЮ ---
    var templateToImport by remember { mutableStateOf<MenuTemplate?>(null) }
    val importMenuLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && templateToImport != null) {
            val currentTemplate = templateToImport!!
            scope.launch {
                try {
                    val addedCount = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            ExcelHelper.importMenu(input, currentTemplate, db)
                        } ?: 0
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Импортировано блюд: $addedCount", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun importMenuFromExcel(template: MenuTemplate) {
        templateToImport = template
        importMenuLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
    }

    // --- UI ---
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Создать меню")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            OutlinedButton(
                onClick = { downloadTemplateLauncher.launch("Шаблон_меню.xlsx") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.DateRange, null)
                Spacer(Modifier.width(8.dp))
                Text("Скачать шаблон Excel для меню")
            }

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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates, key = { it.id }) { template ->
                        Card(
                            Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable { selectedTemplate = template }
                                ) {
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

                                IconButton(onClick = { importMenuFromExcel(template) }) {
                                    Icon(
                                        Icons.Default.FileUpload,
                                        "Импорт",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                IconButton(onClick = { exportMenuToExcel(template) }) {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        "Экспорт",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                IconButton(onClick = { templateToEdit = template }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        "Настройки",
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

    if (templateToEdit != null) {
        EditMenuTemplateDialog(
            template = templateToEdit!!,
            onSave = { updatedTemplate ->
                scope.launch {
                    db.menuTemplateDao().update(updatedTemplate)

                    val currentDays = db.menuDayDao().getDaysForTemplate(updatedTemplate.id).first()
                    val currentCount = currentDays.size

                    if (updatedTemplate.cycleLength > currentCount) {
                        for (i in (currentCount + 1)..updatedTemplate.cycleLength) {
                            db.menuDayDao().insert(MenuDay(templateId = updatedTemplate.id, dayNumber = i))
                        }
                    } else if (updatedTemplate.cycleLength < currentCount) {
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