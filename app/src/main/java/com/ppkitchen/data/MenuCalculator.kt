package com.ppkitchen.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object MenuCalculator {

    /**
     * Возвращает список блюд, которые нужно приготовить на указанную дату.
     */
    fun getDishesForDate(
        order: Order,
        targetDate: String,
        template: MenuTemplate?,
        menuDays: List<MenuDay>,
        allMenuDayDishes: List<MenuDayDish>,
        allDishes: List<Dish>
    ): List<Dish> {
        if (order.menuTemplateId == 0L || template == null) return emptyList()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val start = try { LocalDate.parse(order.deliveryStart, formatter) } catch (e: Exception) { return emptyList() }
        val target = try { LocalDate.parse(targetDate, formatter) } catch (e: Exception) { return emptyList() }

        // Если целевая дата раньше старта или это пропущенный день - ничего не готовим
        if (target.isBefore(start)) return emptyList()
        val skipped = order.skippedSet()
        if (targetDate in skipped) return emptyList()

        // Считаем, сколько дней доставки прошло с момента старта (включительно)
        val allowedDays = order.deliveryDaysOfWeek.split(",").mapNotNull { it.toIntOrNull() }.toSet()

        var deliveryCount = 0L
        var current = start
        while (!current.isAfter(target)) {
            // День недели: 1 - Пн, 7 - Вс. В java.time: 1 - Пн, 7 - Вс (совпадает)
            if (current.dayOfWeek.value in allowedDays && current.format(formatter) !in skipped) {
                deliveryCount++
            }
            current = current.plusDays(1)
        }

        // Если сегодня нет доставки - блюда не нужны
        val currentDayOfWeek = target.dayOfWeek.value
        if (currentDayOfWeek !in allowedDays) return emptyList()

        // Вычисляем день цикла (1..cycleLength)
        val cycleLength = template.cycleLength
        if (cycleLength <= 0) return emptyList()

        val cycleDayNumber = ((deliveryCount - 1) % cycleLength).toInt() + 1

        // Находим нужный день в шаблоне
        val menuDay = menuDays.find { it.dayNumber == cycleDayNumber } ?: return emptyList()

        // Достаем ID блюд для этого дня
        val dishIds = allMenuDayDishes.filter { it.menuDayId == menuDay.id }.map { it.dishId }

        // Возвращаем сами блюда
        return allDishes.filter { it.id in dishIds }
    }
}