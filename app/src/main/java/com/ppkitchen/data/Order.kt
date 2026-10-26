package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val clientId: Long,
    val dateStart: String,
    val dateEnd: String,
    val deliveryStart: String,
    val deliveryEnd: String,
    val calories: Int = 1200,
    val protein: Int = 0,
    val fat: Int = 0,
    val carbs: Int = 0,
    val amount: Int = 0,
    val amountPerDay: Int = 0,
    val dailyPayment: Boolean = false,
    val discountPercent: Int = 0,
    val discountFromDate: String = "",
    val discountHistory: String = "",
    val comment: String = "",
    val deliveryDaysOfWeek: String = "1,2,3,4,5,6,7",
    val isOpenEnded: Boolean = false,
    val courierId: Long = 0L,
    val paid: Boolean = false,
    val paidUntil: String = "",
    val delivered: Boolean = false,
    val skippedDates: String = "",
    val menuTemplateId: Long = 0,
    val rationId: Long = 0
)

/** Разбирает skippedDates в Set. Пример: "2026-10-15|2026-10-18" → Set(2026-10-15, 2026-10-18) */
fun Order.skippedSet(): Set<String> {
    if (skippedDates.isBlank()) return emptySet()
    return skippedDates.split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
}

/** Проверяет, пропущена ли эта дата в заказе */
fun Order.isSkipped(day: String): Boolean = day in skippedSet()

/** Сериализует Set в строку для skippedDates */
fun skippedDatesToString(dates: Set<String>): String {
    return dates.filter { it.isNotBlank() }
        .sorted()
        .joinToString("|")
}