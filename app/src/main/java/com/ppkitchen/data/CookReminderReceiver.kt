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

class CookReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!SettingsPreferences.isCookReminderEnabled(appContext)) {
                    Log.d("CookReminder", "Выключено, пропускаем")
                    return@launch
                }

                val db = AppDatabase.get(appContext)
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                val now = Date()
                val today = sdfDate.format(now)
                val currentTime = sdfTime.format(now)
                val dayOfWeek = dayOfWeekIso(today)

                val cooks = db.cookDao().getActive().first()

                if (cooks.isEmpty()) {
                    Log.d("CookReminder", "Нет активных поваров")
                    CookReminderScheduler.scheduleNext(appContext)
                    return@launch
                }

                var createdCount = 0

                cooks.forEach { cook ->
                    val rate = cook.rateForDay(dayOfWeek)

                    if (rate <= 0) {
                        Log.d("CookReminder", "${cook.name}: сегодня выходной (ставка 0)")
                        return@forEach
                    }

                    val comment = "[авто] ${cook.name} за $today"
                    val category = "Зарплата повара (авто)"

                    val existing = db.expenseDao()
                        .countByDateCategoryComment(today, category, comment)

                    if (existing == 0) {
                        db.expenseDao().insert(
                            Expense(
                                date = today,
                                time = currentTime,
                                category = category,
                                amount = rate,
                                comment = comment
                            )
                        )
                        createdCount++
                        Log.d("CookReminder", "Создан расход: $comment = $rate ₽ в $currentTime")
                    }
                }

                Log.d("CookReminder", "Создано записей: $createdCount")
                CookReminderScheduler.scheduleNext(appContext)
            } catch (e: Exception) {
                Log.e("CookReminder", "Ошибка", e)
            } finally {
                pending.finish()
            }
        }
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