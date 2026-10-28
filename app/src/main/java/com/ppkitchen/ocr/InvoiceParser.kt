package com.ppkitchen.ocr

import java.util.Locale
import kotlin.math.abs

object InvoiceParser {

    private val NUMBER_REGEX = Regex("\\d+[.,]?\\d*")
    private val HEADER_WORDS = listOf("ккал", "калор", "белк", "жир", "углев")

    fun parseInvoice(lines: List<DetectedLine>, knownWords: List<String>): List<InvoiceRowDraft> {
        val rows = groupIntoRows(lines)
        return rows.mapNotNull { row ->
            val text = row.joinToString(" ") { it.text }
            parseMergedLine(text)
        }
    }

    fun parseReference(lines: List<DetectedLine>, knownWords: List<String>): List<InvoiceRowDraft> {
        val allRows = groupIntoRows(lines)

        var colCalX = -1
        var colProtX = -1
        var colFatX = -1
        var colCarbX = -1

        // 1. Ищем строку с заголовками для определения колонок
        val headerRow = allRows.find { row ->
            row.any { it.text.contains("Ккал", true) || it.text.contains("Калорийность", true) }
        }

        if (headerRow != null) {
            for (word in headerRow) {
                val t = word.text.lowercase(Locale.getDefault())
                if (t.contains("ккал") || t.contains("калор")) colCalX = word.centerX
                else if (t.contains("белк")) colProtX = word.centerX
                else if (t.contains("жир")) colFatX = word.centerX
                else if (t.contains("углев")) colCarbX = word.centerX
            }
        }

        val hasColumns = colCalX > 0 && colProtX > 0 && colFatX > 0 && colCarbX > 0

        return allRows.mapNotNull { row ->
            if (row == headerRow) return@mapNotNull null

            // Фильтруем мусорные слова, оставляя только слова с буквами или цифрами
            val words = row.filter { it.text.any { c -> c.isLetterOrDigit() } && it.text.length > 1 }
            if (words.isEmpty()) return@mapNotNull null

            // Пропускаем строки, которые состоят только из заголовков
            if (words.all { w -> HEADER_WORDS.any { w.text.lowercase().contains(it) } }) return@mapNotNull null

            var cal = ""
            var prot = ""
            var fat = ""
            var carb = ""
            val nameParts = mutableListOf<String>()
            val numericWords = mutableListOf<DetectedLine>()

            for (word in words) {
                val cleanNum = word.text.replace(',', '.').replace(Regex("[^0-9.]"), "")
                val num = cleanNum.toDoubleOrNull()
                val isNumber = num != null && cleanNum.isNotEmpty() && word.text.any { it.isDigit() }

                if (isNumber) {
                    numericWords.add(word)
                } else {
                    // Если слово находится слева от колонки Ккал (с отступом), это название
                    if (!hasColumns || word.centerX < colCalX - 150) {
                        nameParts.add(word.text)
                    }
                }
            }

            // 2. Распределяем числа по колонкам
            if (hasColumns) {
                for (word in numericWords) {
                    val num = word.text.replace(',', '.').replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: continue

                    // Вычисляем расстояние до центров колонок
                    val dCal = abs(word.centerX - colCalX)
                    val dProt = abs(word.centerX - colProtX)
                    val dFat = abs(word.centerX - colFatX)
                    val dCarb = abs(word.centerX - colCarbX)

                    val minDist = minOf(dCal, dProt, dFat, dCarb)

                    // Если число очень далеко от всех колонок (например, часть названия), пропускаем
                    if (minDist > 200) continue

                    when (minDist) {
                        dCal -> if (cal.isEmpty()) cal = formatNumber(num)
                        dProt -> if (prot.isEmpty()) prot = formatNumber(num)
                        dFat -> if (fat.isEmpty()) fat = formatNumber(num)
                        dCarb -> if (carb.isEmpty()) carb = formatNumber(num)
                    }
                }
            } else {
                // Запасной вариант, если не нашли заголовки (просто по порядку)
                val nums = numericWords.mapNotNull { it.text.replace(',', '.').replace(Regex("[^0-9.]"), "").toDoubleOrNull() }
                if (nums.size >= 3) {
                    cal = formatNumber(nums[0])
                    prot = formatNumber(nums[1])
                    fat = formatNumber(nums[2])
                    if (nums.size >= 4) carb = formatNumber(nums[3])
                } else if (nums.size == 2) {
                    cal = formatNumber(nums[0])
                    prot = formatNumber(nums[1])
                }
            }

            // Если ни одна цифра не попала в колонки, пропускаем строку
            if (cal.isEmpty() && prot.isEmpty() && fat.isEmpty() && carb.isEmpty()) return@mapNotNull null

            // 3. Формируем название
            var cleanName = nameParts.joinToString(" ")
                .replace(Regex("(?i)ккал|кал|белки|жиры|углеводы|\\bг\\b"), "")
                .trim(' ', ',', '.', '-', '—')

            if (cleanName.count { it.isLetter() } < 2) {
                cleanName = words.firstOrNull { it.text.any { c -> c.isLetter() } }?.text ?: ""
                cleanName = cleanName.replace(Regex("(?i)ккал|кал|белки|жиры|углеводы"), "").trim()
            }

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

        val avgHeight = nonEmpty.map { it.height }.average().toInt().coerceAtLeast(10)
        val tolerance = (avgHeight * 1.5).toInt().coerceAtLeast(20)

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

    private fun parseMergedLine(text: String): InvoiceRowDraft? {
        val cleaned = text.trim()
        if (cleaned.length < 2) return null

        val firstNumber = NUMBER_REGEX.find(cleaned) ?: return null
        val name = cleaned.substring(0, firstNumber.range.first)
            .trim()
            .trimEnd(',', ';', '-', '—', ':')
            .trim()
        if (name.isBlank() || name.count { it.isLetter() } < 2) return null

        val allNumbers = NUMBER_REGEX.findAll(cleaned).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()

        val quantity = allNumbers.getOrNull(0)?.let { formatNumber(it) } ?: ""
        val price = allNumbers.getOrNull(1)?.let { formatNumber(it) } ?: ""

        return InvoiceRowDraft(
            name = name,
            quantity = quantity,
            unit = "г",
            price = price
        )
    }

    private fun formatNumber(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString()
        else String.format(Locale.US, "%.1f", d).trimEnd('0').trimEnd('.')
}