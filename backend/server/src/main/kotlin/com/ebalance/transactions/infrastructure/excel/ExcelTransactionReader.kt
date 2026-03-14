package com.ebalance.transactions.infrastructure.excel

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class RawTransaction(
    val operatedAt: LocalDate,
    val description: String,
    val value: Double,
    val balance: Double
)

class ExcelTransactionReader {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val datePattern = Regex("\\d{2}-\\d{2}-\\d{4}")
    private val ignoredPrefixes = listOf(
        "Softdraft", "Transferência de", "Transferencia de", "Trf.Imed.   de"
    )

    fun read(inputStream: InputStream): List<RawTransaction> =
        HSSFWorkbook(inputStream).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            generateSequence(sheet.getRow(7)) { row ->
                val next = row.rowNum + 1
                if (next <= sheet.lastRowNum) sheet.getRow(next) else null
            }.mapNotNull { parseRow(it) }.toList()
        }

    private fun parseRow(row: Row): RawTransaction? {
        val dateStr = row.getCell(0)?.stringCellValue?.trim() ?: return null
        if (!dateStr.matches(datePattern)) return null
        val date = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull() ?: return null

        val description = row.getCell(2)?.stringCellValue?.trim() ?: return null
        if (description.isBlank()) return null
        if (ignoredPrefixes.any { description.contains(it, ignoreCase = true) }) return null

        val value   = row.getCell(3)?.numericCellValue ?: return null
        val balance = row.getCell(4)?.numericCellValue ?: return null

        return RawTransaction(date, description, value, balance)
    }
}
