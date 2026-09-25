package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cash_deposits")
data class CashDeposit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val amount: Int,
    val comment: String = ""
)