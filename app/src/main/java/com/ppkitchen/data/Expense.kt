package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String = "",   // "HH:mm", пусто = нет данных
    val category: String,
    val amount: Int,
    val comment: String = ""
)