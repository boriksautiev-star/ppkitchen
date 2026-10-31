package com.ppkitchen.ocr

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Режимы перетаскивания
enum class DragMode {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    bitmap: Bitmap,
    onCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Начальная рамка (по центру, 80% от размера)
    var cropRect by remember(containerSize) {
        mutableStateOf(
            Rect(
                containerSize.width * 0.1f,
                containerSize.height * 0.1f,
                containerSize.width * 0.9f,
                containerSize.height * 0.9f
            )
        )
    }

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    val touchRadius = 40.dp.value * 2 // Радиус захвата для пальца (в пикселях)
    val minSize = 100.dp.value * 2    // Минимальный размер рамки

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обрезка фото") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Отмена")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (containerSize.width == 0) return@IconButton

                        // Пересчитываем координаты экрана в координаты картинки
                        val scaleX = bitmap.width.toFloat() / containerSize.width
                        val scaleY = bitmap.height.toFloat() / containerSize.height

                        val left = (cropRect.left * scaleX).roundToInt().coerceIn(0, bitmap.width - 1)
                        val top = (cropRect.top * scaleY).roundToInt().coerceIn(0, bitmap.height - 1)
                        val right = (cropRect.right * scaleX).roundToInt().coerceIn(left + 1, bitmap.width)
                        val bottom = (cropRect.bottom * scaleY).roundToInt().coerceIn(top + 1, bitmap.height)

                        val w = right - left
                        val h = bottom - top

                        if (w > 10 && h > 10) {
                            onCrop(Bitmap.createBitmap(bitmap, left, top, w, h))
                        } else {
                            onCrop(bitmap) // Если рамка слишком маленькая, берем всё фото
                        }
                    }) {
                        Icon(Icons.Default.Check, "Готово")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
                .pointerInput(containerSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Определяем, за что схватился пользователь
                            val r = touchRadius
                            dragMode = when {
                                offset.isNear(cropRect.topLeft, r) -> DragMode.TOP_LEFT
                                offset.isNear(cropRect.topRight, r) -> DragMode.TOP_RIGHT
                                offset.isNear(cropRect.bottomLeft, r) -> DragMode.BOTTOM_LEFT
                                offset.isNear(cropRect.bottomRight, r) -> DragMode.BOTTOM_RIGHT
                                cropRect.contains(offset) -> DragMode.MOVE
                                else -> DragMode.NONE
                            }
                        },
                        onDragEnd = { dragMode = DragMode.NONE },
                        onDragCancel = { dragMode = DragMode.NONE },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            var newLeft = cropRect.left
                            var newTop = cropRect.top
                            var newRight = cropRect.right
                            var newBottom = cropRect.bottom

                            when (dragMode) {
                                DragMode.TOP_LEFT -> {
                                    newLeft = (newLeft + dragAmount.x).coerceIn(0f, newRight - minSize)
                                    newTop = (newTop + dragAmount.y).coerceIn(0f, newBottom - minSize)
                                }
                                DragMode.TOP_RIGHT -> {
                                    newRight = (newRight + dragAmount.x).coerceIn(newLeft + minSize, containerSize.width.toFloat())
                                    newTop = (newTop + dragAmount.y).coerceIn(0f, newBottom - minSize)
                                }
                                DragMode.BOTTOM_LEFT -> {
                                    newLeft = (newLeft + dragAmount.x).coerceIn(0f, newRight - minSize)
                                    newBottom = (newBottom + dragAmount.y).coerceIn(newTop + minSize, containerSize.height.toFloat())
                                }
                                DragMode.BOTTOM_RIGHT -> {
                                    newRight = (newRight + dragAmount.x).coerceIn(newLeft + minSize, containerSize.width.toFloat())
                                    newBottom = (newBottom + dragAmount.y).coerceIn(newTop + minSize, containerSize.height.toFloat())
                                }
                                DragMode.MOVE -> {
                                    val dx = dragAmount.x.coerceIn(-newLeft, containerSize.width - newRight)
                                    val dy = dragAmount.y.coerceIn(-newTop, containerSize.height - newBottom)
                                    newLeft += dx; newRight += dx
                                    newTop += dy; newBottom += dy
                                }
                                DragMode.NONE -> {}
                            }
                            cropRect = Rect(newLeft, newTop, newRight, newBottom)
                        }
                    )
                }
        ) {
            if (containerSize.width > 0) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val imageBitmap = bitmap.asImageBitmap()

                    // 1. Рисуем картинку на весь экран
                    drawImage(
                        image = imageBitmap,
                        dstSize = IntSize(containerSize.width, containerSize.height)
                    )

                    // 2. Затемняем область ВНЕ рамки (рисуем 4 прямоугольника)
                    val darkColor = Color.Black.copy(alpha = 0.6f)
                    // Верх
                    drawRect(darkColor, topLeft = Offset(0f, 0f), size = Size(size.width, cropRect.top))
                    // Низ
                    drawRect(darkColor, topLeft = Offset(0f, cropRect.bottom), size = Size(size.width, size.height - cropRect.bottom))
                    // Лево
                    drawRect(darkColor, topLeft = Offset(0f, cropRect.top), size = Size(cropRect.left, cropRect.height))
                    // Право
                    drawRect(darkColor, topLeft = Offset(cropRect.right, cropRect.top), size = Size(size.width - cropRect.right, cropRect.height))

                    // 3. Рисуем рамку
                    drawRect(
                        color = Color.White,
                        topLeft = cropRect.topLeft,
                        size = cropRect.size,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // 4. Рисуем кружочки на углах (маркеры для перетаскивания)
                    val handleRadius = 10.dp.toPx()
                    val handles = listOf(
                        cropRect.topLeft, cropRect.topRight,
                        cropRect.bottomLeft, cropRect.bottomRight
                    )
                    handles.forEach { point ->
                        // Белый кружок
                        drawCircle(Color.White, radius = handleRadius, center = point)
                        // Внутри черный (чтобы был как прицел)
                        drawCircle(Color.Black, radius = handleRadius * 0.5f, center = point)
                    }
                }
            }
        }
    }
}

// Вспомогательная функция: проверяет, находится ли точка рядом с углом
private fun Offset.isNear(target: Offset, radius: Float): Boolean {
    val dx = this.x - target.x
    val dy = this.y - target.y
    return (dx * dx + dy * dy) <= (radius * radius)
}

// Вспомогательная функция: проверяет, находится ли точка внутри прямоугольника
private fun Rect.contains(offset: Offset): Boolean {
    return offset.x >= left && offset.x <= right && offset.y >= top && offset.y <= bottom
}