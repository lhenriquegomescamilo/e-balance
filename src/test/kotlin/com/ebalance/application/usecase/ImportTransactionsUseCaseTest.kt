package com.ebalance.application.usecase

import com.ebalance.TestFixtures
import com.ebalance.application.port.TransactionReadException
import com.ebalance.application.port.TransactionReader
import com.ebalance.application.port.TransactionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.ByteArrayInputStream
import java.io.InputStream

class ImportTransactionsUseCaseTest : DescribeSpec({
    
    val testDispatcher = StandardTestDispatcher()
    
    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }
    
    afterSpec {
        Dispatchers.resetMain()
    }
    
    describe("ImportTransactionsUseCase") {
        
        lateinit var reader: TransactionReader
        lateinit var repository: TransactionRepository
        lateinit var useCase: ImportTransactionsUseCase
        lateinit var inputStream: InputStream
        
        beforeEach {
            reader = mockk()
            repository = mockk()
            useCase = ImportTransactionsUseCase(reader, repository, testDispatcher)
            inputStream = ByteArrayInputStream("test data".toByteArray())
        }
        
        describe("execute") {
            
            it("should read transactions and save them to repository") {
                runTest {
                    val transactions = TestFixtures.sampleTransactions
                    
                    coEvery { reader.read(any()) } returns transactions
                    coEvery { repository.saveAll(transactions) } returns 3
                    
                    val result = useCase.execute(inputStream)
                    
                    result.totalRead shouldBe 3
                    result.totalInserted shouldBe 3
                    result.duplicatesSkipped shouldBe 0
                    
                    coVerify { reader.read(inputStream) }
                    coVerify { repository.saveAll(transactions) }
                }
            }
            
            it("should calculate duplicates correctly when some transactions already exist") {
                runTest {
                    val transactions = TestFixtures.aListOfTransactions(10)
                    
                    coEvery { reader.read(any()) } returns transactions
                    coEvery { repository.saveAll(transactions) } returns 7
                    
                    val result = useCase.execute(inputStream)
                    
                    result.totalRead shouldBe 10
                    result.totalInserted shouldBe 7
                    result.duplicatesSkipped shouldBe 3
                }
            }
            
            it("should handle empty transaction list") {
                runTest {
                    coEvery { reader.read(any()) } returns emptyList()
                    coEvery { repository.saveAll(emptyList()) } returns 0
                    
                    val result = useCase.execute(inputStream)
                    
                    result.totalRead shouldBe 0
                    result.totalInserted shouldBe 0
                    result.duplicatesSkipped shouldBe 0
                }
            }
            
            it("should propagate exception when reader fails") {
                runTest {
                    coEvery { reader.read(any()) } throws TransactionReadException("Failed to read")
                    
                    val exception = shouldThrow<TransactionReadException> {
                        useCase.execute(inputStream)
                    }
                    
                    exception.message shouldBe "Failed to read"
                }
            }
            
            it("should propagate exception when repository fails") {
                runTest {
                    val transactions = TestFixtures.sampleTransactions
                    
                    coEvery { reader.read(any()) } returns transactions
                    coEvery { repository.saveAll(transactions) } throws RuntimeException("Database error")
                    
                    val exception = shouldThrow<RuntimeException> {
                        useCase.execute(inputStream)
                    }
                    
                    exception.message shouldBe "Database error"
                }
            }
            
            it("should use injected dispatcher for I/O operations") {
                runTest {
                    val transactions = TestFixtures.sampleTransactions
                    
                    coEvery { reader.read(any()) } returns transactions
                    coEvery { repository.saveAll(transactions) } returns 3
                    
                    useCase.execute(inputStream)
                    
                    coVerify { reader.read(any()) }
                    coVerify { repository.saveAll(transactions) }
                }
            }
        }
    }
})