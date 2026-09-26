package com.ppkitchen.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ppkitchen.data.Client
import com.ppkitchen.data.Order
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Формирует файл Excel (.xlsx) с данными для этикеток
 * и открывает системный диалог «Поделиться».
 *
 * Первая строка — служебная (NIIMBOT считает её заголовком и пропускает).
 * Реальные данные начинаются со 2-й строки.
 * Колонки: Имя | Калорийность | Адрес | Телефон
 * Сортировка: по калорийности, затем по имени.
 */
fun exportLabelsToExcelAndShare(
    context: Context,
    date: String,
    orders: List<Order>,
    clients: List<Client>
) {
    if (orders.isEmpty()) return

    // Сортировка: сначала по калориям, затем по имени
    val sortedOrders = orders.sortedWith(
        compareBy(
            { it.calories },
            { clients.find { c -> c.id == it.clientId }?.fullName?.lowercase() ?: "" }
        )
    )

    // Строки данных
    val rows = sortedOrders.map { order ->
        val client = clients.find { it.id == order.clientId }
        listOf(
            client?.fullName ?: "Клиент удалён",
            "${order.calories} ккал",
            client?.address?.ifBlank { "—" } ?: "—",
            client?.phone?.ifBlank { "—" } ?: "—"
        )
    }

    // Имя файла: Этикетки_2026-09-15.xlsx
    val fileName = "Этикетки_${date}.xlsx"

    // Куда сохраняем
    val dir = File(context.cacheDir, "labels").apply { mkdirs() }
    val file = File(dir, fileName)

    // Записываем .xlsx
    writeXlsx(file, rows)

    // Отдаём через FileProvider
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Этикетки на $date")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Сохранить или отправить файл с этикетками"))
}

// ============================================================
// Генерация .xlsx (минимальный OOXML без сторонних библиотек)
// ============================================================

private fun writeXlsx(
    file: File,
    dataRows: List<List<String>>
) {
    val zip = ZipOutputStream(FileOutputStream(file))

    // 1. [Content_Types].xml
    zip.putNextEntry(ZipEntry("[Content_Types].xml"))
    zip.write(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""".trimIndent().toByteArray(Charsets.UTF_8)
    )
    zip.closeEntry()

    // 2. _rels/.rels
    zip.putNextEntry(ZipEntry("_rels/.rels"))
    zip.write(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
    )
    zip.closeEntry()

    // 3. xl/workbook.xml
    zip.putNextEntry(ZipEntry("xl/workbook.xml"))
    zip.write(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Этикетки" sheetId="1" r:id="rId1"/>
</sheets>
</workbook>""".trimIndent().toByteArray(Charsets.UTF_8)
    )
    zip.closeEntry()

    // 4. xl/_rels/workbook.xml.rels
    zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
    zip.write(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
    )
    zip.closeEntry()

    // 5. xl/worksheets/sheet1.xml
    val sheetXml = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("<sheetData>")

        // Служебная первая строка — NIIMBOT считает её заголовком и пропускает.
        // В ячейках "—", чтобы нигде не появлялись слова «Имя», «Калорийность» и т.д.
        append("""<row r="1">""")
        repeat(4) { colIdx ->
            val cellRef = "${columnLetter(colIdx)}1"
            append("""<c r="$cellRef" t="inlineStr"><is><t>—</t></is></c>""")
        }
        append("</row>")

        // Реальные данные — со 2-й строки
        dataRows.forEachIndexed { rowIdx, row ->
            val rowNum = rowIdx + 2
            append("""<row r="$rowNum">""")
            row.forEachIndexed { colIdx, cell ->
                val cellRef = "${columnLetter(colIdx)}$rowNum"
                append("""<c r="$cellRef" t="inlineStr"><is><t>${escapeXml(cell)}</t></is></c>""")
            }
            append("</row>")
        }

        append("</sheetData>")
        append("</worksheet>")
    }

    zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
    zip.write(sheetXml.toByteArray(Charsets.UTF_8))
    zip.closeEntry()

    zip.close()
}

/**
 * Преобразует индекс колонки (0, 1, 2...) в букву Excel (A, B, C...).
 */
private fun columnLetter(index: Int): String {
    var i = index
    val sb = StringBuilder()
    while (i >= 0) {
        sb.insert(0, ('A' + (i % 26)))
        i = i / 26 - 1
    }
    return sb.toString()
}

/**
 * Экранирует спецсимволы для XML.
 */
private fun escapeXml(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}