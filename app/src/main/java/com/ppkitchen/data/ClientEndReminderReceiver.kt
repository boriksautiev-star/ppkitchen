package com.ppkitchen.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ppkitchen.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ClientEndReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!SettingsPreferences.isClientEndReminderEnabled(appContext)) {
                    Log.d("ClientEndReminder", "Выключено, пропускаем")
                    return@launch
                }

                val db = AppDatabase.get(appContext)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = sdf.format(Date())
                val tomorrow = shiftDate(today, 1)

                val allOrders = db.orderDao().getAll().first()
                val clients = db.clientDao().getAll().first()

                // Заказы, у которых завтра последняя доставка.
                val endingOrders = allOrders.filter { o ->
                    if (o.isOpenEnded) return@filter false
                    if (o.deliveryEnd != tomorrow) return@filter false
                    val hasNext = allOrders.any { other ->
                        other.id != o.id &&
                                other.clientId == o.clientId &&
                                other.deliveryStart > o.deliveryEnd
                    }
                    !hasNext
                }

                if (endingOrders.isEmpty()) {
                    Log.d("ClientEndReminder", "Нет заказов, заканчивающихся завтра")
                    ClientEndReminderScheduler.scheduleNext(appContext)
                    return@launch
                }

                val template = SettingsPreferences.getClientEndReminderText(appContext)
                val mode = SettingsPreferences.getClientEndReminderMode(appContext)

                val sortedOrders = endingOrders.sortedWith(
                    compareBy(
                        { it.calories },
                        { clients.find { c -> c.id == it.clientId }?.fullName?.lowercase() ?: "" }
                    )
                )

                if (mode == "separate") {
                    sortedOrders.forEachIndexed { index, o ->
                        val client = clients.find { it.id == o.clientId }
                        val name = client?.fullName ?: "Клиент удалён"
                        val address = client?.address?.ifBlank { "—" } ?: "—"
                        val phone = client?.phone?.ifBlank { "—" } ?: "—"
                        val dateFormatted = formatDisplayDate(o.deliveryEnd)
                        val text = template
                            .replace("{имя}", name)
                            .replace("{дата}", dateFormatted)
                            .replace("{калории}", "${o.calories}")
                            .replace("{адрес}", address)
                            .replace("{телефон}", phone)

                        val notificationId = 2000 + index
                        showNotification(
                            context = appContext,
                            notificationId = notificationId,
                            title = "Заканчивается питание: $name",
                            text = text,
                            clientId = o.clientId,
                            orderId = o.id
                        )
                    }
                } else {
                    val lines = sortedOrders.map { o ->
                        val client = clients.find { it.id == o.clientId }
                        val name = client?.fullName ?: "Клиент удалён"
                        val address = client?.address?.ifBlank { "—" } ?: "—"
                        val phone = client?.phone?.ifBlank { "—" } ?: "—"
                        val dateFormatted = formatDisplayDate(o.deliveryEnd)
                        "• " + template
                            .replace("{имя}", name)
                            .replace("{дата}", dateFormatted)
                            .replace("{калории}", "${o.calories}")
                            .replace("{адрес}", address)
                            .replace("{телефон}", phone)
                    }

                    val title = if (sortedOrders.size == 1)
                        "Заканчивается питание у клиента"
                    else
                        "Заканчивается питание у ${sortedOrders.size} клиентов"

                    showNotification(
                        context = appContext,
                        notificationId = 1002,
                        title = title,
                        text = lines.joinToString("\n\n"),
                        clientId = -1L,
                        orderId = -1L
                    )
                }

                ClientEndReminderScheduler.scheduleNext(appContext)
            } catch (e: Exception) {
                Log.e("ClientEndReminder", "Ошибка", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        clientId: Long,
        orderId: Long
    ) {
        val channelId = "client_end_reminder_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Окончание питания клиентов",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о клиентах, у которых заканчивается период питания"
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (clientId > 0 && orderId > 0) {
                putExtra("renewal_client_id", clientId)
                putExtra("renewal_order_id", orderId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun shiftDate(date: String, days: Int): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(date) ?: return date
            cal.add(Calendar.DAY_OF_MONTH, days)
            sdf.format(cal.time)
        } catch (e: Exception) {
            date
        }
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
}