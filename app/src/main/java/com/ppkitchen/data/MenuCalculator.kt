package com.ppkitchen.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

        if (target.isBefore(start)) return emptyList()

        // Проверяем пропуски напрямую
        val skipped = if (order.skippedDates.isBlank()) {
            emptySet()
        } else {
            order.skippedDates.split("|").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }

        if (targetDate in skipped) return emptyList()

        val allowedDays = order.deliveryDaysOfWeek.split(",").mapNotNull { it.toIntOrNull() }.toSet()

        var deliveryCount = 0L
        var current = start
        while (!current.isAfter(target)) {
            if (current.dayOfWeek.value in allowedDays && current.format(formatter) !in skipped) {
                deliveryCount++
            }
            current = current.plusDays(1)
        }

        val currentDayOfWeek = target.dayOfWeek.value
        if (currentDayOfWeek !in allowedDays) return emptyList()

        val cycleLength = template.cycleLength
        if (cycleLength <= 0) return emptyList()

        val cycleDayNumber = ((deliveryCount - 1) % cycleLength).toInt() + 1
        val menuDay = menuDays.find { it.dayNumber == cycleDayNumber } ?: return emptyList()
        val dishIds = allMenuDayDishes.filter { it.menuDayId == menuDay.id }.map { it.dishId }

        // Базовый список всех блюд, назначенных на этот день
        var resultDishes = allDishes.filter { it.id in dishIds }

        // ФИЛЬТРАЦИЯ ПО РАЦИОНУ
        if (ration != null && ration.categories.isNotBlank()) {
            val allowedCategories = ration.categories.split(",").map { it.trim() }.toSet()
            resultDishes = resultDishes.filter { dish ->
                allowedCategories.contains(dish.category)
            }
        }

        return resultDishes
    }
}