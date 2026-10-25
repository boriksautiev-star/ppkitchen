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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditDialog(
    client: Client?,
    onSave: (Client) -> Unit,
    onCancel: () -> Unit
) {
    val isEdit = client != null

    var firstName by remember { mutableStateOf(client?.firstName ?: "") }
    var lastName by remember { mutableStateOf(client?.lastName ?: "") }
    var phone by remember { mutableStateOf(client?.phone ?: "") }
    var address by remember { mutableStateOf(client?.address ?: "") }
    var comment by remember { mutableStateOf(client?.comment ?: "") }
    var restrictions by remember { mutableStateOf(client?.restrictions ?: "") }
    var status by remember { mutableStateOf(client?.status ?: "активен") }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text(if (isEdit) "Редактировать клиента" else "Новый клиент") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Имя*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Фамилия") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Телефон") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = restrictions,
                    onValueChange = { restrictions = it },
                    label = { Text("Аллергии/Ограничения") },
                    placeholder = { Text("Например: орехи, лактоза, свинина") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Статус") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (firstName.isBlank()) return@TextButton
                    val newClient = (client ?: Client(firstName = "")).copy(
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        comment = comment.trim(),
                        restrictions = restrictions.trim(),
                        status = status.trim()
                    )
                    onSave(newClient)
                },
                enabled = firstName.isNotBlank()
            ) { Text(if (isEdit) "Сохранить" else "Создать") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    )
}