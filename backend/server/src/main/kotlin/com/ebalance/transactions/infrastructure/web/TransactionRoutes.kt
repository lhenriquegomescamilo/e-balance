package com.ebalance.transactions.infrastructure.web

import com.ebalance.transactions.application.GetCategoriesUseCase
import com.ebalance.transactions.application.GetMonthlySummaryUseCase
import com.ebalance.transactions.application.GetTransactionSummaryUseCase
import com.ebalance.transactions.application.GetTransactionsUseCase
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionType
import com.ebalance.transactions.infrastructure.web.dto.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Registers all transaction-related REST endpoints under the calling [Route].
 *
 * Endpoints:
 *   GET /transactions/summary               — aggregated stats + category breakdown (Donut chart)
 *   GET /transactions/monthly-by-category   — monthly trend per category (area/bar chart)
 *   GET /transactions                       — paginated list of individual transactions (table)
 *   GET /categories                         — all categories (filter dropdown)
 */
fun Route.transactionRoutes(
    summaryUseCase: GetTransactionSummaryUseCase,
    transactionsUseCase: GetTransactionsUseCase,
    categoriesUseCase: GetCategoriesUseCase,
    monthlySummaryUseCase: GetMonthlySummaryUseCase
) {

    // ── GET /api/v1/transactions/summary ─────────────────────────────────────
    get("/transactions/summary") {
        try {
            val filter = parseFilter(call.request)
            val result = summaryUseCase.execute(filter)

            val totalExpenses = result.totalExpenses.toDouble()

            call.respond(
                HttpStatusCode.OK,
                TransactionSummaryResponse(
                    summary = SummaryStatsDto(
                        totalIncome      = result.totalIncome.toDouble(),
                        totalExpenses    = totalExpenses,
                        netBalance       = result.netBalance.toDouble(),
                        transactionCount = result.transactionCount
                    ),
                    categories = result.categories.map { cat ->
                        CategorySummaryDto(
                            categoryId           = cat.categoryId,
                            categoryName         = cat.categoryName,
                            totalIncome          = cat.totalIncome.toDouble(),
                            totalExpenses        = cat.totalExpenses.toDouble(),
                            transactionCount     = cat.transactionCount,
                            percentageOfExpenses = if (totalExpenses > 0)
                                cat.totalExpenses.toDouble() / totalExpenses * 100 else 0.0
                        )
                    },
                    filters = AppliedFiltersDto(
                        startDate  = filter.startDate.toString(),
                        endDate    = filter.endDate.toString(),
                        categories = filter.categoryIds
                    )
                )
            )
        } catch (e: DateTimeParseException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_DATE", "Date must be ISO-8601 (YYYY-MM-DD): ${e.parsedString}")
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_PARAMETER", e.message ?: "Invalid request parameter")
            )
        } catch (e: Exception) {
            call.application.environment.log.error("Summary query failed", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")
            )
        }
    }

    // ── GET /api/v1/transactions ─────────────────────────────────────────────
    get("/transactions") {
        try {
            val filter = parseFilter(call.request)
            val rows   = transactionsUseCase.execute(filter)

            call.respond(
                HttpStatusCode.OK,
                TransactionListResponse(
                    transactions = rows.map { tx ->
                        TransactionDto(
                            id           = tx.id,
                            operatedAt   = tx.operatedAt.toString(),
                            description  = tx.description,
                            value        = tx.value.toDouble(),
                            balance      = tx.balance.toDouble(),
                            categoryId   = tx.categoryId,
                            categoryName = tx.categoryName,
                            type         = if (tx.value.signum() >= 0) "INCOME" else "EXPENSE"
                        )
                    },
                    total = rows.size
                )
            )
        } catch (e: DateTimeParseException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_DATE", "Date must be ISO-8601 (YYYY-MM-DD): ${e.parsedString}")
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_PARAMETER", e.message ?: "Invalid request parameter")
            )
        } catch (e: Exception) {
            call.application.environment.log.error("Transactions query failed", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")
            )
        }
    }

    // ── GET /api/v1/transactions/monthly-by-category ─────────────────────────
    //
    // Returns transactions grouped by category AND calendar month.
    // Both totalIncome and totalExpenses are always returned so the frontend
    // can toggle the view without an extra round-trip.
    // Missing months for a given category are zero-filled.
    get("/transactions/monthly-by-category") {
        try {
            val filter = parseFilter(call.request)
            val result = monthlySummaryUseCase.execute(filter)

            call.respond(
                HttpStatusCode.OK,
                MonthlySummaryResponse(
                    months = result.months,
                    series = result.series.map { s ->
                        MonthlyCategorySeriesDto(
                            categoryId   = s.categoryId,
                            categoryName = s.categoryName,
                            monthlyData  = s.monthlyData.map { d ->
                                MonthlyCategoryDataDto(
                                    monthYear        = d.monthYear,
                                    totalIncome      = d.totalIncome.toDouble(),
                                    totalExpenses    = d.totalExpenses.toDouble(),
                                    transactionCount = d.transactionCount
                                )
                            }
                        )
                    },
                    filters = AppliedFiltersDto(
                        startDate  = filter.startDate.toString(),
                        endDate    = filter.endDate.toString(),
                        categories = filter.categoryIds
                    )
                )
            )
        } catch (e: DateTimeParseException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_DATE", "Date must be ISO-8601 (YYYY-MM-DD): ${e.parsedString}")
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_PARAMETER", e.message ?: "Invalid request parameter")
            )
        } catch (e: Exception) {
            call.application.environment.log.error("Monthly summary query failed", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")
            )
        }
    }

    // ── GET /api/v1/categories ───────────────────────────────────────────────
    get("/categories") {
        try {
            val categories = categoriesUseCase.execute()
            call.respond(
                HttpStatusCode.OK,
                CategoryListResponse(
                    categories = categories.map { CategoryDto(it.id, it.name, it.enumName) }
                )
            )
        } catch (e: Exception) {
            call.application.environment.log.error("Categories query failed", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper — parses and validates common query parameters
//
// Query params:
//   startDate  ISO-8601 date (default: 30 days ago)
//   endDate    ISO-8601 date (default: today)
//   categories comma-separated category IDs (default: all)
//   type       INCOME | EXPENSE | ALL (default: ALL)
// ─────────────────────────────────────────────────────────────────────────────
private fun parseFilter(request: ApplicationRequest): TransactionFilter {
    val today = LocalDate.now()

    val startDate = request.queryParameters["startDate"]
        ?.let { LocalDate.parse(it) }
        ?: today.minusDays(30)

    val endDate = request.queryParameters["endDate"]
        ?.let { LocalDate.parse(it) }
        ?: today

    require(startDate <= endDate) { "startDate must not be after endDate" }

    val categoryIds = request.queryParameters["categories"]
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?.map {
            it.trim().toLongOrNull()
                ?: throw IllegalArgumentException("Invalid category ID: '$it' — must be a number")
        }
        ?: emptyList()

    val type = when (request.queryParameters["type"]?.uppercase()) {
        "INCOME"      -> TransactionType.INCOME
        "EXPENSE"     -> TransactionType.EXPENSE
        null, "ALL"   -> TransactionType.ALL
        else          -> throw IllegalArgumentException(
            "Invalid type '${request.queryParameters["type"]}'. Must be INCOME, EXPENSE, or ALL"
        )
    }

    return TransactionFilter(startDate, endDate, categoryIds, type)
}
