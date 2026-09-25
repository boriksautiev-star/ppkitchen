package com.ppkitchen.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.BackupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val calendar = remember { Calendar.getInstance() }
    val defaultStart = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        sdf.format(c.time)
    }
    val defaultEnd = remember { sdf.format(calendar.time) }
    val todayStr = remember { sdf.format(Date()) }

    var startDate by remember { mutableStateOf(defaultStart) }
    var endDate by remember { mutableStateOf(defaultEnd) }
    val isPeriodModified = startDate != defaultStart || endDate != defaultEnd

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf<Uri?>(null) }
    var showBackupSettings by remember { mutableStateOf(false) }
    var showFinanceDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) showRestoreConfirm = uri }

    val revenue by db.orderDao().revenueBetween(startDate, endDate).collectAsState(initial = 0)
    val expensesTotal by db.expenseDao().expensesBetween(startDate, endDate).collectAsState(initial = 0)
    val depositsPeriod by db.cashDepositDao().depositsBetween(startDate, endDate).collectAsState(initial = 0)
    val uniqueClients by db.orderDao().uniqueClientsBetween(startDate, endDate).collectAsState(initial = 0)
    val caloriesList by db.orderDao().caloriesBreakdown(startDate, endDate).collectAsState(initial = emptyList())
    val clients by db.clientDao().getAll().collectAsState(initial = emptyList())
    val allOrders by db.orderDao().getAll().collectAsState(initial = emptyList())
    val dailyOrders by db.orderDao().getDailyOrders().collectAsState(initial = emptyList())

    val activeCount = clients.count { c ->
        val orders = allOrders.filter { it.clientId == c.id }
        orders.any { it.deliveryStart <= todayStr && it.deliveryEnd >= todayStr } ||
                orders.any { it.deliveryStart > todayStr }
    }

    val dailyRevenue = dailyOrders.sumOf { o ->
        val effStart = if (o.deliveryStart > startDate) o.deliveryStart else startDate
        val effEnd = if (o.deliveryEnd < endDate) o.deliveryEnd else endDate
        val effEndWithToday = if (effEnd > todayStr) todayStr else effEnd
        if (effStart > effEndWithToday) 0
        else {
            val fakeOrder = o.copy(deliveryStart = o.deliveryStart, deliveryEnd = o.deliveryEnd)
            sumForRange(fakeOrder, effStart, effEndWithToday)
        }
    }

    val r = (revenue ?: 0) + dailyRevenue
    val e = expensesTotal ?: 0
    val d = depositsPeriod ?: 0
    val profit = r - e
    val margin = if (r > 0) (profit * 100 / r) else 0
    val totalRations = caloriesList.sumOf { it.count }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactTopBar(
                title = "Отчёт",
                actions = {
                    // Одна кнопка «Финансы»
                    IconButton(onClick = { showFinanceDialog = true }) {
                        Icon(Icons.Default.Analytics, "Финансы")
                    }

                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "Меню")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Настройки") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = {
                                menuExpanded = false
                                showSettingsDialog = true
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Отправить отчёт в Excel") },
                            onClick = {
                                menuExpanded = false
                                scope.launch {
                                    try {
                                        val ordersList = withContext(Dispatchers.IO) {
                                            db.orderDao().ordersBetween(startDate, endDate).first()
                                        }
                                        val expensesList = withContext(Dispatchers.IO) {
                                            db.expenseDao().expensesListBetween(startDate, endDate).first()
                                        }
                                        exportReportAndShare(
                                            context = ctx,
                                            startDate = startDate,
                                            endDate = endDate,
                                            revenue = r,
                                            expensesTotal = e,
                                            profit = profit,
                                            margin = margin,
                                            uniqueClients = uniqueClients,
                                            totalRations = totalRations,
                                            caloriesBreakdown = caloriesList.map { it.calories to it.count },
                                            orders = ordersList,
                                            clients = clients,
                                            expenses = expensesList
                                        )
                                    } catch (ex: Exception) {
                                        Toast.makeText(ctx, "Ошибка: ${ex.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Создать копию и отправить") },
                            onClick = {
                                menuExpanded = false
                                try { backupDatabaseAndShare(ctx) }
                                catch (ex: Exception) {
                                    Toast.makeText(ctx, "Ошибка: ${ex.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Отправить последнюю копию") },
                            onClick = {
                                menuExpanded = false
                                val ok = shareLastBackup(ctx)
                                if (!ok) Toast.makeText(ctx, "Копий ещё нет", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Настройки автокопирования") },
                            onClick = {
                                menuExpanded = false
                                showBackupSettings = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Восстановить из копии") },
                            onClick = {
                                menuExpanded = false
                                filePicker.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Период", style = MaterialTheme.typography.titleMedium)
                        if (isPeriodModified) {
                            TextButton(onClick = {
                                startDate = defaultStart
                                endDate = defaultEnd
                            }) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Сбросить")
                            }
                        }
                    }
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
                }
            }

            StatCard("Выручка", "$r ₽")
            StatCard("Расходы", "$e ₽")
            StatCard("Прибыль", "$profit ₽")
            StatCard("Маржинальность", "$margin %")
            StatCard("Клиентов питалось", "$uniqueClients чел.")

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Продано рационов", style = MaterialTheme.typography.bodyMedium)
                    Text("$totalRations", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    if (caloriesList.isEmpty()) {
                        Text("Пока нет данных за выбранный период", style = MaterialTheme.typography.bodySmall)
                    } else {
                        caloriesList.forEach { stat ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${stat.calories} ккал", style = MaterialTheme.typography.bodyLarge)
                                Text("${stat.count} шт.", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            StatCard("Внесено вами", "$d ₽")
            StatCard("Активных клиентов (всего)", "$activeCount")
        }
    }

    if (showFinanceDialog) FinanceDialog(onDismiss = { showFinanceDialog = false })
    if (showSettingsDialog) SettingsDialog(onDismiss = { showSettingsDialog = false })

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initialDate = startDate,
            forceToday = true,
            onDateSelected = { startDate = it; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        SimpleDatePickerDialog(
            initialDate = endDate,
            forceToday = true,
            onDateSelected = { endDate = it; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }

    if (showBackupSettings) {
        BackupSettingsDialog(onDismiss = { showBackupSettings = false })
    }

    if (showRestoreConfirm != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("Восстановить базу?") },
            text = {
                Text(
                    "Все текущие данные будут заменены содержимым файла копии. " +
                            "Действие нельзя отменить.\n\n" +
                            "После восстановления приложение закроется — откройте его заново."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = showRestoreConfirm!!
                    showRestoreConfirm = null
                    val ok = restoreDatabase(ctx, uri)
                    if (ok) {
                        Toast.makeText(ctx, "Восстановлено. Перезапуск…", Toast.LENGTH_LONG).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            Process.killProcess(Process.myPid())
                        }, 2000)
                    } else {
                        Toast.makeText(ctx, "Не удалось восстановить", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Восстановить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(BackupPreferences.isEnabled(ctx)) }
    var periodDays by remember { mutableStateOf(BackupPreferences.getPeriodDays(ctx)) }
    var keepCount by remember { mutableStateOf(BackupPreferences.getKeepCount(ctx)) }
    var folderUri by remember { mutableStateOf(BackupPreferences.getFolderUri(ctx)) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                BackupPreferences.setFolderUri(ctx, uri.toString())
                folderUri = uri.toString()
                Toast.makeText(ctx, "Папка выбрана", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val periods = listOf(1, 3, 7, 14, 30)
    val keeps = listOf(3, 5, 10, 20, 50)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Автоматическое копирование") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Включено")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Divider()
                Text("Периодичность (дней):", style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    periods.forEach { p ->
                        FilterChip(
                            selected = periodDays == p,
                            onClick = { periodDays = p },
                            label = { Text("$p") }
                        )
                    }
                }
                Text("Хранить копий:", style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    keeps.forEach { k ->
                        FilterChip(
                            selected = keepCount == k,
                            onClick = { keepCount = k },
                            label = { Text("$k") }
                        )
                    }
                }
                Divider()
                Text("Папка для сохранения:", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (folderUri == null) "По умолчанию (Android/data/com.ppkitchen/files/Documents/backups/)"
                    else "Выбрана пользователем",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Выбрать папку") }
                    if (folderUri != null) {
                        OutlinedButton(
                            onClick = {
                                BackupPreferences.setFolderUri(ctx, null)
                                folderUri = null
                                Toast.makeText(ctx, "Сброшено к папке по умолчанию", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Сбросить") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                BackupPreferences.setEnabled(ctx, enabled)
                BackupPreferences.setPeriodDays(ctx, periodDays)
                BackupPreferences.setKeepCount(ctx, keepCount)
                if (enabled) {
                    scheduleAutoBackup(ctx)
                    Toast.makeText(ctx, "Включено: раз в $periodDays дн.", Toast.LENGTH_SHORT).show()
                } else {
                    cancelAutoBackup(ctx)
                    Toast.makeText(ctx, "Выключено", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    runBackupNow(ctx)
                    Toast.makeText(ctx, "Копия создаётся в фоне", Toast.LENGTH_SHORT).show()
                }) { Text("Сделать сейчас") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable
fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}