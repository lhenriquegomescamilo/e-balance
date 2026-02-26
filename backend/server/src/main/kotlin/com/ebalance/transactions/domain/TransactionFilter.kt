package com.ebalance.transactions.domain

import java.time.LocalDate

/**
 * Value object representing filter criteria for transaction queries.
 * [categoryIds] empty = all categories.
 */
data class TransactionFilter(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val categoryIds: List<Long> = emptyList(),
    val type: TransactionType = TransactionType.ALL
)

enum class TransactionType { ALL, INCOME, EXPENSE }
