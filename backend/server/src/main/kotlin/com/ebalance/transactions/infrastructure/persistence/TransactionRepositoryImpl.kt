package com.ebalance.transactions.infrastructure.persistence

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
    override fun getSummary(filter: TransactionFilter): TransactionSummaryResult {
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

                return TransactionSummaryResult(
                    totalIncome      = totalIncome,
                    totalExpenses    = totalExpenses,
                    netBalance       = totalIncome - totalExpenses,
                    transactionCount = categories.sumOf { it.transactionCount },
                    categories       = categories
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getTransactions — returns individual rows, newest first
    // ─────────────────────────────────────────────────────────────────────────
    override fun getTransactions(filter: TransactionFilter): List<TransactionRow> {
        val (sql, binder) = buildTransactionsQuery(filter)

        connection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                binder(stmt)

                val rows = mutableListOf<TransactionRow>()
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rows += TransactionRow(
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
                return rows
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCategories — full list for dropdown population
    // ─────────────────────────────────────────────────────────────────────────
    override fun getCategories(): List<CategoryEntry> {
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
                return list
            }
        }
    }

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

    // ─────────────────────────────────────────────────────────────────────────
    // getMonthlySummary — groups by YYYY-MM × category, zero-fills missing months
    // ─────────────────────────────────────────────────────────────────────────
    override fun getMonthlySummary(filter: TransactionFilter): MonthlySummaryResult {
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

        return MonthlySummaryResult(months = allMonths, series = series)
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
