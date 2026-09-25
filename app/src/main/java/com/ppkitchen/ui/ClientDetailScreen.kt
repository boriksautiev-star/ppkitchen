package com.ppkitchen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ppkitchen.data.AppDatabase
import kotlinx.coroutines.launch

@Composable
fun ClientDetailScreen(clientId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val client by db.clientDao().getById(clientId).collectAsState(initial = null)
    val orders by db.orderDao().getByClient(clientId).collectAsState(initial = emptyList())
    val ordersCount by db.orderDao().ordersCountByClient(clientId).collectAsState(initial = 0)
    val totalPaid by db.orderDao().totalPaidByClient(clientId).collectAsState(initial = 0)
    val totalUnpaid by db.orderDao().totalUnpaidByClient(clientId).collectAsState(initial = 0)

    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactTopBar(
                title = client?.fullName ?: "Клиент",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, "Редактировать")
                    }
                }
            )
        }
    ) { pad ->
        val c = client
        if (c == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Клиент не найден")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Контакты", style = MaterialTheme.typography.titleMedium)
                        InfoRow("Имя", c.fullName)
                        InfoRow("Телефон", c.phone.ifBlank { "—" })
                        InfoRow("Адрес", c.address.ifBlank { "—" })
                        InfoRow("Комментарий", c.comment.ifBlank { "—" })
                        InfoRow("Статус", c.status)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Финансы", style = MaterialTheme.typography.titleMedium)
                        InfoRow("Всего заказов", "$ordersCount")
                        InfoRow("Оплачено", "${totalPaid ?: 0} ₽")
                        InfoRow("Долг", "${totalUnpaid ?: 0} ₽")
                    }
                }
            }

            item {
                Text(
                    "История заказов",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (orders.isEmpty()) {
                item {
                    Text(
                        "Заказов пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                items(orders, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${o.dateStart} — ${o.dateEnd}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${o.calories} ккал",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Доставка: ${o.deliveryStart} — ${o.deliveryEnd}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (o.discountPercent > 0) {
                                Spacer(Modifier.height(4.dp))
                                DiscountBadge(o.discountPercent)
                            }
                            if (o.comment.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Комментарий: ${o.comment}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${o.amount} ₽", style = MaterialTheme.typography.bodyMedium)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (o.paid) "[+] оплачено" else "[-] не оплачено", style = MaterialTheme.typography.bodySmall)
                                    Text(if (o.delivered) "[D] доставлено" else "[..] ожидает", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && client != null) {
        ClientEditDialog(
            client = client,
            onSave = { updated ->
                scope.launch { db.clientDao().update(updated) }
                showEditDialog = false
            },
            onCancel = { showEditDialog = false }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}