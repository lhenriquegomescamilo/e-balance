package com.ebalance.transactions.infrastructure.web

import com.ebalance.transactions.application.GetCategoriesUseCase
import com.ebalance.transactions.application.GetMonthlySummaryUseCase
import com.ebalance.transactions.application.GetTransactionSummaryUseCase
import com.ebalance.transactions.application.GetTransactionsUseCase
import com.ebalance.transactions.application.UpdateTransactionCategoryUseCase
import com.ebalance.transactions.domain.CategoryEntry
import com.ebalance.transactions.domain.CategorySummary
import com.ebalance.transactions.domain.MonthlyCategoryData
import com.ebalance.transactions.domain.MonthlyCategorySeries
import com.ebalance.transactions.domain.MonthlySummaryResult
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionPage
import com.ebalance.transactions.domain.TransactionRow
import com.ebalance.transactions.domain.TransactionSummaryResult
import com.ebalance.transactions.infrastructure.web.dto.CategoryListResponse
import com.ebalance.transactions.infrastructure.web.dto.MonthlySummaryResponse
import com.ebalance.transactions.infrastructure.web.dto.TransactionListResponse
import com.ebalance.transactions.infrastructure.web.dto.TransactionSummaryResponse
import com.ebalance.transactions.infrastructure.web.dto.UpdateCategoryRequest
import com.ebalance.transactions.infrastructure.web.dto.UpdateCategoryResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate

// Top-level helper — unambiguous Application receiver, no Kotest lambda nesting issues
private fun Application.configureTestRoutes(
    summaryUseCase: GetTransactionSummaryUseCase,
    transactionsUseCase: GetTransactionsUseCase,
    categoriesUseCase: GetCategoriesUseCase,
    monthlySummaryUseCase: GetMonthlySummaryUseCase,
    updateCategoryUseCase: UpdateTransactionCategoryUseCase
) {
    install(ServerContentNegotiation) { json() }
    routing {
        route("/api/v1") {
            transactionRoutes(
                summaryUseCase,
                transactionsUseCase,
                categoriesUseCase,
                monthlySummaryUseCase,
                updateCategoryUseCase
            )
        }
    }
}

private val clientJson = Json { ignoreUnknownKeys = true }

