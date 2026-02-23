package com.ebalance.infrastructure.excel

import arrow.core.fold
import arrow.core.left
import arrow.core.right
import com.ebalance.domain.error.TransactionReadError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class ExcelTransactionReaderTest : DescribeSpec({
    
    val testDispatcher = StandardTestDispatcher()
    
    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }
    
    afterSpec {
        Dispatchers.resetMain()
    }
    
    describe("ExcelTransactionReader") {
        
        lateinit var reader: ExcelTransactionReader
        
        beforeEach {
            reader = ExcelTransactionReader(testDispatcher)
        }
        
        fun createXlsStream(
            data: List<List<Any?>>,
            includeHeader: Boolean = true
        ): ByteArrayInputStream {
            val workbook = HSSFWorkbook()
            val sheet = workbook.createSheet("Saldos e movimentos")
            
            var rowNum = 0
            
            if (includeHeader) {
                // Add metadata rows (rows 0-5)
                sheet.createRow(rowNum++).createCell(0).setCellValue("Domingo, 22 de Fevereiro de 2026")
                sheet.createRow(rowNum++).createCell(0).setCellValue("LUIS HENRIQUE GOMES CAMILO")
                sheet.createRow(rowNum++) // Empty row
                sheet.createRow(rowNum++).createCell(0).setCellValue("Saldos e movimentos")
                sheet.createRow(rowNum++) // Empty row
                sheet.createRow(rowNum++).createCell(0).setCellValue("Listagem de Movimentos")
                
                // Add header row
                val headerRow = sheet.createRow(rowNum++)
                headerRow.createCell(0).setCellValue("Data Operação")
                headerRow.createCell(1).setCellValue("Data valor")
                headerRow.createCell(2).setCellValue("Descrição")
                headerRow.createCell(3).setCellValue("Montante( EUR )")
                headerRow.createCell(4).setCellValue("Saldo Contabilístico( EUR )")
            }
            
            // Add data rows
            data.forEach { rowData ->
                val row = sheet.createRow(rowNum++)
                rowData.forEachIndexed { index, value ->
                    when (value) {
                        is String -> row.createCell(index).setCellValue(value)
                        is Double -> row.createCell(index).setCellValue(value)
                        is Number -> row.createCell(index).setCellValue(value.toDouble())
                        null -> {} // Skip null cells
                    }
                }
            }
            
            val outputStream = ByteArrayOutputStream()
            workbook.write(outputStream)
            workbook.close()
            return ByteArrayInputStream(outputStream.toByteArray())
        }
        
        describe("read") {
            
            it("should parse valid Excel file with transactions") {
                runTest {
                    val data = listOf(
                        listOf("23-02-2026", "23-02-2026", "Repsol E0394", -75.01, 578.96),
                        listOf("23-02-2026", "23-02-2026", "Farmacia Dias", -39.09, 653.97),
                        listOf("19-02-2026", "19-02-2026", "Other Transfer", 700.0, 703.86)
                    )
                    
                    val inputStream = createXlsStream(data)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions ->
                            transactions shouldHaveSize 3
                            
                            transactions[0].operatedAt shouldBe LocalDate.of(2026, 2, 23)
                            transactions[0].description shouldBe "Repsol E0394"
                            transactions[0].value.toDouble() shouldBe -75.01
                            transactions[0].balance.toDouble() shouldBe 578.96
                            
                            transactions[1].operatedAt shouldBe LocalDate.of(2026, 2, 23)
                            transactions[1].description shouldBe "Farmacia Dias"
                            
                            transactions[2].operatedAt shouldBe LocalDate.of(2026, 2, 19)
                            transactions[2].description shouldBe "Other Transfer"
                            transactions[2].value.toDouble() shouldBe 700.0
                        }
                    )
                }
            }
            
            it("should handle empty data rows") {
                runTest {
                    val inputStream = createXlsStream(emptyList())
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions -> transactions shouldHaveSize 0 }
                    )
                }
            }
            
            it("should skip rows with invalid date format") {
                runTest {
                    val data = listOf(
                        listOf("invalid-date", "23-02-2026", "Test", -10.0, 100.0),
                        listOf("23-02-2026", "23-02-2026", "Valid Transaction", -50.0, 150.0)
                    )
                    
                    val inputStream = createXlsStream(data)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions ->
                            transactions shouldHaveSize 1
                            transactions[0].description shouldBe "Valid Transaction"
                        }
                    )
                }
            }
            
            it("should skip rows with missing description") {
                runTest {
                    val data = listOf(
                        listOf("23-02-2026", "23-02-2026", null, -10.0, 100.0),
                        listOf("23-02-2026", "23-02-2026", "Valid Transaction", -50.0, 150.0)
                    )
                    
                    val inputStream = createXlsStream(data)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions -> transactions shouldHaveSize 1 }
                    )
                }
            }
            
            it("should skip rows with missing value") {
                runTest {
                    val data = listOf(
                        listOf("23-02-2026", "23-02-2026", "Transaction without value", null, 100.0),
                        listOf("23-02-2026", "23-02-2026", "Valid Transaction", -50.0, 150.0)
                    )
                    
                    val inputStream = createXlsStream(data)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions -> transactions shouldHaveSize 1 }
                    )
                }
            }
            
            it("should handle negative and positive values correctly") {
                runTest {
                    val data = listOf(
                        listOf("23-02-2026", "23-02-2026", "Expense", -100.50, 900.0),
                        listOf("22-02-2026", "22-02-2026", "Income", 500.0, 1000.50)
                    )
                    
                    val inputStream = createXlsStream(data)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions ->
                            transactions shouldHaveSize 2
                            transactions[0].value.toDouble() shouldBe -100.50
                            transactions[1].value.toDouble() shouldBe 500.0
                        }
                    )
                }
            }
            
            it("should return InvalidFormat error for invalid Excel file") {
                runTest {
                    val invalidStream = ByteArrayInputStream("not an excel file".toByteArray())
                    
                    val result = reader.read(invalidStream)
                    
                    result.fold(
                        ifLeft = { error ->
                            error.shouldBeInstanceOf<TransactionReadError.InvalidFormat>()
                            error.message shouldContain "Failed to read Excel file"
                        },
                        ifRight = { throw AssertionError("Expected Left but got Right: $it") }
                    )
                }
            }
            
            it("should handle Excel file with only metadata rows") {
                runTest {
                    val inputStream = createXlsStream(emptyList(), includeHeader = true)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions -> transactions shouldHaveSize 0 }
                    )
                }
            }
            
            it("should handle large number of transactions") {
                runTest {
                    val data = (1..100).map { index ->
                        val day = (index % 28 + 1).toString().padStart(2, '0')
                        listOf(
                            "$day-02-2026",
                            "$day-02-2026",
                            "Transaction $index",
                            -index.toDouble(),
                            1000.0 - index
                        )
                    }
                    
                    val inputStream = createXlsStream(data)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions -> transactions shouldHaveSize 100 }
                    )
                }
            }
            
            it("should filter out any Softdraft-related transactions") {
                runTest {
                    val data = listOf(
                        listOf("23-02-2026", "23-02-2026", "Repsol E0394", -75.01, 578.96),
                        listOf("22-02-2026", "22-02-2026", "Transferência de Softdraft - Unipessoal Lda", 700.0, 653.97),
                        listOf("21-02-2026", "21-02-2026", "Farmacia Dias", -39.09, 1353.97),
                        listOf("20-02-2026", "20-02-2026", "TRANSFERÊNCIA DE SOFTDRAFT - UNIPESSOAL LDA", 800.0, 1393.06),
                        listOf("19-02-2026", "19-02-2026", "Pagamento Softdraft", 500.0, 1893.06),
                        listOf("18-02-2026", "18-02-2026", "softdraft payment", 200.0, 2093.06)
                    )
                    
                    val inputStream = createXlsStream(data)
                    val result = reader.read(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions ->
                            transactions shouldHaveSize 2
                            transactions[0].description shouldBe "Repsol E0394"
                            transactions[1].description shouldBe "Farmacia Dias"
                        }
                    )
                }
            }
        }
    }
})