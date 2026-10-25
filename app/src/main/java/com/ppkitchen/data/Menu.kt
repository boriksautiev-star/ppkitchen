package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_templates")
data class MenuTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val cycleLength: Int = 0
)

@Entity(tableName = "menu_days")
data class MenuDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long = 0,
    val dayNumber: Int = 0
)

@Entity(tableName = "menu_day_dishes", primaryKeys = ["menuDayId", "dishId"])
data class MenuDayDish(
    val menuDayId: Long = 0,
    val dishId: Long = 0
)