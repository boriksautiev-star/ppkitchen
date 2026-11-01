package com.ppkitchen.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.dhatim.fastexcel.Workbook
import java.io.InputStream
import java.io.OutputStream

object ExcelHelper {
    private val excelFormatter = DataFormatter()

    private fun getCellString(cell: Cell?): String {
        if (cell == null) return ""
        return excelFormatter.formatCellValue(cell).trim()
    }

    private fun getCellDouble(cell: Cell?): Double {
        if (cell == null) return 0.0
        return excelFormatter.formatCellValue(cell).replace(',', '.').toDoubleOrNull() ?: 0.0
    }

    // ==========================================
    // ИНГРЕДИЕНТЫ
    // ==========================================

    fun createIngredientTemplate(out: OutputStream) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val ws = wb.newWorksheet("Ингредиенты")

        ws.value(0, 0, "Название"); ws.value(0, 1, "Цена")
        ws.value(0, 2, "Категория"); ws.value(0, 3, "Ед.изм")
        ws.value(0, 4, "Ккал"); ws.value(0, 5, "Белки")
        ws.value(0, 6, "Жиры"); ws.value(0, 7, "Углеводы")
        ws.value(0, 8, "Отходы(%)"); ws.value(0, 9, "Варка(%)")
        ws.value(0, 10, "Жарка(%)"); ws.value(0, 11, "Запекание(%)")
        ws.value(0, 12, "Тушение(%)")

        ws.value(1, 0, "Курица грудка"); ws.value(1, 1, 350.0)
        ws.value(1, 2, "Мясо"); ws.value(1, 3, "г")
        ws.value(1, 4, 113.0); ws.value(1, 5, 23.6)
        ws.value(1, 6, 1.9); ws.value(1, 7, 0.4)
        ws.value(1, 8, 5.0); ws.value(1, 9, 25.0)
        ws.value(1, 10, 35.0); ws.value(1, 11, 40.0); ws.value(1, 12, 30.0)

