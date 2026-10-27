package com.ppkitchen.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

object FoodReferenceInitializer {

    suspend fun initializeIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val dao = db.foodReferenceDao()

        if (dao.count() < 100) {
            try {
                val jsonString = context.assets.open("food_reference.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)

                val items = mutableListOf<FoodReference>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    items.add(
                        FoodReference(
                            name = name,
                            calories = obj.getDouble("calories"),
                            protein = obj.getDouble("protein"),
                            fat = obj.getDouble("fat"),
                            carbs = obj.getDouble("carbs"),
                            source = "builtin",
                            wastePercent = getSuggestedWaste(name).toDoubleOrNull() ?: 0.0
                        )
                    )
                }
                dao.insertAll(items)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        syncReferenceToIngredients(db)
    }

    suspend fun syncReferenceToIngredients(db: AppDatabase) {
        val references = db.foodReferenceDao().getAllList()
        val existingIngredients = db.ingredientDao().getAll().first()

        val existingNames = existingIngredients.map { it.name.lowercase(Locale.getDefault()) }.toSet()

        references.forEach { ref ->
            if (!existingNames.contains(ref.name.lowercase(Locale.getDefault()))) {
                val unit = if (ref.name.contains("яйцо", ignoreCase = true)) "шт" else "г"

                val category = when {
                    ref.name.contains("курица", true) || ref.name.contains("говядина", true) || ref.name.contains("свинина", true) || ref.name.contains("индейка", true) || ref.name.contains("телятина", true) || ref.name.contains("баранина", true) -> "Мясо"
                    ref.name.contains("рыба", true) || ref.name.contains("лосось", true) || ref.name.contains("треска", true) || ref.name.contains("минтай", true) || ref.name.contains("хек", true) || ref.name.contains("скумбрия", true) || ref.name.contains("сельдь", true) || ref.name.contains("тунец", true) || ref.name.contains("дорадо", true) || ref.name.contains("сибас", true) || ref.name.contains("форель", true) -> "Рыба"
                    ref.name.contains("молоко", true) || ref.name.contains("сыр", true) || ref.name.contains("творог", true) || ref.name.contains("йогурт", true) || ref.name.contains("кефир", true) || ref.name.contains("сметана", true) || ref.name.contains("масло сливочное", true) -> "Молочка"
                    ref.name.contains("картоф", true) || ref.name.contains("морков", true) || ref.name.contains("лук", true) || ref.name.contains("капуст", true) || ref.name.contains("помидор", true) || ref.name.contains("огурц", true) || ref.name.contains("перец", true) || ref.name.contains("баклажан", true) || ref.name.contains("кабачк", true) -> "Овощи"
                    ref.name.contains("яблок", true) || ref.name.contains("банан", true) || ref.name.contains("апельсин", true) || ref.name.contains("лимон", true) || ref.name.contains("груш", true) || ref.name.contains("виноград", true) || ref.name.contains("клубник", true) -> "Фрукты"
                    ref.name.contains("рис", true) || ref.name.contains("гречк", true) || ref.name.contains("макарон", true) || ref.name.contains("пшено", true) || ref.name.contains("овсян", true) || ref.name.contains("мука", true) -> "Крупы"
                    else -> "Прочее"
                }

                val loss = getSuggestedHeatLoss(ref.name).toDoubleOrNull() ?: 0.0

                val newIng = Ingredient(
                    name = ref.name,
                    pricePerKg = 0.0,
                    unit = unit,
                    category = category,
                    calories = ref.calories,
                    protein = ref.protein,
                    fat = ref.fat,
                    carbs = ref.carbs,
                    wastePercent = ref.wastePercent,
                    lossBoiling = loss,
                    lossFrying = loss,
                    lossBaking = loss,
                    lossStewing = loss
                )
                db.ingredientDao().insert(newIng)
            }
        }
    }

    fun getSuggestedWaste(name: String): String {
        val n = name.lowercase(Locale.getDefault()).trim()
        val waste = when {
            n.contains("картоф") -> 25.0
            n.contains("морков") -> 20.0
            n.contains("свекл") -> 20.0
            n.contains("капуст") -> 20.0
            n.contains("лук реп") || n == "лук" -> 16.0
            n.contains("лук зел") || n.contains("лук порей") -> 20.0
            n.contains("огурц") -> 10.0
            n.contains("помидор") || n.contains("томат") -> 5.0
            n.contains("баклажан") -> 10.0
            n.contains("кабачк") || n.contains("патиссон") -> 10.0
            n.contains("перец болгар") -> 25.0
            n.contains("тыква") -> 10.0
            n.contains("редис") || n.contains("редька") -> 20.0
            n.contains("репа") -> 10.0
            n.contains("чеснок") -> 22.0
            n.contains("зелень") || n.contains("укроп") || n.contains("петрушк") || n.contains("кинза") -> 20.0
            n.contains("гриб") || n.contains("шампиньон") || n.contains("вешенк") -> 10.0
            n.contains("яблок") -> 12.0
            n.contains("груш") -> 10.0
            n.contains("банан") -> 35.0
            n.contains("апельсин") || n.contains("мандарин") || n.contains("грейпфрут") || n.contains("помело") -> 25.0
            n.contains("виноград") -> 10.0
            n.contains("арбуз") || n.contains("дыня") -> 35.0
            n.contains("вишня") || n.contains("черешня") -> 15.0
            n.contains("слива") || n.contains("абрикос") || n.contains("персик") || n.contains("нектарин") -> 10.0
            n.contains("ананас") -> 30.0
            n.contains("авокадо") -> 30.0
            n.contains("киви") -> 20.0
            n.contains("клубник") || n.contains("малин") || n.contains("ежевик") || n.contains("смородин") -> 5.0
            n.contains("курица") || n.contains("куриц") -> {
                if (n.contains("грудк") || n.contains("филе")) 5.0
                else if (n.contains("фарш") || n.contains("печень") || n.contains("сердце")) 0.0
                else 20.0
            }
            n.contains("индейка") || n.contains("индюш") -> {
                if (n.contains("грудк") || n.contains("филе")) 5.0
                else if (n.contains("фарш") || n.contains("печень") || n.contains("сердце")) 0.0
                else 15.0
            }
            n.contains("говядина") || n.contains("телятина") -> {
                if (n.contains("фарш") || n.contains("вырезк") || n.contains("копчен") || n.contains("отварн") || n.contains("печень")) 0.0
                else 15.0
            }
            n.contains("свинина") -> {
                if (n.contains("фарш") || n.contains("копчен") || n.contains("отварн")) 0.0
                else 15.0
            }
            n.contains("баранина") -> 15.0
            n.contains("рыба") || n.contains("лосось") || n.contains("семга") || n.contains("треска") || n.contains("минтай") || n.contains("хек") || n.contains("скумбрия") || n.contains("сельдь") || n.contains("тунец") || n.contains("дорадо") || n.contains("сибас") -> 20.0
            n.contains("форель") || n.contains("горбуша") || n.contains("кета") || n.contains("камбала") || n.contains("судак") -> 20.0
            n.contains("филе рыбы") || n.contains("филе лосося") -> 5.0
            n.contains("яйцо") -> 12.0
            else -> 0.0
        }
        return if (waste > 0) waste.toString() else ""
    }

    fun getSuggestedHeatLoss(name: String): String {
        val n = name.lowercase(Locale.getDefault()).trim()
        val loss = when {
            n.contains("курица") || n.contains("куриц") || n.contains("индейка") || n.contains("индюш") -> {
                if (n.contains("фарш")) 25.0 else 35.0
            }
            n.contains("говядина") || n.contains("свинина") || n.contains("баранина") || n.contains("телятина") -> 40.0
            n.contains("кролик") -> 35.0
            n.contains("рыба") || n.contains("лосось") || n.contains("семга") || n.contains("треска") || n.contains("минтай") || n.contains("хек") || n.contains("скумбрия") || n.contains("сельдь") || n.contains("тунец") || n.contains("дорадо") || n.contains("сибас") || n.contains("форель") || n.contains("горбуша") || n.contains("кета") -> 20.0
            n.contains("картоф") || n.contains("морков") || n.contains("свекл") || n.contains("кабачк") || n.contains("баклажан") || n.contains("лук") || n.contains("перец") -> 15.0
            n.contains("рис") || n.contains("гречк") || n.contains("макарон") || n.contains("пшено") || n.contains("овсян") -> 0.0
            n.contains("яйцо") -> 0.0
            else -> 0.0
        }
        return if (loss > 0) loss.toString() else ""
    }

    fun getHeatLossForMethod(ingName: String, method: String): Double {
        if (method == "Сырой") return 0.0
        val n = ingName.lowercase(Locale.getDefault()).trim()

        val isMeat = n.contains("курица") || n.contains("говядина") || n.contains("свинина") || n.contains("индейка") || n.contains("телятина") || n.contains("баранина")
        val isFish = n.contains("рыба") || n.contains("лосось") || n.contains("треска") || n.contains("минтай") || n.contains("хек") || n.contains("скумбрия")
        val isVeggie = n.contains("картоф") || n.contains("морков") || n.contains("свекл") || n.contains("кабачк") || n.contains("баклажан") || n.contains("лук") || n.contains("перец")
        val isGrain = n.contains("рис") || n.contains("гречк") || n.contains("макарон") || n.contains("пшено") || n.contains("овсян")

        return when (method) {
            "Варка" -> when {
                isMeat -> 25.0
                isFish -> 15.0
                isVeggie -> 8.0
                isGrain -> 0.0
                else -> 5.0
            }
            "Жарка" -> when {
                isMeat -> 35.0
                isFish -> 20.0
                isVeggie -> 15.0
                isGrain -> 0.0
                else -> 10.0
            }
            "Запекание" -> when {
                isMeat -> 40.0
                isFish -> 20.0
                isVeggie -> 15.0
                isGrain -> 0.0
                else -> 10.0
            }
            "Тушение" -> when {
                isMeat -> 30.0
                isFish -> 15.0
                isVeggie -> 10.0
                isGrain -> 0.0
                else -> 5.0
            }
            else -> 0.0
        }
    }
}