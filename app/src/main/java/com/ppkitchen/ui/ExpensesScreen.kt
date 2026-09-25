package com.ppkitchen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
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
    "Закупка", "Упаковка", "Зарплата", "Аренда",
    "Доставка", "Налоги", "Реклама", "Прочее"
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

    // Сворачивание блоков
    var showFilter by remember { mutableStateOf(false) }
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

    // Показываем ли активный фильтр (когда что-то выбрано не по умолчанию)
    val isFilterActive = startDate != defaultStart ||
            endDate != today ||
            categoryFilter != "Все"

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
            // ===== Сворачиваемый фильтр =====
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable { showFilter = !showFilter }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                tint = if (isFilterActive)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Фильтр",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (isFilterActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "активен",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp, vertical = 2.dp
                                        )
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = if (showFilter)
                                Icons.Default.ExpandLess
                            else
                                Icons.Default.ExpandMore,
                            contentDescription = if (showFilter) "Свернуть" else "Развернуть"
                        )
                    }

                    // Краткая инфо, когда фильтр свёрнут, но активен
                    if (!showFilter && isFilterActive) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("$startDate — $endDate")
                                if (categoryFilter != "Все") append(" · $categoryFilter")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showFilter) {
                        Spacer(Modifier.height(10.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showStartPicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("С: $startDate", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = { showEndPicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("По: $endDate", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

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

                        if (isFilterActive) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    startDate = defaultStart
                                    endDate = today
                                    categoryFilter = "Все"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Сбросить фильтр")
                            }
                        }
                    }
                }
            }

            // ===== Итого за период (сворачиваемое) =====
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
                            imageVector = if (showBreakdown)
                                Icons.Default.ExpandLess
                            else
                                Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    if (showBreakdown) {
                        if (breakdown.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Divider()
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

            // ===== Список расходов =====
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
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Левая часть — категория, комментарий
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        e.category,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (e.comment.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            e.comment,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // Правая часть — дата (заметная) и сумма
                                Column(horizontalAlignment = Alignment.End) {
                                    // Заметная дата — бейдж
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            formatDisplayDate(e.date),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp, vertical = 4.dp
                                            )
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${e.amount} ₽",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
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
    val today = remember { sdf.format(Date()) }
    val isEdit = existingExpense != null

    var category by remember { mutableStateOf(existingExpense?.category ?: EXPENSE_CATEGORIES[0]) }
    var amount by remember { mutableStateOf(existingExpense?.amount?.toString() ?: "") }
    var comment by remember { mutableStateOf(existingExpense?.comment ?: "") }
    var date by remember { mutableStateOf(existingExpense?.date ?: today) }

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

                if (isEdit) {
                    onUpdate?.invoke(
                        existingExpense!!.copy(
                            category = category,
                            amount = a,
                            comment = comment.trim(),
                            date = date
                        )
                    )
                } else {
                    onSave?.invoke(
                        Expense(
                            category = category,
                            amount = a,
                            comment = comment.trim(),
                            date = date
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