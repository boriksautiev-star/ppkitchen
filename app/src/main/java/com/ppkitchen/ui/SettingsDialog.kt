package com.ppkitchen.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.core.content.ContextCompat
import com.ppkitchen.data.ChefMessageBuilder
import com.ppkitchen.data.ChefReminderScheduler
import com.ppkitchen.data.SettingsPreferences
import com.ppkitchen.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ContactPhone(
    val number: String,
    val label: String
)

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.get(ctx) }

    // Тема
    var themeMode by remember { mutableStateOf(SettingsPreferences.getThemeMode(ctx)) }

    var courierRateText by remember {
        mutableStateOf(SettingsPreferences.getCourierRate(ctx).toString())
    }
    var autoDelivery by remember {
        mutableStateOf(SettingsPreferences.isAutoDelivery(ctx))
    }

    var chefEnabled by remember {
        mutableStateOf(SettingsPreferences.isChefReminderEnabled(ctx))
    }
    var chefHour by remember {
        mutableStateOf(SettingsPreferences.getChefSendHour(ctx).toString())
    }
    var chefMinute by remember {
        mutableStateOf(
            String.format(Locale.US, "%02d", SettingsPreferences.getChefSendMinute(ctx))
        )
    }
    var chefPhone by remember {
        mutableStateOf(SettingsPreferences.getChefPhone(ctx))
    }
    var chefMessenger by remember {
        mutableStateOf(SettingsPreferences.getChefMessenger(ctx))
    }

    var showTestResult by remember { mutableStateOf<String?>(null) }
    var nextScheduleText by remember {
        mutableStateOf(ChefReminderScheduler.getNextScheduleText(ctx))
    }

    var phonesToPick by remember { mutableStateOf<List<ContactPhone>?>(null) }
    var pickedContactName by remember { mutableStateOf("") }

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = readContactName(ctx, uri) ?: ""
            val phones = readPhonesFromContact(ctx, uri)
            when {
                phones.isEmpty() -> showTestResult = "В контакте не найден номер телефона"
                phones.size == 1 -> {
                    chefPhone = phones.first().number
                    showTestResult = "Контакт выбран: ${phones.first().number}"
                }
                else -> {
                    pickedContactName = name
                    phonesToPick = phones
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) contactPicker.launch(null)
        else showTestResult = "Разрешение на контакты не выдано"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        title = { Text("Настройки") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ===== Тема =====
                Text("Тема", style = MaterialTheme.typography.titleSmall)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "system" to "Как в системе",
                        "light" to "Светлая",
                        "dark" to "Тёмная"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { themeMode = mode },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Divider()

                // ===== Курьер =====
                Text("Курьер", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = courierRateText,
                    onValueChange = { courierRateText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Ставка за точку, ₽") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Считать доставку автоматически", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoDelivery, onCheckedChange = { autoDelivery = it })
                }

                Divider()

                // ===== Повар =====
                Text("Автоотправка повару", style = MaterialTheme.typography.titleSmall)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Включено", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = chefEnabled, onCheckedChange = { chefEnabled = it })
                }

                if (chefEnabled) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chefHour,
                            onValueChange = {
                                chefHour = it.filter { ch -> ch.isDigit() }.take(2)
                            },
                            label = { Text("Час") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = chefMinute,
                            onValueChange = {
                                chefMinute = it.filter { ch -> ch.isDigit() }.take(2)
                            },
                            label = { Text("Минута") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chefPhone,
                            onValueChange = { chefPhone = it },
                            label = { Text("Телефон повара") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) contactPicker.launch(null)
                                else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("👤")
                        }
                    }

                    Text(
                        "Введите вручную или нажмите 👤, чтобы выбрать из контактов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Мессенджер", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("whatsapp", "telegram", "sms").forEach { m ->
                            FilterChip(
                                selected = chefMessenger == m,
                                onClick = { chefMessenger = m },
                                label = {
                                    Text(
                                        when (m) {
                                            "whatsapp" -> "WhatsApp"
                                            "telegram" -> "Telegram"
                                            else -> "SMS"
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Следующее срабатывание", style = MaterialTheme.typography.bodySmall)
                            Text(
                                nextScheduleText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    SettingsPreferences.setChefReminderEnabled(ctx, true)
                                    SettingsPreferences.setChefSendHour(ctx, chefHour.toIntOrNull() ?: 7)
                                    SettingsPreferences.setChefSendMinute(ctx, chefMinute.toIntOrNull() ?: 0)
                                    SettingsPreferences.setChefPhone(ctx, chefPhone)
                                    SettingsPreferences.setChefMessenger(ctx, chefMessenger)

                                    ChefReminderScheduler.scheduleNext(ctx)
                                    nextScheduleText = ChefReminderScheduler.getNextScheduleText(ctx)
                                    showTestResult = "Перепланировано: $nextScheduleText"

                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    val allOrders = db.orderDao().getAll().first()
                                    val clients = db.clientDao().getAll().first()
                                    val todayOrders = ChefMessageBuilder.ordersForDate(allOrders, today)
                                    if (todayOrders.isNotEmpty()) {
                                        val intent = Intent(ctx, com.ppkitchen.data.ChefReminderReceiver::class.java)
                                        ctx.sendBroadcast(intent)
                                    }
                                } catch (e: Exception) {
                                    showTestResult = "Ошибка: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Перепланировать и проверить сейчас")
                    }

                    showTestResult?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        if (!alarmManager.canScheduleExactAlarms()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        "⚠️ Разрешите точные будильники",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Без этого уведомление может прийти с задержкой",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    TextButton(onClick = {
                                        val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        i.data = Uri.parse("package:${ctx.packageName}")
                                        ctx.startActivity(i)
                                    }) { Text("Открыть настройки") }
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "💡 Если уведомление не приходит",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Разрешите автозапуск и снимите ограничения батареи",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SettingsPreferences.setThemeMode(ctx, themeMode)
                ThemeState.update(themeMode)   // <-- мгновенное применение темы
                SettingsPreferences.setCourierRate(ctx, courierRateText.toIntOrNull() ?: 200)
                SettingsPreferences.setAutoDelivery(ctx, autoDelivery)
                SettingsPreferences.setChefReminderEnabled(ctx, chefEnabled)
                SettingsPreferences.setChefSendHour(ctx, chefHour.toIntOrNull() ?: 7)
                SettingsPreferences.setChefSendMinute(ctx, chefMinute.toIntOrNull() ?: 0)
                SettingsPreferences.setChefPhone(ctx, chefPhone)
                SettingsPreferences.setChefMessenger(ctx, chefMessenger)

                if (chefEnabled) {
                    ChefReminderScheduler.scheduleNext(ctx)
                } else {
                    ChefReminderScheduler.cancel(ctx)
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )

    phonesToPick?.let { phones ->
        AlertDialog(
            onDismissRequest = { phonesToPick = null },
            title = { Text("Выберите номер") },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pickedContactName.isNotBlank()) {
                        Text(
                            pickedContactName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    phones.forEach { p ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chefPhone = p.number
                                    showTestResult = "Контакт выбран: ${p.number}"
                                    phonesToPick = null
                                }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.number, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(p.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { phonesToPick = null }) { Text("Отмена") }
            }
        )
    }
}

private fun readContactName(context: Context, contactUri: Uri): String? {
    return try {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        null
    } catch (e: Exception) { null }
}

private fun readPhonesFromContact(context: Context, contactUri: Uri): List<ContactPhone> {
    return try {
        val contactId = getContactId(context, contactUri) ?: return emptyList()
        getPhonesForContact(context, contactId)
    } catch (e: Exception) { emptyList() }
}

private fun getContactId(context: Context, contactUri: Uri): Long? {
    return try {
        val projection = arrayOf(ContactsContract.Contacts._ID)
        context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                if (idx >= 0) return cursor.getLong(idx)
            }
        }
        null
    } catch (e: Exception) { null }
}

private fun getPhonesForContact(context: Context, contactId: Long): List<ContactPhone> {
    val result = mutableListOf<ContactPhone>()
    try {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val args = arrayOf(contactId.toString())

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, args, null
        )?.use { cursor ->
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
            if (numIdx >= 0) {
                while (cursor.moveToNext()) {
                    val rawNum = cursor.getString(numIdx) ?: continue
                    if (rawNum.isBlank()) continue
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1
                    val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                    result.add(ContactPhone(normalizePhone(rawNum), phoneTypeLabel(type, customLabel)))
                }
            }
        }
    } catch (e: Exception) { }
    return result
}

private fun phoneTypeLabel(type: Int, customLabel: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Мобильный"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Домашний"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Рабочий"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Факс (дом)"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Факс (раб.)"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Основной"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Другой"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel ?: "Другой"
        else -> "Телефон"
    }
}

private fun normalizePhone(raw: String): String {
    return raw.replace(Regex("[^0-9+]"), "")
}