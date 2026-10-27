package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredient_categories")
data class IngredientCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "ingredients")
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pricePerKg: Double,
    val unit: String = "г",
    val category: String = "Прочее",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val wastePercent: Double = 0.0,
    val lossBoiling: Double = 0.0,   // Варка
    val lossFrying: Double = 0.0,    // Жарка
    val lossBaking: Double = 0.0,    // Запекание
    val lossStewing: Double = 0.0    // Тушение
) {
    fun getLossForMethod(method: String): Double = when (method) {
        "Варка" -> lossBoiling
        "Жарка" -> lossFrying
        "Запекание" -> lossBaking
        "Тушение" -> lossStewing
        else -> 0.0
    }
}

@Entity(tableName = "dish_categories")
data class DishCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "dishes")
data class Dish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Int = 0,
    val protein: Int = 0,
    val fat: Int = 0,
    val carbs: Int = 0,
    val category: String = "Прочее"
)

@Entity(
    tableName = "dish_ingredients",
    primaryKeys = ["dishId", "ingredientId"]
)
data class DishIngredient(
    val dishId: Long,
    val ingredientId: Long,
    val amount: Double,
    val cookingMethod: String = "Сырой"
)