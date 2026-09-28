package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.Expense
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val EXPENSE_CATEGORIES = listOf(
    "Закупка",
    "Упаковка",
    "Зарплата (ручная)",
    "Зарплата повара (авто)",
    "Аренда",
    "Доставка",
    "Налоги",
    "Реклама",
    "Прочее"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }

    val defaultStart = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        sdf.format(c.time)
    }

    var startDate by remember { mutableStateOf(defaultStart) }
    var endDate by remember { mutableStateOf(today) }
    var categoryFilter by remember { mutableStateOf("Все") }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    var showBreakdown by remember { mutableStateOf(false) }

    val allExpenses by db.expenseDao().getAll().collectAsState(initial = emptyList())

    val filtered = allExpenses.filter { e ->
        val inDateRange = e.date >= startDate && e.date <= endDate
        val matchesCategory = categoryFilter == "Все" || e.category == categoryFilter
        inDateRange && matchesCategory
    }

    val totalAmount = filtered.sumOf { it.amount }
    val breakdown = filtered
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CompactTopBar(title = "Расходы") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Фильтр", style = MaterialTheme.typography.titleMedium)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DateRange, null)
                            Spacer(Modifier.width(4.dp))
                            Text("С: $startDate", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DateRange, null)
                            Spacer(Modifier.width(4.dp))
                            Text("По: $endDate", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = categoryFilter == "Все",
                            onClick = { categoryFilter = "Все" },
                            label = { Text("Все") }
                        )
                        EXPENSE_CATEGORIES.forEach { cat ->
                            FilterChip(
                                selected = categoryFilter == cat,
                                onClick = { categoryFilter = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable { showBreakdown = !showBreakdown }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Итого за период", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$totalAmount ₽",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (showBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showBreakdown) "Свернуть" else "Развернуть"
                        )
                    }

                    if (showBreakdown) {
                        if (breakdown.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "По категориям",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            breakdown.forEach { (cat, sum) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat, style = MaterialTheme.typography.bodyMedium)
                                    Text("$sum ₽", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Пока нет данных за выбранный период",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (breakdown.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Нажмите, чтобы развернуть по категориям",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (allExpenses.isEmpty()) "Пока нет расходов.\nНажмите + чтобы добавить."
                        else "По фильтру ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { e ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingExpense = e }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        e.category,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (e.category == "Зарплата повара (авто)")
                                            FontWeight.Normal
                                        else
                                            FontWeight.Medium
                                    )
                                    Text(
                                        "${e.amount} ₽",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                // Дата + время
                                Text(
                                    if (e.time.isNotBlank())
                                        "${formatDisplayDate(e.date)} · ${e.time}"
                                    else
                                        formatDisplayDate(e.date),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (e.comment.isNotEmpty()) {
                                    Text(e.comment, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ExpenseDialog(
            existingExpense = null,
            onSave = { expense ->
                scope.launch {
                    db.expenseDao().insert(expense)
                    showCreateDialog = false
                }
            },
            onUpdate = null,
            onDelete = null,
            onCancel = { showCreateDialog = false }
        )
    }

    if (editingExpense != null) {
        ExpenseDialog(
            existingExpense = editingExpense,
            onSave = null,
            onUpdate = { updated ->
                scope.launch {
                    db.expenseDao().update(updated)
                    editingExpense = null
                }
            },
            onDelete = { toDelete ->
                scope.launch {
                    db.expenseDao().delete(toDelete)
                    editingExpense = null
                }
            },
            onCancel = { editingExpense = null }
        )
    }

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initialDate = startDate,
            onDateSelected = { startDate = it; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        SimpleDatePickerDialog(
            initialDate = endDate,
            onDateSelected = { endDate = it; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    existingExpense: Expense?,
    onSave: ((Expense) -> Unit)?,
    onUpdate: ((Expense) -> Unit)?,
    onDelete: ((Expense) -> Unit)?,
    onCancel: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }
    val nowTime = remember { sdfTime.format(Date()) }
    val isEdit = existingExpense != null

    var category by remember { mutableStateOf(existingExpense?.category ?: EXPENSE_CATEGORIES[0]) }
    var amount by remember { mutableStateOf(existingExpense?.amount?.toString() ?: "") }
    var comment by remember { mutableStateOf(existingExpense?.comment ?: "") }
    var date by remember { mutableStateOf(existingExpense?.date ?: today) }
    var time by remember {
        mutableStateOf(
            if (isEdit) existingExpense?.time ?: "" else nowTime
        )
    }

    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text(if (isEdit) "Редактировать расход" else "Новый расход") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Категория: $category")
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        EXPENSE_CATEGORIES.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { category = it; expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Сумма, ₽") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Дата: $date")
                }

                // Время
                OutlinedTextField(
                    value = time,
                    onValueChange = { input ->
                        // Разрешаем только цифры и двоеточие, максимум 5 символов
                        time = input.filter { ch -> ch.isDigit() || ch == ':' }.take(5)
                    },
                    label = { Text("Время (ЧЧ:ММ)") },
                    singleLine = true,
                    placeholder = { Text("14:35") },
                    supportingText = {
                        Text(
                            "По умолчанию — текущее время. Можно изменить.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    comment,
                    { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toIntOrNull()
                if (a == null || a <= 0) return@TextButton

                // Простая валидация времени: если введено — оставляем как есть.
                // Если пусто — значит пользователь очистил, сохраняем пустым.
                val normalizedTime = time.trim()

                if (isEdit) {
                    onUpdate?.invoke(
                        existingExpense!!.copy(
                            category = category,
                            amount = a,
                            comment = comment.trim(),
                            date = date,
                            time = normalizedTime
                        )
                    )
                } else {
                    onSave?.invoke(
                        Expense(
                            category = category,
                            amount = a,
                            comment = comment.trim(),
                            date = date,
                            time = normalizedTime
                        )
                    )
                }
            }) { Text(if (isEdit) "Сохранить" else "Создать") }
        },
        dismissButton = {
            Row {
                if (isEdit && onDelete != null) {
                    TextButton(onClick = { onDelete(existingExpense!!) }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    )

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDate = date,
            onDateSelected = { date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

private fun formatDisplayDate(isoDate: String): String {
    return try {
        val inSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outSdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val d = inSdf.parse(isoDate)
        if (d != null) outSdf.format(d) else isoDate
    } catch (e: Exception) {
        isoDate
    }
}