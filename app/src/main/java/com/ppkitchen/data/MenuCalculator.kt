package com.ppkitchen.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object MenuCalculator {

    fun getDishesForDate(
        order: Order,
        targetDate: String,
        template: MenuTemplate?,
        menuDays: List<MenuDay>,
        allMenuDayDishes: List<MenuDayDish>,
        allDishes: List<Dish>,
        ration: Ration? = null
    ): List<Dish> {
        if (order.menuTemplateId == 0L || template == null) return emptyList()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val start = try { LocalDate.parse(order.deliveryStart, formatter) } catch (e: Exception) { return emptyList() }
        val target = try { LocalDate.parse(targetDate, formatter) } catch (e: Exception) { return emptyList() }

        // Если дата раньше начала доставки клиента - ничего не готовим
        if (target.isBefore(start)) return emptyList()

        // Проверяем пропуски
        val skipped = if (order.skippedDates.isBlank()) {
            emptySet()
        } else {
            order.skippedDates.split("|").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        if (targetDate in skipped) return emptyList()

        // Проверяем, является ли день днем доставки
        val allowedDays = order.deliveryDaysOfWeek.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        val currentDayOfWeek = target.dayOfWeek.value
        if (currentDayOfWeek !in allowedDays) return emptyList()

        val cycleLength = template.cycleLength
        if (cycleLength <= 0) return emptyList()

        // ИСПОЛЬЗУЕМ ДАТУ СТАРТА ИЗ ШАБЛОНА
        val anchorDateStr = if (template.startDate.isNotBlank()) template.startDate else "2026-01-01"
        val anchor = try { LocalDate.parse(anchorDateStr, formatter) } catch (e: Exception) { LocalDate.parse("2026-01-01", formatter) }

        val daysSinceAnchor = ChronoUnit.DAYS.between(anchor, target)
        val cycleDayNumber = ((daysSinceAnchor % cycleLength + cycleLength) % cycleLength).toInt() + 1

        val menuDay = menuDays.find { it.dayNumber == cycleDayNumber } ?: return emptyList()
        val dishIds = allMenuDayDishes.filter { it.menuDayId == menuDay.id }.map { it.dishId }

        var resultDishes = allDishes.filter { it.id in dishIds }

        if (ration != null && ration.categories.isNotBlank()) {
            val allowedCategories = ration.categories.split(",").map { it.trim() }.toSet()
            resultDishes = resultDishes.filter { dish ->
                allowedCategories.contains(dish.category)
            }
        }

        return resultDishes
    }
}