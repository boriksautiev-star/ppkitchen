package com.ppkitchen.data

import android.content.Context
import com.ppkitchen.ui.formatDisplayDate
import com.ppkitchen.ui.isDeliveryDayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ShoppingItem(
    val ingredient: Ingredient,
    val totalAmount: Double
)

object ShoppingListGenerator {

    suspend fun generate(context: Context, startDate: String, daysCount: Int): Map<String, List<ShoppingItem>> = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val allOrders = db.orderDao().getAll().first()
        val templates = db.menuTemplateDao().getAll().first()
        val allMenuDays = db.menuDayDao().getAllOnce()
        val allMenuDayDishes = db.menuDayDishDao().getAllOnce()
        val allDishes = db.dishDao().getAll().first()
        val allDishIngredients = db.dishIngredientDao().getAll().first()
        val allIngredients = db.ingredientDao().getAll().first()
        val allRations = db.rationDao().getAll().first()

        val dishCounts = mutableMapOf<Long, Int>()

        val cal = Calendar.getInstance()
        cal.time = sdf.parse(startDate) ?: Date()

        for (i in 0 until daysCount) {
            val currentDate = sdf.format(cal.time)

            val dayOrders = allOrders.filter { o ->
                o.deliveryStart <= currentDate && o.deliveryEnd >= currentDate &&
                        isDeliveryDayOfWeek(o, currentDate) && !o.isSkipped(currentDate)
            }

            dayOrders.forEach { order ->
                val template = templates.find { it.id == order.menuTemplateId }
                if (template != null) {
                    val ration = allRations.find { it.id == order.rationId }

                    val dishesForDay = MenuCalculator.getDishesForDate(
                        order = order,
                        targetDate = currentDate,
                        template = template,
                        menuDays = allMenuDays.filter { it.templateId == template.id },
                        allMenuDayDishes = allMenuDayDishes,
                        allDishes = allDishes,
                        ration = ration
                    )

                    dishesForDay.forEach { dish ->
                        dishCounts[dish.id] = (dishCounts[dish.id] ?: 0) + 1
                    }
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        val ingredientTotals = mutableMapOf<Long, Double>()

        dishCounts.forEach { (dishId, count) ->
            val ingredientsForDish = allDishIngredients.filter { it.dishId == dishId }

            ingredientsForDish.forEach { di ->
                val ing = allIngredients.find { it.id == di.ingredientId }
                if (ing != null) {
                    val waste = ing.wastePercent.coerceIn(0.0, 99.9)
                    val totalNetto = di.amount * count
                    val totalBrutto = if (waste > 0) totalNetto / ((100.0 - waste) / 100.0) else totalNetto

                    ingredientTotals[ing.id] = (ingredientTotals[ing.id] ?: 0.0) + totalBrutto
                }
            }
        }

        val result = mutableMapOf<String, List<ShoppingItem>>()

        ingredientTotals.forEach { (ingId, totalAmount) ->
            val ing = allIngredients.find { it.id == ingId }
            if (ing != null) {
                val item = ShoppingItem(ing, totalAmount)
                val cat = ing.category
                if (!result.containsKey(cat)) result[cat] = emptyList()
                result[cat] = result[cat]!! + item
            }
        }

        result.mapValues { entry -> entry.value.sortedBy { it.ingredient.name } }
    }

    fun formatToString(data: Map<String, List<ShoppingItem>>, startDate: String, daysCount: Int): String {
        val sb = StringBuilder()
        sb.append("🛒 Список закупок на $daysCount дней\n")
        sb.append("С ${formatDisplayDate(startDate)}\n")
        sb.append("─────────────────\n\n")

        if (data.isEmpty()) {
            sb.append("Заказов с назначенным меню на этот период нет.")
            return sb.toString()
        }

        val sortedCategories = data.keys.sorted()
        sortedCategories.forEach { category ->
            sb.append("📦 $category:\n")
            data[category]?.forEach { item ->
                val formattedAmount = formatAmount(item.totalAmount, item.ingredient.unit)
                sb.append(" • ${item.ingredient.name} — $formattedAmount\n")
            }
            sb.append("\n")
        }

        sb.append("─────────────────\n")
        sb.append("Сформировано в PP Kitchen")
        return sb.toString()
    }

    // ИСПРАВЛЕННАЯ ФУНКЦИЯ: Игнорирует старые ошибки в базе ("кг")
    fun formatAmount(amount: Double, unit: String): String {
        if (unit == "шт") {
            return String.format(Locale.US, "%.1f", amount) + " шт"
        }
        // Для всего остального (г, мл, кг, л) мы всегда считали в граммах/миллилитрах
        return if (amount >= 1000) {
            String.format(Locale.US, "%.2f", amount / 1000.0) + " " + if (unit == "мл" || unit == "л") "л" else "кг"
        } else {
            String.format(Locale.US, "%.0f", amount) + " " + if (unit == "мл" || unit == "л") "мл" else "г"
        }
    }
}