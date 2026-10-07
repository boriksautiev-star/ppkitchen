package com.ppkitchen.data

import android.content.Context
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MissedExpensesRestorer {

    data class Result(
        val deliveryCreated: Int,
        val deliveryAmount: Int,
        val cookCreated: Int,
        val cookAmount: Int
    ) {
        val totalCreated: Int get() = deliveryCreated + cookCreated
        val totalAmount: Int get() = deliveryAmount + cookAmount
    }

    suspend fun restore(
        context: Context,
        startDate: String,
        endDate: String,
        dryRun: Boolean
    ): Result {
        val db = AppDatabase.get(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())

        val actualEnd = if (endDate > today) today else endDate
        if (startDate > actualEnd) {
            return Result(0, 0, 0, 0)
        }

        val rate = SettingsPreferences.getCourierRate(context)
        val deliveryEnabled = SettingsPreferences.isDeliveryReminderEnabled(context)
        val cookEnabled = SettingsPreferences.isCookReminderEnabled(context)

        val allOrders = db.orderDao().getAll().first()
        val activeCooks = if (cookEnabled) db.cookDao().getActive().first() else emptyList()

        var deliveryCreated = 0
        var deliveryAmount = 0
        var cookCreated = 0
        var cookAmount = 0

        val cal = Calendar.getInstance().apply {
            time = sdf.parse(startDate) ?: Date()
        }
        val endCal = Calendar.getInstance().apply {
            time = sdf.parse(actualEnd) ?: Date()
        }

        var iterations = 0
        while (!cal.after(endCal) && iterations < 400) {
            val day = sdf.format(cal.time)
            val dayOfWeek = dayOfWeekIso(day)

            if (deliveryEnabled && rate > 0) {
                val portions = allOrders.count { o ->
                    o.deliveryStart <= day &&
                            o.deliveryEnd >= day &&
                            isDeliveryDay(o, day) &&
                            !o.isSkipped(day)
                }

                if (portions > 0) {
                    val amount = portions * rate
                    val comment = "[авто] курьер за $day ($portions × $rate ₽)"

                    val existing = db.expenseDao()
                        .countByDateCategoryComment(day, "Доставка (авто)", comment)

                    if (existing == 0) {
                        if (!dryRun) {
                            db.expenseDao().insert(
                                Expense(
                                    date = day,
                                    time = "23:59",
                                    category = "Доставка (авто)",
                                    amount = amount,
                                    comment = comment
                                )
                            )
                        }
                        deliveryCreated++
                        deliveryAmount += amount
                    }
                }
            }

            if (cookEnabled) {
                activeCooks.forEach { cook ->
                    val cookRate = cook.rateForDay(dayOfWeek)
                    if (cookRate > 0) {
                        val comment = "[авто] ${cook.name} за $day"

                        val existing = db.expenseDao()
                            .countByDateCategoryComment(
                                day,
                                "Зарплата повара (авто)",
                                comment
                            )

                        if (existing == 0) {
                            if (!dryRun) {
                                db.expenseDao().insert(
                                    Expense(
                                        date = day,
                                        time = "23:59",
                                        category = "Зарплата повара (авто)",
                                        amount = cookRate,
                                        comment = comment
                                    )
                                )
                            }
                            cookCreated++
                            cookAmount += cookRate
                        }
                    }
                }
            }

            cal.add(Calendar.DAY_OF_MONTH, 1)
            iterations++
        }

        return Result(
            deliveryCreated = deliveryCreated,
            deliveryAmount = deliveryAmount,
            cookCreated = cookCreated,
            cookAmount = cookAmount
        )
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