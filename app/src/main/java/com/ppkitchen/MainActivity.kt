package com.ppkitchen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.ChefReminderScheduler
import com.ppkitchen.data.ClientEndReminderScheduler
import com.ppkitchen.data.CookReminderScheduler
import com.ppkitchen.data.DeliveryReminderScheduler
import com.ppkitchen.data.FoodReferenceInitializer
import com.ppkitchen.data.SettingsBackup
import com.ppkitchen.data.SettingsPreferences
import com.ppkitchen.ui.MainScreen
import com.ppkitchen.ui.PPKitchenTheme
import com.ppkitchen.ui.ThemeState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingRenewal = mutableStateOf<Pair<Long, Long>?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // === 1. Восстановление настроек, если они были потеряны ===
        SettingsBackup.restoreIfNeeded(this)

        // === 2. Инициализация заводского справочника БЖУ (если база пуста) ===
        lifecycleScope.launch {
            // ИСПРАВЛЕНО: убрали лишний аргумент AppDatabase.get(...)
            FoodReferenceInitializer.initializeIfNeeded(applicationContext)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        ThemeState.update(SettingsPreferences.getThemeMode(this))
        ChefReminderScheduler.scheduleNext(this)
        ClientEndReminderScheduler.scheduleNext(this)
        CookReminderScheduler.scheduleNext(this)
        DeliveryReminderScheduler.scheduleNext(this)

        // === 3. Сохранить резервную копию настроек сразу после запуска ===
        SettingsBackup.backup(this)

        handleIntent(intent)

        setContent {
            PPKitchenTheme {
                MainScreen(
                    pendingRenewal = pendingRenewal.value,
                    onConsumePendingRenewal = { pendingRenewal.value = null }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // === 4. Сохранить настройки при сворачивании — на случай, если что-то пойдёт не так ===
        SettingsBackup.backup(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val clientId = intent?.getLongExtra("renewal_client_id", -1L) ?: -1L
        val orderId = intent?.getLongExtra("renewal_order_id", -1L) ?: -1L
        if (clientId > 0 && orderId > 0) {
            pendingRenewal.value = clientId to orderId
        }
    }
}