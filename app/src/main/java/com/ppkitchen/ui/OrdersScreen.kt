package com.ppkitchen.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.Client
import com.ppkitchen.data.Courier
import com.ppkitchen.data.Order
import com.ppkitchen.data.OrderCourierAssignment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
val ALL_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
val OPEN_ENDED_DATE = "9999-12-31"

fun parseDays(s: String): Set<Int> {
    if (s.isBlank()) return emptySet()
    return s.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
}

fun daysToString(days: Set<Int>): String = days.sorted().joinToString(",")

fun dayOfWeekIso(date: String): Int {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(date) ?: return 1
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    } catch (e: Exception) {
        1
    }
}

fun foodDayToDeliveryDay(foodDay: Int): Int = if (foodDay == 1) 7 else foodDay - 1
fun deliveryDayToFoodDay(deliveryDay: Int): Int = if (deliveryDay == 7) 1 else deliveryDay + 1

fun foodDaysFromDelivery(deliveryDays: Set<Int>): Set<Int> =
    deliveryDays.map { deliveryDayToFoodDay(it) }.toSet()

fun deliveryDaysFromFood(foodDays: Set<Int>): Set<Int> =
    foodDays.map { foodDayToDeliveryDay(it) }.toSet()

fun isDeliveryDay(o: Order, date: String): Boolean {
    val deliveryDays = parseDays(o.deliveryDaysOfWeek)
    if (deliveryDays.isEmpty()) return true
    return dayOfWeekIso(date) in deliveryDays
}

fun firstValidDelivery(fromDate: String, deliveryDays: Set<Int>): String {
    if (deliveryDays.isEmpty()) return fromDate
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        time = sdf.parse(fromDate) ?: return fromDate
    }
    var iterations = 0
    while (iterations < 400) {
        if (dayOfWeekIso(sdf.format(cal.time)) in deliveryDays) {
            return sdf.format(cal.time)
        }
        cal.add(Calendar.DAY_OF_MONTH, 1)
        iterations++
    }
    return fromDate
}

fun lastValidDeliveryUpTo(toDate: String, deliveryDays: Set<Int>): String {
    if (deliveryDays.isEmpty()) return toDate
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        time = sdf.parse(toDate) ?: return toDate
    }
    var iterations = 0
    while (iterations < 400) {
        if (dayOfWeekIso(sdf.format(cal.time)) in deliveryDays) {
            return sdf.format(cal.time)
        }
        cal.add(Calendar.DAY_OF_MONTH, -1)
        iterations++
    }
    return toDate
}

fun computeLastDelivery(startDate: String, portions: Int, deliveryDays: Set<Int>): String {
    if (portions <= 0 || deliveryDays.isEmpty()) return startDate
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        time = sdf.parse(startDate) ?: return startDate
    }
    var counted = 0
    var iterations = 0
    while (iterations < 10000) {
        if (dayOfWeekIso(sdf.format(cal.time)) in deliveryDays) {
            counted++
            if (counted >= portions) break
        }
        cal.add(Calendar.DAY_OF_MONTH, 1)
        iterations++
    }
    return sdf.format(cal.time)
}

fun countDeliveriesBetween(start: String, end: String, deliveryDays: Set<Int>): Int {
    if (start > end || deliveryDays.isEmpty()) return 0
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        time = sdf.parse(start) ?: return 0
    }
    val endCal = Calendar.getInstance().apply {
        time = sdf.parse(end) ?: return 0
    }
    var count = 0
    var iterations = 0
    while (!cal.after(endCal) && iterations < 10000) {
        if (dayOfWeekIso(sdf.format(cal.time)) in deliveryDays) count++
        cal.add(Calendar.DAY_OF_MONTH, 1)
        iterations++
    }
    return count
}

fun countDeliveryDays(o: Order, rangeStart: String, rangeEnd: String): Int {
    val effStart = if (o.deliveryStart > rangeStart) o.deliveryStart else rangeStart
    val effEnd = if (o.deliveryEnd < rangeEnd) o.deliveryEnd else rangeEnd
    if (effStart > effEnd) return 0
    return countDeliveriesBetween(effStart, effEnd, parseDays(o.deliveryDaysOfWeek))
}

fun discountForDay(o: Order, day: String): Int {
    if (o.discountHistory.isNotBlank()) {
        var result = 0
        o.discountHistory.split("|").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size >= 2) {
                val d = parts[0]
                val p = parts[1].toIntOrNull() ?: 0
                if (day >= d) result = p
            }
        }
        return result
    }
    if (o.discountPercent <= 0) return 0
    if (o.discountFromDate.isBlank()) return o.discountPercent
    return if (day >= o.discountFromDate) o.discountPercent else 0
}

fun sumForRange(o: Order, rangeStart: String, rangeEnd: String): Int {
    val effStart = if (o.deliveryStart > rangeStart) o.deliveryStart else rangeStart
    val effEnd = if (o.deliveryEnd < rangeEnd) o.deliveryEnd else rangeEnd
    if (effStart > effEnd) return 0
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        time = sdf.parse(effStart) ?: return 0
    }
    val endCal = Calendar.getInstance().apply {
        time = sdf.parse(effEnd) ?: return 0
    }
    val deliveryDays = parseDays(o.deliveryDaysOfWeek)
    val base = o.amountPerDay
    var sum = 0
    var iterations = 0
    while (!cal.after(endCal) && iterations < 10000) {
        val dayStr = sdf.format(cal.time)
        val isDel = deliveryDays.isEmpty() || dayOfWeekIso(dayStr) in deliveryDays
        if (isDel) {
            val d = discountForDay(o, dayStr)
            sum += base * (100 - d) / 100
        }
        cal.add(Calendar.DAY_OF_MONTH, 1)
        iterations++
    }
    return sum
}

fun currentPricePerDay(o: Order, asOfDate: String): Int {
    val d = discountForDay(o, asOfDate)
    return o.amountPerDay * (100 - d) / 100
}

fun deliveryDaysText(deliveryDays: Set<Int>): String {
    if (deliveryDays.isEmpty() || deliveryDays.size == 7) return "Ежедневно"
    return deliveryDays.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
}

fun skippedFoodDaysText(foodDays: Set<Int>): String? {
    if (foodDays.isEmpty() || foodDays.size == 7) return null
    val skipped = ALL_DAYS - foodDays
    if (skipped.isEmpty()) return null
    return skipped.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
}

fun parseDiscountHistory(history: String): List<Pair<String, Int>> {
    if (history.isBlank()) return emptyList()
    return history.split("|").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size < 2) return@mapNotNull null
        val date = parts[0].trim()
        val percent = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
        date to percent
    }.sortedBy { it.first }
}

data class DiscountPeriod(
    val startDate: String,
    val endDate: String,
    val percent: Int,
    val daysCount: Int,
    val baseAmount: Int,
    val finalAmount: Int,
    val savedAmount: Int
)

