package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.ppkitchen.data.Courier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouriersScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val allCouriers by db.courierDao().getAll().collectAsState(initial = emptyList())

    var statusFilter by remember { mutableStateOf("все") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCourier by remember { mutableStateOf<Courier?>(null) }

    val statuses = listOf("все", "активен", "неактивен")

    val filtered = allCouriers.filter { c ->
        statusFilter == "все" || c.status == statusFilter
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactTopBar(
                title = "Курьеры",
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                statuses.forEach { s ->
                    FilterChip(
                        selected = statusFilter == s,
                        onClick = { statusFilter = s },
                        label = { Text(s) }
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (allCouriers.isEmpty()) "Пока нет курьеров.\nНажмите + чтобы добавить."
                        else "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { c ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingCourier = c }
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
                                        fontWeight = if (c.status == "активен") FontWeight.Bold else FontWeight.Normal
                                    )
                                    StatusBadgeCourier(c.status)
                                }
                                if (c.phone.isNotBlank()) {
                                    Text("Тел: ${c.phone}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (c.comment.isNotBlank()) {
                                    Text(c.comment, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CourierDialog(
            existing = null,
            onSave = { newCourier ->
                scope.launch {
                    db.courierDao().insert(newCourier)
                    showAddDialog = false
                }
            },
            onUpdate = null,
            onDelete = null,
            onCancel = { showAddDialog = false }
        )
    }

    if (editingCourier != null) {
        CourierDialog(
            existing = editingCourier,
            onSave = null,
            onUpdate = { updated ->
                scope.launch {
                    db.courierDao().update(updated)
                    editingCourier = null
                }
            },
            onDelete = { toDelete ->
                scope.launch {
                    db.courierDao().delete(toDelete)
                    editingCourier = null
                }
            },
            onCancel = { editingCourier = null }
        )
    }
}

@Composable
fun StatusBadgeCourier(status: String) {
    val (bg, fg) = if (status == "активен") {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierDialog(
    existing: Courier?,
    onSave: ((Courier) -> Unit)?,
    onUpdate: ((Courier) -> Unit)?,
    onDelete: ((Courier) -> Unit)?,
    onCancel: () -> Unit
) {
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var status by remember { mutableStateOf(existing?.status ?: "активен") }

    val statuses = listOf("активен", "неактивен")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text(if (isEdit) "Редактировать курьера" else "Новый курьер") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Имя*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("Телефон") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    comment,
                    { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Статус: $status")
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        statuses.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { status = it; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val courier = Courier(
                        id = existing?.id ?: 0,
                        name = name.trim(),
                        phone = phone.trim(),
                        comment = comment.trim(),
                        status = status
                    )
                    if (isEdit) onUpdate?.invoke(courier)
                    else onSave?.invoke(courier)
                }
            }) { Text(if (isEdit) "Сохранить" else "Создать") }
        },
        dismissButton = {
            Row {
                if (isEdit && onDelete != null) {
                    TextButton(onClick = { onDelete(existing!!) }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    )
}