        wb.finish()
    }

    fun exportIngredients(out: OutputStream, ingredients: List<Ingredient>) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val ws = wb.newWorksheet("Ингредиенты")

        ws.value(0, 0, "Название"); ws.value(0, 1, "Цена")
        ws.value(0, 2, "Категория"); ws.value(0, 3, "Ед.изм")
        ws.value(0, 4, "Ккал"); ws.value(0, 5, "Белки")
        ws.value(0, 6, "Жиры"); ws.value(0, 7, "Углеводы")
        ws.value(0, 8, "Отходы(%)"); ws.value(0, 9, "Варка(%)")
        ws.value(0, 10, "Жарка(%)"); ws.value(0, 11, "Запекание(%)")
        ws.value(0, 12, "Тушение(%)")

        ingredients.forEachIndexed { index, ing ->
            val row = index + 1
            ws.value(row, 0, ing.name); ws.value(row, 1, ing.pricePerKg)
            ws.value(row, 2, ing.category); ws.value(row, 3, ing.unit)
            ws.value(row, 4, ing.calories); ws.value(row, 5, ing.protein)
            ws.value(row, 6, ing.fat); ws.value(row, 7, ing.carbs)
            ws.value(row, 8, ing.wastePercent); ws.value(row, 9, ing.lossBoiling)
            ws.value(row, 10, ing.lossFrying); ws.value(row, 11, ing.lossBaking)
            ws.value(row, 12, ing.lossStewing)
        }
        wb.finish()
    }

    fun importIngredients(input: InputStream, existingIngredients: List<Ingredient>): Pair<List<Ingredient>, List<Ingredient>> {
        val toInsert = mutableListOf<Ingredient>()
        val toUpdate = mutableListOf<Ingredient>()

        val workbook = WorkbookFactory.create(input)
        try {
            val sheet = workbook.getSheetAt(0)
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                try {
                    val name = getCellString(row.getCell(0))
                    if (name.isNotBlank()) {
                        val price = getCellDouble(row.getCell(1))
                        val cat = getCellString(row.getCell(2)).ifBlank { "Прочее" }
                        val unit = getCellString(row.getCell(3)).ifBlank { "г" }
                        val kcal = getCellDouble(row.getCell(4))
                        val prot = getCellDouble(row.getCell(5))
                        val fat = getCellDouble(row.getCell(6))
                        val carb = getCellDouble(row.getCell(7))
                        val waste = getCellDouble(row.getCell(8))
                        val lossBoil = getCellDouble(row.getCell(9))
                        val lossFry = getCellDouble(row.getCell(10))
                        val lossBake = getCellDouble(row.getCell(11))
                        val lossStew = getCellDouble(row.getCell(12))

                        val existing = existingIngredients.find { it.name.equals(name, ignoreCase = true) }
                        val newIng = Ingredient(
                            id = existing?.id ?: 0, name = name, pricePerKg = price,
                            category = cat, unit = unit, calories = kcal, protein = prot,
                            fat = fat, carbs = carb, wastePercent = waste,
                            lossBoiling = lossBoil, lossFrying = lossFry,
                            lossBaking = lossBake, lossStewing = lossStew
                        )
                        if (existing != null) toUpdate.add(newIng) else toInsert.add(newIng)
                    }
                } catch (_: Exception) { }
            }
        } finally {
            workbook.close()
        }
        return Pair(toUpdate, toInsert)
    }

    // ==========================================
    // БЛЮДА (РЕЦЕПТЫ)
    // ==========================================

    fun createDishTemplate(out: OutputStream) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val wsDishes = wb.newWorksheet("Блюда")
        wsDishes.value(0, 0, "Название"); wsDishes.value(0, 1, "Категория")
        wsDishes.value(0, 2, "Ккал"); wsDishes.value(0, 3, "Белки")
        wsDishes.value(0, 4, "Жиры"); wsDishes.value(0, 5, "Углеводы")
        wsDishes.value(1, 0, "Плов"); wsDishes.value(1, 1, "Обед")
        wsDishes.value(1, 2, 350); wsDishes.value(1, 3, 15)
        wsDishes.value(1, 4, 10); wsDishes.value(1, 5, 45)

        val wsIngredients = wb.newWorksheet("Состав")
        wsIngredients.value(0, 0, "Название блюда"); wsIngredients.value(0, 1, "Ингредиент")
        wsIngredients.value(0, 2, "Количество (г)"); wsIngredients.value(0, 3, "Способ обработки")
        wsIngredients.value(1, 0, "Плов"); wsIngredients.value(1, 1, "Рис")
        wsIngredients.value(1, 2, 100.0); wsIngredients.value(1, 3, "Варка")
        wsIngredients.value(2, 0, "Плов"); wsIngredients.value(2, 1, "Курица")
        wsIngredients.value(2, 2, 150.0); wsIngredients.value(2, 3, "Жарка")
        wb.finish()
    }

    fun exportDishes(out: OutputStream, dishes: List<Dish>, dishIngredients: List<DishIngredient>, ingredients: List<Ingredient>) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val wsDishes = wb.newWorksheet("Блюда")
        wsDishes.value(0, 0, "Название"); wsDishes.value(0, 1, "Категория")
        wsDishes.value(0, 2, "Ккал"); wsDishes.value(0, 3, "Белки")
        wsDishes.value(0, 4, "Жиры"); wsDishes.value(0, 5, "Углеводы")

        dishes.forEachIndexed { index, dish ->
            val row = index + 1
            wsDishes.value(row, 0, dish.name); wsDishes.value(row, 1, dish.category)
            wsDishes.value(row, 2, dish.calories); wsDishes.value(row, 3, dish.protein)
            wsDishes.value(row, 4, dish.fat); wsDishes.value(row, 5, dish.carbs)
        }

        val wsIngredients = wb.newWorksheet("Состав")
        wsIngredients.value(0, 0, "Название блюда"); wsIngredients.value(0, 1, "Ингредиент")
        wsIngredients.value(0, 2, "Количество (г)"); wsIngredients.value(0, 3, "Способ обработки")

        var rowIndex = 1
        dishIngredients.forEach { di ->
            val dish = dishes.find { it.id == di.dishId }
            val ing = ingredients.find { it.id == di.ingredientId }
            if (dish != null && ing != null) {
                wsIngredients.value(rowIndex, 0, dish.name)
                wsIngredients.value(rowIndex, 1, ing.name)
                wsIngredients.value(rowIndex, 2, di.amount)
                wsIngredients.value(rowIndex, 3, di.cookingMethod)
                rowIndex++
            }
        }
        wb.finish()
    }

    suspend fun importDishes(input: InputStream, db: AppDatabase): Pair<Int, Int> {
        val currentDishes = db.dishDao().getAll().first()
        val currentIngredients = db.ingredientDao().getAllOnce()

        val dishMap = mutableMapOf<String, Long>()
        val toInsertDishes = mutableListOf<Dish>()
        val toUpdateDishes = mutableListOf<Dish>()
        val parsedIngredients = mutableListOf<Pair<String, DishIngredient>>()

        val workbook = WorkbookFactory.create(input)
        try {
            val dishSheet = workbook.getSheetAt(0)
            for (rowIndex in 1..dishSheet.lastRowNum) {
                val row = dishSheet.getRow(rowIndex) ?: continue
                try {
                    val name = excelFormatter.formatCellValue(row.getCell(0)).trim()
                    if (name.isNotBlank()) {
                        val cat = excelFormatter.formatCellValue(row.getCell(1)).trim().ifBlank { "Прочее" }
                        val cal = excelFormatter.formatCellValue(row.getCell(2)).replace(',', '.').toDoubleOrNull()?.toInt() ?: 0
                        val prot = excelFormatter.formatCellValue(row.getCell(3)).replace(',', '.').toDoubleOrNull()?.toInt() ?: 0
                        val fat = excelFormatter.formatCellValue(row.getCell(4)).replace(',', '.').toDoubleOrNull()?.toInt() ?: 0
                        val carb = excelFormatter.formatCellValue(row.getCell(5)).replace(',', '.').toDoubleOrNull()?.toInt() ?: 0

                        val existing = currentDishes.find { it.name.equals(name, ignoreCase = true) }
                        val newDish = Dish(id = existing?.id ?: 0, name = name, category = cat, calories = cal, protein = prot, fat = fat, carbs = carb)
                        if (existing != null) toUpdateDishes.add(newDish) else toInsertDishes.add(newDish)
                    }
                } catch (_: Exception) { }
            }

            if (workbook.numberOfSheets > 1) {
                val ingSheet = workbook.getSheetAt(1)
                for (rowIndex in 1..ingSheet.lastRowNum) {
                    val row = ingSheet.getRow(rowIndex) ?: continue
                    try {
                        val dishName = excelFormatter.formatCellValue(row.getCell(0)).trim()
                        val ingName = excelFormatter.formatCellValue(row.getCell(1)).trim()
                        val amount = excelFormatter.formatCellValue(row.getCell(2)).replace(',', '.').toDoubleOrNull() ?: 0.0
                        val method = excelFormatter.formatCellValue(row.getCell(3)).trim().ifBlank { "Сырой" }
                        val ing = currentIngredients.find { it.name.equals(ingName, ignoreCase = true) }

                        if (dishName.isNotBlank() && ing != null && amount > 0) {
                            parsedIngredients.add(dishName to DishIngredient(dishId = 0, ingredientId = ing.id, amount = amount, cookingMethod = method))
                        }
                    } catch (_: Exception) { }
                }
            }
        } finally {
            workbook.close()
        }

        for (dish in toUpdateDishes) {
            db.dishDao().update(dish)
            dishMap[dish.name.lowercase()] = dish.id
            db.dishIngredientDao().deleteForDish(dish.id)
        }
        for (dish in toInsertDishes) {
            val newId = db.dishDao().insert(dish)
            dishMap[dish.name.lowercase()] = newId
        }
        for (pair in parsedIngredients) {
            val realDishId = dishMap[pair.first.lowercase()]
            if (realDishId != null) db.dishIngredientDao().insert(pair.second.copy(dishId = realDishId))
        }

        return Pair(toInsertDishes.size, toUpdateDishes.size)
    }

    // ==========================================
    // МЕНЮ
    // ==========================================

    fun createMenuTemplate(out: OutputStream) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val ws = wb.newWorksheet("Меню")
        ws.value(0, 0, "День (число)"); ws.value(0, 1, "Прием пищи (для справки)"); ws.value(0, 2, "Название блюда")
        ws.value(1, 0, 1); ws.value(1, 1, "Завтрак"); ws.value(1, 2, "Овсянка с ягодами")
        ws.value(2, 0, 1); ws.value(2, 1, "Обед"); ws.value(2, 2, "Куриная грудка с рисом")
        ws.value(3, 0, 2); ws.value(3, 1, "Завтрак"); ws.value(3, 2, "Омлет с сыром")
        wb.finish()
    }

    suspend fun exportMenu(out: OutputStream, template: MenuTemplate, db: AppDatabase) {
        val days = db.menuDayDao().getDaysForTemplate(template.id).first()
        val allDishes = db.dishDao().getAll().first()
        val allDayDishes = db.menuDayDishDao().getAllOnce()

        val wb = Workbook(out, "PP Kitchen", "1.0")
        val ws = wb.newWorksheet("Меню")
        ws.value(0, 0, "День"); ws.value(0, 1, "Прием пищи"); ws.value(0, 2, "Название блюда")

        var rowIndex = 1
        for (day in days.sortedBy { it.dayNumber }) {
            val dishesForDay = allDayDishes.filter { it.menuDayId == day.id }
            for (dd in dishesForDay) {
                val dish = allDishes.find { it.id == dd.dishId } ?: continue
                ws.value(rowIndex, 0, day.dayNumber); ws.value(rowIndex, 1, dish.category); ws.value(rowIndex, 2, dish.name)
                rowIndex++
            }
        }
        wb.finish()
    }

    suspend fun importMenu(input: InputStream, template: MenuTemplate, db: AppDatabase): Int {
        var addedCount = 0
        val days = db.menuDayDao().getDaysForTemplate(template.id).first()
        val allDishes = db.dishDao().getAll().first()

        val workbook = WorkbookFactory.create(input)
        try {
            val sheet = workbook.getSheetAt(0)
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                try {
                    val dayNum = excelFormatter.formatCellValue(row.getCell(0)).replace(',', '.').toDoubleOrNull()?.toInt() ?: continue
                    val dishName = excelFormatter.formatCellValue(row.getCell(2)).trim() ?: continue
                    val menuDay = days.find { it.dayNumber == dayNum }
                    val dish = allDishes.find { it.name.equals(dishName, ignoreCase = true) }

                    if (menuDay != null && dish != null) {
                        db.menuDayDishDao().insert(MenuDayDish(menuDayId = menuDay.id, dishId = dish.id))
                        addedCount++
                    }
                } catch (_: Exception) { }
            }
        } finally {
            workbook.close()
        }
        return addedCount
    }
}