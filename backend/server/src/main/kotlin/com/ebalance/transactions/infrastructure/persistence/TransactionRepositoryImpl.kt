package com.ebalance.transactions.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.transactions.domain.*
import java.math.BigDecimal
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.time.LocalDate

/**
 * SQLite adapter implementing [TransactionRepository].
 *
 * All filtering is pushed down to the database via parameterised SQL — no in-memory filtering.
 * A new connection is opened per call (acceptable for a local SQLite file with low concurrency).
 *
 * Schema reference:
 *   transactions(id, operated_at TEXT, description TEXT, value REAL, balance REAL, category_id INT)
 *   category(id INT, name TEXT, enum_name TEXT)
 */
class TransactionRepositoryImpl(private val dbPath: String) : TransactionRepository {

    private fun connection() = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    // ─────────────────────────────────────────────────────────────────────────
    // getSummary — groups by category, aggregates income/expense totals
    // ─────────────────────────────────────────────────────────────────────────
    override fun getSummary(filter: TransactionFilter): Either<TransactionError.DatabaseError, TransactionSummaryResult> =
        runCatching {
            val (sql, binder) = buildSummaryQuery(filter)

            connection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    binder(stmt)

                    val categories = mutableListOf<CategorySummary>()
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            categories += CategorySummary(
                                categoryId       = rs.getLong("category_id"),
                                categoryName     = rs.getString("category_name"),
                                totalIncome      = rs.getDouble("total_income").toBigDecimal(),
                                totalExpenses    = rs.getDouble("total_expenses").toBigDecimal(),
                                transactionCount = rs.getInt("transaction_count")
                            )
                        }
                    }

                    val totalIncome   = categories.fold(BigDecimal.ZERO) { s, c -> s + c.totalIncome }
                    val totalExpenses = categories.fold(BigDecimal.ZERO) { s, c -> s + c.totalExpenses }

                    TransactionSummaryResult(
                        totalIncome      = totalIncome,
                        totalExpenses    = totalExpenses,
                        netBalance       = totalIncome - totalExpenses,
                        transactionCount = categories.sumOf { it.transactionCount },
                        categories       = categories
                    )
                }
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to load summary", e).left() }
        )

    // ─────────────────────────────────────────────────────────────────────────
    // getTransactions — returns a page of individual rows, newest first.
    // Two queries are issued inside a single connection: COUNT(*) then the
    // paginated SELECT, avoiding an extra round-trip to the DB file.
    // ─────────────────────────────────────────────────────────────────────────
    override fun getTransactions(filter: TransactionFilter): Either<TransactionError.DatabaseError, TransactionPage> =
        runCatching {
            val page     = filter.page.coerceAtLeast(1)
            val pageSize = filter.pageSize.coerceIn(1, 200)
            val offset   = (page - 1) * pageSize

            val (countSql, binder) = buildTransactionsCountQuery(filter)
            val (rowsSql,  _)      = buildTransactionsQuery(filter)

            connection().use { conn ->
                // 1. Total count (same WHERE clause, no ORDER/LIMIT)
                val total = conn.prepareStatement(countSql).use { stmt ->
                    binder(stmt)
                    stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }

                // 2. Paginated rows
                val pagedSql = "$rowsSql LIMIT ? OFFSET ?"
                val rows = conn.prepareStatement(pagedSql).use { stmt ->
                    binder(stmt)
                    // binder fills 2 + |categoryIds| params; LIMIT/OFFSET come after
                    var idx = 2 + filter.categoryIds.size + 1
                    stmt.setInt(idx++, pageSize)
                    stmt.setInt(idx,   offset)

                    val list = mutableListOf<TransactionRow>()
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            list += TransactionRow(
                                id           = rs.getLong("id"),
                                operatedAt   = LocalDate.parse(rs.getString("operated_at")),
                                description  = rs.getString("description"),
                                value        = rs.getDouble("value").toBigDecimal(),
                                balance      = rs.getDouble("balance").toBigDecimal(),
                                categoryId   = rs.getLong("category_id"),
                                categoryName = rs.getString("category_name")
                            )
                        }
                    }
                    list
                }

                val totalPages = if (pageSize > 0) Math.ceil(total.toDouble() / pageSize).toInt().coerceAtLeast(1) else 1
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
            connection().use { conn ->
                conn.prepareStatement("SELECT id, name, enum_name FROM category ORDER BY name").use { stmt ->
                    val list = mutableListOf<CategoryEntry>()
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            list += CategoryEntry(
                                id       = rs.getLong("id"),
                                name     = rs.getString("name"),
                                enumName = rs.getString("enum_name")
                            )
                        }
                    }
                    list
                }
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to load categories", e).left() }
        )

    // ─────────────────────────────────────────────────────────────────────────
    // SQL builders — return (sql, paramBinder) pairs
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSummaryQuery(
        filter: TransactionFilter
    ): Pair<String, (PreparedStatement) -> Unit> {

        val categoryClause = categoryInClause(filter.categoryIds)
        val typeClause     = typeWhereClause(filter.type)

        val sql = """
            SELECT
                t.category_id,
                COALESCE(c.name, 'Desconhecida')                             AS category_name,
                SUM(CASE WHEN t.value > 0 THEN t.value       ELSE 0 END)    AS total_income,
                SUM(CASE WHEN t.value < 0 THEN ABS(t.value)  ELSE 0 END)    AS total_expenses,
                COUNT(*)                                                      AS transaction_count
            FROM transactions t
            LEFT JOIN category c ON t.category_id = c.id
            WHERE t.operated_at >= ?
              AND t.operated_at <= ?
              $categoryClause
              $typeClause
            GROUP BY t.category_id, category_name
            ORDER BY total_expenses DESC
        """.trimIndent()

        val binder: (PreparedStatement) -> Unit = { stmt ->
            var idx = 1
            stmt.setString(idx++, filter.startDate.toString())
            stmt.setString(idx++, filter.endDate.toString())
            filter.categoryIds.forEach { stmt.setLong(idx++, it) }
        }

        return sql to binder
    }

    private fun buildTransactionsQuery(
        filter: TransactionFilter
    ): Pair<String, (PreparedStatement) -> Unit> {

        val categoryClause = categoryInClause(filter.categoryIds)
        val typeClause     = typeWhereClause(filter.type)

        val sql = """
            SELECT
                t.id,
                t.operated_at,
                t.description,
                t.value,
                t.balance,
                t.category_id,
                COALESCE(c.name, 'Desconhecida') AS category_name
            FROM transactions t
            LEFT JOIN category c ON t.category_id = c.id
            WHERE t.operated_at >= ?
              AND t.operated_at <= ?
              $categoryClause
              $typeClause
            ORDER BY t.operated_at DESC, t.id DESC
        """.trimIndent()

        val binder: (PreparedStatement) -> Unit = { stmt ->
            var idx = 1
            stmt.setString(idx++, filter.startDate.toString())
            stmt.setString(idx++, filter.endDate.toString())
            filter.categoryIds.forEach { stmt.setLong(idx++, it) }
        }

        return sql to binder
    }

    // Same WHERE as buildTransactionsQuery but selects COUNT(*) only (no ORDER BY)
    private fun buildTransactionsCountQuery(
        filter: TransactionFilter
    ): Pair<String, (PreparedStatement) -> Unit> {

        val categoryClause = categoryInClause(filter.categoryIds)
        val typeClause     = typeWhereClause(filter.type)

        val sql = """
            SELECT COUNT(*)
            FROM transactions t
            WHERE t.operated_at >= ?
              AND t.operated_at <= ?
              $categoryClause
              $typeClause
        """.trimIndent()

        val binder: (PreparedStatement) -> Unit = { stmt ->
            var idx = 1
            stmt.setString(idx++, filter.startDate.toString())
            stmt.setString(idx++, filter.endDate.toString())
            filter.categoryIds.forEach { stmt.setLong(idx++, it) }
        }

        return sql to binder
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMonthlySummary — groups by YYYY-MM × category, zero-fills missing months
    // ─────────────────────────────────────────────────────────────────────────
    override fun getMonthlySummary(filter: TransactionFilter): Either<TransactionError.DatabaseError, MonthlySummaryResult> =
        runCatching {
            val categoryClause = categoryInClause(filter.categoryIds)
            val typeClause     = typeWhereClause(filter.type)

            val sql = """
                SELECT
                    strftime('%Y-%m', t.operated_at)                             AS month_year,
                    t.category_id,
                    COALESCE(c.name, 'Desconhecida')                             AS category_name,
                    SUM(CASE WHEN t.value > 0 THEN t.value       ELSE 0 END)    AS total_income,
                    SUM(CASE WHEN t.value < 0 THEN ABS(t.value)  ELSE 0 END)    AS total_expenses,
                    COUNT(*)                                                      AS transaction_count
                FROM transactions t
                LEFT JOIN category c ON t.category_id = c.id
                WHERE t.operated_at >= ?
                  AND t.operated_at <= ?
                  $categoryClause
                  $typeClause
                GROUP BY month_year, t.category_id, category_name
                ORDER BY month_year ASC, total_expenses DESC
            """.trimIndent()

            // Raw rows from SQL: (monthYear, categoryId, categoryName, income, expenses, count)
            data class RawRow(
                val monthYear: String,
                val categoryId: Long,
                val categoryName: String,
                val totalIncome: BigDecimal,
                val totalExpenses: BigDecimal,
                val transactionCount: Int
            )

            val rawRows = mutableListOf<RawRow>()

            connection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    var idx = 1
                    stmt.setString(idx++, filter.startDate.toString())
                    stmt.setString(idx++, filter.endDate.toString())
                    filter.categoryIds.forEach { stmt.setLong(idx++, it) }

                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            rawRows += RawRow(
                                monthYear        = rs.getString("month_year"),
                                categoryId       = rs.getLong("category_id"),
                                categoryName     = rs.getString("category_name"),
                                totalIncome      = rs.getDouble("total_income").toBigDecimal(),
                                totalExpenses    = rs.getDouble("total_expenses").toBigDecimal(),
                                transactionCount = rs.getInt("transaction_count")
                            )
                        }
                    }
                }
            }

            // Collect all months present in the result, sorted chronologically
            val allMonths = rawRows.map { it.monthYear }.distinct().sorted()

            // Group rows by category; zero-fill months with no data for that category
            val series = rawRows
                .groupBy { it.categoryId to it.categoryName }
                .map { (key, rows) ->
                    val (catId, catName) = key
                    val byMonth = rows.associateBy { it.monthYear }

                    MonthlyCategorySeries(
                        categoryId   = catId,
                        categoryName = catName,
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
                // Sort by sum of expenses across all months, descending
                .sortedByDescending { s -> s.monthlyData.sumOf { it.totalExpenses } }

            MonthlySummaryResult(months = allMonths, series = series)
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to load monthly summary", e).left() }
        )

    // ─────────────────────────────────────────────────────────────────────────
    // updateTransactionCategory — validates both IDs then runs UPDATE
    // ─────────────────────────────────────────────────────────────────────────
    override fun updateTransactionCategory(transactionId: Long, categoryId: Long): Either<TransactionError, Unit> {
        // Validate transaction exists
        val txCheck = runCatching {
            connection().use { conn ->
                conn.prepareStatement("SELECT 1 FROM transactions WHERE id = ?").use { stmt ->
                    stmt.setLong(1, transactionId)
                    stmt.executeQuery().use { rs -> rs.next() }
                }
            }
        }.fold(
            onSuccess = { found ->
                if (found) null
                else TransactionError.NotFound("Transaction $transactionId not found").left()
            },
            onFailure = { e -> TransactionError.DatabaseError("Failed to check transaction", e).left() }
        )
        if (txCheck != null) return txCheck

        // Validate category exists
        val catCheck = runCatching {
            connection().use { conn ->
                conn.prepareStatement("SELECT 1 FROM category WHERE id = ?").use { stmt ->
                    stmt.setLong(1, categoryId)
                    stmt.executeQuery().use { rs -> rs.next() }
                }
            }
        }.fold(
            onSuccess = { found ->
                if (found) null
                else TransactionError.NotFound("Category $categoryId not found").left()
            },
            onFailure = { e -> TransactionError.DatabaseError("Failed to check category", e).left() }
        )
        if (catCheck != null) return catCheck

        // Apply the update
        return runCatching {
            connection().use { conn ->
                conn.prepareStatement("UPDATE transactions SET category_id = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, categoryId)
                    stmt.setLong(2, transactionId)
                    stmt.executeUpdate()
                }
            }
        }.fold(
            onSuccess = { Unit.right() },
            onFailure = { e -> TransactionError.DatabaseError("Failed to update transaction category", e).left() }
        )
    }

    // Builds "AND t.category_id IN (?,?,?)" or empty string
    private fun categoryInClause(ids: List<Long>): String =
        if (ids.isEmpty()) ""
        else "AND t.category_id IN (${ids.joinToString(",") { "?" }})"

    // Appends sign constraint for INCOME/EXPENSE filtering
    private fun typeWhereClause(type: TransactionType): String = when (type) {
        TransactionType.INCOME  -> "AND t.value > 0"
        TransactionType.EXPENSE -> "AND t.value < 0"
        TransactionType.ALL     -> ""
    }
}
