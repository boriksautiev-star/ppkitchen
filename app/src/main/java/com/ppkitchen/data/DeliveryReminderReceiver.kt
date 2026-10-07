package com.ppkitchen.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DeliveryReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!SettingsPreferences.isDeliveryReminderEnabled(appContext)) {
                    Log.d("DeliveryReminder", "Выключено, пропускаем")
                    return@launch
                }

                val db = AppDatabase.get(appContext)
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                val now = Date()
                val today = sdfDate.format(now)
                val currentTime = sdfTime.format(now)

                val rate = SettingsPreferences.getCourierRate(appContext)
                if (rate <= 0) {
                    Log.d("DeliveryReminder", "Ставка курьера не задана")
                    DeliveryReminderScheduler.scheduleNext(appContext)
                    return@launch
                }

                val allOrders = db.orderDao().getAll().first()

                // Считаем количество доставок на сегодня (без пропущенных)
                val todayPortions = allOrders.count { o ->
                    o.deliveryStart <= today &&
                            o.deliveryEnd >= today &&
                            isDeliveryDay(o, today) &&
                            !o.isSkipped(today)
                }

                if (todayPortions <= 0) {
                    Log.d("DeliveryReminder", "Сегодня доставок нет")
                    DeliveryReminderScheduler.scheduleNext(appContext)
                    return@launch
                }

                val amount = todayPortions * rate
                val comment = "[авто] курьер за $today ($todayPortions × $rate ₽)"
                val category = "Доставка (авто)"

                val existing = db.expenseDao()
                    .countByDateCategoryComment(today, category, comment)

                if (existing == 0) {
                    db.expenseDao().insert(
                        Expense(
                            date = today,
                            time = currentTime,
                            category = category,
                            amount = amount,
                            comment = comment
                        )
                    )
                    Log.d("DeliveryReminder", "Создан расход: $comment = $amount ₽")
                } else {
                    Log.d("DeliveryReminder", "Расход уже создан сегодня")
                }

                DeliveryReminderScheduler.scheduleNext(appContext)
            } catch (e: Exception) {
                Log.e("DeliveryReminder", "Ошибка", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun isDeliveryDay(o: Order, date: String): Boolean {
        val days = o.deliveryDaysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        if (days.isEmpty()) return true
        return dayOfWeekIso(date) in days
    }

    private fun dayOfWeekIso(date: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(date) ?: return 1
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
        } catch (e: Exception) {
            1
        }
    }
}