package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.Cook
import kotlinx.coroutines.launch

val COOK_DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CooksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val allCooks by db.cookDao().getAll().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCook by remember { mutableStateOf<Cook?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactTopBar(
                title = "Повара",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            if (allCooks.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Пока нет поваров.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Нажмите +, чтобы добавить первого.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allCooks, key = { it.id }) { c ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingCook = c }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        c.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (c.active) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (!c.active) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                "неактивен",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                if (c.phone.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Тел: ${c.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                // Ставки: 2 в ряд
                                val rates = listOf(
                                    c.rateMon, c.rateTue, c.rateWed, c.rateThu,
                                    c.rateFri, c.rateSat, c.rateSun
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CookRateBadge("Пн", rates[0], Modifier.weight(1f))
                                        CookRateBadge("Вт", rates[1], Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CookRateBadge("Ср", rates[2], Modifier.weight(1f))
                                        CookRateBadge("Чт", rates[3], Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CookRateBadge("Пт", rates[4], Modifier.weight(1f))
                                        CookRateBadge("Сб", rates[5], Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CookRateBadge("Вс", rates[6], Modifier.weight(1f))
                                        Spacer(Modifier.weight(1f))
                                    }
                                }

                                if (c.comment.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        c.comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CookEditDialog(
            existing = null,
            onSave = { newCook ->
                scope.launch {
                    db.cookDao().insert(newCook)
                    showAddDialog = false
                }
            },
            onUpdate = null,
            onDelete = null,
            onCancel = { showAddDialog = false }
        )
    }

    if (editingCook != null) {
        CookEditDialog(
            existing = editingCook,
            onSave = null,
            onUpdate = { updated ->
                scope.launch {
                    db.cookDao().update(updated)
                    editingCook = null
                }
            },
            onDelete = { toDelete ->
                scope.launch {
                    db.cookDao().delete(toDelete)
                    editingCook = null
                }
            },
            onCancel = { editingCook = null }
        )
    }
}

/** Компактный бейдж «день — ставка» для карточки повара */
@Composable
private fun CookRateBadge(day: String, rate: Int, modifier: Modifier = Modifier) {
    Surface(
        color = if (rate > 0)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                day,
                style = MaterialTheme.typography.labelMedium,
                color = if (rate > 0)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (rate > 0) "$rate ₽" else "—",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (rate > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (rate > 0)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookEditDialog(
    existing: Cook?,
    onSave: ((Cook) -> Unit)?,
    onUpdate: ((Cook) -> Unit)?,
    onDelete: ((Cook) -> Unit)?,
    onCancel: () -> Unit
) {
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }

    // Ставки по дням
    var rateMonText by remember { mutableStateOf(existing?.rateMon?.takeIf { it > 0 }?.toString() ?: "") }
    var rateTueText by remember { mutableStateOf(existing?.rateTue?.takeIf { it > 0 }?.toString() ?: "") }
    var rateWedText by remember { mutableStateOf(existing?.rateWed?.takeIf { it > 0 }?.toString() ?: "") }
    var rateThuText by remember { mutableStateOf(existing?.rateThu?.takeIf { it > 0 }?.toString() ?: "") }
    var rateFriText by remember { mutableStateOf(existing?.rateFri?.takeIf { it > 0 }?.toString() ?: "") }
    var rateSatText by remember { mutableStateOf(existing?.rateSat?.takeIf { it > 0 }?.toString() ?: "") }
    var rateSunText by remember { mutableStateOf(existing?.rateSun?.takeIf { it > 0 }?.toString() ?: "") }

    var active by remember { mutableStateOf(existing?.active ?: true) }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text(if (isEdit) "Редактировать повара" else "Новый повар") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Телефон") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text("Ставка за выход по дням", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Введите сумму за день. Пустое поле — выходной.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Ряд 1: Пн, Вт
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rateMonText,
                        onValueChange = { rateMonText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Пн") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rateTueText,
                        onValueChange = { rateTueText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Вт") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Ряд 2: Ср, Чт
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rateWedText,
                        onValueChange = { rateWedText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Ср") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rateThuText,
                        onValueChange = { rateThuText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Чт") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Ряд 3: Пт, Сб
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rateFriText,
                        onValueChange = { rateFriText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Пт") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rateSatText,
                        onValueChange = { rateSatText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Сб") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Ряд 4: Вс (одна, по центру) + пустое место
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rateSunText,
                        onValueChange = { rateSunText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Вс") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "Быстрые действия:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            val mon = rateMonText
                            rateTueText = mon
                            rateWedText = mon
                            rateThuText = mon
                            rateFriText = mon
                        },
                        label = { Text("Пн → Пт", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            rateSatText = ""
                            rateSunText = ""
                        },
                        label = { Text("Сб/Вс = выходной", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(2f)
                    )
                }

                HorizontalDivider()

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Активен", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Неактивные не получают авто-расходов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val cook = Cook(
                    id = existing?.id ?: 0,
                    name = name.trim(),
                    phone = phone.trim(),
                    rateMon = rateMonText.toIntOrNull() ?: 0,
                    rateTue = rateTueText.toIntOrNull() ?: 0,
                    rateWed = rateWedText.toIntOrNull() ?: 0,
                    rateThu = rateThuText.toIntOrNull() ?: 0,
                    rateFri = rateFriText.toIntOrNull() ?: 0,
                    rateSat = rateSatText.toIntOrNull() ?: 0,
                    rateSun = rateSunText.toIntOrNull() ?: 0,
                    active = active,
                    comment = comment.trim()
                )
                if (isEdit) onUpdate?.invoke(cook)
                else onSave?.invoke(cook)
            }) { Text(if (isEdit) "Сохранить" else "Создать") }
        },
        dismissButton = {
            Row {
                if (isEdit && onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    )

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить повара?") },
            text = {
                Text(
                    "Повар «${existing.name}» будет удалён из справочника. " +
                            "Созданные ранее расходы останутся в разделе «Расходы»."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke(existing)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }
}