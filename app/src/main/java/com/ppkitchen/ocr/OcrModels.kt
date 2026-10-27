package com.ppkitchen.ocr

/**
 * Одна распознанная визуальная строка с координатами.
 */
data class DetectedLine(
    val text: String,
    val top: Int,
    val left: Int,
    val height: Int,
    val centerY: Int // НОВОЕ: центр по вертикали для склеивания строк
)

/**
 * Черновик строки. Все поля — строки для удобного редактирования.
 */
data class InvoiceRowDraft(
    val id: Long = System.nanoTime(),
    val name: String = "",
    val quantity: String = "",
    val unit: String = "г",
    val price: String = "",
    // Новые поля для справочника БЖУ
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = ""
)

enum class OcrState {
    Camera,
    Processing,
    Draft,
    Error
}

enum class OcrMode {
    INVOICE,   // Для накладных (Название, Кол-во, Цена)
    REFERENCE  // Для справочника (Название, Ккал, Б, Ж, У)
}