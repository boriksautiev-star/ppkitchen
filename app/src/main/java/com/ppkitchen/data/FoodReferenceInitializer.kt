package com.ppkitchen.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object FoodReferenceInitializer {

    suspend fun initializeIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val dao = db.foodReferenceDao()

        if (dao.count() > 100) return@withContext

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

    fun getSuggestedWaste(name: String): String {
        val n = name.lowercase().trim()
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

    // ВОЗВРАЩАЕМ ФУНКЦИЮ, КОТОРУЮ ИЩЕТ INGREDIENTEDITSCREEN
    fun getSuggestedHeatLoss(name: String): String {
        val n = name.lowercase().trim()
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

    // НОВАЯ ФУНКЦИЯ ДЛЯ РАСЧЕТА ПОТЕРЬ В ЗАВИСИМОСТИ ОТ СПОСОБА
    fun getHeatLossForMethod(ingName: String, method: String): Double {
        if (method == "Сырой") return 0.0
        val n = ingName.lowercase().trim()

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