fun buildDiscountPeriods(
    entries: List<Pair<String, Int>>,
    orderStart: String,
    orderEnd: String,
    deliveryDaysOfWeek: String,
    basePrice: Int
): List<DiscountPeriod> {
    if (entries.isEmpty() || basePrice <= 0) return emptyList()
    val days = parseDays(deliveryDaysOfWeek)
    val result = mutableListOf<DiscountPeriod>()
    val sorted = entries.sortedBy { it.first }
    sorted.forEachIndexed { idx, (date, percent) ->
        val nextDate = if (idx + 1 < sorted.size) sorted[idx + 1].first else null
        val startDate = if (date < orderStart) orderStart else date
        val rawEnd = nextDate?.let { shiftDate(it, -1) } ?: orderEnd
        val endDate = if (rawEnd > orderEnd) orderEnd else rawEnd
        if (startDate > endDate) return@forEachIndexed
        val count = countDeliveriesBetween(startDate, endDate, days)
        if (count <= 0) return@forEachIndexed
        val base = count * basePrice
        val final = count * basePrice * (100 - percent) / 100
        val saved = base - final
        result.add(
            DiscountPeriod(
                startDate = startDate,
                endDate = endDate,
                percent = percent,
                daysCount = count,
                baseAmount = base,
                finalAmount = final,
                savedAmount = saved
            )
        )
    }
    return result
}

