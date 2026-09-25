package com.ppkitchen.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChefReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!SettingsPreferences.isChefReminderEnabled(appContext)) {
                    Log.d("ChefReminder", "Выключено, пропускаем")
                    return@launch
                }

                val db = AppDatabase.get(appContext)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val allOrders = db.orderDao().getAll().first()
                val clients = db.clientDao().getAll().first()

                val todayOrders = ChefMessageBuilder.ordersForDate(allOrders, today)
                if (todayOrders.isEmpty()) {
                    Log.d("ChefReminder", "Нет заказов на $today")
                    ChefReminderScheduler.scheduleNext(appContext)
                    return@launch
                }

                val text = ChefMessageBuilder.build(today, todayOrders, clients)
                showNotification(appContext, text)

                // Планируем следующий день
                ChefReminderScheduler.scheduleNext(appContext)
            } catch (e: Exception) {
                Log.e("ChefReminder", "Ошибка", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, text: String) {
        val channelId = "chef_reminder_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Список для повара",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Автоматическая отправка списка заказов повару"
            }
            manager.createNotificationChannel(channel)
        }

        val phone = SettingsPreferences.getChefPhone(context)
        val messenger = SettingsPreferences.getChefMessenger(context)

        val openIntent = buildMessengerIntent(context, messenger, phone, text)
        val pendingIntent = PendingIntent.getActivity(
            context,
            200,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Список для повара готов")
            .setContentText("Нажмите, чтобы открыть ${messengerName(messenger)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }

    private fun messengerName(m: String) = when (m) {
        "whatsapp" -> "WhatsApp"
        "telegram" -> "Telegram"
        "sms" -> "SMS"
        else -> "мессенджер"
    }

    private fun buildMessengerIntent(
        context: Context,
        messenger: String,
        phone: String,
        text: String
    ): Intent {
        // Для WhatsApp чистим номер до цифр (без +)
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")

        return when (messenger) {
            "whatsapp" -> {
                val uri = Uri.parse(
                    "whatsapp://send?phone=$cleanPhone&text=${Uri.encode(text)}"
                )
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                }.let { wa ->
                    if (isPackageInstalled(context, "com.whatsapp")) wa
                    else if (isPackageInstalled(context, "com.whatsapp.w4b")) {
                        Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.whatsapp.w4b")
                        }
                    } else fallbackChooser(text)
                }
            }
            "telegram" -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    setPackage("org.telegram.messenger")
                }.let { tg ->
                    if (isPackageInstalled(context, "org.telegram.messenger")) tg
                    else fallbackChooser(text)
                }
            }
            "sms" -> Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                putExtra("sms_body", text)
            }
            else -> fallbackChooser(text)
        }
    }

    private fun fallbackChooser(text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, "Отправить список")
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}