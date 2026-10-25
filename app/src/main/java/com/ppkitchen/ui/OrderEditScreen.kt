package com.ppkitchen.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.Client
import com.ppkitchen.data.MenuTemplate
import com.ppkitchen.data.Order
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDialog(
    existingOrder: Order?,
    clients: List<Client>,
    allOrders: List<Order> = emptyList(),
    preselectedClient: Client? = null,
    onSave: ((Client, Order) -> Unit)?,
    onUpdate: ((Order) -> Unit)?,
    onDelete: ((Order) -> Unit)?,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { com.ppkitchen.data.AppDatabase.get(ctx) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }
    val isEdit = existingOrder != null
    val wasOpenEnded = existingOrder?.isOpenEnded == true
    val isClientLocked = preselectedClient != null

    val menuTemplates by db.menuTemplateDao().getAll().collectAsState(initial = emptyList())

    var mode by remember { mutableStateOf("existing") }
    var query by remember { mutableStateOf("") }
    var selectedClient by remember { mutableStateOf<Client?>(preselectedClient) }

    var newFirstName by remember { mutableStateOf("") }
    var newLastName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newAddress by remember { mutableStateOf("") }
    var newRestrictions by remember { mutableStateOf("") }

    var calories by remember { mutableStateOf((existingOrder?.calories ?: 1200).toString()) }
    var protein by remember { mutableStateOf(if (existingOrder != null && existingOrder.protein > 0) existingOrder.protein.toString() else "") }
    var fat by remember { mutableStateOf(if (existingOrder != null && existingOrder.fat > 0) existingOrder.fat.toString() else "") }
    var carbs by remember { mutableStateOf(if (existingOrder != null && existingOrder.carbs > 0) existingOrder.carbs.toString() else "") }

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

    var selectedMenuTemplateId by remember { mutableStateOf(existingOrder?.menuTemplateId ?: 0L) }
    var showMenuSelector by remember { mutableStateOf(false) }

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

    val clientOrders: List<Order> = remember(selectedClient, allOrders, isEdit) {
        if (isEdit || selectedClient == null) emptyList()
        else allOrders
            .filter { it.clientId == selectedClient!!.id }
            .sortedByDescending { it.deliveryStart }
    }
    val lastOrder: Order? = clientOrders.firstOrNull()
    val previousOrders: List<Order> = clientOrders.drop(1).take(3)

    fun applyFromOrder(src: Order) {
        calories = src.calories.toString()
        protein = if (src.protein > 0) src.protein.toString() else ""
        fat = if (src.fat > 0) src.fat.toString() else ""
        carbs = if (src.carbs > 0) src.carbs.toString() else ""
        pricePerDay = src.amountPerDay.toString()
        val dd = parseDays(src.deliveryDaysOfWeek)
        skippedFoodDays = if (dd.isEmpty()) emptySet()
        else ALL_DAYS - foodDaysFromDelivery(dd)
        val active = discountForDay(src, today)
        discountText = active.toString()
        discountFromDate = ""
        comment = src.comment
        dailyPayment = src.dailyPayment
        isOpenEnded = src.isOpenEnded
        selectedMenuTemplateId = src.menuTemplateId // Подставляем меню из прошлого заказа
        if (!src.isOpenEnded) {
            val count = countDeliveryDays(src, src.deliveryStart, src.deliveryEnd)
            if (count > 0) portionsText = count.toString()
        }
        Toast.makeText(ctx, "Данные подставлены из заказа", Toast.LENGTH_SHORT).show()
    }

    fun doSave() {
        val kcal = calories.toIntOrNull()
        if (kcal == null || kcal <= 0) {
            Toast.makeText(ctx, "Введите калорийность", Toast.LENGTH_SHORT).show()
            return
        }
        val price = pricePerDay.toIntOrNull()
        if (price == null || price <= 0) {
            Toast.makeText(ctx, "Введите цену рациона", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isOpenEnded && portionsInt <= 0) {
            Toast.makeText(ctx, "Введите количество рационов", Toast.LENGTH_SHORT).show()
            return
        }
        if (foodDays.isEmpty()) {
            Toast.makeText(ctx, "Нельзя убрать все дни", Toast.LENGTH_LONG).show()
            return
        }

        val p = protein.toIntOrNull() ?: 0
        val f = fat.toIntOrNull() ?: 0
        val c = carbs.toIntOrNull() ?: 0

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
                protein = p,
                fat = f,
                carbs = c,
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
                paidUntil = debtDate,
                menuTemplateId = selectedMenuTemplateId
            )
            onUpdate?.invoke(updated)
        } else {
            val client: Client = if (mode == "existing") {
                val c = selectedClient
                if (c == null) {
                    Toast.makeText(ctx, "Выберите клиента", Toast.LENGTH_SHORT).show()
                    return
                }
                c
            } else {
                if (newFirstName.isBlank()) {
                    Toast.makeText(ctx, "Введите имя нового клиента", Toast.LENGTH_SHORT).show()
                    return
                }
                Client(
                    firstName = newFirstName.trim(),
                    lastName = newLastName.trim(),
                    phone = newPhone.trim(),
                    address = newAddress.trim(),
                    restrictions = newRestrictions.trim(),
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
                protein = p,
                fat = f,
                carbs = c,
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
                paidUntil = debtDate,
                menuTemplateId = selectedMenuTemplateId
            )
            onSave?.invoke(client, order)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                CompactTopBar(
                    title = if (isEdit) "Редактировать заказ" else "Новый заказ",
                    onBack = onCancel,
                    actions = {
                        TextButton(onClick = { doSave() }) {
                            Text(if (isEdit) "Сохранить" else "Создать")
                        }
                    }
                )

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isEdit) {
                        if (isClientLocked && preselectedClient != null) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "Клиент",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        preselectedClient.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (preselectedClient.phone.isNotBlank()) {
                                        Text(
                                            preselectedClient.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (preselectedClient.address.isNotBlank()) {
                                        Text(
                                            preselectedClient.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
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

                            HorizontalDivider()

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
                                                .heightIn(max = 260.dp)
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
                                                        if (c.restrictions.isNotBlank()) {
                                                            Text(
                                                                "🚫 ${c.restrictions}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.error
                                                            )
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
                                OutlinedTextField(newRestrictions, { newRestrictions = it }, label = { Text("Аллергии/Ограничения") }, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        if (selectedClient != null && clientOrders.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "Прошлые заказы клиента",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Всего: ${clientOrders.size}. Нажмите «Повторить», чтобы подставить данные.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    if (lastOrder != null) {
                                        Card(
                                            Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "Последний заказ",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (lastOrder.isOpenEnded) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            shape = MaterialTheme.shapes.small
                                                        ) {
                                                            Text(
                                                                "Бессрочный",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSecondary,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "${lastOrder.calories} ккал · ${lastOrder.amountPerDay} ₽/день",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (lastOrder.protein > 0 || lastOrder.fat > 0 || lastOrder.carbs > 0) {
                                                    Text(
                                                        "Б${lastOrder.protein} Ж${lastOrder.fat} У${lastOrder.carbs}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                val dd = parseDays(lastOrder.deliveryDaysOfWeek)
                                                val fd = if (dd.isEmpty()) ALL_DAYS else foodDaysFromDelivery(dd)
                                                Text(
                                                    "Дни доставки: ${deliveryDaysText(dd)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!lastOrder.isOpenEnded) {
                                                    val count = countDeliveryDays(lastOrder, lastOrder.deliveryStart, lastOrder.deliveryEnd)
                                                    if (count > 0) {
                                                        Text(
                                                            "Рационов: $count",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                val activeDisc = discountForDay(lastOrder, today)
                                                if (activeDisc > 0) {
                                                    Text(
                                                        "Скидка: $activeDisc%",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                                if (lastOrder.comment.isNotBlank()) {
                                                    Text(
                                                        "Комментарий: ${lastOrder.comment}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                                val skippedText = skippedFoodDaysText(fd)
                                                if (skippedText != null) {
                                                    Text(
                                                        "Не ест: $skippedText",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Button(
                                                    onClick = { applyFromOrder(lastOrder) },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("↻ Повторить последний")
                                                }
                                            }
                                        }
                                    }

                                    if (previousOrders.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Ещё заказы:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        previousOrders.forEach { prev ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        "${prev.calories} ккал · ${prev.amountPerDay} ₽/день",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        "${formatDisplayDate(prev.deliveryStart)} — ${formatDisplayDate(prev.deliveryEnd)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                TextButton(onClick = { applyFromOrder(prev) }) {
                                                    Text(
                                                        "Повторить",
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
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

                    // --- БЛОК ВЫБОРА МЕНЮ ---
                    HorizontalDivider()
                    Text("Шаблон меню", style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(
                            onClick = { showMenuSelector = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val currentName = menuTemplates.find { it.id == selectedMenuTemplateId }?.name ?: "Без меню (свободный выбор)"
                            Text("Текущее: $currentName")
                        }
                        DropdownMenu(
                            expanded = showMenuSelector,
                            onDismissRequest = { showMenuSelector = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Без меню (свободный выбор)") },
                                onClick = { selectedMenuTemplateId = 0L; showMenuSelector = false }
                            )
                            menuTemplates.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.name) },
                                    onClick = { selectedMenuTemplateId = t.id; showMenuSelector = false }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Text("Рацион", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        calories,
                        { calories = it },
                        label = { Text("Калорийность, ккал") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            protein,
                            { input -> protein = input.filter { it.isDigit() }.take(3) },
                            label = { Text("Белки, г") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            fat,
                            { input -> fat = input.filter { it.isDigit() }.take(3) },
                            label = { Text("Жиры, г") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            carbs,
                            { input -> carbs = input.filter { it.isDigit() }.take(3) },
                            label = { Text("Углеводы, г") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

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
                        HorizontalDivider()
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
                                        HorizontalDivider()
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
                                HorizontalDivider()
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

                    HorizontalDivider()

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

                    HorizontalDivider()

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

                    HorizontalDivider()

                    Text("Комментарий", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        comment,
                        { comment = it },
                        label = { Text("Комментарий к заказу") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isOpenEnded) {
                        HorizontalDivider()

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

                    HorizontalDivider()

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

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { doSave() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isEdit) "Сохранить" else "Создать")
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isEdit && !wasOpenEnded && !isOpenEnded) {
                            OutlinedButton(
                                onClick = { showFinishRegularPicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Завершить",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (isEdit && onDelete != null) {
                            OutlinedButton(
                                onClick = { onDelete(existingOrder!!) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Удалить", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отмена", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

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