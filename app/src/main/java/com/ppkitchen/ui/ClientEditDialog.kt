package com.ppkitchen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.Client

@Composable
fun ClientEditDialog(
    client: Client?,
    onSave: (Client) -> Unit,
    onCancel: () -> Unit
) {
    var firstName by remember { mutableStateOf(client?.firstName ?: "") }
    var lastName by remember { mutableStateOf(client?.lastName ?: "") }
    var phone by remember { mutableStateOf(client?.phone ?: "") }
    var address by remember { mutableStateOf(client?.address ?: "") }
    var comment by remember { mutableStateOf(client?.comment ?: "") }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text(if (client == null) "Новый клиент" else "Редактировать клиента") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Фамилия") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Адрес") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(4.dp))
                Text(
                    "Статус клиента определяется автоматически по его заказам: пока идёт доставка — «активен», после последней доставки — «завершён».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (firstName.isNotBlank()) {
                    onSave(
                        Client(
                            id = client?.id ?: 0,
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            comment = comment.trim(),
                            status = client?.status ?: "активен" // значение не используется
                        )
                    )
                }
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    )
}