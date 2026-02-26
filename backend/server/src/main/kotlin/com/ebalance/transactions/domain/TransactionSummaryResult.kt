package com.ebalance.transactions.domain

import java.math.BigDecimal

/** Aggregated spending/income figures for a single category. */
data class CategorySummary(
    val categoryId: Long,
    val categoryName: String,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val transactionCount: Int
)

/** Full summary result returned by the use case. */
data class TransactionSummaryResult(
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val netBalance: BigDecimal,
    val transactionCount: Int,
    val categories: List<CategorySummary>
)
