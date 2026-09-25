package com.ppkitchen.data

import androidx.room.Entity

@Entity(
    tableName = "order_courier_assignments",
    primaryKeys = ["orderId", "date"]
)
data class OrderCourierAssignment(
    val orderId: Long,
    val date: String,     // YYYY-MM-DD — день доставки
    val courierId: Long
)