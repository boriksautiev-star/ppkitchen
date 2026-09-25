package com.ppkitchen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ppkitchen.data.ChefReminderScheduler
import com.ppkitchen.data.SettingsPreferences
import com.ppkitchen.ui.MainScreen
import com.ppkitchen.ui.PPKitchenTheme
import com.ppkitchen.ui.ThemeState

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Инициализируем тему из сохранённых настроек
        ThemeState.update(SettingsPreferences.getThemeMode(this))

        ChefReminderScheduler.scheduleNext(this)

        setContent {
            PPKitchenTheme {
                MainScreen()
            }
        }
    }
}