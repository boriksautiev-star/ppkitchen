package com.ppkitchen.data

import kotlinx.coroutines.flow.first
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.dhatim.fastexcel.Workbook
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

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
    // ПОЛНЫЙ БЭКАП (ВСЁ В ОДНОМ ФАЙЛЕ)
    // ==========================================

    suspend fun exportFullBackup(out: OutputStream, db: AppDatabase) {
        val ingredients = db.ingredientDao().getAllOnce()
        val dishes = db.dishDao().getAll().first()
        val dishIngredients = db.dishIngredientDao().getAll().first()
        val templates = db.menuTemplateDao().getAll().first()

        val allDays = mutableListOf<MenuDay>()
        for (t in templates) {
            allDays.addAll(db.menuDayDao().getDaysForTemplate(t.id).first())
        }
        val dayDishes = db.menuDayDishDao().getAllOnce()

        val wb = Workbook(out, "PP Kitchen Full Backup", "1.0")

        // 1. Ингредиенты
        val ws1 = wb.newWorksheet("1_Ингредиенты")
        ws1.value(0, 0, "Название"); ws1.value(0, 1, "Цена")
        ws1.value(0, 2, "Категория"); ws1.value(0, 3, "Ед.изм")
        ws1.value(0, 4, "Ккал"); ws1.value(0, 5, "Белки")
        ws1.value(0, 6, "Жиры"); ws1.value(0, 7, "Углеводы")
        ws1.value(0, 8, "Отходы(%)"); ws1.value(0, 9, "Варка(%)")
        ws1.value(0, 10, "Жарка(%)"); ws1.value(0, 11, "Запекание(%)")
        ws1.value(0, 12, "Тушение(%)"); ws1.value(0, 13, "Вес 1 шт (г)")
        ingredients.forEachIndexed { i, ing ->
            val r = i + 1
            ws1.value(r, 0, ing.name); ws1.value(r, 1, ing.pricePerKg)
            ws1.value(r, 2, ing.category); ws1.value(r, 3, ing.unit)
            ws1.value(r, 4, ing.calories); ws1.value(r, 5, ing.protein)
            ws1.value(r, 6, ing.fat); ws1.value(r, 7, ing.carbs)
            ws1.value(r, 8, ing.wastePercent); ws1.value(r, 9, ing.lossBoiling)
            ws1.value(r, 10, ing.lossFrying); ws1.value(r, 11, ing.lossBaking)
            ws1.value(r, 12, ing.lossStewing); ws1.value(r, 13, ing.weightPerPiece)
        }

        // 2. Блюда
        val ws2 = wb.newWorksheet("2_Блюда")
        ws2.value(0, 0, "Название"); ws2.value(0, 1, "Категория")
        dishes.forEachIndexed { i, d ->
            ws2.value(i + 1, 0, d.name); ws2.value(i + 1, 1, d.category)
        }

        // 3. Состав блюд
        val ws3 = wb.newWorksheet("3_Состав_Блюд")
        ws3.value(0, 0, "Название блюда"); ws3.value(0, 1, "Ингредиент")
        ws3.value(0, 2, "Выход (г)"); ws3.value(0, 3, "Способ обработки")
        var r3 = 1
        dishIngredients.forEach { di ->
            val dish = dishes.find { it.id == di.dishId }
            val ing = ingredients.find { it.id == di.ingredientId }
            if (dish != null && ing != null) {
                val loss = ing.getLossForMethod(di.cookingMethod)
                val outputWeight = if (loss > 0) di.amount * ((100.0 - loss) / 100.0) else di.amount
                ws3.value(r3, 0, dish.name); ws3.value(r3, 1, ing.name)
                ws3.value(r3, 2, outputWeight); ws3.value(r3, 3, di.cookingMethod)
                r3++
            }
        }

        // 4. Шаблоны меню
        val ws4 = wb.newWorksheet("4_Шаблоны_Меню")
        ws4.value(0, 0, "Название"); ws4.value(0, 1, "Длина цикла"); ws4.value(0, 2, "Дата старта")
        templates.forEachIndexed { i, t ->
            ws4.value(i + 1, 0, t.name); ws4.value(i + 1, 1, t.cycleLength); ws4.value(i + 1, 2, t.startDate)
        }

        // 5. Дни меню (связи)
        val ws5 = wb.newWorksheet("5_Дни_Меню")
        ws5.value(0, 0, "Шаблон меню"); ws5.value(0, 1, "День (число)"); ws5.value(0, 2, "Название блюда")
        var r5 = 1
        dayDishes.forEach { dd ->
            val day = allDays.find { it.id == dd.menuDayId }
            val template = templates.find { it.id == day?.templateId }
            val dish = dishes.find { it.id == dd.dishId }
            if (template != null && day != null && dish != null) {
                ws5.value(r5, 0, template.name)
                ws5.value(r5, 1, day.dayNumber)
                ws5.value(r5, 2, dish.name)
                r5++
            }
        }

        wb.finish()
    }

    suspend fun importFullBackup(input: InputStream, db: AppDatabase, onProgress: (String) -> Unit) {
        val workbook = WorkbookFactory.create(input)
        try {
            // 1. Ингредиенты
            onProgress("Шаг 1/5: Импорт ингредиентов...")
            if (workbook.numberOfSheets > 0) {
                val sheet = workbook.getSheetAt(0)
                val current = db.ingredientDao().getAllOnce()
                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    try {
                        val name = getCellString(row.getCell(0))
                        if (name.isNotBlank()) {
                            val ing = Ingredient(
                                id = current.find { it.name.equals(name, true) }?.id ?: 0,
                                name = name, pricePerKg = getCellDouble(row.getCell(1)),
                                category = getCellString(row.getCell(2)).ifBlank { "Прочее" },
                                unit = getCellString(row.getCell(3)).ifBlank { "г" },
                                calories = getCellDouble(row.getCell(4)), protein = getCellDouble(row.getCell(5)),
                                fat = getCellDouble(row.getCell(6)), carbs = getCellDouble(row.getCell(7)),
                                wastePercent = getCellDouble(row.getCell(8)), lossBoiling = getCellDouble(row.getCell(9)),
                                lossFrying = getCellDouble(row.getCell(10)), lossBaking = getCellDouble(row.getCell(11)),
                                lossStewing = getCellDouble(row.getCell(12)), weightPerPiece = getCellDouble(row.getCell(13))
                            )
                            if (ing.id > 0L) db.ingredientDao().update(ing) else db.ingredientDao().insert(ing)

                            // Обновляем справочник для автозаполнения
                            val ref = db.foodReferenceDao().findByName(name)
                            db.foodReferenceDao().insert(FoodReference(
                                id = ref?.id ?: 0, name = name, calories = ing.calories, protein = ing.protein,
                                fat = ing.fat, carbs = ing.carbs, source = "custom", wastePercent = ing.wastePercent
                            ))
                        }
                    } catch (_: Exception) {}
                }
            }

            // 2. Блюда
            onProgress("Шаг 2/5: Импорт блюд...")
            if (workbook.numberOfSheets > 1) {
                val sheet = workbook.getSheetAt(1)
                val current = db.dishDao().getAll().first()
                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    try {
                        val name = getCellString(row.getCell(0))
                        if (name.isNotBlank()) {
                            val dish = Dish(
                                id = current.find { it.name.equals(name, true) }?.id ?: 0,
                                name = name, category = getCellString(row.getCell(1)).ifBlank { "Прочее" }
                            )
                            if (dish.id > 0L) db.dishDao().update(dish) else db.dishDao().insert(dish)
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. Состав
            onProgress("Шаг 3/5: Связывание состава блюд...")
            if (workbook.numberOfSheets > 2) {
                val sheet = workbook.getSheetAt(2)
                val allDishes = db.dishDao().getAll().first()
                val allIngs = db.ingredientDao().getAllOnce()

                // Очищаем старый состав для импортируемых блюд
                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val dishName = getCellString(row.getCell(0))
                    val dish = allDishes.find { it.name.equals(dishName, true) }
                    if (dish != null) db.dishIngredientDao().deleteForDish(dish.id)
                }

                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    try {
                        val dishName = getCellString(row.getCell(0))
                        val ingName = getCellString(row.getCell(1))
                        val output = getCellDouble(row.getCell(2))
                        val method = getCellString(row.getCell(3)).ifBlank { "Сырой" }

                        val dish = allDishes.find { it.name.equals(dishName, true) }
                        val ing = allIngs.find { it.name.equals(ingName, true) }

                        if (dish != null && ing != null && output > 0) {
                            val loss = ing.getLossForMethod(method)
                            val netWeight = if (loss > 0) output / ((100.0 - loss) / 100.0) else output
                            db.dishIngredientDao().insert(DishIngredient(dish.id, ing.id, netWeight, method))
                        }
                    } catch (_: Exception) {}
                }
            }

            // 4. Шаблоны меню
            onProgress("Шаг 4/5: Импорт шаблонов меню...")
            if (workbook.numberOfSheets > 3) {
                val sheet = workbook.getSheetAt(3)
                val current = db.menuTemplateDao().getAll().first()
                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    try {
                        val name = getCellString(row.getCell(0))
                        if (name.isNotBlank()) {
                            val template = MenuTemplate(
                                id = current.find { it.name.equals(name, true) }?.id ?: 0,
                                name = name, cycleLength = getCellDouble(row.getCell(1)).toInt(),
                                startDate = getCellString(row.getCell(2)).ifBlank { "2026-01-01" }
                            )
                            if (template.id > 0L) db.menuTemplateDao().update(template) else db.menuTemplateDao().insert(template)
                        }
                    } catch (_: Exception) {}
                }
            }

            // 5. Дни меню
            onProgress("Шаг 5/5: Связывание дней меню...")
            if (workbook.numberOfSheets > 4) {
                val sheet = workbook.getSheetAt(4)
                val allTemplates = db.menuTemplateDao().getAll().first()
                val allDishes = db.dishDao().getAll().first()

                val allDays = mutableListOf<MenuDay>()
                for (t in allTemplates) {
                    allDays.addAll(db.menuDayDao().getDaysForTemplate(t.id).first())
                }

                // Очищаем старые дни у шаблонов, которые есть в файле
                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val tName = getCellString(row.getCell(0))
                    val template = allTemplates.find { it.name.equals(tName, true) }
                    if (template != null) {
                        val days = allDays.filter { it.templateId == template.id }
                        for (day in days) {
                            db.menuDayDishDao().deleteForDay(day.id)
                        }
                    }
                }

                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    try {
                        val tName = getCellString(row.getCell(0))
                        val dayNum = getCellDouble(row.getCell(1)).toInt()
                        val dishName = getCellString(row.getCell(2))

                        val template = allTemplates.find { it.name.equals(tName, true) }
                        val dish = allDishes.find { it.name.equals(dishName, true) }

                        if (template != null && dish != null && dayNum > 0) {
                            val day = allDays.find { it.templateId == template.id && it.dayNumber == dayNum }
                            if (day != null) {
                                db.menuDayDishDao().insert(MenuDayDish(day.id, dish.id))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } finally {
            workbook.close()
        }
    }

    // ==========================================
    // ИНГРЕДИЕНТЫ (Отдельно)
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
        ws.value(0, 12, "Тушение(%)"); ws.value(0, 13, "Вес 1 шт (г)")

        ws.value(1, 0, "Курица грудка"); ws.value(1, 1, 350.0)
        ws.value(1, 2, "Мясо"); ws.value(1, 3, "г")
        ws.value(1, 4, 113.0); ws.value(1, 5, 23.6)
        ws.value(1, 6, 1.9); ws.value(1, 7, 0.4)
        ws.value(1, 8, 5.0); ws.value(1, 9, 25.0)
        ws.value(1, 10, 35.0); ws.value(1, 11, 40.0); ws.value(1, 12, 30.0)
        ws.value(1, 13, 0.0)

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
        ws.value(0, 12, "Тушение(%)"); ws.value(0, 13, "Вес 1 шт (г)")

        ingredients.forEachIndexed { index, ing ->
            val row = index + 1
            ws.value(row, 0, ing.name); ws.value(row, 1, ing.pricePerKg)
            ws.value(row, 2, ing.category); ws.value(row, 3, ing.unit)
            ws.value(row, 4, ing.calories); ws.value(row, 5, ing.protein)
            ws.value(row, 6, ing.fat); ws.value(row, 7, ing.carbs)
            ws.value(row, 8, ing.wastePercent); ws.value(row, 9, ing.lossBoiling)
            ws.value(row, 10, ing.lossFrying); ws.value(row, 11, ing.lossBaking)
            ws.value(row, 12, ing.lossStewing); ws.value(row, 13, ing.weightPerPiece)
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
                        val weightPiece = getCellDouble(row.getCell(13))

                        val existing = existingIngredients.find { it.name.equals(name, ignoreCase = true) }
                        val newIng = Ingredient(
                            id = existing?.id ?: 0, name = name, pricePerKg = price,
                            category = cat, unit = unit, calories = kcal, protein = prot,
                            fat = fat, carbs = carb, wastePercent = waste,
                            lossBoiling = lossBoil, lossFrying = lossFry,
                            lossBaking = lossBake, lossStewing = lossStew,
                            weightPerPiece = weightPiece
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
    // БЛЮДА (Отдельно)
    // ==========================================

    fun createDishTemplate(out: OutputStream) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val wsDishes = wb.newWorksheet("Блюда")
        wsDishes.value(0, 0, "Название"); wsDishes.value(0, 1, "Категория")
        wsDishes.value(1, 0, "Плов"); wsDishes.value(1, 1, "Обед")

        val wsIngredients = wb.newWorksheet("Состав (Техкарта)")
        wsIngredients.value(0, 0, "Название блюда"); wsIngredients.value(0, 1, "Ингредиент")
        wsIngredients.value(0, 2, "Выход (г)"); wsIngredients.value(0, 3, "Способ обработки")
        wsIngredients.value(1, 0, "Плов"); wsIngredients.value(1, 1, "Рис")
        wsIngredients.value(1, 2, 100.0); wsIngredients.value(1, 3, "Варка")
        wb.finish()
    }

    fun exportDishes(out: OutputStream, dishes: List<Dish>, dishIngredients: List<DishIngredient>, ingredients: List<Ingredient>) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val wsDishes = wb.newWorksheet("Блюда")
        wsDishes.value(0, 0, "Название"); wsDishes.value(0, 1, "Категория")
        dishes.forEachIndexed { index, dish ->
            wsDishes.value(index + 1, 0, dish.name); wsDishes.value(index + 1, 1, dish.category)
        }

        val wsIngredients = wb.newWorksheet("Состав (Техкарта)")
        wsIngredients.value(0, 0, "Название блюда"); wsIngredients.value(0, 1, "Ингредиент")
        wsIngredients.value(0, 2, "Выход (г)"); wsIngredients.value(0, 3, "Способ обработки")
        var rowIndex = 1
        dishIngredients.forEach { di ->
            val dish = dishes.find { it.id == di.dishId }
            val ing = ingredients.find { it.id == di.ingredientId }
            if (dish != null && ing != null) {
                val loss = ing.getLossForMethod(di.cookingMethod)
                val outputWeight = if (loss > 0) di.amount * ((100.0 - loss) / 100.0) else di.amount
                wsIngredients.value(rowIndex, 0, dish.name); wsIngredients.value(rowIndex, 1, ing.name)
                wsIngredients.value(rowIndex, 2, outputWeight); wsIngredients.value(rowIndex, 3, di.cookingMethod)
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
                    val name = getCellString(row.getCell(0))
                    if (name.isNotBlank()) {
                        val cat = getCellString(row.getCell(1)).ifBlank { "Прочее" }
                        val existing = currentDishes.find { it.name.equals(name, ignoreCase = true) }
                        val newDish = Dish(id = existing?.id ?: 0, name = name, category = cat, calories = 0, protein = 0, fat = 0, carbs = 0)
                        if (existing != null) toUpdateDishes.add(newDish) else toInsertDishes.add(newDish)
                    }
                } catch (_: Exception) { }
            }

            if (workbook.numberOfSheets > 1) {
                val ingSheet = workbook.getSheetAt(1)
                for (rowIndex in 1..ingSheet.lastRowNum) {
                    val row = ingSheet.getRow(rowIndex) ?: continue
                    try {
                        val dishName = getCellString(row.getCell(0))
                        val ingName = getCellString(row.getCell(1))
                        val outputWeight = getCellDouble(row.getCell(2))
                        val method = getCellString(row.getCell(3)).ifBlank { "Сырой" }
                        val ing = currentIngredients.find { it.name.equals(ingName, ignoreCase = true) }
                        if (dishName.isNotBlank() && ing != null && outputWeight > 0) {
                            val loss = ing.getLossForMethod(method)
                            val netWeight = if (loss > 0) outputWeight / ((100.0 - loss) / 100.0) else outputWeight
                            parsedIngredients.add(dishName to DishIngredient(dishId = 0, ingredientId = ing.id, amount = netWeight, cookingMethod = method))
                        }
                    } catch (_: Exception) { }
                }
            }
        } finally {
            workbook.close()
        }

        val ingredientsByDish = parsedIngredients.groupBy { it.first.lowercase() }
        fun calcBju(dishName: String): Dish {
            val dishIngList = ingredientsByDish[dishName.lowercase()] ?: emptyList()
            var kcal = 0.0; var prot = 0.0; var fat = 0.0; var carb = 0.0
            for (pair in dishIngList) {
                val di = pair.second
                val ing = currentIngredients.find { it.id == di.ingredientId } ?: continue
                val grams = if (ing.unit == "шт") di.amount * (if (ing.weightPerPiece > 0) ing.weightPerPiece else 100.0) else di.amount
                val factor = grams / 100.0
                kcal += ing.calories * factor; prot += ing.protein * factor; fat += ing.fat * factor; carb += ing.carbs * factor
            }
            return Dish(id = 0, name = dishName, category = "", calories = kcal.roundToInt(), protein = prot.roundToInt(), fat = fat.roundToInt(), carbs = carb.roundToInt())
        }

        for (dish in toUpdateDishes) {
            val withBju = calcBju(dish.name)
            val finalDish = dish.copy(calories = withBju.calories, protein = withBju.protein, fat = withBju.fat, carbs = withBju.carbs)
            db.dishDao().update(finalDish)
            dishMap[finalDish.name.lowercase()] = finalDish.id
            db.dishIngredientDao().deleteForDish(finalDish.id)
        }
        for (dish in toInsertDishes) {
            val withBju = calcBju(dish.name)
            val finalDish = dish.copy(calories = withBju.calories, protein = withBju.protein, fat = withBju.fat, carbs = withBju.carbs)
            val newId = db.dishDao().insert(finalDish)
            dishMap[finalDish.name.lowercase()] = newId
        }
        for (pair in parsedIngredients) {
            val realDishId = dishMap[pair.first.lowercase()]
            if (realDishId != null) db.dishIngredientDao().insert(pair.second.copy(dishId = realDishId))
        }
        return Pair(toInsertDishes.size, toUpdateDishes.size)
    }

    // ==========================================
    // МЕНЮ (Отдельно)
    // ==========================================
    fun createMenuTemplate(out: OutputStream) {
        val wb = Workbook(out, "PP Kitchen", "1.0")
        val ws = wb.newWorksheet("Меню")
        ws.value(0, 0, "День (число)"); ws.value(0, 1, "Прием пищи (для справки)"); ws.value(0, 2, "Название блюда")
        ws.value(1, 0, 1); ws.value(1, 1, "Завтрак"); ws.value(1, 2, "Овсянка")
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
                    val dayNum = getCellDouble(row.getCell(0)).toInt()
                    val dishName = getCellString(row.getCell(2))
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