package com.ppkitchen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String = "",
    val phone: String = "",
    val address: String = "",
    val comment: String = "",
    val restrictions: String = "",
    val status: String = "активен",
    val preferredTemplateId: Long = 0, // НОВОЕ: Предпочитаемый шаблон меню
    val preferredRationId: Long = 0    // НОВОЕ: Предпочитаемый рацион
) {
    val fullName: String get() =
        listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}