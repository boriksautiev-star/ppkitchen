package com.ppkitchen.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.ppkitchen.data.Client
import com.ppkitchen.data.Order
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun printLabels(
    context: Context,
    date: String,
    orders: List<Order>,
    clients: List<Client>
) {
    if (orders.isEmpty()) return

    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "Этикетки PP Kitchen — $date"
    val adapter = LabelPrintAdapter(date, orders, clients)

    // 58×40 мм → в mils (1 mil = 1/1000 дюйма)
    // 58 мм = 2.283" = 2283 mils; 40 мм = 1.575" = 1575 mils
    val labelSize = PrintAttributes.MediaSize(
        "label_58x40",
        "Этикетка 58×40 мм",
        2283,
        1575
    )

    val attrs = PrintAttributes.Builder()
        .setMediaSize(labelSize)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
        .build()

    printManager.print(jobName, adapter, attrs)
}

private class LabelPrintAdapter(
    private val date: String,
    private val orders: List<Order>,
    private val clients: List<Client>
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder("PPKitchen_labels_$date.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(orders.size)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        val document = PdfDocument()

        orders.forEachIndexed { index, order ->
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                document.close()
                return
            }

            val client = clients.find { it.id == order.clientId }
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, index + 1
            ).create()

            val page = document.startPage(pageInfo)
            drawLabel(page.canvas, order, client, date)
            document.finishPage(page)
        }

        try {
            FileOutputStream(destination.fileDescriptor).use { out ->
                document.writeTo(out)
            }
            document.close()
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            document.close()
            callback.onWriteFailed(e.message)
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        order: Order,
        client: Client?,
        date: String
    ) {
        canvas.drawColor(Color.WHITE)

        val margin = 20f
        val contentWidth = PAGE_WIDTH - margin * 2
        var y = margin + 28f

        // Заголовок
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("PP Kitchen", margin, y, titlePaint)
        y += 20f

        // Разделитель
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1.5f
        }
        canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
        y += 40f

        // Имя клиента
        val namePaint = Paint().apply {
            color = Color.BLACK
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val name = client?.fullName ?: "Клиент удалён"
        canvas.drawText(name, margin, y, namePaint)
        y += 48f

        // Калории + дата
        val infoPaint = Paint().apply {
            color = Color.BLACK
            textSize = 26f
            isAntiAlias = true
        }
        canvas.drawText("${order.calories} ккал", margin, y, infoPaint)
        val dateFormatted = formatDate(date)
        val dateWidth = infoPaint.measureText(dateFormatted)
        canvas.drawText(dateFormatted, margin + contentWidth - dateWidth, y, infoPaint)
        y += 38f

        // Комментарий (если есть)
        if (order.comment.isNotBlank()) {
            val commentPaint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
                isAntiAlias = true
            }
            val lines = breakText(order.comment, commentPaint, contentWidth, 2)
            lines.forEach { line ->
                canvas.drawText(line, margin, y, commentPaint)
                y += 24f
            }
        }

        // Подпись внизу
        val footerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText(
            "Правильное питание",
            margin,
            PAGE_HEIGHT - margin,
            footerPaint
        )
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val inSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outSdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            inSdf.parse(isoDate)?.let { outSdf.format(it) } ?: isoDate
        } catch (e: Exception) {
            isoDate
        }
    }

    private fun breakText(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int
    ): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) {
                current = test
            } else {
                if (current.isNotEmpty()) lines.add(current)
                if (lines.size >= maxLines) return lines
                current = word
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current)
        return lines
    }

    private companion object {
        // 58×40 мм при 300 DPI
        const val PAGE_WIDTH = 685
        const val PAGE_HEIGHT = 472
    }
}