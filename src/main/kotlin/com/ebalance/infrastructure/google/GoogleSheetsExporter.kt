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
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.FileInputStream
import java.math.BigDecimal

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
    }

    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory = JacksonFactory.getDefaultInstance()

    /**
     * Exports transactions to Google Sheets.
     * Maps transactions to the expense tracking spreadsheet format:
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
            
            // Get existing data to find where to append
            val range = "A:G"
            val existingData = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()
            
            val startRow = (existingData.values?.size ?: 0) + 1
            
            // Build the rows to insert
            val rows = transactions.map { transaction ->
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
            
            if (rows.isEmpty()) {
                return GoogleSheetsError.NoDataToExport("No transactions to export").left()
            }
            
            // Append the data
            val valueRange = ValueRange()
                .setValues(rows.map { row -> row.map { it.toString() } })
            
            service.spreadsheets().values()
                .append(spreadsheetId, range, valueRange)
                .setValueInputOption("USER_ENTERED")
                .execute()
            
            ExportResult(
                exported = transactions.size,
                startRow = startRow,
                endRow = startRow + transactions.size - 1
            ).right()
        } catch (e: Exception) {
            GoogleSheetsError.ExportError(e.message ?: "Unknown export error").left()
        }
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
        val startRow: Int,
        val endRow: Int
    )

    sealed class GoogleSheetsError {
        data class AuthenticationError(val message: String) : GoogleSheetsError()
        data class ExportError(val message: String) : GoogleSheetsError()
        data class NoDataToExport(val message: String) : GoogleSheetsError()
    }
}
