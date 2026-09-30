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
import androidx.compose.foundation.horizontalScroll
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
import com.ppkitchen.data.ClientEndReminderScheduler
import com.ppkitchen.data.CookReminderScheduler
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

private val AVAILABLE_PLACEHOLDERS = listOf(
    "{имя}", "{дата}", "{калории}", "{адрес}", "{телефон}"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCooks: () -> Unit = {}
) {
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

    // Автоотправка повару (список заказов)
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
    var chefDayOffset by remember {
        mutableStateOf(SettingsPreferences.getChefDayOffset(ctx))
    }

    // Уведомление об окончании питания
    var clientEndEnabled by remember {
        mutableStateOf(SettingsPreferences.isClientEndReminderEnabled(ctx))
    }
    var clientEndHour by remember {
        mutableStateOf(SettingsPreferences.getClientEndReminderHour(ctx).toString())
    }
    var clientEndMinute by remember {
        mutableStateOf(
            String.format(Locale.US, "%02d", SettingsPreferences.getClientEndReminderMinute(ctx))
        )
    }
    var clientEndText by remember {
        mutableStateOf(SettingsPreferences.getClientEndReminderText(ctx))
    }
    var clientEndMode by remember {
        mutableStateOf(SettingsPreferences.getClientEndReminderMode(ctx))
    }
    var renewalText by remember {
        mutableStateOf(SettingsPreferences.getRenewalMessageText(ctx))
    }

    // Авто-расходы на поваров
    var cookReminderEnabled by remember {
        mutableStateOf(SettingsPreferences.isCookReminderEnabled(ctx))
    }
    var cookReminderHour by remember {
        mutableStateOf(SettingsPreferences.getCookReminderHour(ctx).toString())
    }
    var cookReminderMinute by remember {
        mutableStateOf(
            String.format(Locale.US, "%02d", SettingsPreferences.getCookReminderMinute(ctx))
        )
    }

    var showTestResult by remember { mutableStateOf<String?>(null) }
    var chefNextScheduleText by remember {
        mutableStateOf(ChefReminderScheduler.getNextScheduleText(ctx))
    }
    var clientEndNextScheduleText by remember {
        mutableStateOf(ClientEndReminderScheduler.getNextScheduleText(ctx))
    }
    var cookNextScheduleText by remember {
        mutableStateOf(CookReminderScheduler.getNextScheduleText(ctx))
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

    fun saveAll() {
        SettingsPreferences.setThemeMode(ctx, themeMode)
        ThemeState.update(themeMode)
        SettingsPreferences.setCourierRate(ctx, courierRateText.toIntOrNull() ?: 200)
        SettingsPreferences.setAutoDelivery(ctx, autoDelivery)

        // Повара
        SettingsPreferences.setCookReminderEnabled(ctx, cookReminderEnabled)
        SettingsPreferences.setCookReminderHour(ctx, cookReminderHour.toIntOrNull() ?: 22)
        SettingsPreferences.setCookReminderMinute(ctx, cookReminderMinute.toIntOrNull() ?: 0)
        if (cookReminderEnabled) {
            CookReminderScheduler.scheduleNext(ctx)
        } else {
            CookReminderScheduler.cancel(ctx)
        }

        // Автоотправка повару (список)
        SettingsPreferences.setChefReminderEnabled(ctx, chefEnabled)
        SettingsPreferences.setChefSendHour(ctx, chefHour.toIntOrNull() ?: 7)
        SettingsPreferences.setChefSendMinute(ctx, chefMinute.toIntOrNull() ?: 0)
        SettingsPreferences.setChefPhone(ctx, chefPhone)
        SettingsPreferences.setChefMessenger(ctx, chefMessenger)
        SettingsPreferences.setChefDayOffset(ctx, chefDayOffset)
        if (chefEnabled) {
            ChefReminderScheduler.scheduleNext(ctx)
        } else {
            ChefReminderScheduler.cancel(ctx)
        }

        // Окончание питания
        SettingsPreferences.setClientEndReminderEnabled(ctx, clientEndEnabled)
        SettingsPreferences.setClientEndReminderHour(ctx, clientEndHour.toIntOrNull() ?: 9)
        SettingsPreferences.setClientEndReminderMinute(ctx, clientEndMinute.toIntOrNull() ?: 0)
        SettingsPreferences.setClientEndReminderText(ctx, clientEndText)
        SettingsPreferences.setClientEndReminderMode(ctx, clientEndMode)
        SettingsPreferences.setRenewalMessageText(ctx, renewalText)
        if (clientEndEnabled) {
            ClientEndReminderScheduler.scheduleNext(ctx)
        } else {
            ClientEndReminderScheduler.cancel(ctx)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactTopBar(
                title = "Настройки",
                onBack = onBack,
                actions = {
                    TextButton(onClick = {
                        saveAll()
                        onBack()
                    }) {
                        Text("Сохранить")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ===== Тема =====
            SettingsSection(title = "Тема") {
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
            }

            // ===== Курьер =====
            SettingsSection(title = "Курьер") {
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
                Text(
                    "Если включено — расходы курьера считаются как «порции × ставка». " +
                            "Категория «Доставка» в расходах игнорируется.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== Повара =====
            SettingsSection(title = "Повара") {
                Text(
                    "Автоматически создавать расход на зарплату каждый день, когда повар выходит на смену.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        saveAll()
                        onOpenCooks()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("👨‍🍳 Управление поварами")
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Авто-расходы включены", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = cookReminderEnabled, onCheckedChange = { cookReminderEnabled = it })
                }

                if (cookReminderEnabled) {
                    Text(
                        "Расход создаётся с категорией «Зарплата повара (авто)» в указанное время. " +
                                "Ручные записи зарплаты идут в категорию «Зарплата (ручная)».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = cookReminderHour,
                            onValueChange = { cookReminderHour = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Час") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cookReminderMinute,
                            onValueChange = { cookReminderMinute = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Минута") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    InfoCard(
                        title = "Следующее срабатывание",
                        value = cookNextScheduleText
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    saveAll()
                                    CookReminderScheduler.scheduleNext(ctx)
                                    cookNextScheduleText = CookReminderScheduler.getNextScheduleText(ctx)
                                    val intent = Intent(ctx, com.ppkitchen.data.CookReminderReceiver::class.java)
                                    ctx.sendBroadcast(intent)
                                    showTestResult = "Повара: перепланировано и запущено"
                                } catch (e: Exception) {
                                    showTestResult = "Ошибка: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Перепланировать и проверить сейчас")
                    }
                }
            }

            // ===== Автоотправка повару (список заказов) =====
            SettingsSection(title = "Автоотправка списка заказов повару") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Включено", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = chefEnabled, onCheckedChange = { chefEnabled = it })
                }

                if (chefEnabled) {
                    // Режим отправки: день в день / за день
                    Text("Когда отправлять", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = chefDayOffset == 0,
                            onClick = { chefDayOffset = 0 },
                            label = { Text("День в день", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = chefDayOffset == 1,
                            onClick = { chefDayOffset = 1 },
                            label = { Text("За день", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        if (chefDayOffset == 0)
                            "📅 Список формируется на текущий день — повар получает его утром в день готовки."
                        else
                            "📅 Список формируется на завтра — повар получает его накануне вечером или утром.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chefHour,
                            onValueChange = { chefHour = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Час") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = chefMinute,
                            onValueChange = { chefMinute = it.filter { ch -> ch.isDigit() }.take(2) },
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

                    InfoCard(
                        title = "Следующее срабатывание",
                        value = chefNextScheduleText
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    saveAll()
                                    ChefReminderScheduler.scheduleNext(ctx)
                                    chefNextScheduleText = ChefReminderScheduler.getNextScheduleText(ctx)
                                    val intent = Intent(ctx, com.ppkitchen.data.ChefReminderReceiver::class.java)
                                    ctx.sendBroadcast(intent)
                                    showTestResult = "Повар: перепланировано и запущено"
                                } catch (e: Exception) {
                                    showTestResult = "Ошибка: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Перепланировать и проверить сейчас")
                    }
                }
            }

            // ===== Уведомление об окончании питания =====
            SettingsSection(title = "Уведомление об окончании питания") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Включено", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = clientEndEnabled, onCheckedChange = { clientEndEnabled = it })
                }

                if (clientEndEnabled) {
                    Text("Режим уведомлений", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = clientEndMode == "single",
                            onClick = { clientEndMode = "single" },
                            label = { Text("Одно общее", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = clientEndMode == "separate",
                            onClick = { clientEndMode = "separate" },
                            label = { Text("По одному", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        when (clientEndMode) {
                            "separate" -> "📱 На каждого клиента — отдельное уведомление. При нажатии откроется диалог продления."
                            else -> "📋 Одно уведомление со списком всех клиентов."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = clientEndHour,
                            onValueChange = { clientEndHour = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Час") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = clientEndMinute,
                            onValueChange = { clientEndMinute = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Минута") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ProtectedTemplateField(
                        value = clientEndText,
                        onValueChange = { clientEndText = it },
                        label = "Текст уведомления (шаблон)",
                        placeholders = AVAILABLE_PLACEHOLDERS
                    )

                    ProtectedTemplateField(
                        value = renewalText,
                        onValueChange = { renewalText = it },
                        label = "Шаблон сообщения клиенту",
                        placeholders = AVAILABLE_PLACEHOLDERS
                    )

                    InfoCard(
                        title = "Следующее срабатывание",
                        value = clientEndNextScheduleText
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    saveAll()
                                    ClientEndReminderScheduler.scheduleNext(ctx)
                                    clientEndNextScheduleText = ClientEndReminderScheduler.getNextScheduleText(ctx)
                                    val intent = Intent(ctx, com.ppkitchen.data.ClientEndReminderReceiver::class.java)
                                    ctx.sendBroadcast(intent)
                                    showTestResult = "Окончание питания: перепланировано и запущено"
                                } catch (e: Exception) {
                                    showTestResult = "Ошибка: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Перепланировать и проверить сейчас")
                    }
                }
            }

            // ===== Разрешения =====
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "⚠️ Разрешите точные будильники",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Без этого все уведомления и авто-расходы могут приходить с задержкой.",
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

            // ===== Результат теста =====
            showTestResult?.let {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    saveAll()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить и вернуться")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

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

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProtectedTemplateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholders: List<String>
) {
    var warningText by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { newText ->
                val oldBraces = value.count { it == '{' || it == '}' }
                val newBraces = newText.count { it == '{' || it == '}' }
                if (newBraces != oldBraces) {
                    warningText = "Плейсхолдеры можно добавить только кнопками ниже"
                    return@OutlinedTextField
                }
                if (!containsAllPlaceholders(value, newText)) {
                    warningText = "Плейсхолдеры нельзя удалять или менять"
                    return@OutlinedTextField
                }
                warningText = null
                onValueChange(newText)
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8
        )

        if (warningText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "⚠️ $warningText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Вставить плейсхолдер:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            placeholders.forEach { ph ->
                AssistChip(
                    onClick = {
                        warningText = null
                        onValueChange(value + ph)
                    },
                    label = { Text(ph, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }

        if (value.isNotEmpty()) {
            TextButton(
                onClick = {
                    warningText = null
                    onValueChange("")
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Очистить поле", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun extractPlaceholders(text: String): List<String> {
    val regex = Regex("\\{[а-яёa-z0-9_]+\\}", RegexOption.IGNORE_CASE)
    return regex.findAll(text).map { it.value }.toList()
}

private fun containsAllPlaceholders(oldText: String, newText: String): Boolean {
    val oldList = extractPlaceholders(oldText)
    if (oldList.isEmpty()) return true
    val newList = extractPlaceholders(newText)
    val oldCounts = oldList.groupingBy { it }.eachCount()
    val newCounts = newList.groupingBy { it }.eachCount()
    return oldCounts.all { (key, count) -> (newCounts[key] ?: 0) >= count }
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