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
    val delivered: Boolean = false
)