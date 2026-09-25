package com.ppkitchen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.SettingsPreferences
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }

    val defaultStart = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -6)
        sdf.format(c.time)
    }

    var startDate by remember { mutableStateOf(defaultStart) }
    var endDate by remember { mutableStateOf(today) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Экран: 0 = главный, 1 = разбивка, 2 = цены, 3 = сценарии
    var screen by remember { mutableStateOf(0) }

    val allOrders by db.orderDao().getAll().collectAsState(initial = emptyList())
    val allExpenses by db.expenseDao().getAll().collectAsState(initial = emptyList())

    val courierRate = remember { SettingsPreferences.getCourierRate(ctx) }
    val autoDelivery = remember { SettingsPreferences.isAutoDelivery(ctx) }

    val effectiveEnd = minOf(endDate, today)

    val portions = allOrders.sumOf { o ->
        val effStart = maxOf(o.deliveryStart, startDate)
        val effEnd = minOf(o.deliveryEnd, effectiveEnd)
        if (effStart > effEnd) 0
        else countDeliveriesBetween(effStart, effEnd, parseDays(o.deliveryDaysOfWeek))
    }

    val soldCalories = allOrders.sumOf { o ->
        val effStart = maxOf(o.deliveryStart, startDate)
        val effEnd = minOf(o.deliveryEnd, effectiveEnd)
        if (effStart > effEnd) 0
        else countDeliveriesBetween(effStart, effEnd, parseDays(o.deliveryDaysOfWeek)) * o.calories
    }

    val foodExpenses = allExpenses.filter { it.date >= startDate && it.date <= endDate && it.category == "Закупка" }.sumOf { it.amount }
    val packagingExpenses = allExpenses.filter { it.date >= startDate && it.date <= endDate && it.category == "Упаковка" }.sumOf { it.amount }
    val deliveryExpenses = if (autoDelivery) portions * courierRate
    else allExpenses.filter { it.date >= startDate && it.date <= endDate && it.category == "Доставка" }.sumOf { it.amount }
    val salaryExpenses = allExpenses.filter { it.date >= startDate && it.date <= endDate && it.category == "Зарплата" }.sumOf { it.amount }
    val rentExpenses = allExpenses.filter { it.date >= startDate && it.date <= endDate && it.category == "Аренда" }.sumOf { it.amount }
    val taxExpenses = allExpenses.filter { it.date >= startDate && it.date <= endDate && it.category == "Налоги" }.sumOf { it.amount }
    val adExpenses = allExpenses.filter { it.date >= startDate && it.date <= endDate && it.category == "Реклама" }.sumOf { it.amount }
    val otherExpenses = allExpenses.filter {
        it.date >= startDate && it.date <= endDate &&
                it.category in listOf("Прочее")
    }.sumOf { it.amount }

    val variableExpenses = foodExpenses + packagingExpenses + deliveryExpenses + adExpenses + otherExpenses
    val fixedExpenses = salaryExpenses + rentExpenses + taxExpenses
    val totalExpenses = variableExpenses + fixedExpenses

    val paidOrdersSum = allOrders
        .filter { it.date >= startDate && it.date <= endDate && it.paid && !it.dailyPayment }
        .sumOf { it.amount }
    val dailyRevenueSum = allOrders
        .filter { it.dailyPayment || it.isOpenEnded }
        .sumOf { o ->
            val effStart = maxOf(o.deliveryStart, startDate)
            val effEnd = minOf(o.deliveryEnd, effectiveEnd)
            if (effStart > effEnd) 0
            else sumForRange(o, effStart, effEnd)
        }
    val totalRevenue = paidOrdersSum + dailyRevenueSum

    val pureCostPer100 = if (soldCalories > 0) foodExpenses.toDouble() / soldCalories * 100 else 0.0
    val fullCostPer100 = if (soldCalories > 0) totalExpenses.toDouble() / soldCalories * 100 else 0.0

    val autoPricePer100 = if (soldCalories > 0) totalRevenue.toDouble() / soldCalories * 100 else 0.0

    var pricePer100Text by remember(autoPricePer100) {
        mutableStateOf(
            if (autoPricePer100 > 0) String.format(Locale.US, "%.2f", autoPricePer100) else ""
        )
    }

    val pricePer100 = pricePer100Text.replace(",", ".").toDoubleOrNull() ?: 0.0

    val variableCostPer100 = if (soldCalories > 0) variableExpenses.toDouble() / soldCalories * 100 else 0.0
    val marginPer100 = pricePer100 - variableCostPer100

    val breakEvenCalories: Double = if (marginPer100 > 0)
        fixedExpenses.toDouble() / marginPer100 * 100 else 0.0

    val currentDeficit = breakEvenCalories - soldCalories
    val isProfitable = marginPer100 > 0 && currentDeficit <= 0
    val progress = if (breakEvenCalories > 0)
        (soldCalories / breakEvenCalories).toFloat().coerceIn(0f, 1f) else 0f

    val periodDays = daysBetween(startDate, endDate).coerceAtLeast(1)
    val avgClientCalories = 1500.0 * periodDays
    val breakEvenClients = if (avgClientCalories > 0) breakEvenCalories / avgClientCalories else 0.0
    val currentClients = if (avgClientCalories > 0) soldCalories / avgClientCalories else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (screen != 0) {
                    IconButton(onClick = { screen = 0 }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
                Text(
                    when (screen) {
                        0 -> "Финансы"
                        1 -> "Разбивка расходов"
                        2 -> "Рекомендованные цены"
                        else -> "Сценарии"
                    }
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ===== ГЛАВНЫЙ ЭКРАН =====
                if (screen == 0) {

                    // Период
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
                            Text(startDate, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(endDate, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "$periodDays дней · продано $soldCalories ккал",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    // Блок 1: Себестоимость
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "СЕБЕСТОИМОСТЬ 100 ккал",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))

                            Text(
                                if (fullCostPer100 > 0)
                                    String.format(Locale.US, "%.0f ₽", fullCostPer100)
                                else "—",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "полная (все расходы)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(10.dp))

                            Text(
                                if (pureCostPer100 > 0)
                                    "Чистая (только закупка) — ${String.format(Locale.US, "%.0f", pureCostPer100)} ₽"
                                else "—",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Блок 2: Точка безубыточности
                    Surface(
                        color = if (isProfitable)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "ТОЧКА БЕЗУБЫТОЧНОСТИ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isProfitable)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(10.dp))

                            Text(
                                when {
                                    marginPer100 <= 0 -> "🚨 Цена ниже себестоимости"
                                    isProfitable -> "✅ В плюсе"
                                    else -> "📈 Нужно ещё продать"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isProfitable)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )

                            Spacer(Modifier.height(12.dp))

                            if (marginPer100 > 0) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = if (isProfitable)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Продано $soldCalories",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Нужно ${breakEvenCalories.toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    if (isProfitable)
                                        "Запас: ${(-currentDeficit).toInt()} ккал"
                                    else
                                        "Не хватает: ${currentDeficit.toInt()} ккал (≈ ${String.format(Locale.US, "%.1f", breakEvenClients - currentClients)} клиентов)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isProfitable)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    "Поднимите цену выше переменной себестоимости",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Три кнопки для деталей
                    Text(
                        "Подробнее",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailButton(
                            emoji = "📋",
                            label = "Расходы",
                            onClick = { screen = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        DetailButton(
                            emoji = "💰",
                            label = "Цены",
                            onClick = { screen = 2 },
                            modifier = Modifier.weight(1f)
                        )
                        DetailButton(
                            emoji = "📈",
                            label = "Сценарии",
                            onClick = { screen = 3 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ===== ЭКРАН 1: РАЗБИВКА РАСХОДОВ =====
                if (screen == 1) {
                    Text(
                        "Всего расходов: $totalExpenses ₽",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Переменные: $variableExpenses ₽ · Фиксированные: $fixedExpenses ₽",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Переменные (зависят от объёма)", style = MaterialTheme.typography.titleSmall)

                    ExpenseRow("Закупка продуктов", foodExpenses, totalExpenses)
                    ExpenseRow("Упаковка", packagingExpenses, totalExpenses)
                    ExpenseRow(
                        if (autoDelivery) "Доставка ($portions × $courierRate ₽)" else "Доставка",
                        deliveryExpenses, totalExpenses
                    )
                    if (adExpenses > 0) ExpenseRow("Реклама", adExpenses, totalExpenses)
                    if (otherExpenses > 0) ExpenseRow("Прочее", otherExpenses, totalExpenses)

                    Spacer(Modifier.height(8.dp))
                    Text("Фиксированные (не зависят от объёма)", style = MaterialTheme.typography.titleSmall)

                    ExpenseRow("Зарплата", salaryExpenses, totalExpenses)
                    ExpenseRow("Аренда", rentExpenses, totalExpenses)
                    if (taxExpenses > 0) ExpenseRow("Налоги", taxExpenses, totalExpenses)

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Себестоимость 100 ккал:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Полная — ${String.format(Locale.US, "%.0f", fullCostPer100)} ₽",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Чистая — ${String.format(Locale.US, "%.0f", pureCostPer100)} ₽",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // ===== ЭКРАН 2: РЕКОМЕНДОВАННЫЕ ЦЕНЫ =====
                if (screen == 2) {
                    Text(
                        "Цена за 100 ккал",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = pricePer100Text,
                        onValueChange = { pricePer100Text = it },
                        label = { Text("Текущая цена, ₽") },
                        singleLine = true,
                        supportingText = {
                            Text(
                                if (autoPricePer100 > 0)
                                    "Авто из данных: ${String.format(Locale.US, "%.2f", autoPricePer100)} ₽"
                                else "Введите вручную",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    if (fullCostPer100 > 0) {
                        val recommendedBase = fullCostPer100
                        val recommended20 = fullCostPer100 * 1.20
                        val recommended30 = fullCostPer100 * 1.30

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("В ноль", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    String.format(Locale.US, "%.0f ₽", recommendedBase),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("С наценкой 20%", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    String.format(Locale.US, "%.0f ₽", recommended20),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("С наценкой 30%", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    String.format(Locale.US, "%.0f ₽", recommended30),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Цены по тарифам (с наценкой 20%)",
                            style = MaterialTheme.typography.titleSmall
                        )

                        val tariffs = listOf(900, 1200, 1300, 1500, 1800, 2500)
                        tariffs.forEach { kcal ->
                            val priceForDay = recommended20 * kcal / 100
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$kcal ккал")
                                    Text(
                                        String.format(Locale.US, "%.0f ₽/день", priceForDay),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== ЭКРАН 3: СЦЕНАРИИ =====
                if (screen == 3) {
                    Text(
                        "Что если поднять цену?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Текущая цена: ${String.format(Locale.US, "%.0f", pricePer100)} ₽ за 100 ккал",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    if (marginPer100 > 0) {
                        val scenarios = listOf(0.10, 0.20, 0.30, 0.40)
                        scenarios.forEach { pct ->
                            val newPrice = pricePer100 * (1 + pct)
                            val newMargin = newPrice - variableCostPer100
                            val newBreakEven = if (newMargin > 0) fixedExpenses.toDouble() / newMargin * 100 else 0.0
                            val newClients = newBreakEven / avgClientCalories
                            val percentInt = (pct * 100).toInt()
                            val willBeProfitable = newBreakEven <= soldCalories

                            Surface(
                                color = if (willBeProfitable)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Цена +$percentInt%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (willBeProfitable) {
                                            Text(
                                                "✅",
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "→ ${String.format(Locale.US, "%.0f", newPrice)} ₽ за 100 ккал",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "→ Нужно ${String.format(Locale.US, "%.1f", newClients)} клиентов",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (willBeProfitable) {
                                        Text(
                                            "Хватит текущих клиентов",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "Цена ниже переменной себестоимости. Сначала поднимите цену выше ${String.format(Locale.US, "%.0f", variableCostPer100)} ₽ за 100 ккал.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (screen == 0) {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            } else {
                TextButton(onClick = { screen = 0 }) { Text("Назад") }
            }
        }
    )

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

@Composable
fun DetailButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ExpenseRow(label: String, amount: Int, total: Int) {
    if (amount <= 0) return
    val pct = if (total > 0) amount * 100 / total else 0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row {
            Text(
                "$amount ₽",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "($pct%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
fun CostDialog(onDismiss: () -> Unit) {
    FinanceDialog(onDismiss)
}

@Composable
fun BreakEvenDialog(onDismiss: () -> Unit) {
    FinanceDialog(onDismiss)
}