fun hasNextOrder(order: Order, allOrders: List<Order>): Boolean {
    return allOrders.any { other ->
        other.id != order.id &&
                other.clientId == order.clientId &&
                other.deliveryStart > order.deliveryEnd
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onOpenCouriers: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }

    var selectedDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingOrder by remember { mutableStateOf<Order?>(null) }
    var shareMenuExpanded by remember { mutableStateOf(false) }

    var showCourierPicker by remember { mutableStateOf(false) }
    var selectedCourierForShare by remember { mutableStateOf<Courier?>(null) }

    var showAssignedCouriers by remember { mutableStateOf(false) }
    var selectedCourierForView by remember { mutableStateOf<Courier?>(null) }

    var renewalClientId by remember { mutableStateOf<Long?>(null) }
    var renewalOrderId by remember { mutableStateOf<Long?>(null) }

    val allOrders by db.orderDao().getAll().collectAsState(initial = emptyList())
    val clients by db.clientDao().getAll().collectAsState(initial = emptyList())
    val activeCouriers by db.courierDao().getActive().collectAsState(initial = emptyList())
    val allCouriers by db.courierDao().getAll().collectAsState(initial = emptyList())

    val assignments by remember(selectedDate) {
        db.assignmentDao().getForDate(selectedDate)
    }.collectAsState(initial = emptyList())

    val orders = allOrders.filter { o ->
        o.deliveryStart <= selectedDate &&
                o.deliveryEnd >= selectedDate &&
                isDeliveryDay(o, selectedDate)
    }.sortedWith(
        compareBy(
            { it.calories },
            { clients.find { c -> c.id == it.clientId }?.fullName?.lowercase() ?: "" }
        )
    )

    fun courierIdFor(orderId: Long): Long =
        assignments.find { it.orderId == orderId }?.courierId ?: 0L

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactTopBar(
                title = if (selectedDate == today) "Доставка на сегодня" else "Доставка на $selectedDate",
                actions = {
                    IconButton(onClick = onOpenCouriers) {
                        Icon(Icons.Default.LocalShipping, "Курьеры")
                    }

                    IconButton(onClick = { showAssignedCouriers = true }) {
                        Icon(Icons.Default.Groups, "Назначенные курьеры")
                    }

                    Box {
                        IconButton(onClick = { shareMenuExpanded = true }) {
                            Icon(Icons.Default.Share, "Поделиться")
                        }
                        DropdownMenu(
                            expanded = shareMenuExpanded,
                            onDismissRequest = { shareMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Список для курьера") },
                                onClick = {
                                    shareMenuExpanded = false
                                    if (orders.isEmpty()) {
                                        Toast.makeText(ctx, "На эту дату заказов нет", Toast.LENGTH_SHORT).show()
                                    } else if (activeCouriers.isEmpty()) {
                                        Toast.makeText(ctx, "Нет активных курьеров", Toast.LENGTH_LONG).show()
                                    } else {
                                        showCourierPicker = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Список для повара") },
                                onClick = {
                                    shareMenuExpanded = false
                                    if (orders.isEmpty()) {
                                        Toast.makeText(ctx, "Список пуст", Toast.LENGTH_SHORT).show()
                                    } else {
                                        shareOrders(ctx, selectedDate, orders, clients, ShareMode.CHEF)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Печать этикеток (Excel)") },
                                onClick = {
                                    shareMenuExpanded = false
                                    if (orders.isEmpty()) {
                                        Toast.makeText(ctx, "На эту дату заказов нет", Toast.LENGTH_SHORT).show()
                                    } else {
                                        exportLabelsToExcelAndShare(ctx, selectedDate, orders, clients)
                                    }
                                }
                            )
                        }
                    }

                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, "Выбрать дату")
                    }
                }
            )
        },
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
                Column(Modifier.padding(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedDate = shiftDate(selectedDate, -1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий день")
                        }

                        TextButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, null)
                            Spacer(Modifier.width(6.dp))
                            Text(selectedDate, style = MaterialTheme.typography.titleMedium)
                        }

                        IconButton(onClick = { selectedDate = shiftDate(selectedDate, 1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий день")
                        }
                    }

                    if (selectedDate != today) {
                        TextButton(
                            onClick = { selectedDate = today },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Вернуться к сегодня")
                        }
                    }
                }
            }

            if (orders.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "На $selectedDate доставок нет.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(orders, key = { it.id }) { o ->
                        val client = clients.find { it.id == o.clientId }
                        val stats = dailyStats(o, selectedDate)
                        val currentPrice = currentPricePerDay(o, selectedDate)
                        val activeDiscount = discountForDay(o, selectedDate)
                        val cid = courierIdFor(o.id)
                        val courier = if (cid > 0L) allCouriers.find { it.id == cid } else null

                        val isLastDeliveryToday = o.deliveryEnd == selectedDate && !o.isOpenEnded
                        val isLastDeliveryTomorrow = o.deliveryEnd == shiftDate(selectedDate, 1) && !o.isOpenEnded
                        val alreadyExtended = hasNextOrder(o, allOrders)

                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingOrder = o }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        client?.fullName ?: "Клиент удалён",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "${o.calories} ккал",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(Modifier.height(4.dp))

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (o.isOpenEnded) OpenEndedBadge()
                                    if (activeDiscount > 0) DiscountBadge(activeDiscount)
                                    if (courier != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                "Курьер: ${courier.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                if (isLastDeliveryTomorrow && !alreadyExtended) {
                                    Spacer(Modifier.height(6.dp))
                                    LastDeliveryBadge(text = "⚠️ Завтра последняя доставка")
                                } else if (isLastDeliveryToday && !alreadyExtended) {
                                    Spacer(Modifier.height(6.dp))
                                    LastDeliveryBadge(text = "🔔 Последняя доставка (сегодня)")
                                } else if (alreadyExtended && (isLastDeliveryToday || isLastDeliveryTomorrow)) {
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "✅ Продлено",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                if (client != null && client.address.isNotBlank()) {
                                    Text("Адрес: ${client.address}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (client != null && client.phone.isNotBlank()) {
                                    Text("Тел: ${client.phone}", style = MaterialTheme.typography.bodySmall)
                                }

                                if (o.isOpenEnded) {
                                    Text("Начало доставки: ${o.deliveryStart}", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("Рацион: ${o.dateStart} — ${o.dateEnd}", style = MaterialTheme.typography.bodySmall)
                                    Text("Доставка: ${o.deliveryStart} — ${o.deliveryEnd}", style = MaterialTheme.typography.bodySmall)
                                }

                                val deliveryDays = parseDays(o.deliveryDaysOfWeek)
                                val foodDays = if (deliveryDays.isEmpty()) ALL_DAYS
                                else foodDaysFromDelivery(deliveryDays)
                                Text(
                                    "Дни доставки: ${deliveryDaysText(deliveryDays)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                val skipped = skippedFoodDaysText(foodDays)
                                if (skipped != null) {
                                    Text(
                                        "Клиент не ест: $skipped",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (o.comment.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Комментарий: ${o.comment}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (o.isOpenEnded) {
                                        Text(
                                            "$currentPrice ₽/день",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    } else {
                                        Text("${o.amount} ₽", style = MaterialTheme.typography.bodyMedium)
                                    }

                                    if (o.dailyPayment || o.isOpenEnded) {
                                        if (o.isOpenEnded) {
                                            Text(
                                                "на $selectedDate: ${stats.daysPassed} дн. · ${stats.received} ₽",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                "${stats.daysPassed}/${stats.daysTotal} рационов",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        PaymentStatusText(o)
                                    }
                                }

                                if (!o.isOpenEnded && o.dailyPayment) {
                                    Spacer(Modifier.height(6.dp))
                                    val progress = if (stats.total > 0) stats.received.toFloat() / stats.total.toFloat() else 0f
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Внесено на $selectedDate: ${stats.received} ₽ из ${stats.total} ₽",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                if (o.isOpenEnded) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Внесено на $selectedDate: ${stats.received} ₽",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!o.isOpenEnded && (isLastDeliveryToday || isLastDeliveryTomorrow) && !alreadyExtended) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            renewalClientId = o.clientId
                                            renewalOrderId = o.id
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "✉️ Спросить о продлении",
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
    }

    if (showCourierPicker) {
        CourierPickerDialog(
            couriers = activeCouriers,
            onSelect = { courier ->
                showCourierPicker = false
                selectedCourierForShare = courier
            },
            onDismiss = { showCourierPicker = false }
        )
    }

    if (selectedCourierForShare != null) {
        DistributionDialog(
            courier = selectedCourierForShare!!,
            orders = orders,
            clients = clients,
            allCouriers = allCouriers,
            assignments = assignments,
            selectedDate = selectedDate,
            activeCouriersCount = activeCouriers.size,
            onSend = { checkedOrders ->
                scope.launch {
                    try {
                        orders.forEach { o ->
                            val shouldAssign = checkedOrders.any { it.id == o.id }
                            if (shouldAssign) {
                                db.assignmentDao().insert(
                                    OrderCourierAssignment(
                                        orderId = o.id,
                                        date = selectedDate,
                                        courierId = selectedCourierForShare!!.id
                                    )
                                )
                            } else {
                                db.assignmentDao().delete(o.id, selectedDate)
                            }
                        }
                        shareOrdersForCourier(
                            ctx, selectedDate, checkedOrders, clients,
                            selectedCourierForShare!!.name
                        )
                        selectedCourierForShare = null
                    } catch (e: Exception) {
                        Log.e("CourierShare", "Ошибка", e)
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCancel = { selectedCourierForShare = null }
        )
    }

    if (showAssignedCouriers) {
        AssignedCouriersDialog(
            couriers = allCouriers,
            orders = orders,
            assignments = assignments,
            selectedDate = selectedDate,
            onSelect = { courier ->
                showAssignedCouriers = false
                selectedCourierForView = courier
            },
            onDismiss = { showAssignedCouriers = false }
        )
    }

    if (selectedCourierForView != null) {
        val hisOrders = orders.filter { courierIdFor(it.id) == selectedCourierForView!!.id }
        CourierDayDetailDialog(
            courier = selectedCourierForView!!,
            orders = hisOrders,
            clients = clients,
            selectedDate = selectedDate,
            onSave = { keptOrders ->
                scope.launch {
                    try {
                        hisOrders.forEach { original ->
                            if (keptOrders.none { it.id == original.id }) {
                                db.assignmentDao().delete(original.id, selectedDate)
                            }
                        }
                        Toast.makeText(ctx, "Список обновлён", Toast.LENGTH_SHORT).show()
                        selectedCourierForView = null
                    } catch (e: Exception) {
                        Log.e("CourierDayDetail", "Ошибка", e)
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onRemoveAll = {
                scope.launch {
                    try {
                        db.assignmentDao().deleteByCourierAndDate(
                            selectedCourierForView!!.id, selectedDate
                        )
                        Toast.makeText(ctx, "Курьер снят со всех заказов на этот день", Toast.LENGTH_SHORT).show()
                        selectedCourierForView = null
                    } catch (e: Exception) {
                        Log.e("CourierDayDetail", "Ошибка", e)
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCancel = { selectedCourierForView = null }
        )
    }

    if (showCreateDialog) {
        OrderDialog(
            existingOrder = null,
            clients = clients,
            onSave = { client, order ->
                scope.launch {
                    try {
                        val clientId: Long = if (client.id == 0L) {
                            db.clientDao().insert(client)
                        } else {
                            client.id
                        }
                        db.orderDao().insert(order.copy(clientId = clientId))
                        Toast.makeText(ctx, "Заказ сохранён", Toast.LENGTH_SHORT).show()
                        showCreateDialog = false
                    } catch (e: Exception) {
                        Log.e("OrderSave", "Ошибка сохранения", e)
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onUpdate = null,
            onDelete = null,
            onCancel = { showCreateDialog = false }
        )
    }

    if (editingOrder != null) {
        OrderDialog(
            existingOrder = editingOrder,
            clients = clients,
            onSave = null,
            onUpdate = { updated ->
                scope.launch {
                    try {
                        db.orderDao().update(updated)
                        Toast.makeText(ctx, "Заказ обновлён", Toast.LENGTH_SHORT).show()
                        editingOrder = null
                    } catch (e: Exception) {
                        Log.e("OrderSave", "Ошибка обновления", e)
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDelete = { order ->
                scope.launch {
                    try {
                        db.orderDao().delete(order)
                        Toast.makeText(ctx, "Заказ удалён", Toast.LENGTH_SHORT).show()
                        editingOrder = null
                    } catch (e: Exception) {
                        Log.e("OrderSave", "Ошибка удаления", e)
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCancel = { editingOrder = null }
        )
    }

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDate = selectedDate,
            onDateSelected = {
                selectedDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (renewalClientId != null && renewalOrderId != null) {
        RenewalDialog(
            clientId = renewalClientId!!,
            orderId = renewalOrderId!!,
            onDismiss = {
                renewalClientId = null
                renewalOrderId = null
            }
        )
    }
}

@Composable
fun LastDeliveryBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignedCouriersDialog(
    couriers: List<Courier>,
    orders: List<Order>,
    assignments: List<OrderCourierAssignment>,
    selectedDate: String,
    onSelect: (Courier) -> Unit,
    onDismiss: () -> Unit
) {
    val ordersByCourier = mutableMapOf<Long, MutableList<Order>>()
    orders.forEach { o ->
        val cid = assignments.find { it.orderId == o.id }?.courierId ?: 0L
        if (cid > 0L) {
            ordersByCourier.getOrPut(cid) { mutableListOf() }.add(o)
        }
    }

    val assignedCouriers = ordersByCourier.mapNotNull { (cid, list) ->
        val c = couriers.find { it.id == cid } ?: return@mapNotNull null
        c to list
    }.sortedBy { it.first.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text("Курьеры на $selectedDate") },
        text = {
            if (assignedCouriers.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "На эту дату никто ещё не назначен.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Назначьте через 📤 → «Список для курьера».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    assignedCouriers.forEach { (courier, hisOrders) ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(courier) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        courier.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "${hisOrders.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                val totalCal = hisOrders.sumOf { it.calories }
                                Text(
                                    "Заказов: ${hisOrders.size} · Всего калорий: $totalCal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Нажмите, чтобы посмотреть список",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierDayDetailDialog(
    courier: Courier,
    orders: List<Order>,
    clients: List<Client>,
    selectedDate: String,
    onSave: (List<Order>) -> Unit,
    onRemoveAll: () -> Unit,
    onCancel: () -> Unit
) {
    val checked = remember(orders, courier.id) {
        mutableStateMapOf<Long, Boolean>().apply {
            orders.forEach { put(it.id, true) }
        }
    }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val kept = orders.filter { checked[it.id] == true }
    val totalCal = kept.sumOf { it.calories }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text("Курьер: ${courier.name}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Дата: $selectedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Список доставок (снимите галочку, чтобы убрать заказ):",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        orders.forEach { o ->
                            val client = clients.find { it.id == o.clientId }
                            val isChecked = checked[o.id] == true

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { checked[o.id] = !isChecked }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked[o.id] = it }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        client?.fullName ?: "Клиент удалён",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val subtitle = buildString {
                                        append("${o.calories} ккал")
                                        if (client != null && client.address.isNotBlank()) {
                                            append(" · ${client.address}")
                                        }
                                        if (client != null && client.phone.isNotBlank()) {
                                            append(" · ${client.phone}")
                                        }
                                    }
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))

                Text(
                    "Останется у курьера: ${kept.size} из ${orders.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (kept.isNotEmpty()) {
                    Text(
                        "Суммарно калорий: $totalCal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(kept) }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showRemoveConfirm = true }) {
                    Text("Снять курьера", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    )

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Снять курьера на $selectedDate?") },
            text = {
                Text(
                    "Все заказы (${orders.size}) этого курьера на $selectedDate станут свободными. " +
                            "Прошлые и будущие дни останутся без изменений — история сохранится.\n\n" +
                            "Продолжить?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemoveAll()
                }) {
                    Text("Снять", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierPickerDialog(
    couriers: List<Courier>,
    onSelect: (Courier) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<Courier?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text("Выберите курьера") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                couriers.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = c }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected?.id == c.id,
                            onClick = { selected = c }
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(c.name, fontWeight = FontWeight.Medium)
                            if (c.phone.isNotBlank()) {
                                Text(c.phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onSelect(it) } },
                enabled = selected != null
            ) { Text("Далее") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistributionDialog(
    courier: Courier,
    orders: List<Order>,
    clients: List<Client>,
    allCouriers: List<Courier>,
    assignments: List<OrderCourierAssignment>,
    selectedDate: String,
    activeCouriersCount: Int,
    onSend: (List<Order>) -> Unit,
    onCancel: () -> Unit
) {
    val checked = remember(orders, courier.id, assignments) {
        mutableStateMapOf<Long, Boolean>().apply {
            orders.forEach { o ->
                val a = assignments.find { it.orderId == o.id }
                put(o.id, a?.courierId == courier.id)
            }
        }
    }

    fun courierIdFor(orderId: Long): Long =
        assignments.find { it.orderId == orderId }?.courierId ?: 0L

    fun courierName(id: Long): String? {
        if (id == 0L) return null
        return allCouriers.find { it.id == id }?.name ?: "удалён"
    }

    val myOrders = orders.filter { courierIdFor(it.id) == courier.id }
        .sortedBy { it.calories }

    val freeOrders = orders.filter { courierIdFor(it.id) == 0L }
        .sortedBy { it.calories }

    val otherOrders = orders.filter {
        val cid = courierIdFor(it.id)
        cid != 0L && cid != courier.id
    }.sortedBy { it.calories }

    val checkedOrders = orders.filter { checked[it.id] == true }
    val allChecked = orders.isNotEmpty() && orders.all { checked[it.id] == true }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text("Курьер: ${courier.name}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Дата: $selectedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Кнопка «Выделить все / Снять все» — показывается всегда, если есть заказы
                    if (orders.isNotEmpty()) {
                        TextButton(onClick = {
                            val newVal = !allChecked
                            orders.forEach { checked[it.id] = newVal }
                        }) {
                            Icon(
                                if (allChecked) Icons.Default.Clear else Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (allChecked) "Снять все" else "Выделить все",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                        if (myOrders.isNotEmpty()) {
                            SectionHeader(
                                text = "Назначены ${courier.name}",
                                count = myOrders.size,
                                color = MaterialTheme.colorScheme.primary
                            )
                            myOrders.forEach { o ->
                                OrderRow(
                                    order = o,
                                    client = clients.find { it.id == o.clientId },
                                    isChecked = checked[o.id] == true,
                                    otherCourierName = null,
                                    onToggle = { checked[o.id] = !(checked[o.id] ?: false) }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (freeOrders.isNotEmpty()) {
                            SectionHeader(
                                text = "Свободные",
                                count = freeOrders.size,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            freeOrders.forEach { o ->
                                OrderRow(
                                    order = o,
                                    client = clients.find { it.id == o.clientId },
                                    isChecked = checked[o.id] == true,
                                    otherCourierName = null,
                                    onToggle = { checked[o.id] = !(checked[o.id] ?: false) }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (otherOrders.isNotEmpty()) {
                            SectionHeader(
                                text = "Уже у других курьеров",
                                count = otherOrders.size,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            otherOrders.forEach { o ->
                                OrderRow(
                                    order = o,
                                    client = clients.find { it.id == o.clientId },
                                    isChecked = checked[o.id] == true,
                                    otherCourierName = courierName(courierIdFor(o.id)),
                                    onToggle = { checked[o.id] = !(checked[o.id] ?: false) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))

                Text(
                    "Отправлено будет: ${checkedOrders.size} из ${orders.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                val totalCalories = checkedOrders.sumOf { it.calories }
                if (checkedOrders.isNotEmpty()) {
                    Text(
                        "Суммарно калорий: $totalCalories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Назначение сохранится только на $selectedDate. Другие дни не изменятся.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(checkedOrders) }
            ) { Text("Отправить") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    )
}

@Composable
fun SectionHeader(text: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
    Divider(color = color.copy(alpha = 0.3f))
}

@Composable
fun OrderRow(
    order: Order,
    client: Client?,
    isChecked: Boolean,
    otherCourierName: String?,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                client?.fullName ?: "Клиент удалён",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
            )
            val subtitle = buildString {
                append("${order.calories} ккал")
                if (client != null && client.address.isNotBlank()) {
                    append(" · ${client.address}")
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (otherCourierName != null) {
                Text(
                    "Сейчас у: $otherCourierName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun shareOrdersForCourier(
    ctx: android.content.Context,
    date: String,
    orders: List<Order>,
    clients: List<Client>,
    courierName: String
) {
    val sb = StringBuilder()
    sb.append("Курьеру: $courierName\n")
    sb.append("Дата: $date\n")
    sb.append("Заказов: ${orders.size}\n")
    sb.append("─────────────────\n\n")

    val sorted = orders.sortedWith(
        compareBy(
            { it.calories },
            { clients.find { c -> c.id == it.clientId }?.fullName?.lowercase() ?: "" }
        )
    )

    sorted.forEachIndexed { index, o ->
        val client = clients.find { it.id == o.clientId }
        val name = client?.fullName ?: "Клиент удалён"
        val address = client?.address?.takeIf { it.isNotBlank() } ?: "адрес не указан"
        val phone = client?.phone?.takeIf { it.isNotBlank() } ?: "тел. не указан"

        sb.append("${index + 1}. $name\n")
        sb.append("   Тариф: ${o.calories} ккал\n")
        sb.append("   Адрес: $address\n")
        sb.append("   Телефон: $phone\n")
        if (index < sorted.size - 1) sb.append("\n")
    }

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    ctx.startActivity(Intent.createChooser(sendIntent, "Отправить список курьеру"))
}

data class DailyStats(
    val received: Int,
    val total: Int,
    val daysPassed: Int,
    val daysTotal: Int
)

fun dailyStats(o: Order, asOfDate: String): DailyStats {
    val effectiveEnd = if (asOfDate < o.deliveryEnd) asOfDate else o.deliveryEnd
    val daysPassed = if (o.deliveryStart <= effectiveEnd)
        countDeliveryDays(o, o.deliveryStart, effectiveEnd)
    else 0
    val daysTotal = if (o.isOpenEnded) daysPassed
    else countDeliveryDays(o, o.deliveryStart, o.deliveryEnd)
    val received = if (o.deliveryStart <= effectiveEnd)
        sumForRange(o, o.deliveryStart, effectiveEnd) else 0
    val total = if (o.isOpenEnded) received
    else sumForRange(o, o.deliveryStart, o.deliveryEnd)
    return DailyStats(received, total, daysPassed, daysTotal)
}

@Composable
fun DiscountBadge(percent: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "Скидка $percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun OpenEndedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AllInclusive,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Бессрочно",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PaymentStatusText(order: Order) {
    when {
        order.paid -> Text("[+] оплачено", color = MaterialTheme.colorScheme.primary)
        order.paidUntil.isNotBlank() -> Text("[-] в долг до ${order.paidUntil}")
        else -> Text("[-] не оплачено")
    }
}

fun shiftDate(date: String, days: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(date) ?: return date
        cal.add(Calendar.DAY_OF_MONTH, days)
        sdf.format(cal.time)
    } catch (e: Exception) {
        date
    }
}

fun daysBetween(start: String, end: String): Int {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val d1 = sdf.parse(start)
        val d2 = sdf.parse(end)
        if (d1 == null || d2 == null) return 0
        val diff = d2.time - d1.time
        (TimeUnit.MILLISECONDS.toDays(diff) + 1).toInt().coerceAtLeast(0)
    } catch (e: Exception) {
        0
    }
}

enum class ShareMode { COURIER, CHEF }

private fun shareOrders(
    ctx: android.content.Context,
    date: String,
    orders: List<Order>,
    clients: List<Client>,
    mode: ShareMode
) {
    val sb = StringBuilder()

    val sortedOrders = orders.sortedWith(
        compareBy(
            { it.calories },
            { clients.find { c -> c.id == it.clientId }?.fullName?.lowercase() ?: "" }
        )
    )

    when (mode) {
        ShareMode.COURIER -> {
            sb.append("Заказы для курьера на $date\n")
            sb.append("Количество: ${sortedOrders.size}\n")
            sb.append("─────────────────\n\n")

            sortedOrders.forEachIndexed { index, o ->
                val client = clients.find { it.id == o.clientId }
                val name = client?.fullName ?: "Клиент удалён"
                val address = client?.address?.takeIf { it.isNotBlank() } ?: "адрес не указан"
                val phone = client?.phone?.takeIf { it.isNotBlank() } ?: "тел. не указан"

                sb.append("${index + 1}. $name\n")
                sb.append("   Тариф: ${o.calories} ккал\n")
                sb.append("   Адрес: $address\n")
                sb.append("   Телефон: $phone\n")
                if (index < sortedOrders.size - 1) sb.append("\n")
            }
        }
        ShareMode.CHEF -> {
            sb.append("Заказы для повара на $date\n")
            sb.append("Всего рационов: ${sortedOrders.size}\n")
            sb.append("─────────────────\n\n")

            sortedOrders.forEachIndexed { index, o ->
                val client = clients.find { it.id == o.clientId }
                val name = client?.fullName ?: "Клиент удалён"

                sb.append("${index + 1}. $name — ${o.calories} ккал\n")
                if (o.comment.isNotBlank()) {
                    sb.append("   Комментарий: ${o.comment}\n")
                }
            }

            sb.append("\n─────────────────\n")
            sb.append("ИТОГО ПО ТАРИФАМ:\n")

            val grouped = sortedOrders.groupBy { it.calories }
                .toList()
                .sortedBy { it.first }

            grouped.forEach { (kcal, list) ->
                sb.append("• $kcal ккал — ${list.size} шт.\n")
            }

            val totalCalories = sortedOrders.sumOf { it.calories }
            sb.append("\nВсего калорий за день: $totalCalories\n")
            sb.append("Всего рационов: ${sortedOrders.size}")
        }
    }

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    val chooserTitle = when (mode) {
        ShareMode.COURIER -> "Отправить список курьеру"
        ShareMode.CHEF -> "Отправить список повару"
    }
    ctx.startActivity(Intent.createChooser(sendIntent, chooserTitle))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDialog(
    existingOrder: Order?,
    clients: List<Client>,
    onSave: ((Client, Order) -> Unit)?,
    onUpdate: ((Order) -> Unit)?,
    onDelete: ((Order) -> Unit)?,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }
    val isEdit = existingOrder != null
    val wasOpenEnded = existingOrder?.isOpenEnded == true

    var mode by remember { mutableStateOf("existing") }
    var query by remember { mutableStateOf("") }
    var selectedClient by remember { mutableStateOf<Client?>(null) }

    var newFirstName by remember { mutableStateOf("") }
    var newLastName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newAddress by remember { mutableStateOf("") }

    var calories by remember { mutableStateOf((existingOrder?.calories ?: 1200).toString()) }

    var pickedFirstDate by remember {
        mutableStateOf(existingOrder?.deliveryStart ?: today)
    }

    var portionsText by remember {
        mutableStateOf(
            if (existingOrder != null && !existingOrder.isOpenEnded) {
                countDeliveryDays(existingOrder, existingOrder.deliveryStart, existingOrder.deliveryEnd).toString()
            } else "30"
        )
    }

    var skippedFoodDays by remember {
        mutableStateOf(
            if (existingOrder != null) {
                val dd = parseDays(existingOrder.deliveryDaysOfWeek)
                if (dd.isEmpty()) emptySet()
                else {
                    val fd = foodDaysFromDelivery(dd)
                    ALL_DAYS - fd
                }
            } else emptySet()
        )
    }

    var comment by remember { mutableStateOf(existingOrder?.comment ?: "") }

    val initialActiveDiscount = remember(existingOrder) {
        if (existingOrder != null) discountForDay(existingOrder, today) else 0
    }

    var discountText by remember {
        mutableStateOf(initialActiveDiscount.toString())
    }
    var discountFromDate by remember {
        mutableStateOf(existingOrder?.discountFromDate ?: "")
    }

    var dailyPayment by remember { mutableStateOf(existingOrder?.dailyPayment ?: false) }
    var isOpenEnded by remember { mutableStateOf(existingOrder?.isOpenEnded ?: false) }

    var showFinishConfirm by remember { mutableStateOf(false) }
    var showDiscountFromDatePicker by remember { mutableStateOf(false) }
    var showFinishRegularPicker by remember { mutableStateOf(false) }

    val portionsInt = portionsText.toIntOrNull() ?: 0
    val discountPercent = discountText.toIntOrNull()?.coerceIn(0, 100) ?: 0

    val foodDays: Set<Int> = ALL_DAYS - skippedFoodDays
    val deliveryDays: Set<Int> = deliveryDaysFromFood(foodDays)

    val deliveryStart = remember(pickedFirstDate, deliveryDays) {
        if (deliveryDays.isEmpty()) pickedFirstDate
        else firstValidDelivery(pickedFirstDate, deliveryDays)
    }
    val deliveryEndComputed = remember(deliveryStart, portionsInt, deliveryDays, isOpenEnded) {
        if (isOpenEnded) OPEN_ENDED_DATE
        else if (portionsInt > 0 && deliveryDays.isNotEmpty())
            computeLastDelivery(deliveryStart, portionsInt, deliveryDays)
        else deliveryStart
    }
    val dateStart = remember(deliveryStart) { shiftDate(deliveryStart, 1) }
    val dateEnd = remember(deliveryEndComputed, isOpenEnded) {
        if (isOpenEnded) OPEN_ENDED_DATE
        else shiftDate(deliveryEndComputed, 1)
    }

    var pricePerDay by remember {
        mutableStateOf(
            if (existingOrder != null) existingOrder.amountPerDay.toString() else ""
        )
    }

    var paymentMode by remember {
        mutableStateOf(
            when {
                existingOrder == null -> "unpaid"
                existingOrder.paid -> "paid"
                existingOrder.paidUntil.isNotBlank() -> "debt"
                else -> "unpaid"
            }
        )
    }
    var paidUntil by remember { mutableStateOf(existingOrder?.paidUntil ?: "") }

    val priceBase = pricePerDay.toIntOrNull() ?: 0

    val baseAmountFull = remember(priceBase, portionsInt, isOpenEnded) {
        if (isOpenEnded) 0 else priceBase * portionsInt
    }

    val totalAmountFull = remember(
        priceBase, portionsInt, discountPercent,
        deliveryStart, deliveryDays, isOpenEnded
    ) {
        if (isOpenEnded || portionsInt <= 0 || deliveryDays.isEmpty()) 0
        else {
            val lastDelivery = computeLastDelivery(deliveryStart, portionsInt, deliveryDays)
            val tempOrder = Order(
                date = today,
                clientId = 0L,
                dateStart = shiftDate(deliveryStart, 1),
                dateEnd = shiftDate(lastDelivery, 1),
                deliveryStart = deliveryStart,
                deliveryEnd = lastDelivery,
                amountPerDay = priceBase,
                discountPercent = discountPercent,
                deliveryDaysOfWeek = daysToString(deliveryDays)
            )
            sumForRange(tempOrder, deliveryStart, lastDelivery)
        }
    }
    val discountAmount = baseAmountFull - totalAmountFull

    val currentPricePerDay = remember(priceBase, discountPercent, today) {
        priceBase * (100 - discountPercent) / 100
    }

    val discountChanged = isEdit && discountPercent != initialActiveDiscount

    val displayHistory: List<Pair<String, Int>> = remember(
        existingOrder, discountPercent, today
    ) {
        if (existingOrder == null) return@remember emptyList()
        val saved = parseDiscountHistory(
            existingOrder.discountHistory.ifBlank {
                if (existingOrder.discountPercent > 0)
                    "${existingOrder.discountFromDate.ifBlank { existingOrder.deliveryStart }}:${existingOrder.discountPercent}"
                else ""
            }
        )
        val currentActive = discountForDay(existingOrder, today)
        if (discountPercent != currentActive) {
            saved + (today to discountPercent)
        } else {
            saved
        }
    }.sortedBy { it.first }

    val discountPeriods = remember(
        displayHistory, deliveryStart, deliveryEndComputed,
        deliveryDays, priceBase, isOpenEnded
    ) {
        if (displayHistory.isEmpty() || priceBase <= 0) emptyList()
        else {
            val endForCalc = if (isOpenEnded) today else deliveryEndComputed
            buildDiscountPeriods(
                entries = displayHistory,
                orderStart = deliveryStart,
                orderEnd = endForCalc,
                deliveryDaysOfWeek = daysToString(deliveryDays),
                basePrice = priceBase
            )
        }
    }

    val totalBase = discountPeriods.sumOf { it.baseAmount }
    val totalFinal = discountPeriods.sumOf { it.finalAmount }
    val totalSaved = discountPeriods.sumOf { it.savedAmount }

    var showFirstDeliveryPicker by remember { mutableStateOf(false) }
    var showPaidUntilPicker by remember { mutableStateOf(false) }

    val filtered = clients.filter { c ->
        query.isBlank() ||
                c.fullName.contains(query, ignoreCase = true) ||
                c.phone.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text(if (isEdit) "Редактировать заказ" else "Новый заказ") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isEdit) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = mode == "existing",
                            onClick = { mode = "existing" },
                            label = { Text("Существующий") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = mode == "new",
                            onClick = { mode = "new" },
                            label = { Text("Новый клиент") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Divider()

                    if (mode == "existing") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Поиск по имени или телефону") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Clear, "Очистить")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (clients.isEmpty()) {
                            Text("В базе нет клиентов. Выберите «Новый клиент».")
                        } else if (filtered.isEmpty()) {
                            Text("Ничего не найдено")
                        } else {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    filtered.forEach { c ->
                                        val isSelected = selectedClient?.id == c.id
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .clickable { selectedClient = c }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedClient = c }
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Column {
                                                Text(
                                                    c.fullName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (c.phone.isNotBlank()) {
                                                    Text(c.phone, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (mode == "new") {
                        OutlinedTextField(newFirstName, { newFirstName = it }, label = { Text("Имя*") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(newLastName, { newLastName = it }, label = { Text("Фамилия") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(newPhone, { newPhone = it }, label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(newAddress, { newAddress = it }, label = { Text("Адрес") }, modifier = Modifier.fillMaxWidth())
                    }

                    Divider()
                }

                if (!isEdit || !wasOpenEnded) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Бессрочный заказ", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = isOpenEnded,
                            onCheckedChange = {
                                isOpenEnded = it
                                if (it) dailyPayment = true
                            }
                        )
                    }

                    if (isOpenEnded) {
                        Text(
                            "Заказ идёт, пока вы его не завершите. Клиент платит по дням.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Этот заказ бессрочный",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Идёт с ${existingOrder!!.deliveryStart} до отмены.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { showFinishConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Завершить бессрочный заказ")
                    }
                }

                Divider()

                Text("Рацион", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    calories,
                    { calories = it },
                    label = { Text("Калорийность, ккал") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    pricePerDay,
                    { pricePerDay = it },
                    label = { Text("Цена рациона, ₽/день (без скидки)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = discountText,
                    onValueChange = { input ->
                        discountText = input.filter { ch -> ch.isDigit() }.take(3)
                    },
                    label = { Text("Скидка, %") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEdit && discountChanged && discountPercent > 0) {
                    Text(
                        "Скидка $discountPercent% применится с $today. Прошедшие дни сохранят прежнюю скидку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isEdit && discountChanged && discountPercent == 0) {
                    Text(
                        "Скидка отменяется с $today. Прошедшие дни сохранят прежнюю скидку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (discountPercent > 0) {
                    OutlinedButton(
                        onClick = { showDiscountFromDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DateRange, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (discountFromDate.isBlank() || discountFromDate == today)
                                "Скидка с: $today"
                            else "Скидка с: $discountFromDate",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (discountPeriods.isNotEmpty()) {
                    Divider()
                    Text("История скидок", style = MaterialTheme.typography.titleSmall)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discountPeriods.forEach { p ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${p.startDate} — ${p.endDate}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                "Скидка ${p.percent}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "База: ${p.daysCount} дн. × $priceBase ₽",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${p.baseAmount} ₽",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Экономия",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            "−${p.savedAmount} ₽",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    Divider()
                                    Spacer(Modifier.height(4.dp))

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Итого за период",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${p.finalAmount} ₽",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Базовая сумма (без скидок)", style = MaterialTheme.typography.bodySmall)
                                Text("$totalBase ₽", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Сэкономлено клиентом", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                Text("−$totalSaved ₽", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Divider()
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Итого к оплате", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("$totalFinal ₽", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Divider()

                Text("Клиент не ест в эти дни", style = MaterialTheme.typography.titleSmall)

                Text(
                    "Отметьте дни, когда клиент НЕ питается.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DAY_NAMES.forEachIndexed { idx, name ->
                        val dayNum = idx + 1
                        val isSkipped = dayNum in skippedFoodDays
                        FilterChip(
                            selected = isSkipped,
                            onClick = {
                                skippedFoodDays = if (isSkipped) skippedFoodDays - dayNum
                                else skippedFoodDays + dayNum
                            },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { skippedFoodDays = emptySet() },
                        label = { Text("Каждый день") },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = { skippedFoodDays = setOf(6, 7) },
                        label = { Text("Без выходных") },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = { skippedFoodDays = setOf(1, 2, 3, 4, 5) },
                        label = { Text("Только выходные") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (skippedFoodDays.isEmpty()) {
                    Text(
                        "Клиент ест каждый день. Доставка ежедневная.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (skippedFoodDays.size == 7) {
                    Text(
                        "Нельзя убрать все дни — клиент должен есть хотя бы иногда.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val deliveryText = deliveryDays.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
                    Text(
                        "Дни доставки (накануне): $deliveryText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Divider()

                Text("Параметры доставки", style = MaterialTheme.typography.titleSmall)

                DateField("Первая доставка (выбрано)", pickedFirstDate) { showFirstDeliveryPicker = true }

                if (pickedFirstDate != deliveryStart && deliveryDays.isNotEmpty()) {
                    Text(
                        "Начало доставки: $deliveryStart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (!isOpenEnded) {
                    OutlinedTextField(
                        portionsText,
                        { portionsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Количество рационов") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DeliveryInfo("Последняя доставка", deliveryEndComputed)
                    DeliveryInfo("Начало рациона", dateStart)
                    DeliveryInfo("Конец рациона", dateEnd)
                }

                Divider()

                Text("Комментарий", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    comment,
                    { comment = it },
                    label = { Text("Комментарий к заказу") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isOpenEnded) {
                    Divider()

                    Text("Оплата", style = MaterialTheme.typography.titleSmall)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ежедневная оплата")
                        Switch(
                            checked = dailyPayment,
                            onCheckedChange = { dailyPayment = it }
                        )
                    }

                    if (dailyPayment) {
                        Text(
                            "Клиент оплачивает по дням.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!dailyPayment) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = paymentMode == "paid",
                                onClick = { paymentMode = "paid"; paidUntil = "" },
                                label = { Text("Оплачено") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMode == "unpaid",
                                onClick = { paymentMode = "unpaid"; paidUntil = "" },
                                label = { Text("Не оплачено") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMode == "debt",
                                onClick = { paymentMode = "debt" },
                                label = { Text("В долг") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (paymentMode == "debt") {
                            OutlinedButton(
                                onClick = { showPaidUntilPicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DateRange, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (paidUntil.isBlank()) "Оплатить до..." else "Оплатить до: $paidUntil",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Divider()

                Text("Итого", style = MaterialTheme.typography.titleSmall)

                if (isOpenEnded) {
                    if (discountPercent > 0) {
                        DeliveryInfo("Базовая цена дня", "$priceBase ₽")
                        DeliveryInfo("Скидка", "$discountPercent%")
                        DeliveryInfo("Цена со скидкой", "$currentPricePerDay ₽/день")
                    } else {
                        DeliveryInfo("В день", if (priceBase > 0) "$priceBase ₽" else "—")
                    }
                    DeliveryInfo("Оплата", "Ежедневно, до отмены")
                } else {
                    DeliveryInfo("Базовая сумма", if (baseAmountFull > 0) "$baseAmountFull ₽" else "—")
                    if (discountPercent > 0) {
                        DeliveryInfo("Скидка", "−$discountAmount ₽")
                    }
                    DeliveryInfo("Сумма с учётом скидки", if (totalAmountFull > 0) "$totalAmountFull ₽" else "—")
                    if (dailyPayment && priceBase > 0) {
                        DeliveryInfo("В день (база)", "$priceBase ₽")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kcal = calories.toIntOrNull()
                if (kcal == null || kcal <= 0) {
                    Toast.makeText(ctx, "Введите калорийность", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                val price = pricePerDay.toIntOrNull()
                if (price == null || price <= 0) {
                    Toast.makeText(ctx, "Введите цену рациона", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                if (!isOpenEnded && portionsInt <= 0) {
                    Toast.makeText(ctx, "Введите количество рационов", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                if (foodDays.isEmpty()) {
                    Toast.makeText(ctx, "Нельзя убрать все дни", Toast.LENGTH_LONG).show()
                    return@TextButton
                }

                val isPaid = if (isOpenEnded || dailyPayment) false else paymentMode == "paid"
                val debtDate = if (!isOpenEnded && !dailyPayment && paymentMode == "debt") paidUntil else ""
                val daysString = daysToString(deliveryDays)

                val finalAmount = if (isOpenEnded) 0 else totalAmountFull
                val finalAmountPerDay = price
                val finalDailyPayment = if (isOpenEnded) true else dailyPayment

                val newHistory: String = if (isEdit) {
                    val original = existingOrder!!
                    val currentActive = discountForDay(original, today)

                    if (discountPercent != currentActive) {
                        val baseHistory = original.discountHistory.ifBlank {
                            if (original.discountPercent > 0)
                                "${original.discountFromDate.ifBlank { original.deliveryStart }}:${original.discountPercent}"
                            else ""
                        }
                        val entryDate = if (discountFromDate.isNotBlank()) discountFromDate else today
                        if (baseHistory.isBlank()) "$entryDate:$discountPercent"
                        else "$baseHistory|$entryDate:$discountPercent"
                    } else {
                        original.discountHistory.ifBlank {
                            if (original.discountPercent > 0)
                                "${original.discountFromDate.ifBlank { original.deliveryStart }}:${original.discountPercent}"
                            else ""
                        }
                    }
                } else {
                    if (discountPercent > 0) {
                        val startD = discountFromDate.ifBlank { deliveryStart }
                        "$startD:$discountPercent"
                    } else ""
                }

                if (isEdit) {
                    val updated = existingOrder!!.copy(
                        dateStart = dateStart,
                        dateEnd = dateEnd,
                        deliveryStart = deliveryStart,
                        deliveryEnd = deliveryEndComputed,
                        calories = kcal,
                        amount = finalAmount,
                        amountPerDay = finalAmountPerDay,
                        dailyPayment = finalDailyPayment,
                        discountPercent = discountPercent,
                        discountFromDate = if (discountPercent > 0) discountFromDate else "",
                        discountHistory = newHistory,
                        comment = comment.trim(),
                        deliveryDaysOfWeek = daysString,
                        isOpenEnded = isOpenEnded,
                        paid = isPaid,
                        paidUntil = debtDate
                    )
                    onUpdate?.invoke(updated)
                } else {
                    val client: Client = if (mode == "existing") {
                        val c = selectedClient
                        if (c == null) {
                            Toast.makeText(ctx, "Выберите клиента", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        c
                    } else {
                        if (newFirstName.isBlank()) {
                            Toast.makeText(ctx, "Введите имя нового клиента", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        Client(
                            firstName = newFirstName.trim(),
                            lastName = newLastName.trim(),
                            phone = newPhone.trim(),
                            address = newAddress.trim(),
                            comment = "",
                            status = "активен"
                        )
                    }

                    val order = Order(
                        date = today,
                        clientId = 0L,
                        dateStart = dateStart,
                        dateEnd = dateEnd,
                        deliveryStart = deliveryStart,
                        deliveryEnd = deliveryEndComputed,
                        calories = kcal,
                        amount = finalAmount,
                        amountPerDay = finalAmountPerDay,
                        dailyPayment = finalDailyPayment,
                        discountPercent = discountPercent,
                        discountFromDate = if (discountPercent > 0) discountFromDate else "",
                        discountHistory = newHistory,
                        comment = comment.trim(),
                        deliveryDaysOfWeek = daysString,
                        isOpenEnded = isOpenEnded,
                        paid = isPaid,
                        paidUntil = debtDate
                    )
                    onSave?.invoke(client, order)
                }
            }) { Text(if (isEdit) "Сохранить" else "Создать") }
        },
        dismissButton = {
            Row {
                if (isEdit && !wasOpenEnded && !isOpenEnded) {
                    TextButton(onClick = { showFinishRegularPicker = true }) {
                        Text("Завершить", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                if (isEdit && onDelete != null) {
                    TextButton(onClick = { onDelete(existingOrder!!) }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    )

    if (showFinishConfirm && existingOrder != null) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Завершить бессрочный заказ?") },
            text = { Text("Заказ превратится в обычный с датой завершения — последний прошедший день доставки.") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    val lastDelivery = lastValidDeliveryUpTo(today, deliveryDays)
                    if (lastDelivery < deliveryStart) {
                        Toast.makeText(ctx, "Доставок ещё не было — нечего завершать", Toast.LENGTH_LONG).show()
                        return@TextButton
                    }
                    val count = countDeliveriesBetween(deliveryStart, lastDelivery, deliveryDays)
                    isOpenEnded = false
                    portionsText = count.toString()
                }) { Text("Завершить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("Отмена") }
            }
        )
    }

    if (showFirstDeliveryPicker) {
        SimpleDatePickerDialog(
            initialDate = pickedFirstDate,
            onDateSelected = { pickedFirstDate = it; showFirstDeliveryPicker = false },
            onDismiss = { showFirstDeliveryPicker = false }
        )
    }
    if (showPaidUntilPicker) {
        SimpleDatePickerDialog(
            initialDate = paidUntil.ifBlank { today },
            onDateSelected = { paidUntil = it; showPaidUntilPicker = false },
            onDismiss = { showPaidUntilPicker = false }
        )
    }
    if (showDiscountFromDatePicker) {
        SimpleDatePickerDialog(
            initialDate = discountFromDate.ifBlank { today },
            onDateSelected = { discountFromDate = it; showDiscountFromDatePicker = false },
            onDismiss = { showDiscountFromDatePicker = false }
        )
    }

    if (showFinishRegularPicker) {
        SimpleDatePickerDialog(
            initialDate = today,
            onDateSelected = { selectedDate ->
                showFinishRegularPicker = false
                val lastDelivery = lastValidDeliveryUpTo(selectedDate, deliveryDays)
                if (lastDelivery < deliveryStart) {
                    Toast.makeText(
                        ctx,
                        "Выбранная дата раньше начала доставки",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val count = countDeliveriesBetween(deliveryStart, lastDelivery, deliveryDays)
                    portionsText = count.toString()
                    Toast.makeText(
                        ctx,
                        "Последняя доставка: $lastDelivery. Нажмите «Сохранить».",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { showFinishRegularPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.DateRange, null)
        Spacer(Modifier.width(8.dp))
        Text("$label: $value", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DeliveryInfo(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}