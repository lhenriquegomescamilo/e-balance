package com.ebalance.infrastructure.google

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.domain.model.Category
import com.ebalance.domain.model.Transaction
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.FileInputStream
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * Service for exporting transactions to Google Sheets.
 */
class GoogleSheetsExporter(
    private val credentialsPath: String = "credentials.json",
    private val spreadsheetId: String
) {
    companion object {
        private const val APPLICATION_NAME = "E-Balance"
        private val SCOPES = listOf(SheetsScopes.SPREADSHEETS)
        private val MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy")
    }

    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory = JacksonFactory.getDefaultInstance()

    /**
     * Exports transactions to Google Sheets.
     * Groups transactions by Month/Year and creates/updates sheets accordingly.
     * Each sheet contains:
     * Column A: Categoria (Category)
     * Column B: Valor (Amount)
     * Column C: Conta Bancária (Bank Account)
     * Column D: Responsável (Responsible)
     * Column E: Tipo (Type - Fixa/Variada)
     * Column F: Observações (Description)
     * Column G: Data Pagamento (Payment Date)
     */
    fun export(
        transactions: List<Transaction>,
        bankAccount: String = "Santander",
        responsible: String = "Luis Camilo"
    ): Either<GoogleSheetsError, ExportResult> {
        return try {
            val service = buildService()
            
            // Group transactions by Month/Year
            val transactionsByMonth = transactions.groupBy { transaction ->
                transaction.operatedAt.format(MONTH_YEAR_FORMATTER)
            }
            
            var totalExported = 0
            val sheetResults = mutableListOf<SheetExportResult>()
            
            for ((monthYear, monthTransactions) in transactionsByMonth) {
                val sheetName = monthYear
                
                // Check if sheet exists, create if not
                val sheetId = getOrCreateSheet(service, sheetName)
                
                // Get existing data in that sheet to find where to append
                val existingData = service.spreadsheets().values()
                    .get(spreadsheetId, "$sheetName!A:G")
                    .execute()
                
                val startRow = (existingData.values?.size ?: 0) + 1
                
                // Build the rows to insert
                val rows = monthTransactions.map { transaction ->
                    val category = Category.fromId(transaction.categoryId)
                    val value = formatValue(transaction.value)
                    val date = formatDate(transaction.operatedAt)
                    val type = if (Category.isFixedExpense(category)) "Fixa" else "Variáda"
                    
                    listOf(
                        category.displayName,    // Categoria
                        value,                  // Valor
                        bankAccount,            // Conta Bancária
                        responsible,            // Responsável
                        type,                   // Tipo
                        transaction.description, // Observações
                        date                    // Data Pagamento
                    )
                }
                
                if (rows.isNotEmpty()) {
                    // Append the data
                    val valueRange = ValueRange()
                        .setValues(rows.map { row -> row.map { it.toString() } })
                    
                    service.spreadsheets().values()
                        .append(spreadsheetId, "$sheetName!A:G", valueRange)
                        .setValueInputOption("USER_ENTERED")
                        .execute()
                    
                    sheetResults.add(
                        SheetExportResult(
                            sheetName = sheetName,
                            exported = monthTransactions.size,
                            startRow = startRow,
                            endRow = startRow + monthTransactions.size - 1
                        )
                    )
                    totalExported += monthTransactions.size
                }
            }
            
            if (totalExported == 0) {
                return GoogleSheetsError.NoDataToExport("No transactions to export").left()
            }
            
            ExportResult(
                exported = totalExported,
                sheetResults = sheetResults
            ).right()
        } catch (e: Exception) {
            GoogleSheetsError.ExportError(e.message ?: "Unknown export error").left()
        }
    }

    /**
     * Gets an existing sheet by name or creates a new one if it doesn't exist.
     * @return The sheet ID
     */
    private fun getOrCreateSheet(service: Sheets, sheetName: String): Int {
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        
        // Check if sheet already exists
        val existingSheet = spreadsheet.sheets?.find { it.properties?.title == sheetName }
        if (existingSheet != null) {
            return existingSheet.properties?.sheetId ?: 0
        }
        
        // Add the new sheet
        val addSheetRequest = AddSheetRequest()
            .setProperties(SheetProperties().setTitle(sheetName))
        
        val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
            .setRequests(listOf(Request().setAddSheet(addSheetRequest)))
        
        service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()
        
        // Return a new sheet ID (typically incremented from existing sheets)
        return (spreadsheet.sheets?.size ?: 1) + 1
    }

    private fun buildService(): Sheets {
        val credentials: GoogleCredentials = FileInputStream(credentialsPath)
            .use { stream -> 
                ServiceAccountCredentials.fromStream(stream)
                    .createScoped(SCOPES)
            }

        return Sheets.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private fun formatValue(value: BigDecimal): String {
        // Format as €X.XX (always positive for display)
        val absValue = value.abs()
        return "€${absValue.toPlainString()}"
    }

    private fun formatDate(date: java.time.LocalDate): String {
        // Format as DD/MM/YYYY
        return "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthValue.toString().padStart(2, '0')}/${date.year}"
    }

    data class ExportResult(
        val exported: Int,
        val sheetResults: List<SheetExportResult>
    )

    data class SheetExportResult(
        val sheetName: String,
        val exported: Int,
        val startRow: Int,
        val endRow: Int
    )

    sealed class GoogleSheetsError {
        data class AuthenticationError(val message: String) : GoogleSheetsError()
        data class ExportError(val message: String) : GoogleSheetsError()
        data class NoDataToExport(val message: String) : GoogleSheetsError()
    }
}
