package com.ebalance.transactions.domain

import java.math.BigDecimal

/** Aggregated figures for one category in one calendar month. */
data class MonthlyCategoryData(
    val monthYear: String,           // "YYYY-MM", e.g. "2026-02"
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val transactionCount: Int
)

/** All monthly data points for a single category, zero-filled for months with no activity. */
data class MonthlyCategorySeries(
    val categoryId: Long,
    val categoryName: String,
    val monthlyData: List<MonthlyCategoryData>   // one entry per month in MonthlySummaryResult.months
)

/**
 * Full result for the monthly-by-category query.
 *
 * [months]  — sorted list of all "YYYY-MM" strings present in the result window.
 * [series]  — one series per category, sorted by total expenses descending.
 *             Each series has exactly [months].size data points (zero-filled).
 */
data class MonthlySummaryResult(
    val months: List<String>,
    val series: List<MonthlyCategorySeries>
)
