package com.ppkitchen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ppkitchen.data.MessengerSender
import com.ppkitchen.data.Order
import com.ppkitchen.data.SettingsPreferences
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RenewalDialog(
    clientId: Long,
    orderId: Long,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }

    var client by remember { mutableStateOf<Client?>(null) }
    var order by remember { mutableStateOf<Order?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var messageText by remember { mutableStateOf("") }

    LaunchedEffect(clientId, orderId) {
        try {
            val c = db.clientDao().getById(clientId).first()
            val o = db.orderDao().getAll().first().find { it.id == orderId }
            client = c
            order = o

            if (c != null && o != null) {
                val template = SettingsPreferences.getRenewalMessageText(ctx)
                messageText = template
                    .replace("{имя}", c.fullName)
                    .replace("{дата}", formatDisplayDate(o.deliveryEnd))
                    .replace("{калории}", "${o.calories}")
                    .replace("{адрес}", c.address.ifBlank { "—" })
                    .replace("{телефон}", c.phone.ifBlank { "—" })
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = {
            Text(
                "Продление: ${client?.fullName ?: "…"}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (client == null || order == null) {
                    Text(
                        "Не удалось загрузить данные клиента или заказа.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val c = client!!
                    val o = order!!

                    // Карточка с данными клиента
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                c.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${o.calories} ккал · доставка ${formatDisplayDate(o.deliveryEnd)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (c.phone.isNotBlank()) {
                                Text(
                                    "Тел: ${c.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (c.address.isNotBlank()) {
                                Text(
                                    "Адрес: ${c.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Текст сообщения (редактируемый)
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("Текст сообщения") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 12
                    )

                    Text(
                        "Можно отредактировать перед отправкой",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Text(
                        "Отправить через:",
                        style = MaterialTheme.typography.titleSmall
                    )

                    // WhatsApp
                    Button(
                        onClick = {
                            MessengerSender.sendWhatsApp(ctx, c.phone, messageText)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = c.phone.isNotBlank()
                    ) {
                        Text("📱 WhatsApp")
                    }

                    // Telegram
                    Button(
                        onClick = {
                            MessengerSender.sendTelegram(ctx, messageText)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✈️ Telegram")
                    }

                    // SMS
                    OutlinedButton(
                        onClick = {
                            MessengerSender.sendSms(ctx, c.phone, messageText)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = c.phone.isNotBlank()
                    ) {
                        Text("💬 SMS")
                    }

                    if (c.phone.isBlank()) {
                        Text(
                            "У клиента не указан телефон. WhatsApp и SMS недоступны.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

private fun formatDisplayDate(isoDate: String): String {
    return try {
        val inSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outSdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        inSdf.parse(isoDate)?.let { outSdf.format(it) } ?: isoDate
    } catch (e: Exception) {
        isoDate
    }
}