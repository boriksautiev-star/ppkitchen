package com.ppkitchen.ocr

data class DetectedLine(
    val text: String,
    val top: Int,
    val left: Int,
    val height: Int,
    val centerY: Int,
    val centerX: Int
)

data class InvoiceRowDraft(
    val id: Long = System.nanoTime(),
    val name: String = "",
    val quantity: String = "",
    val unit: String = "г",
    val price: String = "",
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
    INVOICE,
    REFERENCE
}