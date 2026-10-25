package com.ppkitchen.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object TestDataGenerator {

    private data class IngData(
        val name: String, val pricePerKg: Double, val unit: String, val category: String,
        val cal: Double, val prot: Double, val fat: Double, val carb: Double
    )

    private data class Comp(val name: String, val amount: Double)

    private data class DishData(val name: String, val category: String, val components: List<Comp>)

    suspend fun generateMonthMenu(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)

        // 1. ИНГРЕДИЕНТЫ (БЖУ взято из твоего списка)
        val ingredientsList = listOf(
            IngData("Курица грудка", 350.0, "кг", "Мясо", 113.0, 23.6, 1.9, 0.4),
            IngData("Гречка", 90.0, "кг", "Крупы", 345.0, 12.6, 2.6, 68.0),
            IngData("Рис бурый", 120.0, "кг", "Крупы", 337.0, 7.4, 1.8, 72.9),
            IngData("Лосось", 1100.0, "кг", "Рыба", 208.0, 20.0, 13.0, 0.0),
            IngData("Брокколи", 250.0, "кг", "Овощи", 34.0, 2.8, 0.4, 6.6),
            IngData("Яйцо куриное", 9.0, "шт", "Молочка", 78.0, 6.3, 5.7, 0.4),
            IngData("Творог 5%", 320.0, "кг", "Молочка", 121.0, 17.0, 5.0, 1.8),
            IngData("Овсяные хлопья", 80.0, "кг", "Крупы", 352.0, 12.3, 6.1, 61.8),
            IngData("Масло оливковое", 800.0, "л", "Прочее", 898.0, 0.0, 99.8, 0.0),
            IngData("Помидоры", 180.0, "кг", "Овощи", 20.0, 1.1, 0.2, 3.8),
            IngData("Огурцы", 150.0, "кг", "Овощи", 15.0, 0.8, 0.1, 3.0),
            IngData("Перец болгарский", 220.0, "кг", "Овощи", 26.0, 1.0, 0.3, 5.3),
            IngData("Сыр Российский", 600.0, "кг", "Молочка", 364.0, 23.0, 30.0, 0.0),
            IngData("Банан", 100.0, "кг", "Фрукты", 89.0, 1.5, 0.1, 22.4),
            IngData("Яблоко", 90.0, "кг", "Фрукты", 52.0, 0.4, 0.4, 13.8)
        )

        val ingDao = db.ingredientDao()
        val dishDao = db.dishDao()
        val dishIngDao = db.dishIngredientDao()
        val templateDao = db.menuTemplateDao()
        val menuDayDao = db.menuDayDao()
        val menuDayDishDao = db.menuDayDishDao()

        val existingIngredients = ingDao.getAll().first()
        val ingMap = mutableMapOf<String, Long>()

        for (ing in ingredientsList) {
            val existing = existingIngredients.find { it.name == ing.name }
            if (existing != null) {
                ingMap[ing.name] = existing.id
            } else {
                val newId = ingDao.insert(
                    Ingredient(
                        name = ing.name, pricePerKg = ing.pricePerKg, unit = ing.unit,
                        category = ing.category, calories = ing.cal, protein = ing.prot,
                        fat = ing.fat, carbs = ing.carb
                    )
                )
                ingMap[ing.name] = newId
            }
        }

        // 2. БЛЮДА
        val dishesList = listOf(
            DishData("Курица с гречкой и брокколи", "Обед", listOf(Comp("Курица грудка", 150.0), Comp("Гречка", 80.0), Comp("Брокколи", 100.0), Comp("Масло оливковое", 5.0))),
            DishData("Лосось с рисом и овощами", "Обед", listOf(Comp("Лосось", 150.0), Comp("Рис бурый", 80.0), Comp("Помидоры", 100.0), Comp("Огурцы", 100.0), Comp("Масло оливковое", 5.0))),
            DishData("Омлет с сыром и овощами", "Завтрак", listOf(Comp("Яйцо куриное", 2.0), Comp("Сыр Российский", 30.0), Comp("Перец болгарский", 50.0), Comp("Помидоры", 50.0), Comp("Масло оливковое", 5.0))),
            DishData("Творог с бананом", "Завтрак", listOf(Comp("Творог 5%", 150.0), Comp("Банан", 100.0))),
            DishData("Овсянка с яблоком", "Завтрак", listOf(Comp("Овсяные хлопья", 60.0), Comp("Яблоко", 100.0), Comp("Масло оливковое", 5.0))),
            DishData("Курица с рисом и овощами", "Обед", listOf(Comp("Курица грудка", 150.0), Comp("Рис бурый", 80.0), Comp("Перец болгарский", 100.0), Comp("Огурцы", 100.0), Comp("Масло оливковое", 5.0))),
            DishData("Салат с курицей", "Ужин", listOf(Comp("Курица грудка", 100.0), Comp("Помидоры", 100.0), Comp("Огурцы", 100.0), Comp("Перец болгарский", 50.0), Comp("Масло оливковое", 10.0))),
            DishData("Творог с яблоком", "Ужин", listOf(Comp("Творог 5%", 150.0), Comp("Яблоко", 100.0)))
        )

        val existingDishes = dishDao.getAll().first()
        val dishIds = mutableListOf<Long>()

        for (d in dishesList) {
            val existing = existingDishes.find { it.name == d.name }
            if (existing != null) {
                dishIds.add(existing.id)
                continue
            }

            var totalCal = 0.0; var totalProt = 0.0; var totalFat = 0.0; var totalCarb = 0.0
            for (c in d.components) {
                val ing = ingredientsList.find { it.name == c.name } ?: continue
                val factor = if (ing.unit == "шт") c.amount else c.amount / 100.0
                totalCal += ing.cal * factor; totalProt += ing.prot * factor; totalFat += ing.fat * factor; totalCarb += ing.carb * factor
            }

            val newDishId = dishDao.insert(
                Dish(
                    name = d.name, calories = totalCal.toInt(), protein = totalProt.toInt(),
                    fat = totalFat.toInt(), carbs = totalCarb.toInt(), category = d.category
                )
            )
            dishIds.add(newDishId)

            for (c in d.components) {
                val ingId = ingMap[c.name] ?: continue
                dishIngDao.insert(DishIngredient(dishId = newDishId, ingredientId = ingId, amount = c.amount))
            }
        }

        // 3. ШАБЛОН НА 30 ДНЕЙ
        val allTemplates = templateDao.getAll().first()
        val oldTestTemplate = allTemplates.find { it.name == "Тестовое меню на 30 дней" }
        if (oldTestTemplate != null) {
            val oldDays = menuDayDao.getDaysForTemplate(oldTestTemplate.id).first()
            for (day in oldDays) {
                menuDayDishDao.deleteForDay(day.id)
                menuDayDao.delete(day)
            }
            templateDao.delete(oldTestTemplate)
        }

        val template = MenuTemplate(name = "Тестовое меню на 30 дней", cycleLength = 30)
        val templateId = templateDao.insert(template)

        // Индексы блюд: 0,1,5 - Обеды. 2,3,4 - Завтраки. 6,7 - Ужины.
        val breakfastIds = listOf(dishIds[2], dishIds[3], dishIds[4])
        val lunchIds = listOf(dishIds[0], dishIds[1], dishIds[5])
        val dinnerIds = listOf(dishIds[6], dishIds[7])

        for (dayNum in 1..30) {
            val menuDayId = menuDayDao.insert(MenuDay(templateId = templateId, dayNumber = dayNum))
            menuDayDishDao.insert(MenuDayDish(menuDayId = menuDayId, dishId = breakfastIds[(dayNum - 1) % breakfastIds.size]))
            menuDayDishDao.insert(MenuDayDish(menuDayId = menuDayId, dishId = lunchIds[(dayNum - 1) % lunchIds.size]))
            menuDayDishDao.insert(MenuDayDish(menuDayId = menuDayId, dishId = dinnerIds[(dayNum - 1) % dinnerIds.size]))
        }
    }
}