package com.ebalance.application.usecase

import arrow.core.fold
import arrow.core.left
import arrow.core.right
import com.ebalance.TestFixtures
import com.ebalance.application.port.CategoryClassifierPort
import com.ebalance.domain.error.ImportError
import com.ebalance.domain.error.TransactionReadError
import com.ebalance.domain.error.TransactionRepositoryError
import com.ebalance.application.port.TransactionReader
import com.ebalance.application.port.TransactionRepository
import com.ebalance.classification.TextClassifier
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
        lateinit var  classififer: CategoryClassifierPort

        beforeEach {
            reader = mockk()
            repository = mockk()
            classififer = mockk()
            useCase = ImportTransactionsUseCase(reader, repository, classififer, testDispatcher)
            inputStream = ByteArrayInputStream("test data".toByteArray())
        }
        
        describe("execute") {
            
            it("should read transactions and save them to repository") {
                runTest {
                    val transactions = TestFixtures.sampleTransactions
                    
                    coEvery { reader.read(any()) } returns transactions.right()
                    coEvery { repository.saveAll(transactions) } returns 
                        TransactionRepository.SaveResult(inserted = 3, duplicates = 0).right()
                    
                    val result = useCase.execute(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { importResult ->
                            importResult.totalRead shouldBe 3
                            importResult.totalInserted shouldBe 3
                            importResult.duplicatesSkipped shouldBe 0
                        }
                    )
                    
                    coVerify { reader.read(inputStream) }
                    coVerify { repository.saveAll(transactions) }
                }
            }
            
            it("should calculate duplicates correctly when some transactions already exist") {
                runTest {
                    val transactions = TestFixtures.aListOfTransactions(10)
                    
                    coEvery { reader.read(any()) } returns transactions.right()
                    coEvery { repository.saveAll(transactions) } returns 
                        TransactionRepository.SaveResult(inserted = 7, duplicates = 3).right()
                    
                    val result = useCase.execute(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { importResult ->
                            importResult.totalRead shouldBe 10
                            importResult.totalInserted shouldBe 7
                            importResult.duplicatesSkipped shouldBe 3
                        }
                    )
                }
            }
            
            it("should handle empty transaction list") {
                runTest {
                    coEvery { reader.read(any()) } returns emptyList<com.ebalance.domain.model.Transaction>().right()
                    coEvery { repository.saveAll(emptyList()) } returns 
                        TransactionRepository.SaveResult(inserted = 0, duplicates = 0).right()
                    
                    val result = useCase.execute(inputStream)
                    
                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { importResult ->
                            importResult.totalRead shouldBe 0
                            importResult.totalInserted shouldBe 0
                            importResult.duplicatesSkipped shouldBe 0
                        }
                    )
                }
            }
            
            it("should return ReadError when reader fails") {
                runTest {
                    val readError = TransactionReadError.InvalidFormat("Failed to read")
                    coEvery { reader.read(any()) } returns readError.left()
                    
                    val result = useCase.execute(inputStream)
                    
                    result.fold(
                        ifLeft = { error ->
                            error.shouldBeInstanceOf<ImportError.ReadError>()
                            error.error shouldBe readError
                        },
                        ifRight = { throw AssertionError("Expected Left but got Right: $it") }
                    )
                }
            }
            
            it("should return PersistenceError when repository fails") {
                runTest {
                    val transactions = TestFixtures.sampleTransactions
                    val repoError = TransactionRepositoryError.ConnectionError("Database error")
                    
                    coEvery { reader.read(any()) } returns transactions.right()
                    coEvery { repository.saveAll(transactions) } returns repoError.left()
                    
                    val result = useCase.execute(inputStream)
                    
                    result.fold(
                        ifLeft = { error ->
                            error.shouldBeInstanceOf<ImportError.PersistenceError>()
                            error.error shouldBe repoError
                        },
                        ifRight = { throw AssertionError("Expected Left but got Right: $it") }
                    )
                }
            }
            
            it("should use injected dispatcher for I/O operations") {
                runTest {
                    val transactions = TestFixtures.sampleTransactions
                    
                    coEvery { reader.read(any()) } returns transactions.right()
                    coEvery { repository.saveAll(transactions) } returns 
                        TransactionRepository.SaveResult(inserted = 3, duplicates = 0).right()
                    
                    useCase.execute(inputStream)
                    
                    coVerify { reader.read(any()) }
                    coVerify { repository.saveAll(transactions) }
                }
            }
        }
    }
})