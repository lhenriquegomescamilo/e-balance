package com.ebalance.transactions.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.transactions.domain.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.ceil

class TransactionRepositoryImpl(private val database: Database) : TransactionRepository {

    private fun absOf(expr: Column<Double>): Expression<Double> =
        CustomFunction("ABS", DoubleColumnType(), expr)

    private fun applyFilters(query: Query, filter: TransactionFilter): Query {
        var q = query
        if (filter.categoryIds.isNotEmpty()) {
            q = q.andWhere { TransactionsTable.categoryId inList filter.categoryIds }
        }
        when (filter.type) {
            TransactionType.INCOME  -> q = q.andWhere { TransactionsTable.value greater 0.0 }
            TransactionType.EXPENSE -> q = q.andWhere { TransactionsTable.value less 0.0 }
            TransactionType.ALL     -> {}
        }
        return q
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getSummary — groups by category, aggregates income/expense totals
    // ─────────────────────────────────────────────────────────────────────────
    override fun getSummary(filter: TransactionFilter): Either<TransactionError.DatabaseError, TransactionSummaryResult> =
        runCatching {
            transaction(database) {
                val catName = Coalesce(CategoryTable.name, stringParam("Desconhecida"))
                val totalIncome = Sum(
                    Case()
                        .When(Op.build { TransactionsTable.value greater 0.0 }, TransactionsTable.value)
                        .Else(doubleParam(0.0)),
                    DoubleColumnType()
                )
                val totalExpenses = Sum(
                    Case()
                        .When(Op.build { TransactionsTable.value less 0.0 }, absOf(TransactionsTable.value))
                        .Else(doubleParam(0.0)),
                    DoubleColumnType()
                )
                val txCount = TransactionsTable.id.count()

                val baseQuery = TransactionsTable
                    .join(CategoryTable, JoinType.LEFT, TransactionsTable.categoryId, CategoryTable.id)
                    .select(TransactionsTable.categoryId, catName, totalIncome, totalExpenses, txCount)
                    .where {
                        (TransactionsTable.operatedAt greaterEq filter.startDate.toString()) and
                        (TransactionsTable.operatedAt lessEq filter.endDate.toString())
                    }

                val categories = applyFilters(baseQuery, filter)
                    .groupBy(TransactionsTable.categoryId, CategoryTable.name)
                    .orderBy(totalExpenses to SortOrder.DESC)
                    .map { row ->
                        CategorySummary(
                            categoryId       = row[TransactionsTable.categoryId],
                            categoryName     = row[catName],
                            totalIncome      = (row[totalIncome] ?: 0.0).toBigDecimal(),
                            totalExpenses    = (row[totalExpenses] ?: 0.0).toBigDecimal(),
                            transactionCount = row[txCount].toInt()
                        )
                    }

                val totalIncomeBD   = categories.fold(BigDecimal.ZERO) { s, c -> s + c.totalIncome }
                val totalExpensesBD = categories.fold(BigDecimal.ZERO) { s, c -> s + c.totalExpenses }

                TransactionSummaryResult(
                    totalIncome      = totalIncomeBD,
                    totalExpenses    = totalExpensesBD,
                    netBalance       = totalIncomeBD - totalExpensesBD,
                    transactionCount = categories.sumOf { it.transactionCount },
                    categories       = categories
                )
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to load summary", e).left() }
        )

    // ─────────────────────────────────────────────────────────────────────────
    // getTransactions — returns a page of individual rows, newest first
    // ─────────────────────────────────────────────────────────────────────────
    override fun getTransactions(filter: TransactionFilter): Either<TransactionError.DatabaseError, TransactionPage> =
        runCatching {
            val page     = filter.page.coerceAtLeast(1)
            val pageSize = filter.pageSize.coerceIn(1, 200)
            val offset   = (page - 1) * pageSize

            transaction(database) {
                val catName = Coalesce(CategoryTable.name, stringParam("Desconhecida"))

                fun buildQuery() = applyFilters(
                    TransactionsTable
                        .join(CategoryTable, JoinType.LEFT, TransactionsTable.categoryId, CategoryTable.id)
                        .select(
                            TransactionsTable.id, TransactionsTable.operatedAt, TransactionsTable.description,
                            TransactionsTable.value, TransactionsTable.balance, TransactionsTable.categoryId, catName
                        )
                        .where {
                            (TransactionsTable.operatedAt greaterEq filter.startDate.toString()) and
                            (TransactionsTable.operatedAt lessEq filter.endDate.toString())
                        },
                    filter
                )

                val total = buildQuery().count().toInt()
                val rows  = buildQuery()
                    .orderBy(TransactionsTable.operatedAt to SortOrder.DESC, TransactionsTable.id to SortOrder.DESC)
                    .limit(pageSize, offset.toLong())
                    .map { row ->
                        TransactionRow(
                            id           = row[TransactionsTable.id],
                            operatedAt   = LocalDate.parse(row[TransactionsTable.operatedAt]),
                            description  = row[TransactionsTable.description],
                            value        = row[TransactionsTable.value].toBigDecimal(),
                            balance      = row[TransactionsTable.balance].toBigDecimal(),
                            categoryId   = row[TransactionsTable.categoryId],
                            categoryName = row[catName]
                        )
                    }

                val totalPages = ceil(total.toDouble() / pageSize).toInt().coerceAtLeast(1)
                TransactionPage(rows = rows, total = total, page = page, pageSize = pageSize, totalPages = totalPages)
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to load transactions", e).left() }
        )

    // ─────────────────────────────────────────────────────────────────────────
    // getCategories — full list for dropdown population
    // ─────────────────────────────────────────────────────────────────────────
    override fun getCategories(): Either<TransactionError.DatabaseError, List<CategoryEntry>> =
        runCatching {
            transaction(database) {
                CategoryTable.selectAll()
                    .orderBy(CategoryTable.name to SortOrder.ASC)
                    .map { row ->
                        CategoryEntry(
                            id       = row[CategoryTable.id],
                            name     = row[CategoryTable.name],
                            enumName = row[CategoryTable.enumName]
                        )
                    }
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to load categories", e).left() }
        )

    // ─────────────────────────────────────────────────────────────────────────
    // getMonthlySummary — groups by YYYY-MM × category, zero-fills missing months
    // ─────────────────────────────────────────────────────────────────────────
    override fun getMonthlySummary(filter: TransactionFilter): Either<TransactionError.DatabaseError, MonthlySummaryResult> =
        runCatching {
            transaction(database) {
                val catName      = Coalesce(CategoryTable.name, stringParam("Desconhecida"))
                val monthYearExpr = ToCharDate(TransactionsTable.operatedAt, "YYYY-MM")
                val totalIncome  = Sum(
                    Case()
                        .When(Op.build { TransactionsTable.value greater 0.0 }, TransactionsTable.value)
                        .Else(doubleParam(0.0)),
                    DoubleColumnType()
                )
                val totalExpenses = Sum(
                    Case()
                        .When(Op.build { TransactionsTable.value less 0.0 }, absOf(TransactionsTable.value))
                        .Else(doubleParam(0.0)),
                    DoubleColumnType()
                )
                val txCount = TransactionsTable.id.count()

                val baseQuery = TransactionsTable
                    .join(CategoryTable, JoinType.LEFT, TransactionsTable.categoryId, CategoryTable.id)
                    .select(monthYearExpr, TransactionsTable.categoryId, catName, totalIncome, totalExpenses, txCount)
                    .where {
                        (TransactionsTable.operatedAt greaterEq filter.startDate.toString()) and
                        (TransactionsTable.operatedAt lessEq filter.endDate.toString())
                    }

                data class RawRow(
                    val monthYear: String,
                    val categoryId: Long,
                    val categoryName: String,
                    val totalIncome: BigDecimal,
                    val totalExpenses: BigDecimal,
                    val transactionCount: Int
                )

                val rawRows = applyFilters(baseQuery, filter)
                    .groupBy(monthYearExpr, TransactionsTable.categoryId, CategoryTable.name)
                    .orderBy(monthYearExpr to SortOrder.ASC, totalExpenses to SortOrder.DESC)
                    .map { row ->
                        RawRow(
                            monthYear        = row[monthYearExpr],
                            categoryId       = row[TransactionsTable.categoryId],
                            categoryName     = row[catName],
                            totalIncome      = (row[totalIncome] ?: 0.0).toBigDecimal(),
                            totalExpenses    = (row[totalExpenses] ?: 0.0).toBigDecimal(),
                            transactionCount = row[txCount].toInt()
                        )
                    }

                val allMonths = rawRows.map { it.monthYear }.distinct().sorted()

                val series = rawRows
                    .groupBy { it.categoryId to it.categoryName }
                    .map { (key, rows) ->
                        val (catId, catName2) = key
                        val byMonth = rows.associateBy { it.monthYear }
                        MonthlyCategorySeries(
                            categoryId   = catId,
                            categoryName = catName2,
                            monthlyData  = allMonths.map { month ->
                                val row = byMonth[month]
                                MonthlyCategoryData(
                                    monthYear        = month,
                                    totalIncome      = row?.totalIncome      ?: BigDecimal.ZERO,
                                    totalExpenses    = row?.totalExpenses    ?: BigDecimal.ZERO,
                                    transactionCount = row?.transactionCount ?: 0
                                )
                            }
                        )
                    }
                    .sortedByDescending { s -> s.monthlyData.sumOf { it.totalExpenses } }

                MonthlySummaryResult(months = allMonths, series = series)
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to load monthly summary", e).left() }
        )

    // ─────────────────────────────────────────────────────────────────────────
    // updateTransactionCategory — validates both IDs then runs UPDATE
    // ─────────────────────────────────────────────────────────────────────────
    override fun updateTransactionCategory(transactionId: Long, categoryId: Long): Either<TransactionError, Unit> {
        val txCheck = runCatching {
            transaction(database) {
                TransactionsTable.selectAll()
                    .where { TransactionsTable.id eq transactionId }
                    .count() > 0
            }
        }.fold(
            onSuccess = { found ->
                if (found) null
                else TransactionError.NotFound("Transaction $transactionId not found").left()
            },
            onFailure = { e -> TransactionError.DatabaseError("Failed to check transaction", e).left() }
        )
        if (txCheck != null) return txCheck

        val catCheck = runCatching {
            transaction(database) {
                CategoryTable.selectAll()
                    .where { CategoryTable.id eq categoryId }
                    .count() > 0
            }
        }.fold(
            onSuccess = { found ->
                if (found) null
                else TransactionError.NotFound("Category $categoryId not found").left()
            },
            onFailure = { e -> TransactionError.DatabaseError("Failed to check category", e).left() }
        )
        if (catCheck != null) return catCheck

        return runCatching {
            transaction(database) {
                TransactionsTable.update({ TransactionsTable.id eq transactionId }) {
                    it[TransactionsTable.categoryId] = categoryId
                }
            }
        }.fold(
            onSuccess = { Unit.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to update transaction category", e).left() }
        )
    }
}
