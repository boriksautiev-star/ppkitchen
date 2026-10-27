package com.ppkitchen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.Client

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditDialog(
    client: Client?,
    onSave: (Client) -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val isEdit = client != null

    val menuTemplates by db.menuTemplateDao().getAll().collectAsState(initial = emptyList())
    val rations by db.rationDao().getAll().collectAsState(initial = emptyList())

    var firstName by remember { mutableStateOf(client?.firstName ?: "") }
    var lastName by remember { mutableStateOf(client?.lastName ?: "") }
    var phone by remember { mutableStateOf(client?.phone ?: "") }
    var address by remember { mutableStateOf(client?.address ?: "") }
    var comment by remember { mutableStateOf(client?.comment ?: "") }
    var restrictions by remember { mutableStateOf(client?.restrictions ?: "") }
    var status by remember { mutableStateOf(client?.status ?: "активен") }

    // НОВЫЕ ПОЛЯ
    var preferredTemplateId by remember { mutableStateOf(client?.preferredTemplateId ?: 0L) }
    var preferredRationId by remember { mutableStateOf(client?.preferredRationId ?: 0L) }

    var showTemplateMenu by remember { mutableStateOf(false) }
    var showRationMenu by remember { mutableStateOf(false) }

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

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Пищевые предпочтения", style = MaterialTheme.typography.titleSmall)

                // ВЫБОР ШАБЛОНА МЕНЮ
                Box {
                    OutlinedButton(
                        onClick = { showTemplateMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentName = menuTemplates.find { it.id == preferredTemplateId }?.name ?: "Без меню"
                        Text("Меню: $currentName")
                    }
                    DropdownMenu(
                        expanded = showTemplateMenu,
                        onDismissRequest = { showTemplateMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Без меню") },
                            onClick = { preferredTemplateId = 0L; showTemplateMenu = false }
                        )
                        menuTemplates.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = { preferredTemplateId = t.id; showTemplateMenu = false }
                            )
                        }
                    }
                }

                // ВЫБОР РАЦИОНА
                Box {
                    OutlinedButton(
                        onClick = { showRationMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentName = rations.find { it.id == preferredRationId }?.name ?: "Без рациона"
                        Text("Рацион: $currentName")
                    }
                    DropdownMenu(
                        expanded = showRationMenu,
                        onDismissRequest = { showRationMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Без рациона") },
                            onClick = { preferredRationId = 0L; showRationMenu = false }
                        )
                        rations.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.name) },
                                onClick = { preferredRationId = r.id; showRationMenu = false }
                            )
                        }
                    }
                }
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
                        status = status.trim(),
                        preferredTemplateId = preferredTemplateId,
                        preferredRationId = preferredRationId
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