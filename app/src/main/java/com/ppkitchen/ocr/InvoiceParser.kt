package com.ppkitchen.ocr

import java.util.Locale
import kotlin.math.abs

object InvoiceParser {

    private val UNIT_REGEX = Regex("^(шт|кг|г|л|мл|уп|упак|пач)\\.?$", RegexOption.IGNORE_CASE)
    private val NUMBER_REGEX = Regex("\\d+[.,]?\\d*")
    private val HEADER_WORDS = listOf(
        "наимен", "товар", "кол-во", "количест", "ед.", "ед.изм",
        "цена", "стоим", "сумма", "итого", "всего"
    )

    fun parseInvoice(lines: List<DetectedLine>): List<InvoiceRowDraft> {
        val rows = groupIntoRows(lines)
        return rows.mapNotNull { rowToDraft(it) }
    }

    fun parseReference(lines: List<DetectedLine>): List<InvoiceRowDraft> {
        val rows = groupIntoRows(lines)
        return rows.mapNotNull { row ->
            val texts = row.map { it.text.trim() }.filter { it.isNotEmpty() }
            if (texts.isEmpty()) return@mapNotNull null

            // Ищем ячейку с названием (где больше всего букв)
            val nameCell = texts.maxByOrNull { it.count { c -> c.isLetter() } } ?: return@mapNotNull null
            if (nameCell.count { it.isLetter() } < 2) return@mapNotNull null // Мусорная строка

            // Берем числа ТОЛЬКО из других ячеек, игнорируя название (чтобы не захватить "3,2%" из имени)
            val otherCells = texts.filter { it != nameCell }
            val allNumbers = otherCells.flatMap { cell ->
                NUMBER_REGEX.findAll(cell).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            }.toMutableList()

            if (allNumbers.isEmpty()) return@mapNotNull null // Нет чисел — это не БЖУ

            var cal = ""
            var prot = ""
            var fat = ""
            var carb = ""

            // Если в строке 3 или 4 числа, они идут строго по порядку колонок: Ккал, Б, Ж, У
            if (allNumbers.size >= 3) {
                cal = formatNumber(allNumbers[0])
                prot = formatNumber(allNumbers[1])
                fat = formatNumber(allNumbers[2])
                if (allNumbers.size >= 4) {
                    carb = formatNumber(allNumbers[3])
                }
            } else if (allNumbers.size == 2) {
                // Если 2 числа, скорее всего это Ккал и что-то еще. Самое большое — калории.
                val maxNum = allNumbers.maxOrNull() ?: 0.0
                cal = formatNumber(maxNum)
                allNumbers.remove(maxNum)
                prot = formatNumber(allNumbers[0])
            }

            // Очищаем название от случайных чисел
            var cleanName = nameCell.replace(NUMBER_REGEX, "")
                .replace(Regex("(?i)ккал|кал|белки|жиры|углеводы|\\bг\\b|\\bккал\\b"), "")
                .replace(Regex("\\s+"), " ")
                .trim(',', ';', '-', '—', ':').trim()

            if (cleanName.count { it.isLetter() } < 2) return@mapNotNull null

            InvoiceRowDraft(
                name = cleanName,
                calories = cal,
                protein = prot,
                fat = fat,
                carbs = carb
            )
        }
    }

    private fun groupIntoRows(lines: List<DetectedLine>): List<List<DetectedLine>> {
        val nonEmpty = lines.filter { it.text.isNotBlank() }
        if (nonEmpty.isEmpty()) return emptyList()

        val avgHeight = nonEmpty.map { it.height }.average().toInt().coerceAtLeast(1)
        val tolerance = (avgHeight * 1.2).toInt().coerceAtLeast(10)

        val sorted = nonEmpty.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<DetectedLine>>()
        val rowCenters = mutableListOf<Int>()

        for (line in sorted) {
            val idx = rowCenters.indexOfFirst { abs(it - line.centerY) <= tolerance }
            if (idx >= 0) {
                rows[idx].add(line)
                rowCenters[idx] = rows[idx].map { it.centerY }.average().toInt()
            } else {
                rows.add(mutableListOf(line))
                rowCenters.add(line.centerY)
            }
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    private fun rowToDraft(row: List<DetectedLine>): InvoiceRowDraft? {
        val texts = row.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (texts.isEmpty()) return null

        if (texts.size == 1) {
            return parseMergedLine(texts.first())
        }

        val unitCell = texts.firstOrNull { UNIT_REGEX.matches(it) }
        val withoutUnit = if (unitCell != null) texts.filter { it != unitCell } else texts

        val nameCell = withoutUnit.maxByOrNull { cell -> cell.count { it.isLetter() } }
        val nameLetters = nameCell?.count { it.isLetter() } ?: 0
        if (nameCell == null || nameLetters < 2) {
            return parseMergedLine(texts.joinToString(" "))
        }

        val lowerName = nameCell.lowercase(Locale.getDefault())
        val isHeader = HEADER_WORDS.any { lowerName.contains(it) } &&
                withoutUnit.none { NUMBER_REGEX.matches(it) }
        if (isHeader) return null
        if (lowerName == "итого" || lowerName == "всего") return null

        val numberTexts = withoutUnit.filter { it != nameCell }
        val numbers = numberTexts.mapNotNull { parseNumber(it) }

        val quantity = numbers.getOrNull(0)?.let { formatNumber(it) } ?: ""
        val price = numbers.getOrNull(1)?.let { formatNumber(it) } ?: ""
        val unit = unitCell?.lowercase(Locale.getDefault()) ?: "г"

        return InvoiceRowDraft(
            name = nameCell.trim(),
            quantity = quantity,
            unit = unit,
            price = price
        )
    }

    private fun parseMergedLine(text: String): InvoiceRowDraft? {
        val cleaned = text.trim()
        if (cleaned.length < 2) return null

        val lower = cleaned.lowercase(Locale.getDefault())
        if (HEADER_WORDS.any { lower.startsWith(it) } && NUMBER_REGEX.find(cleaned) == null) {
            return null
        }
        if (lower == "итого" || lower == "всего") return null

        val firstNumber = NUMBER_REGEX.find(cleaned) ?: return InvoiceRowDraft(name = cleaned)
        val name = cleaned.substring(0, firstNumber.range.first)
            .trim()
            .trimEnd(',', ';', '-', '—', ':')
            .trim()
        if (name.isBlank() || name.count { it.isLetter() } < 2) return null

        val allNumbers = NUMBER_REGEX.findAll(cleaned).mapNotNull { parseNumber(it.value) }.toList()
        val tail = cleaned.substring(firstNumber.range.last + 1)
        val unitMatch = Regex("(?i)\\b(шт|кг|г|л|мл|уп|упак|пач)\\b").find(tail)
        val unit = unitMatch?.value?.lowercase(Locale.getDefault()) ?: "г"

        return InvoiceRowDraft(
            name = name,
            quantity = allNumbers.getOrNull(0)?.let { formatNumber(it) } ?: "",
            unit = unit,
            price = allNumbers.getOrNull(1)?.let { formatNumber(it) } ?: ""
        )
    }

    private fun parseNumber(raw: String): Double? {
        val cleaned = raw
            .replace('\u00A0', ' ')
            .replace(" ", "")
            .replace("₽", "")
            .replace("руб", "", ignoreCase = true)
            .replace(',', '.')
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun formatNumber(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString()
        else String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
}