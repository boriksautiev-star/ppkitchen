package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rations")
data class Ration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,          // Название (например, "Стандарт 1800")
    val calories: Int,         // Калорийность (для справки)
    val categories: String     // Список категорий блюд через запятую (например, "Завтрак,Обед,Ужин")
)