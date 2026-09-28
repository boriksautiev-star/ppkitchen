package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cooks")
data class Cook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    // Ставка за выход по дням недели. 0 = не работает в этот день.
    val rateMon: Int = 0,
    val rateTue: Int = 0,
    val rateWed: Int = 0,
    val rateThu: Int = 0,
    val rateFri: Int = 0,
    val rateSat: Int = 0,
    val rateSun: Int = 0,
    val active: Boolean = true,
    val comment: String = ""
) {
    /** Ставка за конкретный день недели (1=Пн ... 7=Вс). 0 = не работает. */
    fun rateForDay(dayOfWeekIso: Int): Int = when (dayOfWeekIso) {
        1 -> rateMon
        2 -> rateTue
        3 -> rateWed
        4 -> rateThu
        5 -> rateFri
        6 -> rateSat
        7 -> rateSun
        else -> 0
    }
}