class TransactionRoutesTest : DescribeSpec({

    val summaryUseCase        = mockk<GetTransactionSummaryUseCase>()
    val transactionsUseCase   = mockk<GetTransactionsUseCase>()
    val categoriesUseCase     = mockk<GetCategoriesUseCase>()
    val monthlySummaryUseCase = mockk<GetMonthlySummaryUseCase>()
    val updateCategoryUseCase = mockk<UpdateTransactionCategoryUseCase>()

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureTestRoutes(
                summaryUseCase,
                transactionsUseCase,
                categoriesUseCase,
                monthlySummaryUseCase,
                updateCategoryUseCase
            )
        }
        block()
    }

    fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ClientContentNegotiation) { json(clientJson) }
    }

    beforeEach { clearAllMocks() }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    fun aSummaryResult() = TransactionSummaryResult(
        totalIncome      = BigDecimal("1000.00"),
        totalExpenses    = BigDecimal("400.00"),
        netBalance       = BigDecimal("600.00"),
        transactionCount = 10,
        categories       = listOf(
            CategorySummary(1L, "Food", BigDecimal.ZERO, BigDecimal("400.00"), 5)
        )
    )

    fun aTransactionPage(page: Int = 1, pageSize: Int = 20) = TransactionPage(
        rows = listOf(
            TransactionRow(
                id           = 1L,
                operatedAt   = LocalDate.of(2026, 1, 15),
                description  = "Supermarket",
                value        = BigDecimal("-50.00"),
                balance      = BigDecimal("950.00"),
                categoryId   = 1L,
                categoryName = "Food"
            )
        ),
        total      = 1,
        page       = page,
        pageSize   = pageSize,
        totalPages = 1
    )

    fun aMonthlySummaryResult() = MonthlySummaryResult(
        months = listOf("2026-01", "2026-02"),
        series = listOf(
            MonthlyCategorySeries(
                categoryId   = 1L,
                categoryName = "Food",
                monthlyData  = listOf(
                    MonthlyCategoryData("2026-01", BigDecimal.ZERO, BigDecimal("200.00"), 3),
                    MonthlyCategoryData("2026-02", BigDecimal.ZERO, BigDecimal("150.00"), 2)
                )
            )
        )
    )

    // ── GET /api/v1/transactions/summary ──────────────────────────────────────

    describe("GET /api/v1/transactions/summary") {

        it("returns 200 with aggregated totals") {
            every { summaryUseCase.execute(any()) } returns aSummaryResult()

            testApp {
                val body = jsonClient().get("/api/v1/transactions/summary")
                    .body<TransactionSummaryResponse>()

                body.summary.totalIncome      shouldBe 1000.0
                body.summary.totalExpenses    shouldBe 400.0
                body.summary.netBalance       shouldBe 600.0
                body.summary.transactionCount shouldBe 10
            }
        }

        it("calculates percentageOfExpenses correctly for each category") {
            every { summaryUseCase.execute(any()) } returns aSummaryResult()

            testApp {
                val body = jsonClient().get("/api/v1/transactions/summary")
                    .body<TransactionSummaryResponse>()

                // Food has 400 of 400 total expenses → 100%
                body.categories.first().percentageOfExpenses shouldBe 100.0
            }
        }

        it("returns percentageOfExpenses as 0.0 when totalExpenses is zero") {
            every { summaryUseCase.execute(any()) } returns TransactionSummaryResult(
                totalIncome      = BigDecimal("500.00"),
                totalExpenses    = BigDecimal.ZERO,
                netBalance       = BigDecimal("500.00"),
                transactionCount = 3,
                categories       = listOf(
                    CategorySummary(1L, "Income", BigDecimal("500.00"), BigDecimal.ZERO, 3)
                )
            )

            testApp {
                val body = jsonClient().get("/api/v1/transactions/summary")
                    .body<TransactionSummaryResponse>()

                body.categories.first().percentageOfExpenses shouldBe 0.0
            }
        }

        it("echoes back the applied date filters in the response") {
            every { summaryUseCase.execute(any()) } returns aSummaryResult()

            testApp {
                val body = jsonClient()
                    .get("/api/v1/transactions/summary?startDate=2026-01-01&endDate=2026-03-31")
                    .body<TransactionSummaryResponse>()

                body.filters.startDate shouldBe "2026-01-01"
                body.filters.endDate   shouldBe "2026-03-31"
            }
        }

        it("returns 400 INVALID_DATE when startDate is not a valid ISO-8601 date") {
            testApp {
                val response = createClient {}.get("/api/v1/transactions/summary?startDate=not-a-date")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_DATE") shouldBe true
            }
        }

        it("returns 400 INVALID_DATE when endDate is not a valid ISO-8601 date") {
            testApp {
                val response = createClient {}.get("/api/v1/transactions/summary?endDate=99-99-9999")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_DATE") shouldBe true
            }
        }

        it("returns 400 INVALID_PARAMETER when startDate is after endDate") {
            testApp {
                val response = createClient {}
                    .get("/api/v1/transactions/summary?startDate=2026-03-01&endDate=2026-01-01")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_PARAMETER") shouldBe true
            }
        }

        it("returns 400 INVALID_PARAMETER when type is not INCOME, EXPENSE, or ALL") {
            testApp {
                val response = createClient {}.get("/api/v1/transactions/summary?type=UNKNOWN")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_PARAMETER") shouldBe true
            }
        }

        it("returns 500 INTERNAL_ERROR when the use case throws an unexpected exception") {
            every { summaryUseCase.execute(any()) } throws RuntimeException("DB connection lost")

            testApp {
                val response = createClient {}.get("/api/v1/transactions/summary")

                response.status shouldBe HttpStatusCode.InternalServerError
                response.bodyAsText().contains("INTERNAL_ERROR") shouldBe true
            }
        }
    }

    // ── GET /api/v1/transactions ──────────────────────────────────────────────

    describe("GET /api/v1/transactions") {

        it("returns 200 with a paginated transaction list") {
            every { transactionsUseCase.execute(any()) } returns aTransactionPage()

            testApp {
                val response = jsonClient().get("/api/v1/transactions")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<TransactionListResponse>()
                body.transactions.size shouldBe 1
                body.total             shouldBe 1
                body.page              shouldBe 1
                body.pageSize          shouldBe 20
                body.totalPages        shouldBe 1
            }
        }

        it("assigns EXPENSE type to transactions with a negative value") {
            every { transactionsUseCase.execute(any()) } returns aTransactionPage()

            testApp {
                val body = jsonClient().get("/api/v1/transactions").body<TransactionListResponse>()

                body.transactions.first().type shouldBe "EXPENSE"
            }
        }

        it("assigns INCOME type to transactions with a positive value") {
            every { transactionsUseCase.execute(any()) } returns TransactionPage(
                rows = listOf(
                    TransactionRow(2L, LocalDate.of(2026, 1, 1), "Salary", BigDecimal("2000.00"), BigDecimal("2000.00"), 2L, "Income")
                ),
                total = 1, page = 1, pageSize = 20, totalPages = 1
            )

            testApp {
                val body = jsonClient().get("/api/v1/transactions").body<TransactionListResponse>()

                body.transactions.first().type shouldBe "INCOME"
            }
        }

        it("passes page and pageSize query params through to the filter") {
            val capturedFilter = slot<TransactionFilter>()
            every { transactionsUseCase.execute(capture(capturedFilter)) } returns aTransactionPage(page = 3, pageSize = 50)

            testApp {
                jsonClient().get("/api/v1/transactions?page=3&pageSize=50")

                capturedFilter.captured.page     shouldBe 3
                capturedFilter.captured.pageSize shouldBe 50
            }
        }

        it("defaults to page=1 and pageSize=20 when params are absent") {
            val capturedFilter = slot<TransactionFilter>()
            every { transactionsUseCase.execute(capture(capturedFilter)) } returns aTransactionPage()

            testApp {
                jsonClient().get("/api/v1/transactions")

                capturedFilter.captured.page     shouldBe 1
                capturedFilter.captured.pageSize shouldBe 20
            }
        }

        it("returns 400 INVALID_DATE when a date param is malformed") {
            testApp {
                val response = createClient {}.get("/api/v1/transactions?startDate=bad-date")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_DATE") shouldBe true
            }
        }
    }

    // ── GET /api/v1/transactions/monthly-by-category ──────────────────────────

    describe("GET /api/v1/transactions/monthly-by-category") {

        it("returns 200 with months list and category series data") {
            every { monthlySummaryUseCase.execute(any()) } returns aMonthlySummaryResult()

            testApp {
                val response = jsonClient().get("/api/v1/transactions/monthly-by-category")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<MonthlySummaryResponse>()
                body.months shouldBe listOf("2026-01", "2026-02")
                body.series.size shouldBe 1
                body.series.first().categoryName shouldBe "Food"
                body.series.first().monthlyData.size shouldBe 2
            }
        }

        it("returns 400 INVALID_DATE when a date query param is malformed") {
            testApp {
                val response = createClient {}
                    .get("/api/v1/transactions/monthly-by-category?startDate=oops")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_DATE") shouldBe true
            }
        }

        it("returns 500 INTERNAL_ERROR when the use case throws an unexpected exception") {
            every { monthlySummaryUseCase.execute(any()) } throws RuntimeException("Unexpected failure")

            testApp {
                val response = createClient {}.get("/api/v1/transactions/monthly-by-category")

                response.status shouldBe HttpStatusCode.InternalServerError
                response.bodyAsText().contains("INTERNAL_ERROR") shouldBe true
            }
        }
    }

    // ── PATCH /api/v1/transactions/{id}/category ──────────────────────────────

    describe("PATCH /api/v1/transactions/{id}/category") {

        it("returns 200 with a success response when the category is updated") {
            justRun { updateCategoryUseCase.execute(42L, 5L) }

            testApp {
                val response = jsonClient().patch("/api/v1/transactions/42/category") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateCategoryRequest(categoryId = 5L))
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<UpdateCategoryResponse>()
                body.transactionId shouldBe 42L
                body.categoryId    shouldBe 5L
                body.message       shouldBe "Category updated successfully"
            }
        }

        it("returns 400 INVALID_PARAMETER when the transaction id is not a number") {
            testApp {
                val response = createClient {}.patch("/api/v1/transactions/not-a-number/category") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"categoryId":1}""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_PARAMETER") shouldBe true
            }
        }

        it("returns 404 NOT_FOUND when the transaction does not exist") {
            every { updateCategoryUseCase.execute(any(), any()) } throws
                NoSuchElementException("Transaction 999 not found")

            testApp {
                val response = jsonClient().patch("/api/v1/transactions/999/category") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateCategoryRequest(categoryId = 1L))
                }

                response.status shouldBe HttpStatusCode.NotFound
                response.bodyAsText().contains("NOT_FOUND") shouldBe true
            }
        }

        it("returns 404 NOT_FOUND when the category does not exist") {
            every { updateCategoryUseCase.execute(any(), any()) } throws
                NoSuchElementException("Category 99 not found")

            testApp {
                val response = jsonClient().patch("/api/v1/transactions/1/category") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateCategoryRequest(categoryId = 99L))
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        it("returns 400 INVALID_PARAMETER when the use case throws IllegalArgumentException") {
            every { updateCategoryUseCase.execute(any(), any()) } throws
                IllegalArgumentException("Category ID must be positive")

            testApp {
                val response = jsonClient().patch("/api/v1/transactions/1/category") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateCategoryRequest(categoryId = -1L))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_PARAMETER") shouldBe true
            }
        }
    }

    // ── GET /api/v1/categories ────────────────────────────────────────────────

    describe("GET /api/v1/categories") {

        val categories = listOf(
            CategoryEntry(1L, "Food", "FOOD"),
            CategoryEntry(2L, "Transport", "TRANSPORT"),
            CategoryEntry(3L, "Entertainment", "ENTERTAINMENT")
        )

        it("returns 200 with all categories when no ids param is provided") {
            every { categoriesUseCase.execute(emptyList()) } returns categories

            testApp {
                val response = jsonClient().get("/api/v1/categories")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<CategoryListResponse>()
                body.categories.size shouldBe 3
            }
        }

        it("returns 200 with only the requested categories when ids param is provided") {
            every { categoriesUseCase.execute(listOf(1L, 3L)) } returns
                categories.filter { it.id in listOf(1L, 3L) }

            testApp {
                val response = jsonClient().get("/api/v1/categories?ids=1,3")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<CategoryListResponse>()
                body.categories.size shouldBe 2
                body.categories.map { it.id } shouldBe listOf(1L, 3L)
            }
        }

        it("returns 400 INVALID_PARAMETER when ids contains a non-numeric value") {
            testApp {
                val response = createClient {}.get("/api/v1/categories?ids=1,abc,3")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().contains("INVALID_PARAMETER") shouldBe true
            }
        }

        it("returns 500 INTERNAL_ERROR when the use case throws an unexpected exception") {
            every { categoriesUseCase.execute(any()) } throws RuntimeException("DB error")

            testApp {
                val response = createClient {}.get("/api/v1/categories")

                response.status shouldBe HttpStatusCode.InternalServerError
                response.bodyAsText().contains("INTERNAL_ERROR") shouldBe true
            }
        }
    }
})
