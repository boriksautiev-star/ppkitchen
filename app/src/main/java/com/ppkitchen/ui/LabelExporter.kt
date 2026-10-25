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

fun exportLabelsToExcelAndShare(
    context: Context,
    date: String,
    orders: List<Order>,
    clients: List<Client>
) {
    if (orders.isEmpty()) return

    val sortedOrders = orders.sortedWith(
        compareBy(
            { it.calories },
            { clients.find { c -> c.id == it.clientId }?.fullName?.lowercase() ?: "" }
        )
    )

    val rows = sortedOrders.map { order ->
        val client = clients.find { it.id == order.clientId }
        val bju = if (order.protein > 0 || order.fat > 0 || order.carbs > 0) {
            "Б${order.protein} Ж${order.fat} У${order.carbs}"
        } else ""

        listOf(
            client?.fullName ?: "Клиент удалён",
            "${order.calories} ккал",
            bju,
            client?.address?.ifBlank { "—" } ?: "—",
            client?.phone?.ifBlank { "—" } ?: "—"
        )
    }

    val fileName = "Этикетки_${date}.xlsx"
    val dir = File(context.cacheDir, "labels").apply { mkdirs() }
    val file = File(dir, fileName)

    writeXlsx(file, rows)

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

private fun writeXlsx(
    file: File,
    dataRows: List<List<String>>
) {
    val zip = ZipOutputStream(FileOutputStream(file))

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

    zip.putNextEntry(ZipEntry("_rels/.rels"))
    zip.write(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
    )
    zip.closeEntry()

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

    zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
    zip.write(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
    )
    zip.closeEntry()

    val sheetXml = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("<sheetData>")

        append("""<row r="1">""")
        repeat(5) { colIdx ->
            val cellRef = "${columnLetter(colIdx)}1"
            append("""<c r="$cellRef" t="inlineStr"><is><t>—</t></is></c>""")
        }
        append("</row>")

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

private fun columnLetter(index: Int): String {
    var i = index
    val sb = StringBuilder()
    while (i >= 0) {
        sb.insert(0, ('A' + (i % 26)))
        i = i / 26 - 1
    }
    return sb.toString()
}

private fun escapeXml(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}