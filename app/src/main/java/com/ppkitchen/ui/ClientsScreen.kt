package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.Client
import com.ppkitchen.data.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

const val STATUS_ACTIVE = "активен"
const val STATUS_FINISHED = "завершён"
const val STATUS_NO_ORDERS = "нет заказов"

fun computeClientStatus(clientId: Long, orders: List<Order>, today: String): String {
    val clientOrders = orders.filter { it.clientId == clientId }
    if (clientOrders.isEmpty()) return STATUS_NO_ORDERS
    val hasActive = clientOrders.any { it.deliveryStart <= today && it.deliveryEnd >= today }
    if (hasActive) return STATUS_ACTIVE
    val hasFuture = clientOrders.any { it.deliveryStart > today }
    if (hasFuture) return STATUS_ACTIVE
    return STATUS_FINISHED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(onClientClick: (Long) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }

    val clients by db.clientDao().getAll().collectAsState(initial = emptyList())
    val allOrders by db.orderDao().getAll().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("все") }

    val statuses = listOf("все", STATUS_ACTIVE, STATUS_FINISHED, STATUS_NO_ORDERS)

    data class ClientWithStatus(val client: Client, val status: String)

    val withStatus = clients.map { c ->
        ClientWithStatus(c, computeClientStatus(c.id, allOrders, today))
    }

    val filtered = withStatus.filter { cws ->
        val matchesQuery = query.isBlank() ||
                cws.client.fullName.contains(query, ignoreCase = true) ||
                cws.client.phone.contains(query, ignoreCase = true)
        val matchesStatus = statusFilter == "все" || cws.status == statusFilter
        matchesQuery && matchesStatus
    }

    val sorted = filtered.sortedWith(
        compareBy(
            { if (it.status == STATUS_ACTIVE) 0 else 1 },
            { it.client.firstName.lowercase() },
            { it.client.lastName.lowercase() }
        )
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CompactTopBar(title = "Клиенты") },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
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

            Spacer(Modifier.height(8.dp))

            if (sorted.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (clients.isEmpty()) "Пока нет клиентов.\nНажмите + чтобы добавить."
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
                    items(sorted, key = { it.client.id }) { cws ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClientClick(cws.client.id) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        cws.client.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (cws.status == STATUS_ACTIVE) FontWeight.Bold else FontWeight.Normal
                                    )
                                    StatusBadge(cws.status)
                                }
                                if (cws.client.phone.isNotBlank()) {
                                    Text(cws.client.phone, style = MaterialTheme.typography.bodySmall)
                                }
                                if (cws.client.address.isNotBlank()) {
                                    Text(cws.client.address, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ClientEditDialog(
            client = null,
            onSave = { newClient ->
                scope.launch { db.clientDao().insert(newClient) }
                showAddDialog = false
            },
            onCancel = { showAddDialog = false }
        )
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        STATUS_ACTIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
        STATUS_FINISHED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}