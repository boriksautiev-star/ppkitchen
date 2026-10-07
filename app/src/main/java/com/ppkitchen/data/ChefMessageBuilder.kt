package com.ppkitchen.data

import java.text.SimpleDateFormat
import java.util.*

object ChefMessageBuilder {

    fun isDeliveryDay(order: Order, date: String): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(date) ?: return true
        val day = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val days = order.deliveryDaysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        return days.isEmpty() || day in days
    }

    fun ordersForDate(allOrders: List<Order>, date: String): List<Order> {
        return allOrders.filter { o ->
            o.deliveryStart <= date &&
                    o.deliveryEnd >= date &&
                    isDeliveryDay(o, date) &&
                    !o.isSkipped(date)
        }
    }

    fun build(date: String, orders: List<Order>, clients: List<Client>): String {
        val sortedOrders = orders.sortedWith(
            compareBy(
                { it.calories },
                { clients.find { c -> c.id == it.clientId }?.fullName?.lowercase() ?: "" }
            )
        )

        val sb = StringBuilder()
        sb.append("Заказы для повара на $date\n")
        sb.append("Всего рационов: ${sortedOrders.size}\n")
        sb.append("─────────────────\n\n")

        sortedOrders.forEachIndexed { index, o ->
            val client = clients.find { it.id == o.clientId }
            val name = client?.fullName ?: "Клиент удалён"
            sb.append("${index + 1}. $name — ${o.calories} ккал\n")
            if (o.comment.isNotBlank()) {
                sb.append("   Комментарий: ${o.comment}\n")
            }
        }

        sb.append("\n─────────────────\n")
        sb.append("ИТОГО ПО ТАРИФАМ:\n")

        val grouped = sortedOrders.groupBy { it.calories }.toList().sortedBy { it.first }
        grouped.forEach { (kcal, list) ->
            sb.append("• $kcal ккал — ${list.size} шт.\n")
        }

        val totalCalories = sortedOrders.sumOf { it.calories }
        sb.append("\nВсего калорий за день: $totalCalories\n")
        sb.append("Всего рационов: ${sortedOrders.size}")

        return sb.toString()
    }
}