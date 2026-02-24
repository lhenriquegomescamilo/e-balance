package com.ebalance.infrastructure.excel

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.ebalance.application.port.TransactionReader
import com.ebalance.domain.error.TransactionReadError
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
    private val datePattern = Regex("\\d{2}-\\d{2}-\\d{4}")

    override suspend fun read(inputStream: InputStream): Either<TransactionReadError, List<Transaction>> =
        withContext(ioDispatcher) {
            either {
                // Try to parse the Excel workbook using runCatching
                val workbook = openWorkbook(inputStream).bind()

                // Parse transactions from the workbook using generateSequence
                workbook.use { parseTransactions(it) }
            }
        }

    /**
     * Opens an HSSFWorkbook from the input stream.
     * Uses runCatching + fold for error handling.
     */
    private fun openWorkbook(inputStream: InputStream): Either<TransactionReadError.InvalidFormat, HSSFWorkbook> =
        runCatching { HSSFWorkbook(inputStream) }
            .fold(
                onSuccess = { it.right() },
                onFailure = { e ->
                    TransactionReadError.InvalidFormat(
                        message = "Failed to read Excel file: ${e.message}",
                        cause = e
                    ).left()
                }
            )

    /**
     * Parses all transactions from the workbook using generateSequence.
     */
    private fun parseTransactions(workbook: HSSFWorkbook): List<Transaction> {
        val sheet = workbook.getSheetAt(0)

        // Use generateSequence to create a lazy sequence of rows starting from row 7
        return generateSequence(sheet.getRow(7)) { row ->
            val nextRowNum = row.rowNum + 1
            if (nextRowNum <= sheet.lastRowNum) sheet.getRow(nextRowNum) else null
        }
            .mapNotNull { row -> parseRow(row) }
            .toList()
    }

    /**
     * Parses a single row into a Transaction.
     * Returns null if the row should be skipped (invalid/empty data).
     */
    private fun parseRow(row: org.apache.poi.ss.usermodel.Row): Transaction? {
        // Get operation date string
        val operatedAtStr = row.getCell(0)?.stringCellValue?.trim() ?: return null

        // Validate date format
        if (!operatedAtStr.matches(datePattern)) return null

        // Parse date using runCatching
        val operatedAt = runCatching {
            LocalDate.parse(operatedAtStr, dateFormatter)
        }.getOrNull() ?: return null

        // Get description
        val description = row.getCell(2)?.stringCellValue?.trim() ?: return null
        if (description.isBlank()) return null

        // Filter out any Softdraft-related transactions
        val ignored = listOf("Softdraft", "Transferência de", "Transferencia de", "Trf.Imed.   de")
        if (ignored.any { it -> description.contains(it, ignoreCase = true) }) return null

        // Get value (amount)
        val value = row.getCell(3)?.numericCellValue?.let { BigDecimal.valueOf(it) }
            ?: return null

        // Get balance
        val balance = row.getCell(4)?.numericCellValue?.let { BigDecimal.valueOf(it) }
            ?: return null

        return Transaction(
            operatedAt = operatedAt,
            description = description,
            value = value,
            balance = balance
        )
    }
}