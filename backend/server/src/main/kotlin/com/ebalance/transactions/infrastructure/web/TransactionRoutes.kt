package com.ebalance.transactions.infrastructure.web

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.ebalance.transactions.application.GetCategoriesUseCase
import com.ebalance.transactions.application.GetMonthlySummaryUseCase
import com.ebalance.transactions.application.GetTransactionSummaryUseCase
import com.ebalance.transactions.application.GetTransactionsUseCase
import com.ebalance.transactions.application.UpdateTransactionCategoryUseCase
import com.ebalance.transactions.domain.TransactionError
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionType
import com.ebalance.transactions.infrastructure.web.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Registers all transaction-related REST endpoints under the calling [Route].
 *
 * Endpoints:
 *   GET   /transactions/summary               — aggregated stats + category breakdown (Donut chart)
 *   GET   /transactions/monthly-by-category   — monthly trend per category (area/bar chart)
 *   GET   /transactions                       — paginated list of individual transactions (table)
 *   PATCH /transactions/{id}/category         — update the category of a single transaction
 *   GET   /categories                         — all categories (filter dropdown)
 */
fun Route.transactionRoutes(
    summaryUseCase: GetTransactionSummaryUseCase,
    transactionsUseCase: GetTransactionsUseCase,
    categoriesUseCase: GetCategoriesUseCase,
    monthlySummaryUseCase: GetMonthlySummaryUseCase,
    updateCategoryUseCase: UpdateTransactionCategoryUseCase
) {

    // ── GET /api/v1/transactions/summary ─────────────────────────────────────
    get("/transactions/summary") {
        call.application.environment.log.info("GET /transactions/summary params=${call.request.queryString()}")
        either {
            val filter = parseFilter(call.request).bind()
            filter to summaryUseCase.execute(filter).bind()
        }.fold(
            ifLeft  = { call.respondError(it) },
            ifRight = { (filter, result) ->
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
            }
        )
    }

    // ── GET /api/v1/transactions ─────────────────────────────────────────────
    get("/transactions") {
        call.application.environment.log.info("GET /transactions params=${call.request.queryString()}")
        either {
            val filter = parseFilter(call.request).bind()
            transactionsUseCase.execute(filter).bind()
        }.fold(
            ifLeft  = { call.respondError(it) },
            ifRight = { page ->
                call.respond(
                    HttpStatusCode.OK,
                    TransactionListResponse(
                        transactions = page.rows.map { tx ->
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
                        total      = page.total,
                        page       = page.page,
                        pageSize   = page.pageSize,
                        totalPages = page.totalPages
                    )
                )
            }
        )
    }

    // ── GET /api/v1/transactions/monthly-by-category ─────────────────────────
    //
    // Returns transactions grouped by category AND calendar month.
    // Both totalIncome and totalExpenses are always returned so the frontend
    // can toggle the view without an extra round-trip.
    // Missing months for a given category are zero-filled.
    get("/transactions/monthly-by-category") {
        call.application.environment.log.info("GET /transactions/monthly-by-category params=${call.request.queryString()}")
        either {
            val filter = parseFilter(call.request).bind()
            filter to monthlySummaryUseCase.execute(filter).bind()
        }.fold(
            ifLeft  = { call.respondError(it) },
            ifRight = { (filter, result) ->
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
            }
        )
    }

    // ── PATCH /api/v1/transactions/{id}/category ─────────────────────────────
    patch("/transactions/{id}/category") {
        call.application.environment.log.info("PATCH /transactions/${call.parameters["id"]}/category")
        either {
            val transactionId = call.parameters["id"]?.toLongOrNull()
                ?: raise(TransactionError.InvalidParameter("Transaction ID must be a number"))
            val body = call.receive<UpdateCategoryRequest>()
            updateCategoryUseCase.execute(transactionId, body.categoryId).bind()
            transactionId to body.categoryId
        }.fold(
            ifLeft  = { call.respondError(it) },
            ifRight = { (transactionId, categoryId) ->
                call.respond(
                    HttpStatusCode.OK,
                    UpdateCategoryResponse(
                        transactionId = transactionId,
                        categoryId    = categoryId,
                        message       = "Category updated successfully"
                    )
                )
            }
        )
    }

    // ── GET /api/v1/categories ───────────────────────────────────────────────
    // Optional query param: ids=1,2,3  — returns only the matching categories.
    // Omitting ids (or ids=) returns all categories.
    get("/categories") {
        call.application.environment.log.info("GET /categories")
        either {
            val ids = call.request.queryParameters["ids"]
                ?.split(",")?.filter { it.isNotBlank() }
                ?.map { it.trim().toLongOrNull()
                    ?: raise(TransactionError.InvalidParameter("Invalid category ID: '$it' — must be a number")) }
                ?: emptyList()
            categoriesUseCase.execute(ids).bind()
        }.fold(
            ifLeft  = { call.respondError(it) },
            ifRight = { categories ->
                call.respond(
                    HttpStatusCode.OK,
                    CategoryListResponse(
                        categories = categories.map { CategoryDto(it.id, it.name, it.enumName) }
                    )
                )
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper — maps a TransactionError to the appropriate HTTP response (DRY)
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun ApplicationCall.respondError(error: TransactionError) = when (error) {
    is TransactionError.InvalidDate      -> {
        application.environment.log.warn("Transaction invalid date: ${error.message}")
        respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_DATE", error.message))
    }
    is TransactionError.InvalidParameter -> {
        application.environment.log.warn("Transaction invalid parameter: ${error.message}")
        respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_PARAMETER", error.message))
    }
    is TransactionError.NotFound         -> {
        application.environment.log.warn("Transaction not found: ${error.message}")
        respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", error.message))
    }
    is TransactionError.DatabaseError    -> {
        application.environment.log.error("Transaction database error: ${error.message}", error.cause)
        respond(HttpStatusCode.InternalServerError,
            ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
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
//   page       1-based page number (default: 1)
//   pageSize   rows per page 1-200 (default: 20)
// ─────────────────────────────────────────────────────────────────────────────
private fun parseFilter(request: ApplicationRequest): Either<TransactionError, TransactionFilter> =
    runCatching {
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

        val page     = request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val pageSize = request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 200) ?: 20

        TransactionFilter(startDate, endDate, categoryIds, type, page, pageSize)
    }.fold(
        onSuccess = { it.right() },
        onFailure = { e -> when (e) {
            is DateTimeParseException   -> TransactionError.InvalidDate(
                e.parsedString ?: "", "Date must be ISO-8601 (YYYY-MM-DD): ${e.parsedString}"
            ).left()
            is IllegalArgumentException -> TransactionError.InvalidParameter(
                e.message ?: "Invalid request parameter"
            ).left()
            else -> TransactionError.DatabaseError("Error parsing filter", e).left()
        }}
    )
