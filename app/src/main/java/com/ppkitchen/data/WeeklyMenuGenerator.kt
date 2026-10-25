package com.ppkitchen.data

import android.content.Context
import android.content.Intent
import com.ppkitchen.ui.formatDisplayDate
import com.ppkitchen.ui.isDeliveryDayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WeeklyMenuGenerator {

    suspend fun generateAndShare(context: Context, startDate: String) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val allOrders = db.orderDao().getAll().first()
        val templates = db.menuTemplateDao().getAll().first()
        val allMenuDays = db.menuDayDao().getAllOnce()
        val allMenuDayDishes = db.menuDayDishDao().getAllOnce()
        val allDishes = db.dishDao().getAll().first()

        val sb = StringBuilder()
        sb.append("Меню для повара на неделю\n")
        sb.append("С ${formatDisplayDate(startDate)}\n")
        sb.append("─────────────────\n\n")

        val cal = Calendar.getInstance()
        cal.time = sdf.parse(startDate) ?: Date()

        for (i in 0 until 7) {
            val currentDate = sdf.format(cal.time)
            val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Понедельник"
                Calendar.TUESDAY -> "Вторник"
                Calendar.WEDNESDAY -> "Среда"
                Calendar.THURSDAY -> "Четверг"
                Calendar.FRIDAY -> "Пятница"
                Calendar.SATURDAY -> "Суббота"
                Calendar.SUNDAY -> "Воскресенье"
                else -> ""
            }

            sb.append("$dayName (${formatDisplayDate(currentDate)}):\n")

            // Ищем все заказы, которые доставляются в этот день
            val dayOrders = allOrders.filter { o ->
                o.deliveryStart <= currentDate && o.deliveryEnd >= currentDate &&
                        isDeliveryDayOfWeek(o, currentDate) && !o.isSkipped(currentDate)
            }

            if (dayOrders.isEmpty()) {
                sb.append("• Нет доставок\n")
            } else {
                // Считаем количество каждого блюда
                val dishCounts = mutableMapOf<String, Int>()

                dayOrders.forEach { order ->
                    val template = templates.find { it.id == order.menuTemplateId }
                    if (template != null) {
                        val dishes = MenuCalculator.getDishesForDate(
                            order = order,
                            targetDate = currentDate,
                            template = template,
                            menuDays = allMenuDays.filter { it.templateId == template.id },
                            allMenuDayDishes = allMenuDayDishes,
                            allDishes = allDishes
                        )
                        dishes.forEach { dish ->
                            dishCounts[dish.name] = (dishCounts[dish.name] ?: 0) + 1
                        }
                    }
                }

                if (dishCounts.isEmpty()) {
                    sb.append("• Блюда не назначены (у клиентов нет шаблона меню)\n")
                } else {
                    dishCounts.forEach { (dishName, count) ->
                        sb.append("• $dishName — $count порций\n")
                    }
                }
            }
            sb.append("\n")
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        withContext(Dispatchers.Main) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Отправить меню повару"))
        }
    }
}