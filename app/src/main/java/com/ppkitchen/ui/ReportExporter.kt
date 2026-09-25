package com.ppkitchen.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ppkitchen.data.Client
import com.ppkitchen.data.Expense
import com.ppkitchen.data.Order
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Формирует CSV-файл отчёта и открывает системный диалог "Поделиться"
fun exportReportAndShare(
    context: Context,
    startDate: String,
    endDate: String,
    revenue: Int,
    expensesTotal: Int,
    profit: Int,
    margin: Int,
    uniqueClients: Int,
    totalRations: Int,
    caloriesBreakdown: List<Pair<Int, Int>>,
    orders: List<Order>,
    clients: List<Client>,
    expenses: List<Expense>
) {
    val csv = buildReportCsv(
        startDate, endDate, revenue, expensesTotal, profit, margin,
        uniqueClients, totalRations, caloriesBreakdown,
        orders, clients, expenses
    )

    // Имя файла: Отчёт_2026-09-01_2026-09-12.csv
    val fileName = "Отчёт_${startDate}_${endDate}.csv"

    // Папка cache/exports/
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    // UTF-8 BOM — чтобы Excel открыл русский текст правильно
    file.writeText("\uFEFF$csv", Charsets.UTF_8)

    // Получаем content:// URI через FileProvider
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Отчёт за $startDate — $endDate")
        putExtra(Intent.EXTRA_TEXT, "Отчёт PP Kitchen за период $startDate — $endDate")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
}

private fun buildReportCsv(
    startDate: String,
    endDate: String,
    revenue: Int,
    expensesTotal: Int,
    profit: Int,
    margin: Int,
    uniqueClients: Int,
    totalRations: Int,
    caloriesBreakdown: List<Pair<Int, Int>>,
    orders: List<Order>,
    clients: List<Client>,
    expenses: List<Expense>
): String {
    val sb = StringBuilder()
    val sep = ";"

    fun row(vararg cells: Any?) {
        sb.append(cells.joinToString(sep) { escapeCsv(it?.toString() ?: "") })
        sb.append("\n")
    }

    // --- Титул ---
    row("ОТЧЁТ PP KITCHEN")
    row("Период:", startDate, "—", endDate)
    row("Сформирован:", SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()))
    row()

    // --- Финансы ---
    row("ФИНАНСЫ")
    row("Показатель", "Значение, ₽")
    row("Выручка", revenue)
    row("Расходы", expensesTotal)
    row("Прибыль", profit)
    row("Маржинальность, %", margin)
    row()

    // --- Питание ---
    row("ПИТАНИЕ")
    row("Показатель", "Значение")
    row("Клиентов питалось", uniqueClients)
    row("Продано рационов (всего)", totalRations)
    row()
    row("Разбивка по тарифам")
    row("Калорийность, ккал", "Количество, шт.")
    caloriesBreakdown.forEach { (kcal, count) ->
        row(kcal, count)
    }
    row()

    // --- Заказы ---
    row("ЗАКАЗЫ ЗА ПЕРИОД")
    row("Дата оформления", "Клиент", "Калорийность", "Рацион с", "Рацион по", "Доставка с", "Доставка по", "Сумма, ₽", "Оплата")
    orders.forEach { o ->
        val client = clients.find { it.id == o.clientId }
        val name = client?.fullName ?: "Клиент удалён"
        val payment = when {
            o.paid -> "Оплачено"
            o.paidUntil.isNotBlank() -> "В долг до ${o.paidUntil}"
            else -> "Не оплачено"
        }
        row(o.date, name, o.calories, o.dateStart, o.dateEnd, o.deliveryStart, o.deliveryEnd, o.amount, payment)
    }
    row()

    // --- Расходы ---
    row("РАСХОДЫ ЗА ПЕРИОД")
    row("Дата", "Категория", "Сумма, ₽", "Комментарий")
    expenses.forEach { e ->
        row(e.date, e.category, e.amount, e.comment)
    }
    row()
    row("ИТОГО РАСХОДОВ", expensesTotal)

    return sb.toString()
}

// Экранирование для CSV: если в значении есть ; " \n — оборачиваем в кавычки
private fun escapeCsv(value: String): String {
    return if (value.contains(';') || value.contains('"') || value.contains('\n')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
}