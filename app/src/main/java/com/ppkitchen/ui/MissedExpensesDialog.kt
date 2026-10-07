package com.ppkitchen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.MissedExpensesRestorer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedExpensesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }

    // По умолчанию — последние 30 дней
    val defaultStart = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -30)
        sdf.format(c.time)
    }

    var startDate by remember { mutableStateOf(defaultStart) }
    var endDate by remember { mutableStateOf(today) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var preview by remember { mutableStateOf<MissedExpensesRestorer.Result?>(null) }
    var actualResult by remember { mutableStateOf<MissedExpensesRestorer.Result?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text("Восстановление расходов") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Автоматически создаст пропущенные расходы: " +
                            "«Доставка (авто)» и «Зарплата повара (авто)» за дни, " +
                            "по которым их нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text("Период", style = MaterialTheme.typography.titleSmall)

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

                Text(
                    "Будущие дни не учитываются. Дубликаты не создаются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // ===== Результат предпросмотра =====
                if (preview != null && actualResult == null) {
                    val p = preview!!
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Будет создано (предпросмотр):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(6.dp))

                            if (p.totalCreated == 0) {
                                Text(
                                    "Нечего восстанавливать — все записи уже созданы.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                if (p.deliveryCreated > 0) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Доставка (авто)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "${p.deliveryCreated} шт. · ${p.deliveryAmount} ₽",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (p.cookCreated > 0) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Зарплата повара (авто)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "${p.cookCreated} шт. · ${p.cookAmount} ₽",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Divider()
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Итого",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${p.totalCreated} шт. · ${p.totalAmount} ₽",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                try {
                                    val result = MissedExpensesRestorer.restore(
                                        context = ctx,
                                        startDate = startDate,
                                        endDate = endDate,
                                        dryRun = false
                                    )
                                    actualResult = result
                                    preview = null
                                } catch (e: Exception) {
                                    // ignore
                                } finally {
                                    isWorking = false
                                }
                            }
                        },
                        enabled = p.totalCreated > 0 && !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Восстановить за период")
                    }
                }

                // ===== Результат реального создания =====
                if (actualResult != null) {
                    val r = actualResult!!
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "✅ Восстановление завершено",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            if (r.totalCreated == 0) {
                                Text(
                                    "Нечего было создавать.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    "Создано записей: ${r.totalCreated}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "На общую сумму: ${r.totalAmount} ₽",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Откройте «Расходы» — новые записи уже там.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ===== Кнопка предпросмотра (если ещё нет результата) =====
                if (actualResult == null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                try {
                                    preview = MissedExpensesRestorer.restore(
                                        context = ctx,
                                        startDate = startDate,
                                        endDate = endDate,
                                        dryRun = true
                                    )
                                } catch (e: Exception) {
                                    // ignore
                                } finally {
                                    isWorking = false
                                }
                            }
                        },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (preview == null) "Проверить, что пропущено" else "Обновить предпросмотр")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initialDate = startDate,
            onDateSelected = { startDate = it; showStartPicker = false; preview = null; actualResult = null },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        SimpleDatePickerDialog(
            initialDate = endDate,
            onDateSelected = { endDate = it; showEndPicker = false; preview = null; actualResult = null },
            onDismiss = { showEndPicker = false }
        )
    }
}