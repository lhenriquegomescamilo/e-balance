package com.ebalance.infrastructure.excel

import com.ebalance.application.port.TransactionReadException
import com.ebalance.application.port.TransactionReader
import com.ebalance.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Excel (.xls) implementation of TransactionReader.
 * Parses bank transaction exports in the format:
 * - Row 0-5: Header/metadata
 * - Row 6: Column headers
 * - Row 7+: Transaction data
 * 
 * Columns: Data Operação | Data valor | Descrição | Montante | Saldo
 */
class ExcelTransactionReader(
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : TransactionReader {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    override suspend fun read(inputStream: InputStream): List<Transaction> = withContext(ioDispatcher) {
        try {
            HSSFWorkbook(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val transactions = mutableListOf<Transaction>()

                // Data starts at row 7 (index 6 is header)
                for (rowNum in 7..sheet.lastRowNum) {
                    val row = sheet.getRow(rowNum) ?: continue

                    // Skip empty rows or rows that look like footer/summary
                    val operatedAtStr = row.getCell(0)?.stringCellValue?.trim() ?: continue
                    if (operatedAtStr.isEmpty() || !operatedAtStr.matches(Regex("\\d{2}-\\d{2}-\\d{4}"))) {
                        continue
                    }

                    val operatedAt = parseDate(operatedAtStr)
                    val description = row.getCell(2)?.stringCellValue?.trim() ?: continue

                    val value = row.getCell(3)?.numericCellValue?.let { BigDecimal.valueOf(it) }
                        ?: continue

                    val balance = row.getCell(4)?.numericCellValue?.let { BigDecimal.valueOf(it) }
                        ?: continue

                    transactions.add(
                        Transaction(
                            operatedAt = operatedAt,
                            description = description,
                            value = value,
                            balance = balance
                        )
                    )
                }

                transactions
            }
        } catch (e: Exception) {
            throw TransactionReadException("Failed to read Excel file: ${e.message}", e)
        }
    }

    private fun parseDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr, dateFormatter)
    }
}
