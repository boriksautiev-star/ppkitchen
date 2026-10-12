package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.CashDeposit
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingDeposit by remember { mutableStateOf<CashDeposit?>(null) }

    val allDeposits by db.cashDepositDao().getAll().collectAsState(initial = emptyList())
    val depositsTotal by db.cashDepositDao().depositsTotal().collectAsState(initial = 0)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CompactTopBar(title = "Касса") },
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
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Внесено вами всего",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${depositsTotal ?: 0} ₽",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Это ваши личные средства, вложенные в бизнес. " +
                                "На выручку и прибыль они не влияют.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (allDeposits.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Пока нет внесений.\nНажмите + чтобы добавить.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allDeposits, key = { it.id }) { d ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingDeposit = d }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Внесение",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "+${d.amount} ₽",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    formatDisplayDate(d.date),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (d.comment.isNotEmpty()) {
                                    Text(
                                        d.comment,
                                        style = MaterialTheme.typography.bodySmall
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
        CashDialog(
            existing = null,
            onSave = { newDeposit ->
                scope.launch {
                    db.cashDepositDao().insert(newDeposit)
                    showCreateDialog = false
                }
            },
            onUpdate = null,
            onDelete = null,
            onCancel = { showCreateDialog = false }
        )
    }

    if (editingDeposit != null) {
        CashDialog(
            existing = editingDeposit,
            onSave = null,
            onUpdate = { updated ->
                scope.launch {
                    db.cashDepositDao().update(updated)
                    editingDeposit = null
                }
            },
            onDelete = { toDelete ->
                scope.launch {
                    db.cashDepositDao().delete(toDelete)
                    editingDeposit = null
                }
            },
            onCancel = { editingDeposit = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashDialog(
    existing: CashDeposit?,
    onSave: ((CashDeposit) -> Unit)?,
    onUpdate: ((CashDeposit) -> Unit)?,
    onDelete: ((CashDeposit) -> Unit)?,
    onCancel: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }
    val isEdit = existing != null

    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: today) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text(if (isEdit) "Редактировать внесение" else "Внести средства") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    amount,
                    { amount = it.filter { ch -> ch.isDigit() } },
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

                Text(
                    "Внесения не уменьшают прибыль и не увеличивают выручку. " +
                            "Это отдельный поток ваших личных средств в бизнес.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toIntOrNull()
                if (a == null || a <= 0) return@TextButton

                if (isEdit) {
                    onUpdate?.invoke(
                        existing!!.copy(
                            amount = a,
                            comment = comment.trim(),
                            date = date
                        )
                    )
                } else {
                    onSave?.invoke(
                        CashDeposit(
                            amount = a,
                            comment = comment.trim(),
                            date = date
                        )
                    )
                }
            }) { Text(if (isEdit) "Сохранить" else "Внести") }
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

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDate = date,
            onDateSelected = { date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}