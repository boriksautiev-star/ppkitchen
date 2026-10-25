package com.ppkitchen.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object FoodReferenceInitializer {

    /**
     * Читает food_reference.json из assets и наполняет базу.
     * Вызывать при старте приложения — если таблица пустая, справочник будет загружен.
     */
    suspend fun initializeIfNeeded(ctx: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            if (db.foodReferenceDao().count() > 0) return@withContext

            val json = ctx.assets.open("food_reference.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val arr = JSONArray(json)
            val items = ArrayList<FoodReference>(arr.length())

            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    FoodReference(
                        name = o.getString("name"),
                        calories = o.optDouble("calories", 0.0),
                        protein = o.optDouble("protein", 0.0),
                        fat = o.optDouble("fat", 0.0),
                        carbs = o.optDouble("carbs", 0.0),
                        source = "builtin"
                    )
                )
            }

            db.foodReferenceDao().insertAll(items)
        } catch (e: Exception) {
            // Файл не найден или битый — ничего страшного, справочник останется пустым,
            // пользователь сможет ввести продукты вручную.
            e.printStackTrace()
        }
    }
}